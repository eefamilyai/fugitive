package app.fugitive.engine.mode

import app.fugitive.engine.geo.Geo
import app.fugitive.engine.rules.DataKeys
import app.fugitive.engine.rules.EndReasons
import app.fugitive.engine.rules.EventTypes
import app.fugitive.engine.rules.OutReasons
import app.fugitive.engine.rules.RuleContext
import app.fugitive.engine.settings.ChoiceOption
import app.fugitive.engine.settings.ChoiceSetting
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.NumberSetting
import app.fugitive.engine.settings.Setting
import app.fugitive.engine.settings.SettingCategory
import app.fugitive.engine.settings.SettingUnit
import app.fugitive.engine.settings.ToggleSetting
import app.fugitive.engine.zone.ZoneMath
import app.fugitive.model.GameResult
import app.fugitive.model.GameState
import app.fugitive.model.Objective
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import app.fugitive.model.jsonOf
import app.fugitive.model.long
import app.fugitive.model.string
import kotlinx.serialization.json.JsonPrimitive

/** Runners must reach an extraction point that opens near the end, and hold it to escape. */
class ExtractionMode : BaseMode() {
    override val id = ID
    override val name = "Extraction"
    override val tagline = "Reach the evac point before time runs out."
    override val icon = "helicopter"
    override val accent = 0xFFFBBF24
    override val rules = listOf(
        "Plays like Classic, but surviving isn't enough.",
        "Extraction points open on the map near the end of the game.",
        "Runners must stand inside one for the hold time to escape.",
        "Escaped runners win. Anyone still in the field when time runs out loses.",
        "Hunters win if nobody escapes.",
    )

    val points = NumberSetting(
        "extraction_points", "Extraction points", "How many evac points open.",
        SettingCategory.MODE, default = 1, min = 1, max = 5, unit = SettingUnit.COUNT,
    )
    val openMin = NumberSetting(
        "extraction_open_min", "Opens with … left", "Extraction opens this many minutes before the end.",
        SettingCategory.MODE, default = 8, min = 1, max = 120, unit = SettingUnit.MINUTES,
        suggestions = listOf(3, 5, 8, 10, 15),
    )
    val radius = NumberSetting(
        "extraction_radius_m", "Extraction radius", "Size of each evac point.",
        SettingCategory.MODE, default = 30, min = 10, max = 300, step = 5, unit = SettingUnit.METERS,
    )
    val holdSec = NumberSetting(
        "extraction_hold_sec", "Hold time", "Runners must stay inside this long to escape.",
        SettingCategory.MODE, default = 20, min = 0, max = 300, step = 5, unit = SettingUnit.SECONDS,
        specialLabels = mapOf(0L to "Instant"),
    )
    val placement = ChoiceSetting(
        "extraction_placement", "Placement", "Where evac points appear.",
        SettingCategory.MODE, default = "anywhere",
        options = listOf(
            ChoiceOption("anywhere", "Anywhere in the play area"),
            ChoiceOption("edge", "Near the edge", "Far from the centre — a long run."),
            ChoiceOption("final_zone", "Inside the final zone"),
        ),
    )
    val visibleToHunters = ToggleSetting(
        "extraction_visible_hunters", "Hunters see extraction", "Off makes it a secret only runners know.",
        SettingCategory.MODE, default = true,
    )

    override fun settings(): List<Setting<*>> = listOf(points, openMin, radius, holdSec, placement, visibleToHunters)

    override fun defaults(): Map<String, JsonPrimitive> = mapOf(
        CoreSettings.TIME_LIMIT_ENABLED.key to JsonPrimitive(true),
        CoreSettings.DURATION_MIN.key to JsonPrimitive(25),
    )
    override fun hiddenSettings(): Set<String> = setOf(CoreSettings.TIME_LIMIT_ENABLED.key)

    override fun roleLabel(role: Role): String = if (role == Role.RUNNER) "Runner" else super.roleLabel(role)
    override fun objective(role: Role): String = when (role) {
        Role.HUNTER -> "Stop the runners from escaping"
        Role.RUNNER -> "Survive, then reach extraction"
        else -> super.objective(role)
    }

    override fun validate(ctx: ValidationContext): List<ConfigIssue> {
        val issues = mutableListOf<ConfigIssue>()
        if (ctx.settings[openMin] >= ctx.settings[CoreSettings.DURATION_MIN]) {
            issues += ConfigIssue("Extraction opens before the game even starts — it will be open from the start.", blocking = false, settingKey = openMin.key)
        }
        return issues
    }

    override fun setup(ctx: SetupContext, game: GameState): GameState {
        val area = ctx.playArea ?: return game
        val endsAt = game.endsAt ?: return game
        val r = ctx.settings[radius].toDouble()
        val opens = (endsAt - ctx.settings[openMin] * 60_000).coerceAtLeast(game.headstartEndsAt)
        val visibleTo = if (ctx.settings[visibleToHunters]) emptyList() else listOf(Role.RUNNER.id)
        val objectives = (0 until ctx.settings[points].toInt()).associate { i ->
            val point = when (ctx.settings[placement]) {
                "final_zone" -> game.zone?.final?.let { f -> Geo.randomPointInCircle(f.center, (f.radius - r).coerceAtLeast(0.0), ctx.random) }
                "edge" -> Geo.destination(area.center, ctx.random.nextDouble() * 360, area.radius * (0.7 + ctx.random.nextDouble() * 0.2))
                else -> null
            } ?: Geo.randomPointInCircle(area.center, (area.radius * 0.85 - r).coerceAtLeast(0.0), ctx.random)
            val id = "extract_$i"
            id to Objective(
                id = id, type = OBJECTIVE_TYPE, lat = point.lat, lng = point.lng, radius = r,
                activeFrom = opens, activeUntil = endsAt,
                label = if (ctx.settings[points] > 1) "Evac ${'A' + i}" else "Evac", visibleTo = visibleTo,
            )
        }
        return game.copy(objectives = objectives)
    }

    override fun onTick(ctx: RuleContext) {
        val open = ctx.state.objectives.values.filter { it.type == OBJECTIVE_TYPE && it.isActive(ctx.now) }
        if (open.isEmpty()) return
        ctx.once("extraction_open") {
            ctx.emit(EventTypes.OBJECTIVE, "🚁 Extraction is open! Get to the evac point.", importance = 2)
        }
        val hold = ctx.settings[holdSec] * 1000
        val forgive = ctx.settings[CoreSettings.ZONE_GPS_TOLERANCE]
        for (runner in ctx.state.active(Role.RUNNER)) {
            val fix = ctx.usableFix(runner.id) ?: continue
            val inside = open.firstOrNull { o ->
                Geo.distance(fix.point, o.point) <= o.radius + ZoneMath.tolerance(fix, forgive)
            }
            val since = runner.data.long(DataKeys.EXTRACTION_SINCE)
            if (inside == null) {
                if (since != null) {
                    ctx.setPlayerData(runner.id, DataKeys.EXTRACTION_SINCE, null)
                    ctx.setPlayerData(runner.id, DataKeys.EXTRACTION_ID, null)
                }
                continue
            }
            if (since == null || runner.data.string(DataKeys.EXTRACTION_ID) != inside.id) {
                ctx.setPlayerData(runner.id, DataKeys.EXTRACTION_SINCE, jsonOf(ctx.now))
                ctx.setPlayerData(runner.id, DataKeys.EXTRACTION_ID, jsonOf(inside.id))
                if (hold > 0) {
                    ctx.emit(EventTypes.OBJECTIVE, "Hold position — extracting in ${hold / 1000}s", target = runner.id, audience = listOf(runner.id), importance = 1)
                    ctx.emit(EventTypes.OBJECTIVE, "⚠️ ${runner.name} is at ${inside.label ?: "extraction"}!", target = runner.id, audience = listOf(Role.HUNTER.id), importance = 2)
                }
                if (hold > 0) continue
            }
            val start = runner.data.long(DataKeys.EXTRACTION_SINCE) ?: ctx.now
            if (ctx.now - start >= hold) {
                ctx.setStatus(runner.id, PlayerStatus.ESCAPED, OutReasons.ESCAPED)
                ctx.emit(EventTypes.ESCAPED, "🚁 ${runner.name} escaped!", target = runner.id, importance = 2)
            }
        }
    }

    override fun checkResult(ctx: RuleContext): GameResult? {
        val players = ctx.state.players.values
        val active = players.filter { it.role == Role.RUNNER && it.status == PlayerStatus.ACTIVE }
        val escaped = players.filter { it.status == PlayerStatus.ESCAPED }
        val hunters = players.filter { it.role == Role.HUNTER }
        if (active.isEmpty() || ctx.isTimeUp) {
            return if (escaped.isNotEmpty()) GameResult(
                reason = EndReasons.ESCAPED, winningRole = Role.RUNNER, winnerIds = escaped.map { it.id },
                headline = "Runners escaped!",
                detail = "${escaped.size} of ${players.count { it.originalRole == Role.RUNNER }} made it out.",
            ) else GameResult(
                reason = if (ctx.isTimeUp) EndReasons.TIME_UP else EndReasons.ALL_CAUGHT,
                winningRole = Role.HUNTER, winnerIds = hunters.map { it.id },
                headline = "Hunters win!", detail = "Nobody made it to extraction.",
            )
        }
        if (hunters.none { it.status == PlayerStatus.ACTIVE } && ctx.stage.huntersReleased) {
            return GameResult(
                reason = EndReasons.NO_HUNTERS, winningRole = Role.RUNNER, winnerIds = (active + escaped).map { it.id },
                headline = "Runners win!", detail = "No hunters are left in the game.",
            )
        }
        return null
    }

    companion object {
        const val ID = "extraction"
        const val OBJECTIVE_TYPE = "extraction"
    }
}
