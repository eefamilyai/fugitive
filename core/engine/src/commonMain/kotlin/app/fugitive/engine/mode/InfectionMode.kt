package app.fugitive.engine.mode

import app.fugitive.engine.rules.EndReasons
import app.fugitive.engine.rules.EventTypes
import app.fugitive.engine.rules.OutReasons
import app.fugitive.engine.rules.RuleContext
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.NumberSetting
import app.fugitive.engine.settings.Setting
import app.fugitive.engine.settings.SettingCategory
import app.fugitive.engine.settings.SettingUnit
import app.fugitive.engine.settings.ToggleSetting
import app.fugitive.engine.time.GameClock
import app.fugitive.model.GameResult
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import app.fugitive.model.jsonOf
import kotlinx.serialization.json.JsonPrimitive

/** Virus mode: whoever gets caught joins the infected. Last survivors win. */
class InfectionMode : BaseMode() {
    override val id = ID
    override val name = "Infection"
    override val tagline = "Every catch turns a survivor into a zombie."
    override val icon = "virus"
    override val accent = 0xFF84CC16
    override val rules = listOf(
        "One or more players start infected.",
        "Infected players catch survivors — who then become infected too.",
        "Newly infected players are frozen for a moment before they can hunt.",
        "Survivors win if anyone is still human when the time runs out.",
        "The infected win once every survivor has turned.",
    )

    val freezeSec = NumberSetting(
        "infection_freeze_sec", "Turning time", "Newly infected players can't catch anyone for this long.",
        SettingCategory.MODE, default = 20, min = 0, max = 300, step = 5, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "Instant"),
    )
    val revealLastSurvivor = ToggleSetting(
        "infection_reveal_last", "Expose the last survivor", "When one survivor remains, everyone sees them live.",
        SettingCategory.MODE, default = true,
    )

    override fun settings(): List<Setting<*>> = listOf(freezeSec, revealLastSurvivor)

    override fun defaults(): Map<String, JsonPrimitive> = mapOf(
        CoreSettings.HUNTER_SELECTION.key to JsonPrimitive("random"),
        CoreSettings.RUNNER_REVEAL.key to JsonPrimitive("interval"),
        CoreSettings.RUNNER_REVEAL_INTERVAL_SEC.key to JsonPrimitive(60),
        CoreSettings.PROXIMITY_ALERT_M.key to JsonPrimitive(75),
    )

    override fun roleLabel(role: Role): String = when (role) {
        Role.HUNTER -> "Infected"
        Role.RUNNER -> "Survivor"
        else -> super.roleLabel(role)
    }

    override fun objective(role: Role): String = when (role) {
        Role.HUNTER -> "Infect every survivor"
        Role.RUNNER -> "Stay human until the timer ends"
        else -> super.objective(role)
    }

    override fun onCatch(ctx: RuleContext, catcher: PlayerState?, target: PlayerState) {
        val freeze = ctx.settings[freezeSec] * 1000
        ctx.updatePlayer(target.id) {
            it.copy(
                role = Role.HUNTER, status = PlayerStatus.ACTIVE, statusChangedAt = ctx.now,
                caughtBy = catcher?.id, outReason = OutReasons.INFECTED,
                frozenUntil = if (freeze > 0) ctx.now + freeze else null,
                zoneExitAt = null, revealed = false,
                data = it.data + ("infected_at" to jsonOf(ctx.now)),
            )
        }
        if (catcher != null) CoreOutcomes.creditCatch(ctx, catcher)
        val by = catcher?.let { " by ${it.name}" } ?: ""
        ctx.emit(EventTypes.INFECTED, "🧟 ${target.name} was infected$by", catcher?.id, target.id, importance = 1)
    }

    override fun onTick(ctx: RuleContext) {
        if (!ctx.settings[revealLastSurvivor]) return
        val survivors = ctx.state.active(Role.RUNNER)
        if (survivors.size == 1 && ctx.state.players.size > 2) {
            val last = survivors.first()
            ctx.once("last_survivor") {
                ctx.updatePlayer(last.id) { it.copy(revealed = true) }
                ctx.emit(EventTypes.REVEALED, "${last.name} is the last survivor — now visible to everyone!", target = last.id, importance = 2)
            }
        }
    }

    override fun checkResult(ctx: RuleContext): GameResult? {
        val players = ctx.state.players.values
        val survivors = players.filter { it.role == Role.RUNNER && it.status == PlayerStatus.ACTIVE }
        if (survivors.isEmpty()) {
            return GameResult(
                reason = EndReasons.ALL_CAUGHT, winningRole = Role.HUNTER,
                winnerIds = players.filter { it.originalRole == Role.HUNTER }.map { it.id },
                headline = "The infection wins!",
                detail = "Everyone turned in ${GameClock.format(ctx.now - ctx.state.headstartEndsAt)}.",
            )
        }
        if (ctx.isTimeUp) {
            return GameResult(
                reason = EndReasons.TIME_UP, winningRole = Role.RUNNER, winnerIds = survivors.map { it.id },
                headline = "Survivors win!",
                detail = "${survivors.size} ${if (survivors.size == 1) "survivor" else "survivors"} made it.",
            )
        }
        return null
    }

    companion object { const val ID = "infection" }
}
