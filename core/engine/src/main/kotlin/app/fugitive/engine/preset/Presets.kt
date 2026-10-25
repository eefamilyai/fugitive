package app.fugitive.engine.preset

import app.fugitive.engine.mode.AssassinMode
import app.fugitive.engine.mode.ClassicMode
import app.fugitive.engine.mode.ExtractionMode
import app.fugitive.engine.mode.InfectionMode
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.model.jsonOf
import kotlinx.serialization.json.JsonPrimitive

/** A named bundle of a mode and setting values — a one-tap starting point. */
data class GamePreset(
    val id: String,
    val name: String,
    val description: String,
    val modeId: String,
    val emoji: String,
    val values: Map<String, JsonPrimitive>,
)

private fun values(vararg pairs: Pair<String, Any>): Map<String, JsonPrimitive> = pairs.associate { (k, v) -> k to jsonOf(v) }

object BuiltInPresets {
    val CLASSIC = GamePreset(
        "classic", "Classic Manhunt", "Live tracking, a shrinking zone and a 30 minute clock.", ClassicMode.ID, "🏃",
        values(),
    )
    val HIDE_AND_SEEK = GamePreset(
        "hide_seek", "Hide & Seek", "Runners only show up every 2 minutes. Hunters stay hidden.", ClassicMode.ID, "🙈",
        values(
            CoreSettings.RUNNER_REVEAL.key to "interval",
            CoreSettings.RUNNER_REVEAL_INTERVAL_SEC.key to 120,
            CoreSettings.HUNTER_REVEAL.key to "hidden",
            CoreSettings.PROXIMITY_ALERT_M.key to 60,
            CoreSettings.ENDGAME_REVEAL_MIN.key to 3,
            CoreSettings.HEADSTART_SEC.key to 180,
        ),
    )
    val BATTLE_ROYALE = GamePreset(
        "battle_royale", "Battle Royale", "A fast, aggressive zone. Outside means out.", ClassicMode.ID, "🌀",
        values(
            CoreSettings.DURATION_MIN.key to 20,
            CoreSettings.HEADSTART_SEC.key to 60,
            CoreSettings.ZONE_STAGES.key to 6,
            CoreSettings.ZONE_DELAY_MIN.key to 1,
            CoreSettings.ZONE_SHRINK_MIN.key to 15,
            CoreSettings.ZONE_START_RADIUS_M.key to 600,
            CoreSettings.ZONE_END_RADIUS_M.key to 40,
            CoreSettings.ZONE_PENALTY.key to "eliminate",
            CoreSettings.ZONE_GRACE_SEC.key to 0,
        ),
    )
    val FAIR_CHASE = GamePreset(
        "fair_chase", "Fair Chase", "Everyone sees everyone. Pure speed and tactics.", ClassicMode.ID, "⚔️",
        values(
            CoreSettings.HUNTER_REVEAL.key to "live",
            CoreSettings.RUNNER_REVEAL.key to "live",
            CoreSettings.CATCH_METHOD.key to "proximity",
            CoreSettings.CATCH_RADIUS_M.key to 10,
            "ability_cloak" to false,
            "ability_sonar" to false,
            "ability_radar_scan" to false,
        ),
    )
    val FUGITIVE = GamePreset(
        "fugitive", "The Fugitive", "One runner. Everyone else hunts. Survive 15 minutes.", ClassicMode.ID, "🕵️",
        values(
            CoreSettings.HUNTER_SELECTION.key to "one_runner",
            CoreSettings.DURATION_MIN.key to 15,
            CoreSettings.RUNNER_REVEAL.key to "interval",
            CoreSettings.RUNNER_REVEAL_INTERVAL_SEC.key to 60,
            CoreSettings.PROXIMITY_ALERT_M.key to 150,
            CoreSettings.PROXIMITY_DIRECTION.key to true,
            "ability_cloak_uses" to 2,
        ),
    )
    val ZOMBIES = GamePreset(
        "zombies", "Zombie Outbreak", "One patient zero. Every catch spreads the virus.", InfectionMode.ID, "🧟",
        values(),
    )
    val GREAT_ESCAPE = GamePreset(
        "great_escape", "The Great Escape", "Survive, then sprint for the evac point.", ExtractionMode.ID, "🚁",
        values(),
    )
    val ASSASSINS = GamePreset(
        "assassins", "Assassins", "A secret target each. Trust no one.", AssassinMode.ID, "🎯",
        values(),
    )
    val CASUAL = GamePreset(
        "casual", "Casual Walk", "Big area, slow updates, no eliminations. Great for families.", ClassicMode.ID, "🌳",
        values(
            CoreSettings.LOCATION_INTERVAL_SEC.key to 5,
            CoreSettings.ZONE_PENALTY.key to "warn",
            CoreSettings.ZONE_START_RADIUS_M.key to 1500,
            CoreSettings.ZONE_END_RADIUS_M.key to 300,
            CoreSettings.CATCH_METHOD.key to "confirm",
            CoreSettings.DISCONNECT_PENALTY.key to "nothing",
        ),
    )

    val all = listOf(CLASSIC, HIDE_AND_SEEK, BATTLE_ROYALE, FUGITIVE, FAIR_CHASE, ZOMBIES, GREAT_ESCAPE, ASSASSINS, CASUAL)
}
