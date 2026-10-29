package app.fugitive.engine.mode

import app.fugitive.engine.rules.RuleContext
import app.fugitive.model.GameResult

/** Hunters chase runners; caught runners are out. Survive until the timer ends to win. */
class ClassicMode : BaseMode() {
    override val id = ID
    override val name = "Classic Manhunt"
    override val tagline = "Hunters chase. Runners survive. Caught means out."
    override val icon = "classic"
    override val accent = 0xFFFF5A36
    override val rules = listOf(
        "Hunters are chosen; everyone else is a runner.",
        "Runners get a head start while hunters wait.",
        "Hunters see runners on the map according to the visibility settings.",
        "Caught runners are out and can spectate.",
        "Runners win if anyone survives until the time runs out. Hunters win by catching everybody.",
    )

    override fun checkResult(ctx: RuleContext): GameResult? = CoreOutcomes.huntersVsRunnersResult(ctx)

    companion object { const val ID = "classic" }
}
