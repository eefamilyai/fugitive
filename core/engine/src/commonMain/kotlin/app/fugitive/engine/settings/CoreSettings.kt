package app.fugitive.engine.settings

import app.fugitive.engine.settings.SettingCategory.Companion.CATCHING
import app.fugitive.engine.settings.SettingCategory.Companion.FAIRNESS
import app.fugitive.engine.settings.SettingCategory.Companion.MATCH
import app.fugitive.engine.settings.SettingCategory.Companion.ROLES
import app.fugitive.engine.settings.SettingCategory.Companion.VISIBILITY
import app.fugitive.engine.settings.SettingCategory.Companion.ZONE

/**
 * Settings shared by every game mode. Modes can hide any of these, override
 * their defaults, and add their own (see GameMode).
 */
object CoreSettings {

    // ── Match ───────────────────────────────────────────────────────────
    val TIME_LIMIT_ENABLED = ToggleSetting(
        "time_limit", "Time limit", "End the game after a fixed duration. Without one, the game ends only when a side wins.",
        MATCH, default = true,
    )
    val DURATION_MIN = NumberSetting(
        "duration_min", "Game length", "How long runners must survive.",
        MATCH, default = 30, min = 3, max = 240, step = 1, unit = SettingUnit.MINUTES,
        suggestions = listOf(10, 15, 20, 30, 45, 60, 90),
        visibleWhen = { it[TIME_LIMIT_ENABLED] },
    )
    val HEADSTART_SEC = NumberSetting(
        "headstart_sec", "Head start", "Runners scatter while hunters wait and can't see anyone.",
        MATCH, default = 120, min = 0, max = 1800, step = 15, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "None"), suggestions = listOf(0, 30, 60, 120, 180, 300, 600),
    )
    val COUNTDOWN_SEC = NumberSetting(
        "countdown_sec", "Start countdown", "Countdown shown to everyone before the game begins.",
        MATCH, default = 10, min = 3, max = 60, step = 1, unit = SettingUnit.SECONDS,
        suggestions = listOf(3, 5, 10, 30), advanced = true,
    )

    // ── Roles ───────────────────────────────────────────────────────────
    val HUNTER_SELECTION = ChoiceSetting(
        "hunter_selection", "Choosing hunters", "How hunters are picked when the game starts.",
        ROLES, default = "manual",
        options = listOf(
            ChoiceOption("manual", "Host picks", "Tap players in the lobby to make them hunters. Random if nobody is picked."),
            ChoiceOption("random", "Random", "A random set of players becomes the hunters."),
            ChoiceOption("volunteers", "Volunteers", "Random among players who volunteered; others fill in if needed."),
            ChoiceOption("one_runner", "Everyone vs one", "One random runner — everyone else hunts."),
        ),
    )
    val HUNTER_COUNT = NumberSetting(
        "hunter_count", "Number of hunters", "Used for random and volunteer selection.",
        ROLES, default = 1, min = 1, max = 20, unit = SettingUnit.COUNT,
        visibleWhen = { it[HUNTER_SELECTION] in setOf("random", "volunteers", "manual") },
    )
    val LATE_JOIN = ChoiceSetting(
        "late_join", "Late joiners", "What happens when someone joins after the game started.",
        ROLES, default = "hunter",
        options = listOf(
            ChoiceOption("hunter", "Join as hunter"),
            ChoiceOption("runner", "Join as runner"),
            ChoiceOption("spectator", "Spectate only"),
            ChoiceOption("blocked", "Not allowed"),
        ),
        advanced = true,
    )

    // ── Location & visibility ───────────────────────────────────────────
    val LOCATION_INTERVAL_SEC = NumberSetting(
        "location_interval_sec", "Location update rate", "How often every phone shares its position. Faster is smoother but uses more battery.",
        VISIBILITY, default = 2, min = 1, max = 60, unit = SettingUnit.SECONDS,
        suggestions = listOf(1, 2, 3, 5, 10, 30),
    )
    val RUNNER_REVEAL = ChoiceSetting(
        "runner_reveal", "Runners shown to hunters", "How hunters see where the runners are.",
        VISIBILITY, default = "live",
        options = listOf(
            ChoiceOption("live", "Live", "Hunters always see every runner in real time."),
            ChoiceOption("interval", "Periodic pings", "Hunters see a snapshot of every runner at a fixed interval."),
            ChoiceOption("hidden", "Hidden", "Hunters never see runners (except via abilities and penalties)."),
        ),
    )
    val RUNNER_REVEAL_INTERVAL_SEC = NumberSetting(
        "runner_reveal_interval_sec", "Ping interval", "Time between runner location pings.",
        VISIBILITY, default = 120, min = 15, max = 1800, step = 15, unit = SettingUnit.SECONDS,
        suggestions = listOf(30, 60, 120, 180, 300, 600),
        visibleWhen = { it[RUNNER_REVEAL] == "interval" },
    )
    val HUNTER_REVEAL = ChoiceSetting(
        "hunter_reveal", "Hunters shown to runners", "Whether runners can see the hunters.",
        VISIBILITY, default = "hidden",
        options = listOf(
            ChoiceOption("hidden", "Hidden", "Runners never see hunters on the map."),
            ChoiceOption("live", "Live", "Runners always see hunters in real time."),
            ChoiceOption("interval", "Periodic pings", "Runners see hunters at a fixed interval."),
        ),
    )
    val HUNTER_REVEAL_INTERVAL_SEC = NumberSetting(
        "hunter_reveal_interval_sec", "Hunter ping interval", "Time between hunter location pings.",
        VISIBILITY, default = 180, min = 15, max = 1800, step = 15, unit = SettingUnit.SECONDS,
        suggestions = listOf(60, 120, 180, 300, 600),
        visibleWhen = { it[HUNTER_REVEAL] == "interval" },
    )
    val PROXIMITY_ALERT_M = NumberSetting(
        "proximity_alert_m", "Hunter-nearby alert", "Runners feel a heartbeat vibration when a hunter is within this distance.",
        VISIBILITY, default = 100, min = 0, max = 1000, step = 10, unit = SettingUnit.METERS,
        specialLabels = mapOf(0L to "Off"), suggestions = listOf(0, 50, 100, 200, 400),
    )
    val PROXIMITY_DIRECTION = ToggleSetting(
        "proximity_direction", "Show threat direction", "The nearby-alert also shows which direction the hunter is in.",
        VISIBILITY, default = false, visibleWhen = { it[PROXIMITY_ALERT_M] > 0 },
    )
    val SHOW_TEAMMATES = ToggleSetting(
        "show_teammates", "See teammates", "Players see the live location of others with the same role.",
        VISIBILITY, default = true,
    )
    val REVEAL_DURING_HEADSTART = ToggleSetting(
        "reveal_during_headstart", "Hunters see runners during head start", "Off makes the head start a true escape window.",
        VISIBILITY, default = false, advanced = true,
    )
    val ENDGAME_REVEAL_MIN = NumberSetting(
        "endgame_reveal_min", "Endgame reveal", "In the final minutes, all runners are revealed live.",
        VISIBILITY, default = 0, min = 0, max = 60, unit = SettingUnit.MINUTES,
        specialLabels = mapOf(0L to "Off"), suggestions = listOf(0, 2, 5, 10),
        visibleWhen = { it[TIME_LIMIT_ENABLED] && it[RUNNER_REVEAL] != "live" },
    )
    val SPECTATORS_SEE_ALL = ToggleSetting(
        "spectators_see_all", "Spectators see everyone", "Caught and eliminated players can watch the whole game.",
        VISIBILITY, default = true,
    )
    val SHOW_TRAILS = ToggleSetting(
        "show_trails", "Movement trails", "Draw a fading trail behind visible players.",
        VISIBILITY, default = true, advanced = true,
    )
    val TRAIL_MINUTES = NumberSetting(
        "trail_minutes", "Trail length", "How much recent movement trails show.",
        VISIBILITY, default = 2, min = 1, max = 30, unit = SettingUnit.MINUTES,
        visibleWhen = { it[SHOW_TRAILS] }, advanced = true,
    )

    // ── Catching ────────────────────────────────────────────────────────
    val CATCH_METHOD = ChoiceSetting(
        "catch_method", "How catches work", "How a hunter proves they tagged someone.",
        CATCHING, default = "confirm",
        options = listOf(
            ChoiceOption("confirm", "Tag & confirm", "The hunter taps Catch; the runner confirms on their phone."),
            ChoiceOption("proximity", "GPS proximity", "The hunter taps Catch when within range — no confirmation."),
            ChoiceOption("code", "Secret code", "Each runner has a code; the hunter must type it in after the tag."),
            ChoiceOption("auto", "Automatic", "Caught automatically when a hunter stays within range."),
        ),
    )
    val CATCH_RADIUS_M = NumberSetting(
        "catch_radius_m", "Catch range", "Maximum GPS distance between hunter and runner for a catch.",
        CATCHING, default = 15, min = 3, max = 200, unit = SettingUnit.METERS,
        suggestions = listOf(5, 10, 15, 25, 50),
        visibleWhen = { it[CATCH_METHOD] in setOf("proximity", "auto") || it[CATCH_REQUIRE_RANGE] },
    )
    val CATCH_REQUIRE_RANGE = ToggleSetting(
        "catch_require_range", "Require range for claims", "Hunters can only claim a catch when GPS says they're close.",
        CATCHING, default = false,
        visibleWhen = { it[CATCH_METHOD] in setOf("confirm", "code") },
    )
    val CATCH_CONFIRM_TIMEOUT_SEC = NumberSetting(
        "catch_confirm_timeout_sec", "Confirmation window", "How long the runner has to respond to a catch claim.",
        CATCHING, default = 30, min = 5, max = 180, unit = SettingUnit.SECONDS,
        visibleWhen = { it[CATCH_METHOD] == "confirm" },
    )
    val CATCH_TIMEOUT_ACCEPTS = ToggleSetting(
        "catch_timeout_accepts", "No answer counts as caught", "If the runner doesn't respond in time, the catch stands.",
        CATCHING, default = true, visibleWhen = { it[CATCH_METHOD] == "confirm" },
    )
    val AUTO_CATCH_DWELL_SEC = NumberSetting(
        "auto_catch_dwell_sec", "Auto-catch dwell", "Hunter must stay within range this long (filters GPS jitter).",
        CATCHING, default = 3, min = 0, max = 60, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "Instant"), visibleWhen = { it[CATCH_METHOD] == "auto" },
    )
    val HUNTER_CATCH_COOLDOWN_SEC = NumberSetting(
        "hunter_catch_cooldown_sec", "Hunter cooldown after a catch", "A hunter is frozen briefly after catching someone.",
        CATCHING, default = 0, min = 0, max = 600, step = 5, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "None"), advanced = true,
    )
    val ALLOW_SELF_REPORT = ToggleSetting(
        "allow_self_report", "Runners can report themselves", "Adds an \"I was caught\" button for runners — handy when GPS is unreliable.",
        CATCHING, default = true,
    )

    // ── Zone ────────────────────────────────────────────────────────────
    val ZONE_ENABLED = ToggleSetting(
        "zone_enabled", "Shrinking zone", "A circular play area that gets smaller over time.",
        ZONE, default = true,
    )
    private val zoneOn: (Settings) -> Boolean = { it[ZONE_ENABLED] }
    val ZONE_CENTER = ChoiceSetting(
        "zone_center", "Zone centre", "Where the play area is centred.",
        ZONE, default = "host",
        options = listOf(
            ChoiceOption("host", "Host's location", "Centred on the host when the game starts."),
            ChoiceOption("centroid", "Middle of all players", "Centred on the average position of everyone."),
            ChoiceOption("custom", "Pick on map", "Choose the exact centre on the map."),
        ),
        visibleWhen = zoneOn,
    )
    val ZONE_CENTER_POINT = PointSetting(
        "zone_center_point", "Custom centre", "Long-press the map to move the centre.",
        ZONE, visibleWhen = { it[ZONE_ENABLED] && it[ZONE_CENTER] == "custom" },
    )
    val ZONE_START_RADIUS_M = NumberSetting(
        "zone_start_radius_m", "Starting radius", "Size of the play area at the start.",
        ZONE, default = 800, min = 50, max = 20_000, step = 25, unit = SettingUnit.METERS,
        suggestions = listOf(250, 500, 800, 1500, 3000, 5000), visibleWhen = zoneOn,
    )
    val ZONE_END_RADIUS_M = NumberSetting(
        "zone_end_radius_m", "Final radius", "Size of the play area once it has fully shrunk.",
        ZONE, default = 75, min = 10, max = 10_000, step = 5, unit = SettingUnit.METERS,
        suggestions = listOf(25, 50, 75, 150, 300), visibleWhen = zoneOn,
    )
    val ZONE_STYLE = ChoiceSetting(
        "zone_style", "Shrink style", "How the zone closes in.",
        ZONE, default = "staged",
        options = listOf(
            ChoiceOption("staged", "Staged", "Battle-royale style: the next circle is announced, then the zone closes to it."),
            ChoiceOption("continuous", "Continuous", "The zone shrinks smoothly and constantly."),
        ),
        visibleWhen = zoneOn,
    )
    val ZONE_STAGES = NumberSetting(
        "zone_stages", "Number of stages", "How many times the zone closes in.",
        ZONE, default = 4, min = 1, max = 12, unit = SettingUnit.COUNT,
        visibleWhen = { it[ZONE_ENABLED] && it[ZONE_STYLE] == "staged" },
    )
    val ZONE_SHIFT = ToggleSetting(
        "zone_shift", "Shifting zone", "Each new circle is placed randomly inside the previous one instead of staying centred.",
        ZONE, default = true, visibleWhen = zoneOn,
    )
    val ZONE_DELAY_MIN = NumberSetting(
        "zone_delay_min", "Shrinking begins after", "Time after hunters are released before the zone starts to shrink.",
        ZONE, default = 3, min = 0, max = 180, unit = SettingUnit.MINUTES,
        specialLabels = mapOf(0L to "Immediately"), suggestions = listOf(0, 2, 5, 10, 15), visibleWhen = zoneOn,
    )
    val ZONE_SHRINK_MIN = NumberSetting(
        "zone_shrink_min", "Shrinking lasts", "Time from the first shrink until the final radius. Shortened automatically to fit the game length.",
        ZONE, default = 20, min = 1, max = 240, unit = SettingUnit.MINUTES,
        suggestions = listOf(5, 10, 15, 20, 30, 45), visibleWhen = zoneOn,
    )
    val ZONE_WARNING_SEC = NumberSetting(
        "zone_warning_sec", "Warning before shrink", "The next circle is shown this long before the zone moves.",
        ZONE, default = 60, min = 0, max = 600, step = 10, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "No warning"), visibleWhen = zoneOn,
    )
    val ZONE_PENALTY = ChoiceSetting(
        "zone_penalty", "Outside the zone", "What happens to a player caught outside the zone.",
        ZONE, default = "eliminate",
        options = listOf(
            ChoiceOption("eliminate", "Eliminated", "They lose immediately (after the grace period)."),
            ChoiceOption("reveal", "Revealed", "Their live location is shown to everyone until they return."),
            ChoiceOption("warn", "Warning only", "They're just warned. Honour system."),
        ),
        visibleWhen = zoneOn,
    )
    val ZONE_GRACE_SEC = NumberSetting(
        "zone_grace_sec", "Grace period", "How long a player may be outside before the penalty applies.",
        ZONE, default = 0, min = 0, max = 300, step = 5, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "Instant"), suggestions = listOf(0, 5, 10, 30, 60),
        visibleWhen = { it[ZONE_ENABLED] && it[ZONE_PENALTY] != "warn" },
    )
    val ZONE_GPS_TOLERANCE = ToggleSetting(
        "zone_gps_tolerance", "Forgive GPS inaccuracy", "Players count as inside while their GPS accuracy circle touches the zone (max 25 m).",
        ZONE, default = true, visibleWhen = zoneOn, advanced = true,
    )
    val ZONE_AFFECTS_HUNTERS = ToggleSetting(
        "zone_affects_hunters", "Zone applies to hunters", "Hunters must also stay inside the zone.",
        ZONE, default = false, visibleWhen = zoneOn, advanced = true,
    )

    // ── Fairness & connectivity ─────────────────────────────────────────
    val STALE_AFTER_SEC = NumberSetting(
        "stale_after_sec", "Location goes stale after", "Locations older than this are shown faded with a \"last seen\" time.",
        FAIRNESS, default = 30, min = 5, max = 600, step = 5, unit = SettingUnit.SECONDS, advanced = true,
    )
    val DISCONNECT_TIMEOUT_SEC = NumberSetting(
        "disconnect_timeout_sec", "Disconnected after", "A player who sends no location for this long is treated as disconnected.",
        FAIRNESS, default = 180, min = 0, max = 1800, step = 15, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "Never"),
    )
    val DISCONNECT_PENALTY = ChoiceSetting(
        "disconnect_penalty", "Disconnected runners", "What happens to runners who go dark (turned off GPS, lost signal).",
        FAIRNESS, default = "reveal",
        options = listOf(
            ChoiceOption("reveal", "Reveal last location", "Hunters see their last known position."),
            ChoiceOption("eliminate", "Eliminate", "They're out of the game."),
            ChoiceOption("nothing", "Nothing"),
        ),
        visibleWhen = { it[DISCONNECT_TIMEOUT_SEC] > 0 },
    )
    val MAX_ACCURACY_M = NumberSetting(
        "max_accuracy_m", "Ignore fixes worse than", "GPS readings less accurate than this are not used for catches or zone checks.",
        FAIRNESS, default = 75, min = 10, max = 500, step = 5, unit = SettingUnit.METERS, advanced = true,
    )

    val ALL: List<Setting<*>> = listOf(
        TIME_LIMIT_ENABLED, DURATION_MIN, HEADSTART_SEC, COUNTDOWN_SEC,
        HUNTER_SELECTION, HUNTER_COUNT, LATE_JOIN,
        LOCATION_INTERVAL_SEC, RUNNER_REVEAL, RUNNER_REVEAL_INTERVAL_SEC, HUNTER_REVEAL, HUNTER_REVEAL_INTERVAL_SEC,
        PROXIMITY_ALERT_M, PROXIMITY_DIRECTION, SHOW_TEAMMATES, REVEAL_DURING_HEADSTART, ENDGAME_REVEAL_MIN,
        SPECTATORS_SEE_ALL, SHOW_TRAILS, TRAIL_MINUTES,
        CATCH_METHOD, CATCH_REQUIRE_RANGE, CATCH_RADIUS_M, CATCH_CONFIRM_TIMEOUT_SEC, CATCH_TIMEOUT_ACCEPTS,
        AUTO_CATCH_DWELL_SEC, HUNTER_CATCH_COOLDOWN_SEC, ALLOW_SELF_REPORT,
        ZONE_ENABLED, ZONE_CENTER, ZONE_CENTER_POINT, ZONE_START_RADIUS_M, ZONE_END_RADIUS_M, ZONE_STYLE, ZONE_STAGES,
        ZONE_SHIFT, ZONE_DELAY_MIN, ZONE_SHRINK_MIN, ZONE_WARNING_SEC, ZONE_PENALTY, ZONE_GRACE_SEC,
        ZONE_GPS_TOLERANCE, ZONE_AFFECTS_HUNTERS,
        STALE_AFTER_SEC, DISCONNECT_TIMEOUT_SEC, DISCONNECT_PENALTY, MAX_ACCURACY_M,
    )
}
