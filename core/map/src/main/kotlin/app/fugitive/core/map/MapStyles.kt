package app.fugitive.core.map

/** A base map style. Any MapLibre style JSON URL works, including your own or MapTiler/Stadia ones. */
data class MapStyle(
    val id: String,
    val name: String,
    val uri: String,
    val dark: Boolean,
    val description: String = "",
)

/**
 * Registry of base maps. The defaults use OpenFreeMap — free vector tiles with
 * no API key and no usage limits (© OpenMapTiles, © OpenStreetMap contributors).
 */
object MapStyles {
    const val AUTO = "auto"
    const val CUSTOM = "custom"

    val LIBERTY = MapStyle("liberty", "Streets", "https://tiles.openfreemap.org/styles/liberty", dark = false, "Detailed streets with 3D buildings")
    val BRIGHT = MapStyle("bright", "Bright", "https://tiles.openfreemap.org/styles/bright", dark = false, "High-contrast classic map")
    val POSITRON = MapStyle("positron", "Minimal light", "https://tiles.openfreemap.org/styles/positron", dark = false, "Clean and quiet — markers pop")
    val DARK = MapStyle("dark", "Night", "https://tiles.openfreemap.org/styles/dark", dark = true, "Dark map, easy on the eyes at night")
    val FIORD = MapStyle("fiord", "Midnight blue", "https://tiles.openfreemap.org/styles/fiord", dark = true, "Moody blue dark map")

    private val registry = linkedMapOf(
        LIBERTY.id to LIBERTY, BRIGHT.id to BRIGHT, POSITRON.id to POSITRON, DARK.id to DARK, FIORD.id to FIORD,
    )

    val all: List<MapStyle> get() = registry.values.toList()

    /** Register an additional style (e.g. from a plugin module). */
    fun register(style: MapStyle) { registry[style.id] = style }

    /** Picks the style for a preference id; "auto" follows the app theme. */
    fun resolve(id: String, darkTheme: Boolean, customUrl: String = ""): MapStyle = when {
        id == CUSTOM && customUrl.isNotBlank() -> MapStyle(CUSTOM, "Custom", customUrl, dark = darkTheme)
        id == AUTO || registry[id] == null -> if (darkTheme) DARK else LIBERTY
        else -> registry.getValue(id)
    }
}
