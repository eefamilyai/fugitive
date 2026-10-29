package app.fugitive.engine.rules

import app.fugitive.engine.GameEngine
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.engine.zone.ZoneMath
import app.fugitive.engine.zone.ZonePhase
import app.fugitive.model.GameAction
import app.fugitive.model.GameEvent
import app.fugitive.model.GameResult
import app.fugitive.model.GameState
import app.fugitive.model.LocationFix
import app.fugitive.model.Member
import app.fugitive.model.PendingCatch
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import app.fugitive.model.bool
import app.fugitive.model.jsonOf
import app.fugitive.model.long
import app.fugitive.model.string

data class RefereeInput(
    val game: GameState,
    val members: Map<PlayerId, Member>,
    val locations: Map<PlayerId, LocationFix>,
    /** Unprocessed actions for this round, any order. */
    val actions: List<GameAction>,
    val hostId: PlayerId,
    val now: Long,
)

data class RefereeOutcome(
    val game: GameState,
    val events: List<GameEvent>,
    /** Action ids that were handled (or rejected) and can be deleted. */
    val consumedActions: List<String>,
) {
    fun changedFrom(previous: GameState): Boolean = game != previous || events.isNotEmpty() || consumedActions.isNotEmpty()
}

/**
 * The authoritative game loop, run about once per second on the host device.
 * It is a pure function of its input, so it is trivially unit-testable and any
 * device can take over as referee if the host drops out.
 */
class Referee(private val engine: GameEngine) {

    fun tick(input: RefereeInput): RefereeOutcome {
        val ctx = RuleContext(input.game, engine, input.now, input.locations, input.members, input.hostId)
        val consumed = mutableListOf<String>()
        if (ctx.state.result != null) {
            return RefereeOutcome(ctx.state, emptyList(), input.actions.map { it.id })
        }

        announceStages(ctx)
        handleMembership(ctx)
        for (action in input.actions.sortedBy { it.createdAt }) {
            if (action.round == ctx.state.round) handleAction(ctx, action)
            consumed += action.id
            if (ctx.state.result != null) break
        }
        if (ctx.state.result == null) {
            expirePendingCatches(ctx)
            autoCatch(ctx)
            enforceZone(ctx)
            enforceConnectivity(ctx)
            pruneEffects(ctx)
            ctx.mode.onTick(ctx)
            announceTime(ctx)
            ctx.mode.checkResult(ctx)?.let { result -> finish(ctx, result) }
        }
        return RefereeOutcome(ctx.state, ctx.events, consumed)
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private fun handleAction(ctx: RuleContext, action: GameAction) {
        when (action.type) {
            ActionTypes.CATCH_CLAIM -> claimCatch(ctx, action)
            ActionTypes.CATCH_RESPONSE -> respondToCatch(ctx, action)
            ActionTypes.CODE_CATCH -> codeCatch(ctx, action)
            ActionTypes.SELF_REPORT -> selfReport(ctx, action)
            ActionTypes.USE_ABILITY -> useAbility(ctx, action)
            ActionTypes.LEAVE -> leave(ctx, action.by)
            ActionTypes.HOST_END -> if (action.by == ctx.hostId) finish(
                ctx, GameResult(reason = EndReasons.HOST_ENDED, headline = "Game ended", detail = "The host ended the game early."),
            )
            else -> ctx.mode.handleAction(ctx, action)
        }
    }

    private fun claimCatch(ctx: RuleContext, action: GameAction) {
        val target = action.target ?: return
        val check = CatchRules.check(engine, ctx.state, action.by, target, ctx.now, ctx.locations)
        if (check is CatchCheck.Denied) {
            ctx.emit(EventTypes.INFO, "Catch not counted: ${check.reason}", audience = listOf(action.by))
            return
        }
        val distance = (check as CatchCheck.Allowed).distanceMeters
        when (ctx.settings[CoreSettings.CATCH_METHOD]) {
            "confirm" -> {
                val timeout = ctx.settings[CoreSettings.CATCH_CONFIRM_TIMEOUT_SEC] * 1000
                val pending = PendingCatch(action.id, action.by, target, ctx.now, ctx.now + timeout, distance)
                ctx.updateState { it.copy(pendingCatches = it.pendingCatches + (pending.id to pending)) }
                ctx.emit(
                    EventTypes.CATCH_CLAIMED, "${ctx.name(action.by)} says they caught you!",
                    action.by, target, importance = 2, audience = listOf(target),
                )
                ctx.emit(
                    EventTypes.CATCH_CLAIMED, "Waiting for ${ctx.name(target)} to confirm…",
                    action.by, target, audience = listOf(action.by),
                )
            }
            "code" -> ctx.emit(EventTypes.INFO, "Enter ${ctx.name(target)}'s code to confirm the catch", audience = listOf(action.by))
            else -> applyCatch(ctx, action.by, target)
        }
    }

    private fun respondToCatch(ctx: RuleContext, action: GameAction) {
        val id = action.payload.string("catch") ?: return
        val pending = ctx.state.pendingCatches[id] ?: return
        if (pending.runnerId != action.by) return
        ctx.updateState { it.copy(pendingCatches = it.pendingCatches - id) }
        if (action.payload.bool("accept") == true) {
            applyCatch(ctx, pending.hunterId, pending.runnerId)
        } else {
            ctx.emit(
                EventTypes.CATCH_DISPUTED, "${ctx.name(pending.runnerId)} disputes the catch",
                pending.runnerId, pending.hunterId, importance = 1, audience = listOf(pending.hunterId, pending.runnerId),
            )
        }
    }

    private fun codeCatch(ctx: RuleContext, action: GameAction) {
        val hunter = ctx.player(action.by) ?: return
        val lockedUntil = hunter.data.long(DataKeys.CODE_LOCKED_UNTIL) ?: 0
        if (ctx.now < lockedUntil) {
            ctx.emit(EventTypes.WRONG_CODE, "Too many wrong codes — try again in ${GameClock.format(lockedUntil - ctx.now)}", audience = listOf(hunter.id))
            return
        }
        val code = action.payload.string("code")?.trim()
        val target = ctx.state.players.values.firstOrNull {
            it.status == PlayerStatus.ACTIVE && it.catchCode == code && it.id != hunter.id && ctx.mode.canCatch(ctx.state, hunter, it)
        }
        if (code.isNullOrEmpty() || target == null) {
            val fails = (hunter.data.long(DataKeys.CODE_FAILS) ?: 0) + 1
            ctx.setPlayerData(hunter.id, DataKeys.CODE_FAILS, jsonOf(fails))
            if (fails >= 5) {
                ctx.setPlayerData(hunter.id, DataKeys.CODE_FAILS, jsonOf(0))
                ctx.setPlayerData(hunter.id, DataKeys.CODE_LOCKED_UNTIL, jsonOf(ctx.now + 60_000))
            }
            ctx.emit(EventTypes.WRONG_CODE, "That code doesn't match anyone you can catch", audience = listOf(hunter.id))
            return
        }
        val check = CatchRules.check(engine, ctx.state, hunter.id, target.id, ctx.now, ctx.locations)
        if (check is CatchCheck.Denied) {
            ctx.emit(EventTypes.INFO, "Catch not counted: ${check.reason}", audience = listOf(hunter.id))
            return
        }
        ctx.setPlayerData(hunter.id, DataKeys.CODE_FAILS, null)
        applyCatch(ctx, hunter.id, target.id)
    }

    private fun selfReport(ctx: RuleContext, action: GameAction) {
        if (!ctx.settings[CoreSettings.ALLOW_SELF_REPORT]) return
        val runner = ctx.player(action.by) ?: return
        if (runner.status != PlayerStatus.ACTIVE) return
        if (ctx.stage == GameStage.COUNTDOWN) return
        val hunter = ctx.player(action.target)?.takeIf { it.status == PlayerStatus.ACTIVE && ctx.mode.canCatch(ctx.state, it, runner) }
        if (hunter == null && ctx.state.players.values.none { ctx.mode.canCatch(ctx.state, it, runner) }) return
        ctx.mode.onCatch(ctx, hunter, runner)
        clearPendingFor(ctx, runner.id)
    }

    private fun useAbility(ctx: RuleContext, action: GameAction) {
        val ability = engine.ability(action.payload.string("ability") ?: return) ?: return
        val player = ctx.player(action.by) ?: return
        val blocked = ability.blockedReason(ctx, player)
        if (blocked != null) {
            ctx.emit(EventTypes.INFO, "${ability.name}: $blocked", audience = listOf(player.id))
            return
        }
        ability.activate(ctx, player)
    }

    private fun leave(ctx: RuleContext, id: PlayerId) {
        val p = ctx.player(id) ?: return
        if (p.status != PlayerStatus.ACTIVE) return
        ctx.setStatus(id, PlayerStatus.LEFT, OutReasons.LEFT)
        ctx.emit(EventTypes.LEFT, "${p.name} left the game", target = id)
        clearPendingFor(ctx, id)
    }

    private fun applyCatch(ctx: RuleContext, catcherId: PlayerId, targetId: PlayerId) {
        val catcher = ctx.player(catcherId) ?: return
        val target = ctx.player(targetId) ?: return
        if (target.status != PlayerStatus.ACTIVE) return
        ctx.mode.onCatch(ctx, catcher, target)
        clearPendingFor(ctx, targetId)
    }

    private fun clearPendingFor(ctx: RuleContext, id: PlayerId) {
        ctx.updateState { s -> s.copy(pendingCatches = s.pendingCatches.filterValues { it.runnerId != id && it.hunterId != id }) }
    }

    // ── Timed rules ─────────────────────────────────────────────────────

    private fun expirePendingCatches(ctx: RuleContext) {
        val accept = ctx.settings[CoreSettings.CATCH_TIMEOUT_ACCEPTS]
        for (p in ctx.state.pendingCatches.values.filter { ctx.now >= it.expiresAt }) {
            ctx.updateState { it.copy(pendingCatches = it.pendingCatches - p.id) }
            if (accept) {
                applyCatch(ctx, p.hunterId, p.runnerId)
            } else {
                ctx.emit(EventTypes.CATCH_DISPUTED, "Catch on ${ctx.name(p.runnerId)} expired unconfirmed", audience = listOf(p.hunterId, p.runnerId))
            }
        }
    }

    private fun autoCatch(ctx: RuleContext) {
        if (ctx.settings[CoreSettings.CATCH_METHOD] != "auto" || ctx.stage != GameStage.ACTIVE) return
        val dwell = ctx.settings[CoreSettings.AUTO_CATCH_DWELL_SEC] * 1000
        val active = ctx.state.players.values.filter { it.status == PlayerStatus.ACTIVE }
        for (target in active) {
            val current = ctx.player(target.id) ?: continue
            if (current.status != PlayerStatus.ACTIVE) continue
            val catcher = active.firstOrNull { c ->
                c.id != target.id && CatchRules.check(engine, ctx.state, c.id, target.id, ctx.now, ctx.locations) is CatchCheck.Allowed
            }
            val dwellBy = current.data.string(DataKeys.DWELL_HUNTER)
            if (catcher == null) {
                if (dwellBy != null) {
                    ctx.setPlayerData(target.id, DataKeys.DWELL_HUNTER, null)
                    ctx.setPlayerData(target.id, DataKeys.DWELL_SINCE, null)
                }
                continue
            }
            if (dwellBy != catcher.id) {
                ctx.setPlayerData(target.id, DataKeys.DWELL_HUNTER, jsonOf(catcher.id))
                ctx.setPlayerData(target.id, DataKeys.DWELL_SINCE, jsonOf(ctx.now))
                if (dwell > 0) continue
            }
            val since = ctx.player(target.id)?.data?.long(DataKeys.DWELL_SINCE) ?: ctx.now
            if (ctx.now - since >= dwell) {
                ctx.setPlayerData(target.id, DataKeys.DWELL_HUNTER, null)
                ctx.setPlayerData(target.id, DataKeys.DWELL_SINCE, null)
                applyCatch(ctx, catcher.id, target.id)
            }
        }
    }

    private fun enforceZone(ctx: RuleContext) {
        val plan = ctx.state.zone ?: return
        if (ctx.stage != GameStage.ACTIVE) return
        val zone = ZoneMath.snapshot(plan, ctx.now) ?: return
        val penalty = ctx.settings[CoreSettings.ZONE_PENALTY]
        val grace = ctx.settings[CoreSettings.ZONE_GRACE_SEC] * 1000
        val forgive = ctx.settings[CoreSettings.ZONE_GPS_TOLERANCE]
        for (p in ctx.state.players.values.toList()) {
            if (p.status != PlayerStatus.ACTIVE || p.role.id !in plan.appliesTo) continue
            val fix = ctx.usableFix(p.id) ?: continue
            val inside = ZoneMath.isInside(fix, zone.current, forgive)
            if (inside) {
                if (p.zoneExitAt != null) {
                    ctx.updatePlayer(p.id) { it.copy(zoneExitAt = null, revealed = false) }
                    ctx.emit(EventTypes.BACK_IN_ZONE, "Back inside the zone", target = p.id, audience = listOf(p.id))
                }
                continue
            }
            val exitAt = p.zoneExitAt ?: ctx.now.also {
                ctx.updatePlayer(p.id) { it.copy(zoneExitAt = ctx.now) }
                val warn = when {
                    penalty == "warn" -> "You're outside the zone!"
                    grace > 0 -> "You're outside the zone! ${grace / 1000}s to get back in"
                    else -> "You're outside the zone!"
                }
                ctx.emit(EventTypes.OUTSIDE_ZONE, warn, target = p.id, audience = listOf(p.id), importance = 2)
            }
            if (ctx.now - exitAt < grace) continue
            when (penalty) {
                "eliminate" -> {
                    ctx.setStatus(p.id, PlayerStatus.ELIMINATED, OutReasons.ZONE)
                    clearPendingFor(ctx, p.id)
                    ctx.emit(EventTypes.ELIMINATED, "☠️ ${p.name} was eliminated by the zone", target = p.id, importance = 2)
                }
                "reveal" -> if (!p.revealed) {
                    ctx.updatePlayer(p.id) { it.copy(revealed = true) }
                    ctx.emit(EventTypes.REVEALED, "📍 ${p.name} is outside the zone and revealed", target = p.id, importance = 1)
                }
            }
        }
    }

    private fun enforceConnectivity(ctx: RuleContext) {
        val timeout = ctx.settings[CoreSettings.DISCONNECT_TIMEOUT_SEC] * 1000
        if (timeout <= 0 || ctx.stage == GameStage.COUNTDOWN) return
        val penalty = ctx.settings[CoreSettings.DISCONNECT_PENALTY]
        for (p in ctx.state.players.values.toList()) {
            if (p.status != PlayerStatus.ACTIVE || p.role == Role.SPECTATOR) continue
            val lastSeen = ctx.locations[p.id]?.time ?: ctx.state.startAt
            val dark = ctx.now - lastSeen > timeout
            val flagged = p.data.bool(DataKeys.DISCONNECTED) == true
            if (dark && !flagged) {
                ctx.setPlayerData(p.id, DataKeys.DISCONNECTED, jsonOf(true))
                val isPrey = ctx.state.players.values.any { it.id != p.id && ctx.mode.canCatch(ctx.state, it, p) }
                when {
                    isPrey && penalty == "eliminate" -> {
                        ctx.setStatus(p.id, PlayerStatus.ELIMINATED, OutReasons.DISCONNECT)
                        ctx.emit(EventTypes.ELIMINATED, "📵 ${p.name} lost connection and is out", target = p.id, importance = 1)
                    }
                    isPrey && penalty == "reveal" -> {
                        ctx.setPlayerData(p.id, DataKeys.REVEAL_DISCONNECT, jsonOf(true))
                        ctx.emit(EventTypes.DISCONNECTED, "📵 ${p.name} went dark — last location revealed", target = p.id, importance = 1)
                    }
                    else -> ctx.emit(EventTypes.DISCONNECTED, "📵 ${p.name} lost connection", target = p.id)
                }
            } else if (!dark && flagged) {
                ctx.setPlayerData(p.id, DataKeys.DISCONNECTED, null)
                ctx.setPlayerData(p.id, DataKeys.REVEAL_DISCONNECT, null)
                ctx.emit(EventTypes.RECONNECTED, "${p.name} is back online", target = p.id)
            }
        }
    }

    private fun handleMembership(ctx: RuleContext) {
        if (ctx.members.isEmpty()) return
        // Players removed from the lobby (kicked or left) leave the game.
        for (p in ctx.state.players.values.toList()) {
            if (p.status == PlayerStatus.ACTIVE && p.id !in ctx.members) leave(ctx, p.id)
        }
        // Late joiners.
        for (m in ctx.members.values) {
            if (m.id in ctx.state.players) continue
            val role = ctx.mode.lateJoinRole(ctx.settings, ctx.state) ?: continue
            val player = GameFactory.newPlayer(engine, ctx.settings, m, role, ctx.state.players.values.mapNotNull { it.catchCode }.toSet(), ctx.random)
            ctx.updateState { it.copy(players = it.players + (m.id to player)) }
            ctx.emit(EventTypes.JOINED, "${m.name} joined as ${ctx.mode.roleLabel(role).lowercase()}", target = m.id)
        }
    }

    private fun pruneEffects(ctx: RuleContext) {
        if (ctx.state.effects.values.any { ctx.now >= it.endsAt }) {
            ctx.updateState { s -> s.copy(effects = s.effects.filterValues { ctx.now < it.endsAt }) }
        }
    }

    private fun announceStages(ctx: RuleContext) {
        if (ctx.stage == GameStage.ACTIVE) {
            ctx.once("released") {
                ctx.emit(EventTypes.RELEASED, "🔥 Hunters released — the hunt is on!", importance = 2)
            }
        }
        val plan = ctx.state.zone ?: return
        if (ctx.stage == GameStage.COUNTDOWN) return
        for (stage in plan.stages) {
            if (ctx.now >= stage.announceAt && ctx.now < stage.shrinkStartAt) ctx.once("zone_announce_${stage.index}") {
                val secs = (stage.shrinkStartAt - ctx.now) / 1000
                ctx.emit(EventTypes.ZONE_WARNING, "⚠️ Zone closing in ${GameClock.format(secs * 1000)} — head for the new circle", importance = 1)
            }
            if (ctx.now >= stage.shrinkStartAt && ctx.now < stage.shrinkEndAt) ctx.once("zone_shrink_${stage.index}") {
                val last = stage.index == plan.stages.lastIndex
                ctx.emit(EventTypes.ZONE_SHRINKING, if (last) "🌀 Final shrink! The zone is closing in" else "🌀 The zone is closing in!", importance = 2)
            }
        }
        val snapshot = ZoneMath.snapshot(plan, ctx.now)
        if (snapshot?.phase == ZonePhase.FINAL) ctx.once("zone_final") {
            ctx.emit(EventTypes.ZONE_FINAL, "The zone has reached its final size", importance = 1)
        }
    }

    private fun announceTime(ctx: RuleContext) {
        val endsAt = ctx.state.endsAt ?: return
        val left = endsAt - ctx.now
        val total = endsAt - ctx.state.headstartEndsAt
        if (total > 12 * 60_000 && left in 1..5 * 60_000) ctx.once("time_5") {
            ctx.emit(EventTypes.TIME_WARNING, "⏱️ 5 minutes left", importance = 1)
        }
        if (total > 3 * 60_000 && left in 1..60_000) ctx.once("time_1") {
            ctx.emit(EventTypes.TIME_WARNING, "⏱️ 1 minute left!", importance = 2)
        }
        val endgame = ctx.settings[CoreSettings.ENDGAME_REVEAL_MIN] * 60_000
        if (endgame > 0 && ctx.settings[CoreSettings.RUNNER_REVEAL] != "live" && left in 1..endgame) ctx.once("endgame") {
            ctx.emit(EventTypes.REVEALED, "👁️ Endgame — every runner is now revealed!", importance = 2)
        }
    }

    private fun finish(ctx: RuleContext, result: GameResult) {
        ctx.end(result)
        ctx.emit(EventTypes.GAME_OVER, "🏁 ${result.headline} ${result.detail}".trim(), importance = 2)
    }
}
