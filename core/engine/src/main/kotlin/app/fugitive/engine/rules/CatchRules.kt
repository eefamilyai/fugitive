package app.fugitive.engine.rules

import app.fugitive.engine.GameEngine
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.model.GameState
import app.fugitive.model.LocationFix
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerStatus
import kotlin.math.min

sealed interface CatchCheck {
    data class Allowed(val distanceMeters: Double?) : CatchCheck
    data class Denied(val reason: String) : CatchCheck
}

/**
 * Shared catch eligibility. The game screen uses it to enable the Catch
 * button (and say why it is disabled); the referee uses it to validate claims.
 */
object CatchRules {

    /** Whether the current catch method needs both players' GPS within range. */
    fun requiresRange(method: String, requireRangeSetting: Boolean): Boolean =
        method == "proximity" || method == "auto" || requireRangeSetting

    fun check(
        engine: GameEngine,
        game: GameState,
        catcherId: PlayerId,
        targetId: PlayerId,
        now: Long,
        locations: Map<PlayerId, LocationFix>,
    ): CatchCheck {
        val settings = engine.settings(game.config)
        val mode = engine.mode(game.modeId)
        val catcher = game.players[catcherId] ?: return CatchCheck.Denied("You're not in this game")
        val target = game.players[targetId] ?: return CatchCheck.Denied("Unknown player")
        if (game.result != null) return CatchCheck.Denied("The game is over")
        if (GameClock.stage(game, now) != GameStage.ACTIVE) return CatchCheck.Denied("Hunters aren't released yet")
        if (catcher.status != PlayerStatus.ACTIVE) return CatchCheck.Denied("You're out of the game")
        catcher.frozenUntil?.let { if (now < it) return CatchCheck.Denied("Frozen for ${GameClock.format(it - now)}") }
        if (target.status != PlayerStatus.ACTIVE) return CatchCheck.Denied("${target.name} is already out")
        if (!mode.canCatch(game, catcher, target)) return CatchCheck.Denied("You can't catch ${target.name}")
        if (game.pendingCatches.values.any { it.runnerId == targetId }) return CatchCheck.Denied("Waiting for ${target.name} to confirm")

        val a = locations[catcherId]
        val b = locations[targetId]
        val distance = if (a != null && b != null) Geo.distance(a.point, b.point) else null
        val method = settings[CoreSettings.CATCH_METHOD]
        if (requiresRange(method, settings[CoreSettings.CATCH_REQUIRE_RANGE])) {
            if (a == null || b == null || distance == null) return CatchCheck.Denied("Waiting for GPS")
            val maxAcc = settings[CoreSettings.MAX_ACCURACY_M]
            if (a.accuracy > maxAcc || b.accuracy > maxAcc) return CatchCheck.Denied("GPS signal too weak")
            val radius = settings[CoreSettings.CATCH_RADIUS_M].toDouble()
            if (distance > radius + tolerance(a, b)) {
                return CatchCheck.Denied("Too far — ${distance.toInt()} m (need ${radius.toInt()} m)")
            }
        }
        return CatchCheck.Allowed(distance)
    }

    /** Extra slack for combined GPS inaccuracy, capped so range still means something. */
    fun tolerance(a: LocationFix, b: LocationFix): Double = min((a.accuracy + b.accuracy) / 2.0, 15.0)
}
