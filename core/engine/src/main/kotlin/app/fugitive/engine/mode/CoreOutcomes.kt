package app.fugitive.engine.mode

import app.fugitive.engine.rules.EndReasons
import app.fugitive.engine.rules.EventTypes
import app.fugitive.engine.rules.OutReasons
import app.fugitive.engine.rules.RuleContext
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.time.GameClock
import app.fugitive.model.GameResult
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role

/** Reusable building blocks for mode implementations. */
object CoreOutcomes {

    fun eliminateOnCatch(ctx: RuleContext, catcher: PlayerState?, target: PlayerState) {
        ctx.setStatus(target.id, PlayerStatus.CAUGHT, OutReasons.CAUGHT, catcher?.id)
        if (catcher != null) creditCatch(ctx, catcher)
        val by = catcher?.let { " by ${it.name}" } ?: ""
        ctx.emit(EventTypes.CAUGHT, "${target.name} was caught$by", catcher?.id, target.id, importance = 1)
    }

    fun creditCatch(ctx: RuleContext, catcher: PlayerState) {
        val cooldown = ctx.settings[CoreSettings.HUNTER_CATCH_COOLDOWN_SEC] * 1000
        ctx.updatePlayer(catcher.id) {
            it.copy(catches = it.catches + 1, frozenUntil = if (cooldown > 0) ctx.now + cooldown else it.frozenUntil)
        }
    }

    /**
     * Standard win check for hunters vs runners:
     * hunters win when no runner is active; runners win when time runs out or no hunter is left.
     */
    fun huntersVsRunnersResult(
        ctx: RuleContext,
        hunterWinHeadline: String = "Hunters win!",
        runnerWinHeadline: String = "Runners win!",
    ): GameResult? {
        val players = ctx.state.players.values
        val activeRunners = players.filter { it.role == Role.RUNNER && it.status == PlayerStatus.ACTIVE }
        val escaped = players.filter { it.status == PlayerStatus.ESCAPED }
        val activeHunters = players.filter { it.role == Role.HUNTER && it.status == PlayerStatus.ACTIVE }
        val elapsed = GameClock.format(ctx.now - ctx.state.headstartEndsAt)

        if (activeRunners.isEmpty() && escaped.isEmpty()) {
            return GameResult(
                reason = EndReasons.ALL_CAUGHT, winningRole = Role.HUNTER,
                winnerIds = players.filter { it.role == Role.HUNTER }.map { it.id },
                headline = hunterWinHeadline, detail = "Every runner was caught in $elapsed.",
            )
        }
        if (activeRunners.isEmpty()) {
            return GameResult(
                reason = EndReasons.ESCAPED, winningRole = Role.RUNNER, winnerIds = escaped.map { it.id },
                headline = runnerWinHeadline, detail = "${escaped.size} escaped.",
            )
        }
        if (activeHunters.isEmpty() && ctx.stage.huntersReleased) {
            return GameResult(
                reason = EndReasons.NO_HUNTERS, winningRole = Role.RUNNER,
                winnerIds = (activeRunners + escaped).map { it.id },
                headline = runnerWinHeadline, detail = "No hunters are left in the game.",
            )
        }
        if (ctx.isTimeUp) {
            val winners = activeRunners + escaped
            return GameResult(
                reason = EndReasons.TIME_UP, winningRole = Role.RUNNER, winnerIds = winners.map { it.id },
                headline = runnerWinHeadline,
                detail = "${winners.size} ${if (winners.size == 1) "runner" else "runners"} survived until the end.",
            )
        }
        return null
    }
}
