package app.fugitive.engine.visibility

import app.fugitive.engine.GameEngine
import app.fugitive.engine.ability.AbilityIds
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.rules.DataKeys
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.model.GameState
import app.fugitive.model.LocationFix
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import app.fugitive.model.bool

enum class VisibilityKind { HIDDEN, LIVE, SNAPSHOT }

/**
 * How [VisibilityContext.viewer] may see [VisibilityContext.subject].
 * For SNAPSHOT, the client shows the subject's position as of [snapshotAt].
 */
data class VisibilityDecision(
    val kind: VisibilityKind,
    val snapshotAt: Long? = null,
    val nextSnapshotAt: Long? = null,
    /** Short explanation shown in the UI, e.g. "Cloaked" or "Outside zone". */
    val reason: String? = null,
) {
    val isVisible: Boolean get() = kind != VisibilityKind.HIDDEN

    companion object {
        val LIVE = VisibilityDecision(VisibilityKind.LIVE)
        val HIDDEN = VisibilityDecision(VisibilityKind.HIDDEN)
        fun live(reason: String?) = VisibilityDecision(VisibilityKind.LIVE, reason = reason)
        fun hidden(reason: String?) = VisibilityDecision(VisibilityKind.HIDDEN, reason = reason)
    }
}

class VisibilityContext(
    val engine: GameEngine,
    val game: GameState,
    val settings: Settings,
    val viewer: PlayerState,
    val subject: PlayerState,
    val now: Long,
) {
    val stage: GameStage = GameClock.stage(game, now)
    fun effectActive(type: String, owner: (PlayerState?) -> Boolean): Boolean =
        game.effects.values.any { it.type == type && it.isActive(now) && owner(game.players[it.ownerId]) }
}

/**
 * Core visibility rules. Modes can override per pair via GameMode.visibility.
 * Rules are evaluated on each client for its own viewer.
 */
object VisibilityRules {

    fun decide(engine: GameEngine, game: GameState, viewerId: PlayerId, subjectId: PlayerId, now: Long): VisibilityDecision {
        val viewer = game.players[viewerId] ?: return spectatorView(engine, game)
        val subject = game.players[subjectId] ?: return VisibilityDecision.HIDDEN
        val ctx = VisibilityContext(engine, game, engine.settings(game.config), viewer, subject, now)
        return decide(ctx)
    }

    /** Someone in the lobby but not in the game (joined late as observer). */
    private fun spectatorView(engine: GameEngine, game: GameState): VisibilityDecision =
        if (engine.settings(game.config)[CoreSettings.SPECTATORS_SEE_ALL]) VisibilityDecision.live("Spectating") else VisibilityDecision.HIDDEN

    fun decide(ctx: VisibilityContext): VisibilityDecision {
        val s = ctx.settings
        val viewer = ctx.viewer
        val subject = ctx.subject
        if (viewer.id == subject.id) return VisibilityDecision.LIVE
        if (ctx.stage == GameStage.ENDED) return VisibilityDecision.live("Game over")

        // Spectators and players who are out.
        val viewerOut = viewer.status != PlayerStatus.ACTIVE || viewer.role == Role.SPECTATOR
        if (viewerOut) {
            return if (s[CoreSettings.SPECTATORS_SEE_ALL]) VisibilityDecision.live("Spectating") else VisibilityDecision.HIDDEN
        }
        if (subject.status != PlayerStatus.ACTIVE || subject.role == Role.SPECTATOR) return VisibilityDecision.HIDDEN

        ctx.engine.mode(ctx.game.modeId).visibility(ctx)?.let { return it }

        if (viewer.role == subject.role) {
            return if (s[CoreSettings.SHOW_TEAMMATES]) VisibilityDecision.live("Teammate") else VisibilityDecision.HIDDEN
        }

        return when {
            viewer.role == Role.HUNTER && subject.role == Role.RUNNER -> hunterSeesRunner(ctx)
            viewer.role == Role.RUNNER && subject.role == Role.HUNTER -> runnerSeesHunter(ctx)
            else -> VisibilityDecision.HIDDEN
        }
    }

    fun hunterSeesRunner(ctx: VisibilityContext): VisibilityDecision {
        val s = ctx.settings
        val subject = ctx.subject
        if (ctx.stage != GameStage.ACTIVE && !s[CoreSettings.REVEAL_DURING_HEADSTART]) return VisibilityDecision.hidden("Head start")
        if (ctx.effectActive(AbilityIds.CLOAK) { it?.id == subject.id }) return VisibilityDecision.hidden("Cloaked")
        if (subject.revealed || subject.data.bool(DataKeys.REVEAL_ZONE) == true) return VisibilityDecision.live("Outside zone")
        if (subject.data.bool(DataKeys.REVEAL_DISCONNECT) == true) return VisibilityDecision.live("Last known")
        if (ctx.effectActive(AbilityIds.RADAR_SCAN) { it?.role == ctx.viewer.role }) return VisibilityDecision.live("Radar scan")
        val endsAt = ctx.game.endsAt
        val endgame = s[CoreSettings.ENDGAME_REVEAL_MIN] * 60_000
        if (endsAt != null && endgame > 0 && ctx.now >= endsAt - endgame) return VisibilityDecision.live("Endgame")
        return byMode(
            mode = s[CoreSettings.RUNNER_REVEAL],
            intervalMs = s[CoreSettings.RUNNER_REVEAL_INTERVAL_SEC] * 1000,
            base = ctx.game.headstartEndsAt, now = ctx.now,
        )
    }

    fun runnerSeesHunter(ctx: VisibilityContext): VisibilityDecision {
        val s = ctx.settings
        if (ctx.effectActive(AbilityIds.SONAR) { it?.id == ctx.viewer.id }) return VisibilityDecision.live("Sonar")
        if (ctx.subject.revealed) return VisibilityDecision.live("Outside zone")
        return byMode(
            mode = s[CoreSettings.HUNTER_REVEAL],
            intervalMs = s[CoreSettings.HUNTER_REVEAL_INTERVAL_SEC] * 1000,
            base = ctx.game.startAt, now = ctx.now,
        )
    }

    /** Applies a "live" / "interval" / "hidden" reveal setting. */
    fun byMode(mode: String, intervalMs: Long, base: Long, now: Long): VisibilityDecision = when (mode) {
        "live" -> VisibilityDecision.LIVE
        "interval" -> {
            val (at, next) = pingWindow(base, intervalMs, now)
            if (at == null) VisibilityDecision(VisibilityKind.HIDDEN, nextSnapshotAt = next, reason = "First ping soon")
            else VisibilityDecision(VisibilityKind.SNAPSHOT, snapshotAt = at, nextSnapshotAt = next, reason = "Ping")
        }
        else -> VisibilityDecision.HIDDEN
    }

    /** Most recent ping time (or null before the first) and the next one. Pings happen at base, base+i, … */
    fun pingWindow(base: Long, intervalMs: Long, now: Long): Pair<Long?, Long> {
        val i = intervalMs.coerceAtLeast(1000)
        if (now < base) return null to base
        val k = (now - base) / i
        val at = base + k * i
        return at to at + i
    }

    /** A hunter close to a runner, for proximity alerts. */
    data class Threat(val playerId: PlayerId, val distance: Double, val bearing: Double)

    /**
     * Opponents within [radius] of [viewerId] that could catch them, nearest first.
     * Deliberately ignores visibility — the alert is a separate, coarse signal.
     */
    fun threats(
        engine: GameEngine,
        game: GameState,
        viewerId: PlayerId,
        locations: Map<PlayerId, LocationFix>,
        radius: Double,
        now: Long,
    ): List<Threat> {
        if (radius <= 0) return emptyList()
        val viewer = game.players[viewerId] ?: return emptyList()
        if (viewer.status != PlayerStatus.ACTIVE) return emptyList()
        if (GameClock.stage(game, now) != GameStage.ACTIVE) return emptyList()
        val mode = engine.mode(game.modeId)
        val me = locations[viewerId] ?: return emptyList()
        val stale = engine.settings(game.config)[CoreSettings.STALE_AFTER_SEC] * 1000 * 2
        return game.players.values
            .filter { it.id != viewerId && it.status == PlayerStatus.ACTIVE && mode.canCatch(game, it, viewer) }
            .mapNotNull { p ->
                val f = locations[p.id] ?: return@mapNotNull null
                if (now - f.time > stale) return@mapNotNull null
                val d = Geo.distance(me.point, f.point)
                if (d <= radius) Threat(p.id, d, Geo.bearing(me.point, f.point)) else null
            }
            .sortedBy { it.distance }
    }
}
