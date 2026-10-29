package app.fugitive.engine.settings

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Declarative description of one game setting.
 *
 * Settings are data, not code: the lobby's settings editor renders any
 * [Setting] automatically, the backend stores its value as a JSON primitive
 * under [key], and rules read it through [Settings]. Adding an option to the
 * game is therefore a single declaration here (or in a mode / ability).
 */
sealed class Setting<T : Any> {
    abstract val key: String
    abstract val title: String
    abstract val description: String
    abstract val category: SettingCategory
    abstract val default: T
    /** Shown only when this returns true (e.g. interval only when reveal mode is "interval"). */
    abstract val visibleWhen: (Settings) -> Boolean
    /** Advanced settings are collapsed by default in the editor. */
    abstract val advanced: Boolean
    /** Rendered indented under the previous setting (sub-option). */
    abstract val nested: Boolean

    abstract fun decode(raw: JsonPrimitive?): T
    abstract fun encode(value: T): JsonPrimitive
    /** Human readable value for summaries, in metric units (UI may reformat). */
    abstract fun describe(value: T): String

    @Suppress("UNCHECKED_CAST")
    fun describeRaw(raw: JsonPrimitive?): String = describe(decode(raw))
}

data class SettingCategory(val id: String, val title: String, val icon: String, val order: Int) {
    companion object {
        val MATCH = SettingCategory("match", "Match", "timer", 0)
        val ROLES = SettingCategory("roles", "Roles & teams", "groups", 10)
        val VISIBILITY = SettingCategory("visibility", "Location & visibility", "visibility", 20)
        val CATCHING = SettingCategory("catching", "Catching", "back_hand", 30)
        val ZONE = SettingCategory("zone", "Shrinking zone", "radar", 40)
        val MODE = SettingCategory("mode", "Mode rules", "tune", 50)
        val ABILITIES = SettingCategory("abilities", "Abilities", "bolt", 60)
        val FAIRNESS = SettingCategory("fairness", "Fairness & connectivity", "shield", 70)
    }
}

enum class SettingUnit { NONE, SECONDS, MINUTES, METERS, COUNT, PERCENT }

class ToggleSetting(
    override val key: String,
    override val title: String,
    override val description: String = "",
    override val category: SettingCategory,
    override val default: Boolean,
    override val visibleWhen: (Settings) -> Boolean = { true },
    override val advanced: Boolean = false,
    override val nested: Boolean = false,
) : Setting<Boolean>() {
    override fun decode(raw: JsonPrimitive?): Boolean = raw?.booleanOrNull ?: default
    override fun encode(value: Boolean) = JsonPrimitive(value)
    override fun describe(value: Boolean) = if (value) "On" else "Off"
}

class NumberSetting(
    override val key: String,
    override val title: String,
    override val description: String = "",
    override val category: SettingCategory,
    override val default: Long,
    val min: Long,
    val max: Long,
    val step: Long = 1,
    val unit: SettingUnit = SettingUnit.NONE,
    /** Labels for special values, e.g. 0 -> "Off" or "Instant". */
    val specialLabels: Map<Long, String> = emptyMap(),
    /** Quick-pick values offered as chips next to the slider. */
    val suggestions: List<Long> = emptyList(),
    override val visibleWhen: (Settings) -> Boolean = { true },
    override val advanced: Boolean = false,
    override val nested: Boolean = false,
) : Setting<Long>() {
    init { require(min <= default && default <= max) { "Default of $key outside range" } }

    override fun decode(raw: JsonPrimitive?): Long {
        val v = raw?.longOrNull ?: raw?.doubleOrNull?.toLong() ?: return default
        return v.coerceIn(min, max)
    }
    override fun encode(value: Long) = JsonPrimitive(value.coerceIn(min, max))
    override fun describe(value: Long): String = specialLabels[value] ?: SettingFormat.format(value, unit)
}

data class ChoiceOption(val id: String, val label: String, val description: String = "")

class ChoiceSetting(
    override val key: String,
    override val title: String,
    override val description: String = "",
    override val category: SettingCategory,
    override val default: String,
    val options: List<ChoiceOption>,
    override val visibleWhen: (Settings) -> Boolean = { true },
    override val advanced: Boolean = false,
    override val nested: Boolean = false,
) : Setting<String>() {
    init { require(options.any { it.id == default }) { "Default of $key is not an option" } }

    override fun decode(raw: JsonPrimitive?): String {
        val v = raw?.contentOrNull ?: return default
        return if (options.any { it.id == v }) v else default
    }
    override fun encode(value: String) = JsonPrimitive(value)
    override fun describe(value: String) = options.firstOrNull { it.id == value }?.label ?: value
    fun option(id: String) = options.firstOrNull { it.id == id }
}

/** A map location stored as "lat,lng"; empty string means "not set". */
class PointSetting(
    override val key: String,
    override val title: String,
    override val description: String = "",
    override val category: SettingCategory,
    override val default: String = "",
    override val visibleWhen: (Settings) -> Boolean = { true },
    override val advanced: Boolean = false,
    override val nested: Boolean = false,
) : Setting<String>() {
    override fun decode(raw: JsonPrimitive?): String = raw?.contentOrNull ?: default
    override fun encode(value: String) = JsonPrimitive(value)
    override fun describe(value: String) = if (value.isBlank()) "Not set" else "Pinned on map"
}

object SettingFormat {
    fun format(value: Long, unit: SettingUnit): String = when (unit) {
        SettingUnit.NONE, SettingUnit.COUNT -> value.toString()
        SettingUnit.PERCENT -> "$value%"
        SettingUnit.SECONDS -> duration(value)
        SettingUnit.MINUTES -> if (value >= 60 && value % 60 == 0L) "${value / 60} h" else if (value >= 60) "${value / 60} h ${value % 60} min" else "$value min"
        SettingUnit.METERS -> meters(value.toDouble())
    }

    fun duration(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds % 60 == 0L -> "${seconds / 60} min"
        else -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    fun meters(m: Double): String = when {
        m < 1000 -> "${m.toInt()} m"
        m < 10_000 -> "${app.fugitive.model.Fmt.fixed(m / 1000, 1)} km"
        else -> "${(m / 1000).toInt()} km"
    }
}
