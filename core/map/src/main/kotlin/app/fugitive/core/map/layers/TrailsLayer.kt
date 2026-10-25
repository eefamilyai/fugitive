package app.fugitive.core.map.layers

import app.fugitive.core.map.MapContext
import app.fugitive.core.map.MapLayer
import app.fugitive.core.map.internal.EMPTY
import app.fugitive.core.map.internal.featureCollection
import app.fugitive.core.map.internal.toPoint
import app.fugitive.model.GeoPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString

data class Trail(val id: String, val points: List<GeoPoint>, val color: Int)

/** Movement trails (live recent trails in-game, full paths on the results replay). */
class TrailsLayer(id: String = "trails", private val width: Float = 3.5f, private val opacity: Float = 0.7f) : MapLayer(id) {
    private var trails: List<Trail> = emptyList()

    fun submit(trails: List<Trail>) {
        if (trails == this.trails) return
        this.trails = trails
        invalidate()
    }

    override fun install(style: Style, ctx: MapContext) {
        style.addSource(GeoJsonSource(sid("src"), EMPTY))
        style.addLayer(
            LineLayer(sid("casing"), sid("src")).withProperties(
                lineColor("#0B0F14"), lineWidth(width + 2.5f), lineOpacity(opacity * 0.45f),
                lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(sid("line"), sid("src")).withProperties(
                lineColor(get("color")), lineWidth(width), lineOpacity(opacity),
                lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_ROUND),
            ),
        )
    }

    override fun render(style: Style, ctx: MapContext, nowMs: Long) {
        val features = trails.filter { it.points.size >= 2 }.map { t ->
            Feature.fromGeometry(LineString.fromLngLats(t.points.map { it.toPoint() })).apply {
                addStringProperty("id", t.id)
                addStringProperty("color", String.format("#%06X", 0xFFFFFF and t.color))
            }
        }
        style.getSourceAs<GeoJsonSource>(sid("src"))?.setGeoJson(featureCollection(features))
    }
}
