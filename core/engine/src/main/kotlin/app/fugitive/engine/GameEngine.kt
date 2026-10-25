package app.fugitive.engine

import app.fugitive.engine.ability.Ability
import app.fugitive.engine.ability.CloakAbility
import app.fugitive.engine.ability.RadarScanAbility
import app.fugitive.engine.ability.SonarAbility
import app.fugitive.engine.mode.AssassinMode
import app.fugitive.engine.mode.ClassicMode
import app.fugitive.engine.mode.ConfigIssue
import app.fugitive.engine.mode.ExtractionMode
import app.fugitive.engine.mode.GameMode
import app.fugitive.engine.mode.InfectionMode
import app.fugitive.engine.mode.ValidationContext
import app.fugitive.engine.preset.GamePreset
import app.fugitive.engine.preset.BuiltInPresets
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.settings.SettingsSchema
import app.fugitive.model.GameConfig
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.Role

/**
 * Entry point to the rules: the registry of modes, abilities and presets.
 *
 * Extending the game is a matter of passing more entries here (the app wires
 * this up through dependency injection, so a new module can contribute a mode
 * without editing existing code).
 */
class GameEngine(
    modes: List<GameMode> = BuiltIns.modes(),
    abilities: List<Ability> = BuiltIns.abilities(),
    presets: List<GamePreset> = BuiltInPresets.all,
) {
    val modes: List<GameMode> = modes.distinctBy { it.id }
    val abilities: List<Ability> = abilities.distinctBy { it.id }
    val presets: List<GamePreset> = presets.filter { p -> modes.any { it.id == p.modeId } }

    private val modesById = this.modes.associateBy { it.id }
    private val abilitiesById = this.abilities.associateBy { it.id }
    private val schemaCache = HashMap<String, SettingsSchema>()

    init { require(this.modes.isNotEmpty()) { "At least one game mode is required" } }

    val defaultMode: GameMode get() = modesById[ClassicMode.ID] ?: modes.first()

    fun mode(id: String?): GameMode = modesById[id] ?: defaultMode
    fun ability(id: String): Ability? = abilitiesById[id]
    fun preset(id: String?): GamePreset? = presets.firstOrNull { it.id == id }

    @Synchronized
    fun schema(modeId: String): SettingsSchema = schemaCache.getOrPut(modeId) {
        val mode = mode(modeId)
        val all = CoreSettings.ALL + mode.settings() + abilities.flatMap { it.settings() }
        SettingsSchema(all, hidden = mode.hiddenSettings() + hiddenAbilitySettings(mode))
    }

    /** Abilities nobody in this mode could use are hidden from the editor. */
    private fun hiddenAbilitySettings(mode: GameMode): Set<String> =
        abilities.filter { a -> a.roles.none { it in mode.roles } }.flatMap { a -> a.settings().map { it.key } }.toSet()

    fun settings(config: GameConfig): Settings = Settings(schema(config.modeId), config.values, mode(config.modeId).defaults())

    /** Applies a preset: its mode plus its overrides, replacing previous values. */
    fun applyPreset(preset: GamePreset): GameConfig = GameConfig(modeId = preset.modeId, presetId = preset.id, values = preset.values)

    /** Changes mode while keeping compatible values the host already set. */
    fun switchMode(config: GameConfig, modeId: String): GameConfig {
        val schema = schema(modeId)
        return GameConfig(modeId = modeId, presetId = null, values = config.values.filterKeys { schema[it] != null })
    }

    fun validate(
        config: GameConfig,
        participants: List<Member>,
        roster: Map<PlayerId, Role>,
        hostHasLocation: Boolean,
    ): List<ConfigIssue> {
        val mode = mode(config.modeId)
        val settings = settings(config)
        val issues = mutableListOf<ConfigIssue>()
        val players = participants.filter { roster[it.id] != Role.SPECTATOR }
        if (players.size < mode.minPlayers) {
            issues += ConfigIssue("${mode.name} needs at least ${mode.minPlayers} players", blocking = true)
        }
        if (mode.usesHunterSelection && settings[CoreSettings.HUNTER_SELECTION] == "manual") {
            val hunters = roster.count { (id, r) -> r == Role.HUNTER && participants.any { it.id == id } }
            if (hunters >= players.size && players.isNotEmpty()) issues += ConfigIssue("Someone has to be a runner", blocking = true)
        }
        if (mode.usesHunterSelection && settings[CoreSettings.HUNTER_SELECTION] != "manual" &&
            settings[CoreSettings.HUNTER_SELECTION] != "one_runner" && settings[CoreSettings.HUNTER_COUNT] >= players.size && players.size >= 2
        ) {
            issues += ConfigIssue("Only ${players.size - 1} hunter(s) possible — the rest will be runners", blocking = false, settingKey = CoreSettings.HUNTER_COUNT.key)
        }
        if (settings[CoreSettings.ZONE_ENABLED]) {
            if (settings[CoreSettings.ZONE_END_RADIUS_M] >= settings[CoreSettings.ZONE_START_RADIUS_M]) {
                issues += ConfigIssue("Final zone must be smaller than the starting zone", blocking = true, settingKey = CoreSettings.ZONE_END_RADIUS_M.key)
            }
            val center = settings[CoreSettings.ZONE_CENTER]
            if (center == "custom" && settings[CoreSettings.ZONE_CENTER_POINT].isBlank()) {
                issues += ConfigIssue("Pick the zone centre on the map", blocking = true, settingKey = CoreSettings.ZONE_CENTER_POINT.key)
            }
            if (center == "host" && !hostHasLocation) {
                issues += ConfigIssue("Waiting for the host's GPS to centre the zone", blocking = false)
            }
            if (settings[CoreSettings.TIME_LIMIT_ENABLED] &&
                settings[CoreSettings.ZONE_DELAY_MIN] >= settings[CoreSettings.DURATION_MIN]
            ) {
                issues += ConfigIssue("The zone would never shrink — its delay is longer than the game", blocking = false, settingKey = CoreSettings.ZONE_DELAY_MIN.key)
            }
        }
        if (settings[CoreSettings.RUNNER_REVEAL] == "hidden" && settings[CoreSettings.PROXIMITY_ALERT_M] == 0L &&
            !settings[CoreSettings.ZONE_ENABLED] && mode.usesHunterSelection
        ) {
            issues += ConfigIssue("Runners are completely invisible — consider pings or a zone", blocking = false)
        }
        issues += mode.validate(ValidationContext(config, settings, participants, roster, hostHasLocation))
        return issues
    }
}

object BuiltIns {
    fun modes(): List<GameMode> = listOf(ClassicMode(), InfectionMode(), ExtractionMode(), AssassinMode())
    fun abilities(): List<Ability> = listOf(RadarScanAbility(), CloakAbility(), SonarAbility())
}
