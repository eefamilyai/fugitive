package app.fugitive.core.map.layers

import android.graphics.Color
import app.fugitive.core.map.MapContext
import app.fugitive.core.map.MapLayer
import app.fugitive.core.map.internal.EMPTY
import app.fugitive.core.map.internal.featureCollection
import app.fugitive.core.map.internal.circleOutline
import app.fugitive.core.map.internal.worldWithHole
import app.fugitive.model.Circle
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature

/**
 * The play area: everything outside [current] is shaded; [next] (the circle
 * the zone is closing to) is drawn dashed. Also used in the lobby as a preview.
 */
class ZoneLayer(id: String = "zone") : MapLayer(id) {
    private var current: Circle? = null
    private var next: Circle? = null
    private var color: Int = Color.parseColor("#A855F7")
    private var danger = false

    /** @param danger tint the zone red (e.g. when the local player is outside it). */
    fun submit(current: Circle?, next: Circle?, color: Int, danger: Boolean = false) {
        if (current == this.current && next == this.next && color == this.color && danger == this.danger) return
        this.current = current; this.next = next; this.color = color; this.danger = danger
        invalidate()
    }

    override fun install(style: Style, ctx: MapContext) {
        style.addSource(GeoJsonSource(sid("shade"), EMPTY))
        style.addSource(GeoJsonSource(sid("edge"), EMPTY))
        style.addSource(GeoJsonSource(sid("next"), EMPTY))
        style.addLayer(FillLayer(sid("shade-l"), sid("shade")).withProperties(fillColor(color), fillOpacity(0.28f)))
        style.addLayer(LineLayer(sid("edge-glow"), sid("edge")).withProperties(lineColor(color), lineWidth(9f), lineOpacity(0.25f), lineJoin(Property.LINE_JOIN_ROUND)))
        style.addLayer(LineLayer(sid("edge-l"), sid("edge")).withProperties(lineColor(color), lineWidth(3f), lineJoin(Property.LINE_JOIN_ROUND)))
        style.addLayer(
            LineLayer(sid("next-l"), sid("next")).withProperties(
                lineColor(Color.WHITE), lineWidth(2.5f), lineDasharray(arrayOf(2f, 1.5f)), lineOpacity(0.95f), lineCap(Property.LINE_CAP_ROUND),
            ),
        )
    }

    override fun render(style: Style, ctx: MapContext, nowMs: Long) {
        val tint = if (danger) Color.parseColor("#EF4444") else color
        style.getLayer(sid("shade-l"))?.setProperties(fillColor(tint), fillOpacity(if (ctx.darkStyle) 0.30f else 0.13f))
        style.getLayer(sid("edge-l"))?.setProperties(lineColor(tint))
        style.getLayer(sid("edge-glow"))?.setProperties(lineColor(tint))
        val c = current
        style.getSourceAs<GeoJsonSource>(sid("shade"))?.setGeoJson(c?.let { featureCollection(listOf(Feature.fromGeometry(worldWithHole(it)))) } ?: EMPTY)
        style.getSourceAs<GeoJsonSource>(sid("edge"))?.setGeoJson(c?.let { featureCollection(listOf(Feature.fromGeometry(circleOutline(it)))) } ?: EMPTY)
        style.getSourceAs<GeoJsonSource>(sid("next"))?.setGeoJson(next?.let { featureCollection(listOf(Feature.fromGeometry(circleOutline(it)))) } ?: EMPTY)
    }
}
