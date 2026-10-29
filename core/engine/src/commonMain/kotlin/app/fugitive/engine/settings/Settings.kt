package app.fugitive.engine.settings

import kotlinx.serialization.json.JsonPrimitive

/** Ordered collection of settings available for a particular mode. */
class SettingsSchema(val settings: List<Setting<*>>, val hidden: Set<String> = emptySet()) {
    private val byKey = settings.associateBy { it.key }

    init {
        val dupes = settings.groupBy { it.key }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "Duplicate setting keys: $dupes" }
    }

    operator fun get(key: String): Setting<*>? = byKey[key]

    /** Settings grouped by category, in display order, skipping hidden keys. */
    fun grouped(): List<Pair<SettingCategory, List<Setting<*>>>> =
        settings.filter { it.key !in hidden }
            .groupBy { it.category }
            .toList()
            .sortedBy { it.first.order }
}

/**
 * Typed read access to configuration values.
 * Look-up order: explicit [values] → [overrides] (mode defaults) → the setting's default.
 */
class Settings(
    val schema: SettingsSchema,
    val values: Map<String, JsonPrimitive>,
    val overrides: Map<String, JsonPrimitive> = emptyMap(),
) {
    operator fun <T : Any> get(setting: Setting<T>): T =
        setting.decode(values[setting.key] ?: overrides[setting.key])

    fun raw(key: String): JsonPrimitive? = values[key] ?: overrides[key]

    fun isVisible(setting: Setting<*>): Boolean = setting.key !in schema.hidden && setting.visibleWhen(this)

    /** Copy with one value changed (used by the editor). */
    fun <T : Any> with(setting: Setting<T>, value: T): Settings =
        Settings(schema, values + (setting.key to setting.encode(value)), overrides)

    /** Seconds → millis helper for duration settings. */
    fun millis(setting: NumberSetting): Long = get(setting) * when (setting.unit) {
        SettingUnit.MINUTES -> 60_000L
        SettingUnit.SECONDS -> 1_000L
        else -> 1L
    }
}
