package app.fugitive.core.map.layers

import app.fugitive.core.map.MapContext
import app.fugitive.core.map.MapLayer
import app.fugitive.core.map.internal.EMPTY
import app.fugitive.core.map.internal.featureCollection
import app.fugitive.core.map.internal.circleOutline
import app.fugitive.core.map.internal.circlePolygon
import app.fugitive.model.Circle
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature

/** A tinted ring, e.g. the hunter's catch range or the proximity alert radius. */
class RingLayer(id: String, private val dashed: Boolean = true) : MapLayer(id) {
    private var circle: Circle? = null
    private var color: Int = 0
    private var fillAlpha = 0.08f

    fun submit(circle: Circle?, color: Int, fillAlpha: Float = 0.08f) {
        if (circle == this.circle && color == this.color && fillAlpha == this.fillAlpha) return
        this.circle = circle; this.color = color; this.fillAlpha = fillAlpha
        invalidate()
    }

    override fun install(style: Style, ctx: MapContext) {
        style.addSource(GeoJsonSource(sid("fill"), EMPTY))
        style.addSource(GeoJsonSource(sid("line"), EMPTY))
        style.addLayer(FillLayer(sid("fill-l"), sid("fill")))
        val line = LineLayer(sid("line-l"), sid("line")).withProperties(lineWidth(1.8f), lineOpacity(0.9f))
        if (dashed) line.setProperties(lineDasharray(arrayOf(1.5f, 1.5f)))
        style.addLayer(line)
    }

    override fun render(style: Style, ctx: MapContext, nowMs: Long) {
        style.getLayer(sid("fill-l"))?.setProperties(fillColor(color), fillOpacity(fillAlpha))
        style.getLayer(sid("line-l"))?.setProperties(lineColor(color))
        val c = circle
        style.getSourceAs<GeoJsonSource>(sid("fill"))?.setGeoJson(c?.let { featureCollection(listOf(Feature.fromGeometry(circlePolygon(it, 64)))) } ?: EMPTY)
        style.getSourceAs<GeoJsonSource>(sid("line"))?.setGeoJson(c?.let { featureCollection(listOf(Feature.fromGeometry(circleOutline(it, 64)))) } ?: EMPTY)
    }
}
