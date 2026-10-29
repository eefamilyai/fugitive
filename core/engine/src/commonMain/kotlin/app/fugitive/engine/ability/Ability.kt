package app.fugitive.engine.ability

import app.fugitive.engine.rules.EventTypes
import app.fugitive.engine.rules.RuleContext
import app.fugitive.engine.settings.NumberSetting
import app.fugitive.engine.settings.Setting
import app.fugitive.engine.settings.SettingCategory
import app.fugitive.engine.settings.SettingUnit
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.settings.ToggleSetting
import app.fugitive.engine.time.GameStage
import app.fugitive.model.Effect
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role

/**
 * A limited-use power. Each ability declares its own settings (enabled, uses,
 * duration, cooldown) which appear automatically in the lobby's settings editor.
 * The default activation adds a timed [Effect] whose [id] other rules look for
 * (see VisibilityRules). Register new abilities with the GameEngine.
 */
abstract class Ability(
    val id: String,
    val name: String,
    val description: String,
    /** Icon key resolved by the UI. */
    val icon: String,
    /** Roles that may use this ability. */
    val roles: Set<Role>,
    defaultEnabled: Boolean,
    defaultUses: Long,
    defaultDurationSec: Long,
    defaultCooldownSec: Long,
) {
    val enabled = ToggleSetting("ability_$id", name, description, SettingCategory.ABILITIES, defaultEnabled)
    val uses = NumberSetting(
        "ability_${id}_uses", "Uses per game", category = SettingCategory.ABILITIES,
        default = defaultUses, min = 1, max = 20, unit = SettingUnit.COUNT,
        visibleWhen = { it[enabled] }, nested = true,
    )
    val duration = NumberSetting(
        "ability_${id}_duration_sec", "Lasts", category = SettingCategory.ABILITIES,
        default = defaultDurationSec, min = 5, max = 600, step = 5, unit = SettingUnit.SECONDS,
        visibleWhen = { it[enabled] }, nested = true,
    )
    val cooldown = NumberSetting(
        "ability_${id}_cooldown_sec", "Cooldown", category = SettingCategory.ABILITIES,
        default = defaultCooldownSec, min = 0, max = 3600, step = 15, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "None"), visibleWhen = { it[enabled] }, nested = true,
    )

    open fun settings(): List<Setting<*>> = listOf(enabled, uses, duration, cooldown)

    open fun isAvailableTo(settings: Settings, player: PlayerState): Boolean =
        settings[enabled] && player.role in roles

    /** Reason the player can't use the ability right now, or null if they can. */
    open fun blockedReason(ctx: RuleContext, player: PlayerState): String? {
        if (!isAvailableTo(ctx.settings, player)) return "Not available"
        if (player.status != PlayerStatus.ACTIVE) return "You're out of the game"
        if (ctx.stage == GameStage.COUNTDOWN || ctx.stage == GameStage.ENDED) return "Not now"
        val used = player.abilityUses[id] ?: 0
        if (used >= ctx.settings[uses]) return "No uses left"
        val ready = player.abilityReadyAt[id] ?: 0
        if (ctx.now < ready) return "Recharging"
        return null
    }

    /** Apply the ability. Default: add a timed effect and announce it. */
    open fun activate(ctx: RuleContext, player: PlayerState) {
        val durationMs = ctx.settings[duration] * 1000
        val effect = Effect(
            id = "$id-${player.id}-${ctx.now}", type = id, ownerId = player.id,
            startsAt = ctx.now, endsAt = ctx.now + durationMs,
        )
        ctx.updateState { it.copy(effects = it.effects + (effect.id to effect)) }
        ctx.updatePlayer(player.id) {
            it.copy(
                abilityUses = it.abilityUses + (id to (it.abilityUses[id] ?: 0) + 1),
                abilityReadyAt = it.abilityReadyAt + (id to ctx.now + durationMs + ctx.settings[cooldown] * 1000),
            )
        }
        announce(ctx, player)
    }

    /** Feed message(s) for activation; override for custom audiences. */
    open fun announce(ctx: RuleContext, player: PlayerState) {
        ctx.emit(EventTypes.ABILITY, "${player.name} used $name", player.id, importance = 1)
    }
}

/** Hunter: reveals every runner live for a short time. */
class RadarScanAbility : Ability(
    id = "radar_scan", name = "Radar scan",
    description = "Hunters reveal every runner's live location for a short time.",
    icon = "radar", roles = setOf(Role.HUNTER),
    defaultEnabled = true, defaultUses = 2, defaultDurationSec = 15, defaultCooldownSec = 300,
) {
    override fun announce(ctx: RuleContext, player: PlayerState) {
        ctx.emit(EventTypes.ABILITY, "📡 ${player.name} ran a radar scan — runners revealed!", player.id, importance = 2)
    }
}

/** Runner: invisible to hunters for a while. */
class CloakAbility : Ability(
    id = "cloak", name = "Cloak",
    description = "Runners vanish from the hunters' map for a short time.",
    icon = "cloak", roles = setOf(Role.RUNNER),
    defaultEnabled = true, defaultUses = 1, defaultDurationSec = 45, defaultCooldownSec = 300,
) {
    override fun announce(ctx: RuleContext, player: PlayerState) {
        ctx.emit(EventTypes.ABILITY, "👻 ${player.name} activated a cloak", player.id, importance = 1)
    }
}

/** Runner: briefly sees all hunters. */
class SonarAbility : Ability(
    id = "sonar", name = "Sonar",
    description = "Runners see every hunter's live location for a short time.",
    icon = "sonar", roles = setOf(Role.RUNNER),
    defaultEnabled = true, defaultUses = 1, defaultDurationSec = 15, defaultCooldownSec = 240,
) {
    override fun announce(ctx: RuleContext, player: PlayerState) {
        ctx.emit(EventTypes.ABILITY, "🔊 ${player.name} pinged the hunters with sonar", player.id, importance = 1)
    }
}

object AbilityIds {
    const val RADAR_SCAN = "radar_scan"
    const val CLOAK = "cloak"
    const val SONAR = "sonar"
}
