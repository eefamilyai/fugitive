package app.fugitive.core.map.layers

import android.graphics.Color
import app.fugitive.core.map.MapContext
import app.fugitive.core.map.MapLayer
import app.fugitive.core.map.internal.EMPTY
import app.fugitive.core.map.internal.MarkerBitmaps
import app.fugitive.core.map.internal.circlePolygon
import app.fugitive.core.map.internal.featureCollection
import app.fugitive.core.map.internal.toPoint
import app.fugitive.engine.geo.Geo
import app.fugitive.model.Circle
import app.fugitive.model.GeoPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature

enum class MarkerLook {
    /** Real-time position. */
    LIVE,
    /** Position from a periodic ping (ghosted, with age badge). */
    SNAPSHOT,
    /** Live but old (signal lost). */
    STALE,
    /** Player is out of the game. */
    OUT,
}

data class PlayerMarker(
    val id: String,
    val point: GeoPoint,
    val avatar: String,
    /** Ring colour (role). */
    val ringColor: Int,
    /** Fill colour (player colour). */
    val fillColor: Int,
    val label: String?,
    val bearing: Float? = null,
    val accuracy: Float = 0f,
    val look: MarkerLook = MarkerLook.LIVE,
    val isMe: Boolean = false,
    val selected: Boolean = false,
    val badge: String? = null,
    /** Expected time until the next update, used to pace the glide animation. */
    val glideMs: Long = 1000,
)

/**
 * Player avatars with smooth gliding between updates, heading arrows and GPS
 * accuracy halos. Tap targets report the player id.
 */
class PlayersLayer(id: String = "players") : MapLayer(id) {
    private class Anim(var from: GeoPoint, var to: GeoPoint, var start: Long, var duration: Long)

    private var markers: List<PlayerMarker> = emptyList()
    private val anims = HashMap<String, Anim>()
    private val images = HashSet<String>()
    private var showAccuracy = true

    override val clickableLayerIds: List<String> get() = listOf(sid("icons"))

    fun submit(markers: List<PlayerMarker>, showAccuracy: Boolean = true) {
        val now = System.currentTimeMillis()
        for (m in markers) {
            val a = anims[m.id]
            if (a == null) {
                anims[m.id] = Anim(m.point, m.point, now, 0)
            } else if (a.to != m.point) {
                val cur = position(a, now)
                val jump = Geo.distance(cur, m.point)
                // Teleport for big jumps (first fix, ping snapshots); glide otherwise.
                if (jump > 400 || m.look == MarkerLook.SNAPSHOT) { a.from = m.point; a.to = m.point; a.duration = 0 }
                else { a.from = cur; a.to = m.point; a.start = now; a.duration = m.glideMs.coerceIn(250, 2500) }
            }
        }
        anims.keys.retainAll(markers.map { it.id }.toSet())
        if (markers != this.markers || showAccuracy != this.showAccuracy) {
            this.markers = markers; this.showAccuracy = showAccuracy
            invalidate()
        }
    }

    private fun position(a: Anim, now: Long): GeoPoint {
        if (a.duration <= 0 || now >= a.start + a.duration) return a.to
        val t = (now - a.start).toDouble() / a.duration
        val eased = t * (2 - t) // ease-out
        return Geo.lerp(a.from, a.to, eased)
    }

    override fun isAnimating(nowMs: Long): Boolean = anims.values.any { it.duration > 0 && nowMs < it.start + it.duration }

    override fun install(style: Style, ctx: MapContext) {
        images.clear()
        style.addSource(GeoJsonSource(sid("acc"), EMPTY))
        style.addSource(GeoJsonSource(sid("pts"), EMPTY))
        style.addLayer(FillLayer(sid("acc-l"), sid("acc")).withProperties(fillColor(get("color")), fillOpacity(0.12f)))
        style.addLayer(
            SymbolLayer(sid("heading"), sid("pts")).withProperties(
                iconImage(get("hicon")), iconRotate(get("bearing")), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            ).withFilter(org.maplibre.android.style.expressions.Expression.has("hicon")),
        )
        style.addLayer(
            SymbolLayer(sid("icons"), sid("pts")).withProperties(
                iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true),
                iconAnchor(Property.ICON_ANCHOR_CENTER), symbolSortKey(get("z")),
            ),
        )
    }

    override fun render(style: Style, ctx: MapContext, nowMs: Long) {
        val used = HashSet<String>()
        val features = ArrayList<Feature>(markers.size)
        val halos = ArrayList<Feature>()
        for (m in markers) {
            val pos = anims[m.id]?.let { position(it, nowMs) } ?: m.point
            val key = "fug-av-" + listOf(m.avatar, m.ringColor, m.fillColor, m.label, m.look, m.isMe, m.selected, m.badge).hashCode()
            if (key !in images) {
                style.addImage(
                    key,
                    MarkerBitmaps.avatar(
                        ctx.density, m.avatar, m.ringColor, m.fillColor, m.label, m.badge,
                        ghost = m.look == MarkerLook.SNAPSHOT, dimmed = m.look == MarkerLook.STALE || m.look == MarkerLook.OUT,
                        isMe = m.isMe, selected = m.selected, crossed = m.look == MarkerLook.OUT,
                    ),
                )
                images += key
            }
            used += key
            val f = Feature.fromGeometry(pos.toPoint())
            f.addStringProperty("id", m.id)
            f.addStringProperty("icon", key)
            f.addNumberProperty("z", if (m.isMe) 1000 else if (m.selected) 900 else m.look.ordinal.let { 500 - it * 100 })
            if (m.bearing != null && m.look == MarkerLook.LIVE) {
                val hkey = "fug-hd-${m.ringColor}"
                if (hkey !in images) { style.addImage(hkey, MarkerBitmaps.heading(ctx.density, m.ringColor)); images += hkey }
                used += hkey
                f.addStringProperty("hicon", hkey)
                f.addNumberProperty("bearing", m.bearing)
            }
            features += f
            if (showAccuracy && m.accuracy > 6f && (m.look == MarkerLook.LIVE)) {
                val h = Feature.fromGeometry(circlePolygon(Circle(pos.lat, pos.lng, m.accuracy.toDouble()), 40))
                h.addStringProperty("color", colorHex(if (m.isMe) Color.parseColor("#3B82F6") else m.ringColor))
                halos += h
            }
        }
        // Self on top: GeoJSON order matters for equal sort keys.
        features.sortBy { (it.getNumberProperty("z") ?: 0).toInt() }
        style.getSourceAs<GeoJsonSource>(sid("pts"))?.setGeoJson(featureCollection(features))
        style.getSourceAs<GeoJsonSource>(sid("acc"))?.setGeoJson(featureCollection(halos))
        // Drop images no longer used so memory stays flat over long games.
        (images - used).filter { it.startsWith("fug-av-") }.forEach { style.removeImage(it); images -= it }
    }

    private fun colorHex(c: Int) = String.format("#%06X", 0xFFFFFF and c)
}
