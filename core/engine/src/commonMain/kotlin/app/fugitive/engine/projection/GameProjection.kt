package app.fugitive.engine.projection

import app.fugitive.engine.GameEngine
import app.fugitive.engine.ability.Ability
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.rules.CatchCheck
import app.fugitive.engine.rules.CatchRules
import app.fugitive.engine.rules.DataKeys
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.engine.visibility.VisibilityDecision
import app.fugitive.engine.visibility.VisibilityKind
import app.fugitive.engine.visibility.VisibilityRules
import app.fugitive.engine.zone.ZoneMath
import app.fugitive.engine.zone.ZoneSnapshot
import app.fugitive.model.GameState
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.Member
import app.fugitive.model.Objective
import app.fugitive.model.PendingCatch
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import app.fugitive.model.long

/** One player as the local viewer may see them right now. */
data class ProjectedPlayer(
    val state: PlayerState,
    val member: Member?,
    val roleLabel: String,
    val visibility: VisibilityDecision,
    /** Position to draw (live or snapshot), or null when hidden/unknown. */
    val fix: LocationFix?,
    val ageMs: Long?,
    val isStale: Boolean,
    val distanceToMe: Double?,
    val bearingFromMe: Double?,
    val isMe: Boolean,
    val online: Boolean,
) {
    val id: PlayerId get() = state.id
}

data class ZoneStatus(
    val inside: Boolean,
    /** Positive = metres inside the edge; negative = metres outside. */
    val marginMeters: Double,
    val outsideSince: Long?,
    val graceLeftMs: Long?,
    val applies: Boolean,
)

data class AbilityState(
    val ability: Ability,
    val usesLeft: Int,
    val readyAt: Long,
    val activeUntil: Long?,
    val blockedReason: String?,
)

data class CatchOption(val target: ProjectedPlayer, val check: CatchCheck)

/** Everything the game screen (and notification) needs, computed for the local player. */
data class GameProjection(
    val lobby: LobbySnapshot,
    val game: GameState,
    val settings: Settings,
    val now: Long,
    val stage: GameStage,
    val me: PlayerState?,
    val myFix: LocationFix?,
    val myRoleLabel: String,
    val objective: String,
    val players: List<ProjectedPlayer>,
    val zone: ZoneSnapshot?,
    val zoneStatus: ZoneStatus?,
    val threats: List<VisibilityRules.Threat>,
    val catchOptions: List<CatchOption>,
    val incomingCatch: PendingCatch?,
    val outgoingCatches: List<PendingCatch>,
    val abilities: List<AbilityState>,
    val objectives: List<Objective>,
    val timeLeftMs: Long?,
    val stageEndsInMs: Long?,
    /** Next ping time for opponents shown periodically, if any. */
    val nextPingAt: Long?,
    val activeCounts: Map<Role, Int>,
    val modeName: String,
    /** Someone in the game could catch the local player. */
    val isPrey: Boolean,
) {
    val isSpectating: Boolean get() = me == null || me.status != PlayerStatus.ACTIVE || me.role == Role.SPECTATOR
    val isOver: Boolean get() = game.result != null
}

/** Builds [GameProjection]s. Pure apart from reading [history]. */
class GameProjector(private val engine: GameEngine) {

    fun project(
        lobby: LobbySnapshot,
        locations: Map<PlayerId, LocationFix>,
        history: LocationHistory,
        selfId: PlayerId,
        now: Long,
    ): GameProjection? {
        val game = lobby.game ?: return null
        val mode = engine.mode(game.modeId)
        val settings = engine.settings(game.config)
        val stage = GameClock.stage(game, now)
        val me = game.players[selfId]
        val myFix = locations[selfId]
        val staleMs = settings[CoreSettings.STALE_AFTER_SEC] * 1000

        val players = game.players.values.map { p ->
            val isMe = p.id == selfId
            val vis = if (isMe) VisibilityDecision.LIVE else VisibilityRules.decide(engine, game, selfId, p.id, now)
            val live = locations[p.id]
            val fix = when (vis.kind) {
                VisibilityKind.LIVE -> live
                VisibilityKind.SNAPSHOT -> vis.snapshotAt?.let { at -> history.at(p.id, at) ?: live?.takeIf { it.time <= at } }
                VisibilityKind.HIDDEN -> null
            }
            val age = fix?.let { (now - it.time).coerceAtLeast(0) }
            ProjectedPlayer(
                state = p, member = lobby.members[p.id], roleLabel = mode.roleLabel(p.role), visibility = vis, fix = fix,
                ageMs = age, isStale = vis.kind == VisibilityKind.LIVE && age != null && age > staleMs,
                distanceToMe = if (!isMe && fix != null && myFix != null) Geo.distance(myFix.point, fix.point) else null,
                bearingFromMe = if (!isMe && fix != null && myFix != null) Geo.bearing(myFix.point, fix.point) else null,
                isMe = isMe, online = lobby.members[p.id]?.online ?: false,
            )
        }.sortedWith(compareBy<ProjectedPlayer>({ !it.isMe }, { it.state.status.ordinal }, { it.distanceToMe ?: Double.MAX_VALUE }))

        val zone = game.zone?.let { ZoneMath.snapshot(it, now) }
        val zoneStatus = if (zone != null && myFix != null && me != null) {
            val forgive = settings[CoreSettings.ZONE_GPS_TOLERANCE]
            val applies = me.role.id in game.zone!!.appliesTo && me.status == PlayerStatus.ACTIVE
            val grace = settings[CoreSettings.ZONE_GRACE_SEC] * 1000
            ZoneStatus(
                inside = ZoneMath.isInside(myFix, zone.current, forgive),
                marginMeters = ZoneMath.marginToEdge(myFix.point, zone.current),
                outsideSince = me.zoneExitAt,
                graceLeftMs = me.zoneExitAt?.let { (it + grace - now).coerceAtLeast(0) },
                applies = applies,
            )
        } else null

        val alertRadius = settings[CoreSettings.PROXIMITY_ALERT_M].toDouble()
        val threats = if (me != null) VisibilityRules.threats(engine, game, selfId, locations, alertRadius, now) else emptyList()

        val catchOptions = if (me != null && me.status == PlayerStatus.ACTIVE) {
            players.filter { !it.isMe && it.state.status == PlayerStatus.ACTIVE && mode.canCatch(game, me, it.state) }
                .map { CatchOption(it, CatchRules.check(engine, game, selfId, it.id, now, locations)) }
        } else emptyList()

        val abilities = if (me != null) engine.abilities.filter { it.isAvailableTo(settings, me) }.map { a ->
            val used = me.abilityUses[a.id] ?: 0
            val left = (settings[a.uses] - used).toInt().coerceAtLeast(0)
            val readyAt = me.abilityReadyAt[a.id] ?: 0
            val active = game.effects.values.filter { it.type == a.id && it.ownerId == selfId && it.isActive(now) }.maxOfOrNull { it.endsAt }
            val blocked = when {
                me.status != PlayerStatus.ACTIVE -> "Out of the game"
                stage == GameStage.COUNTDOWN -> "Not yet"
                left <= 0 -> "Used up"
                active != null -> "Active"
                now < readyAt -> "Recharging"
                else -> null
            }
            AbilityState(a, left, readyAt, active, blocked)
        } else emptyList()

        val objectives = game.objectives.values.filter { o ->
            o.isActive(now) && (o.visibleTo.isEmpty() || me == null || me.role.id in o.visibleTo || me.status != PlayerStatus.ACTIVE)
        }

        val nextPing = players.filter { it.visibility.nextSnapshotAt != null }.minOfOrNull { it.visibility.nextSnapshotAt!! }

        return GameProjection(
            lobby = lobby, game = game, settings = settings, now = now, stage = stage, me = me, myFix = myFix,
            myRoleLabel = me?.let { mode.roleLabel(it.role) } ?: "Spectator",
            objective = me?.let {
                when (it.status) {
                    PlayerStatus.ACTIVE -> mode.objectiveFor(game, it)
                    PlayerStatus.ESCAPED -> "You escaped! Watch the rest play out"
                    else -> "You're out — spectating"
                }
            } ?: "Spectating",
            players = players, zone = zone, zoneStatus = zoneStatus, threats = threats,
            catchOptions = catchOptions,
            incomingCatch = game.pendingCatches.values.firstOrNull { it.runnerId == selfId },
            outgoingCatches = game.pendingCatches.values.filter { it.hunterId == selfId },
            abilities = abilities, objectives = objectives,
            timeLeftMs = game.endsAt?.let { (it - now).coerceAtLeast(0) },
            stageEndsInMs = GameClock.untilNextBoundary(game, now),
            nextPingAt = nextPing,
            activeCounts = game.players.values.filter { it.status == PlayerStatus.ACTIVE }.groupingBy { it.role }.eachCount(),
            modeName = mode.name,
            isPrey = me != null && me.status == PlayerStatus.ACTIVE && game.players.values.any { it.id != me.id && it.status == PlayerStatus.ACTIVE && mode.canCatch(game, it, me) },
        )
    }

    /** Remaining code-entry lockout for a hunter, if any. */
    fun codeLockout(game: GameState, selfId: PlayerId, now: Long): Long? =
        game.players[selfId]?.data?.long(DataKeys.CODE_LOCKED_UNTIL)?.let { (it - now).takeIf { left -> left > 0 } }
}
