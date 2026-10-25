package app.fugitive.core.map.layers

import app.fugitive.core.map.MapContext
import app.fugitive.core.map.MapLayer
import app.fugitive.core.map.internal.EMPTY
import app.fugitive.core.map.internal.MarkerBitmaps
import app.fugitive.core.map.internal.circleOutline
import app.fugitive.core.map.internal.circlePolygon
import app.fugitive.core.map.internal.featureCollection
import app.fugitive.core.map.internal.toPoint
import app.fugitive.model.Circle
import app.fugitive.model.GeoPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature

data class MapPin(
    val id: String,
    val point: GeoPoint,
    val emoji: String,
    val color: Int,
    val label: String? = null,
    /** Optional area drawn around the pin (e.g. extraction radius). */
    val radius: Double? = null,
)

/** Labelled pins with optional radius: extraction points, map pings, the zone-centre picker. */
class PinsLayer(id: String) : MapLayer(id) {
    private var pins: List<MapPin> = emptyList()
    private val images = HashSet<String>()
    override val clickableLayerIds: List<String> get() = listOf(sid("icons"))

    fun submit(pins: List<MapPin>) {
        if (pins == this.pins) return
        this.pins = pins
        invalidate()
    }

    override fun install(style: Style, ctx: MapContext) {
        images.clear()
        style.addSource(GeoJsonSource(sid("area"), EMPTY))
        style.addSource(GeoJsonSource(sid("edge"), EMPTY))
        style.addSource(GeoJsonSource(sid("pts"), EMPTY))
        style.addLayer(FillLayer(sid("area-l"), sid("area")).withProperties(fillColor(get("color")), fillOpacity(0.22f)))
        style.addLayer(LineLayer(sid("edge-l"), sid("edge")).withProperties(lineColor(get("color")), lineWidth(2.5f), lineDasharray(arrayOf(2f, 1f))))
        style.addLayer(
            SymbolLayer(sid("icons"), sid("pts")).withProperties(
                iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            ),
        )
    }

    override fun render(style: Style, ctx: MapContext, nowMs: Long) {
        val pts = ArrayList<Feature>(); val areas = ArrayList<Feature>(); val edges = ArrayList<Feature>()
        for (p in pins) {
            val key = "fug-pin-" + listOf(p.emoji, p.color, p.label).hashCode()
            if (key !in images) { style.addImage(key, MarkerBitmaps.pin(ctx.density, p.emoji, p.color, p.label)); images += key }
            pts += Feature.fromGeometry(p.point.toPoint()).apply { addStringProperty("id", p.id); addStringProperty("icon", key) }
            p.radius?.let { r ->
                val c = Circle(p.point.lat, p.point.lng, r)
                val hex = String.format("#%06X", 0xFFFFFF and p.color)
                areas += Feature.fromGeometry(circlePolygon(c, 64)).apply { addStringProperty("color", hex) }
                edges += Feature.fromGeometry(circleOutline(c, 64)).apply { addStringProperty("color", hex) }
            }
        }
        style.getSourceAs<GeoJsonSource>(sid("pts"))?.setGeoJson(featureCollection(pts))
        style.getSourceAs<GeoJsonSource>(sid("area"))?.setGeoJson(featureCollection(areas))
        style.getSourceAs<GeoJsonSource>(sid("edge"))?.setGeoJson(featureCollection(edges))
    }
}
