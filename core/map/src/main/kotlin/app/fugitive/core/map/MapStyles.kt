package app.fugitive.core.map

/** How a style is obtained. */
sealed interface StyleSource {
    /** A ready-made MapLibre style JSON URL. */
    data class Url(val uri: String) : StyleSource

    /** A style assembled on the device (see [StyleComposer]) — e.g. satellite + vector labels. */
    data class Composed(val recipe: StyleRecipe) : StyleSource
}

enum class StyleCategory(val label: String) { SATELLITE("Satellite"), STREETS("Streets"), TERRAIN("Terrain"), DARK("Dark") }

/**
 * A base map. Add your own with [MapStyles.register] — any MapLibre style URL works,
 * or compose one from raster/vector pieces with a [StyleRecipe].
 */
data class MapStyle(
    val id: String,
    val name: String,
    val description: String,
    val source: StyleSource,
    val category: StyleCategory,
    /** Whether markers/labels should assume a dark background. */
    val dark: Boolean,
    /** Deepest zoom the user may reach (imagery gets soft when over-zoomed too far). */
    val maxZoom: Double = 21.0,
    /** Vector styles can have terrain (hillshade) shading layered in. */
    val supportsTerrainShading: Boolean = false,
    /** Optional preview thumbnail resource (drawable). */
    val previewRes: Int? = null,
    /** Needs an API key the user supplied. */
    val premium: Boolean = false,
)

/** Recipes understood by [StyleComposer]. */
sealed interface StyleRecipe {
    /** Raster imagery, optionally with roads and labels from the OpenFreeMap vector tiles on top. */
    data class Imagery(val tiles: String, val attribution: String, val maxZoom: Int, val labels: Boolean) : StyleRecipe

    /** A vector style URL with terrain shading (hillshade) inserted below roads and labels. */
    data class Shaded(val baseUrl: String, val exaggeration: Float, val dark: Boolean) : StyleRecipe

    /** Plain raster tiles (e.g. a topographic map). */
    data class Raster(val tiles: String, val attribution: String, val maxZoom: Int) : StyleRecipe
}

/**
 * Registry of base maps. Defaults need no API key:
 * vector maps from OpenFreeMap (© OpenMapTiles © OpenStreetMap contributors),
 * imagery and topographic maps from Esri, elevation from the AWS Terrain Tiles open dataset.
 */
object MapStyles {
    const val AUTO = "auto"
    const val CUSTOM = "custom"

    private const val OFM = "https://tiles.openfreemap.org/styles"
    const val ESRI_IMAGERY = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
    const val ESRI_TOPO = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}"
    const val ESRI_ATTRIBUTION = "Imagery © Esri, Maxar, Earthstar Geographics, and the GIS User Community"

    val HYBRID = MapStyle(
        "hybrid", "Satellite", "Aerial photos with roads and names — see every tree, wall and alley",
        StyleSource.Composed(StyleRecipe.Imagery(ESRI_IMAGERY, ESRI_ATTRIBUTION, maxZoom = 19, labels = true)),
        StyleCategory.SATELLITE, dark = true, maxZoom = 19.6, previewRes = R.drawable.map_preview_hybrid,
    )
    val SATELLITE = MapStyle(
        "satellite", "Satellite (clean)", "Pure aerial imagery, no labels",
        StyleSource.Composed(StyleRecipe.Imagery(ESRI_IMAGERY, ESRI_ATTRIBUTION, maxZoom = 19, labels = false)),
        StyleCategory.SATELLITE, dark = true, maxZoom = 19.6, previewRes = R.drawable.map_preview_satellite,
    )
    val STREETS = MapStyle(
        "liberty", "Streets 3D", "Detailed streets, paths and 3D buildings (tilt to see them)",
        StyleSource.Url("$OFM/liberty"), StyleCategory.STREETS, dark = false,
        supportsTerrainShading = true, previewRes = R.drawable.map_preview_liberty,
    )
    val TERRAIN = MapStyle(
        "terrain", "Terrain", "Streets with strong hill shading — read the land at a glance",
        StyleSource.Composed(StyleRecipe.Shaded("$OFM/liberty", exaggeration = 0.6f, dark = false)),
        StyleCategory.TERRAIN, dark = false, previewRes = R.drawable.map_preview_terrain,
    )
    val TOPO = MapStyle(
        "topo", "Topographic", "Classic topo map with contours, trails and landmarks",
        StyleSource.Composed(StyleRecipe.Raster(ESRI_TOPO, "Map © Esri, HERE, Garmin, USGS, NGA, OpenStreetMap contributors", maxZoom = 19)),
        StyleCategory.TERRAIN, dark = false, maxZoom = 20.0, previewRes = R.drawable.map_preview_topo,
    )
    val BRIGHT = MapStyle(
        "bright", "Bright", "High-contrast classic map", StyleSource.Url("$OFM/bright"), StyleCategory.STREETS,
        dark = false, supportsTerrainShading = true, previewRes = R.drawable.map_preview_bright,
    )
    val POSITRON = MapStyle(
        "positron", "Minimal", "Clean and quiet — players stand out", StyleSource.Url("$OFM/positron"), StyleCategory.STREETS,
        dark = false, supportsTerrainShading = true, previewRes = R.drawable.map_preview_positron,
    )
    val DARK = MapStyle(
        "dark", "Night", "Dark map, easy on the eyes at night", StyleSource.Url("$OFM/dark"), StyleCategory.DARK,
        dark = true, supportsTerrainShading = true, previewRes = R.drawable.map_preview_dark,
    )
    val FIORD = MapStyle(
        "fiord", "Midnight", "Moody blue dark map", StyleSource.Url("$OFM/fiord"), StyleCategory.DARK,
        dark = true, supportsTerrainShading = true, previewRes = R.drawable.map_preview_fiord,
    )

    private val registry = linkedMapOf<String, MapStyle>().apply {
        listOf(HYBRID, SATELLITE, STREETS, TERRAIN, TOPO, BRIGHT, POSITRON, DARK, FIORD).forEach { put(it.id, it) }
    }

    /** Register an additional style (e.g. from a plugin module). */
    fun register(style: MapStyle) { registry[style.id] = style }

    /** Premium styles unlocked with a (free) MapTiler key. */
    fun mapTiler(key: String): List<MapStyle> {
        if (key.isBlank()) return emptyList()
        fun mt(id: String, name: String, desc: String, cat: StyleCategory, dark: Boolean, maxZoom: Double = 22.0) = MapStyle(
            "mt-$id", name, desc, StyleSource.Url("https://api.maptiler.com/maps/$id/style.json?key=${key.trim()}"),
            cat, dark, maxZoom = maxZoom, premium = true,
        )
        return listOf(
            mt("hybrid", "Satellite HD", "MapTiler high-resolution imagery with labels", StyleCategory.SATELLITE, true, 21.0),
            mt("outdoor-v2", "Outdoor", "Contours, trails, hill shading and peaks", StyleCategory.TERRAIN, false),
            mt("topo-v2", "Topo", "Detailed topographic map with contours", StyleCategory.TERRAIN, false),
            mt("streets-v2", "Streets HD", "MapTiler streets with 3D buildings", StyleCategory.STREETS, false),
            mt("winter-v2", "Winter", "Ski & snow focused outdoor map", StyleCategory.TERRAIN, false),
            mt("streets-v2-dark", "Streets Dark", "MapTiler dark streets", StyleCategory.DARK, true),
        )
    }

    fun all(mapTilerKey: String = ""): List<MapStyle> = registry.values.toList() + mapTiler(mapTilerKey)

    /** Picks the style for a preference id; "auto" = Satellite. Unknown ids fall back gracefully. */
    fun resolve(id: String, darkTheme: Boolean, customUrl: String = "", mapTilerKey: String = ""): MapStyle = when {
        id == CUSTOM && customUrl.isNotBlank() ->
            MapStyle(CUSTOM, "Custom", customUrl, StyleSource.Url(customUrl), StyleCategory.STREETS, dark = darkTheme)
        id == AUTO -> HYBRID
        else -> all(mapTilerKey).firstOrNull { it.id == id } ?: HYBRID
    }
}
