package app.fugitive.core.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** What [GameMap] hands to MapLibre: either a URL it loads itself, or a JSON document. */
sealed interface ResolvedStyle {
    data class Uri(val uri: String) : ResolvedStyle
    data class Json(val json: String) : ResolvedStyle
}

/**
 * Builds style documents on the device: satellite imagery with vector roads/labels on top,
 * and terrain shading (hillshade from elevation tiles) layered into any vector style.
 * Base style JSON is cached in memory and on disk so maps still open with a weak signal.
 */
class StyleComposer(private val context: Context) {
    private val memory = ConcurrentHashMap<String, String>()

    suspend fun resolve(style: MapStyle, terrainShading: Boolean): ResolvedStyle {
        val src = style.source
        return when {
            src is StyleSource.Url && !(terrainShading && style.supportsTerrainShading) -> ResolvedStyle.Uri(src.uri)
            src is StyleSource.Url -> ResolvedStyle.Json(shaded(src.uri, 0.35f, style.dark))
            src is StyleSource.Composed -> when (val r = src.recipe) {
                is StyleRecipe.Imagery -> ResolvedStyle.Json(imagery(r))
                is StyleRecipe.Shaded -> ResolvedStyle.Json(shaded(r.baseUrl, r.exaggeration, r.dark))
                is StyleRecipe.Raster -> ResolvedStyle.Json(raster(r))
            }
            else -> error("Unsupported style source")
        }
    }

    // ── Recipes ─────────────────────────────────────────────────────────

    private fun raster(r: StyleRecipe.Raster): String = JSONObject().apply {
        put("version", 8)
        put("glyphs", GLYPHS)
        put("sources", JSONObject().put("raster", rasterSource(r.tiles, r.attribution, r.maxZoom)))
        put("layers", JSONArray().put(background("#DDDDDD")).put(rasterLayer("raster", "raster")))
    }.toString()

    private suspend fun imagery(r: StyleRecipe.Imagery): String {
        val style = JSONObject()
        style.put("version", 8)
        style.put("glyphs", GLYPHS)
        val sources = JSONObject().put("imagery", rasterSource(r.tiles, r.attribution, r.maxZoom))
        val layers = JSONArray().put(background("#0B0F15")).put(
            rasterLayer("imagery", "imagery").put("paint", JSONObject().put("raster-fade-duration", 150).put("raster-contrast", 0.08).put("raster-saturation", 0.05)),
        )
        if (r.labels) {
            // Borrow roads, paths and labels from the vector style and restyle them for imagery.
            runCatching { JSONObject(baseJson(LIBERTY)) }.getOrNull()?.let { base ->
                base.optString("sprite").takeIf { it.isNotEmpty() }?.let { style.put("sprite", it) }
                val baseSources = base.getJSONObject("sources")
                sources.put(VECTOR, baseSources.getJSONObject(VECTOR))
                val baseLayers = base.getJSONArray("layers")
                for (i in 0 until baseLayers.length()) {
                    val l = baseLayers.getJSONObject(i)
                    if (l.optString("source") != VECTOR) continue
                    adaptForImagery(l)?.let { layers.put(it) }
                }
            }
        }
        style.put("sources", sources)
        style.put("layers", layers)
        return style.toString()
    }

    /** Keeps roads, paths, rail, boundaries and labels; tuned to read well on photos. */
    private fun adaptForImagery(l: JSONObject): JSONObject? {
        val type = l.optString("type")
        val layer = l.optString("source-layer")
        val id = l.optString("id")
        val paint = l.optJSONObject("paint") ?: JSONObject().also { l.put("paint", it) }
        return when {
            type == "line" && layer == "transportation" -> {
                if (id.contains("casing") || id.contains("hatching")) return null
                val minor = id.contains("path") || id.contains("service") || id.contains("minor") || id.contains("track")
                paint.put("line-opacity", if (minor) 0.45 else 0.6)
                if (id.contains("path")) paint.put("line-color", "#FFF3D6")
                l
            }
            type == "line" && layer == "boundary" -> l
            type == "symbol" -> {
                val layout = l.optJSONObject("layout")
                if (layout?.has("text-field") == true) {
                    paint.put("text-color", if (layer == "water_name") "#CDE8FF" else "#FFFFFF")
                    paint.put("text-halo-color", "rgba(0,0,0,0.85)")
                    paint.put("text-halo-width", 1.4)
                    paint.put("text-halo-blur", 0.4)
                }
                l
            }
            else -> null
        }
    }

    private suspend fun shaded(baseUrl: String, exaggeration: Float, dark: Boolean): String {
        val base = JSONObject(baseJson(baseUrl))
        base.getJSONObject("sources").put(DEM, demSource())
        val layers = base.getJSONArray("layers")
        val hill = JSONObject()
            .put("id", "fug-hillshade").put("type", "hillshade").put("source", DEM)
            .put("paint", JSONObject()
                .put("hillshade-exaggeration", exaggeration)
                .put("hillshade-shadow-color", if (dark) "#000000" else "#3E3426")
                .put("hillshade-highlight-color", if (dark) "#2A3342" else "#FFFFFF")
                .put("hillshade-accent-color", if (dark) "#05070A" else "#6B5E4B")
                .put("hillshade-illumination-anchor", "viewport"))
        // Insert after land/water fills, before roads and buildings, so streets stay crisp.
        var at = layers.length()
        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            val sl = l.optString("source-layer")
            if (sl == "transportation" || sl == "building" || l.optString("type") == "symbol") { at = i; break }
        }
        val rebuilt = JSONArray()
        for (i in 0 until layers.length()) {
            if (i == at) rebuilt.put(hill)
            rebuilt.put(layers.getJSONObject(i))
        }
        if (at == layers.length()) rebuilt.put(hill)
        base.put("layers", rebuilt)
        return base.toString()
    }

    // ── Pieces ──────────────────────────────────────────────────────────

    private fun rasterSource(tiles: String, attribution: String, maxZoom: Int) = JSONObject()
        .put("type", "raster").put("tiles", JSONArray().put(tiles)).put("tileSize", 256)
        .put("maxzoom", maxZoom).put("attribution", attribution)

    private fun demSource() = JSONObject()
        .put("type", "raster-dem").put("tiles", JSONArray().put(TERRARIUM)).put("tileSize", 256)
        .put("maxzoom", 15).put("encoding", "terrarium")
        .put("attribution", "Elevation: Mapzen Terrain Tiles (AWS Open Data)")

    private fun background(color: String) = JSONObject().put("id", "fug-bg").put("type", "background")
        .put("paint", JSONObject().put("background-color", color))

    private fun rasterLayer(id: String, source: String) = JSONObject().put("id", id).put("type", "raster").put("source", source)

    /** Fetches a base style JSON: memory → network → disk cache. */
    private suspend fun baseJson(url: String): String = memory[url] ?: withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "styles/" + url.hashCode().toUInt().toString(16) + ".json")
        val fetched = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 8_000; readTimeout = 10_000
                setRequestProperty("User-Agent", "Fugitive-Android")
                inputStream.bufferedReader().use { it.readText() }
            }
        }.getOrNull()
        val json = fetched?.also { file.parentFile?.mkdirs(); file.writeText(it) } ?: file.takeIf { it.exists() }?.readText()
            ?: error("Couldn't load map style (no connection)")
        memory[url] = json
        json
    }

    companion object {
        private const val VECTOR = "openmaptiles"
        private const val DEM = "fug-dem"
        private const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
        private const val GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
        private const val TERRARIUM = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"

        @Volatile private var instance: StyleComposer? = null
        fun get(context: Context): StyleComposer =
            instance ?: synchronized(this) { instance ?: StyleComposer(context.applicationContext).also { instance = it } }
    }
}
