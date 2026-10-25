package app.fugitive.core.map

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fugitive.model.GeoPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

enum class CameraMode {
    /** Camera keeps the local player centred. */
    FOLLOW,
    /** Camera follows the player and rotates with their heading. */
    FOLLOW_HEADING,
    /** Camera frames everyone visible plus the zone. */
    OVERVIEW,
    /** User is panning freely. */
    FREE,
}

/** Imperative camera control for [GameMap], plus the mode the UI shows. */
@Stable
class MapCameraState(initialMode: CameraMode = CameraMode.FOLLOW) {
    var mode by mutableStateOf(initialMode)
    var bearing by mutableStateOf(0.0)
        internal set
    var zoom by mutableStateOf(15.5)
        internal set
    internal var map: MapLibreMap? by mutableStateOf(null)
    private var pendingTarget: GeoPoint? = null
    private var lastFollowAt = 0L

    val isReady: Boolean get() = map != null

    internal fun attach(m: MapLibreMap) {
        map = m
        pendingTarget?.let { jumpTo(it, zoom); pendingTarget = null }
    }

    fun jumpTo(point: GeoPoint, zoom: Double? = null) {
        val m = map ?: run { pendingTarget = point; return }
        m.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(point.lat, point.lng)).zoom(zoom ?: m.cameraPosition.zoom).build(),
        ))
    }

    fun animateTo(point: GeoPoint, zoom: Double? = null, bearing: Double? = null, durationMs: Int = 700) {
        val m = map ?: run { pendingTarget = point; return }
        val b = CameraPosition.Builder().target(LatLng(point.lat, point.lng)).zoom(zoom ?: m.cameraPosition.zoom)
        if (bearing != null) b.bearing(bearing)
        m.easeCamera(CameraUpdateFactory.newCameraPosition(b.build()), durationMs, false)
    }

    /** Called on each own-location fix; moves the camera when following. */
    fun follow(point: GeoPoint, heading: Float?) {
        if (mode != CameraMode.FOLLOW && mode != CameraMode.FOLLOW_HEADING) return
        val m = map ?: run { pendingTarget = point; return }
        val now = System.currentTimeMillis()
        val dur = (now - lastFollowAt).coerceIn(300, 1200).toInt()
        lastFollowAt = now
        val b = CameraPosition.Builder().target(LatLng(point.lat, point.lng))
        if (mode == CameraMode.FOLLOW_HEADING && heading != null) b.bearing(heading.toDouble())
        m.easeCamera(CameraUpdateFactory.newCameraPosition(b.build()), dur, false)
    }

    /** Frames all [points] with [paddingPx] margin. */
    /** Frames all [points]. Returns false if the map isn't laid out yet (caller may retry). */
    fun fit(points: List<GeoPoint>, paddingPx: Int = 160, maxZoom: Double = 17.0, animate: Boolean = true): Boolean {
        val m = map ?: return false
        if (points.isEmpty() || m.width <= 0f || m.height <= 0f) return false
        if (points.size == 1) {
            if (animate) animateTo(points.first(), minOf(maxZoom, 16.0)) else jumpTo(points.first(), minOf(maxZoom, 16.0))
            return true
        }
        val bounds = LatLngBounds.Builder().apply { points.forEach { include(LatLng(it.lat, it.lng)) } }.build()
        return runCatching {
            val position = m.getCameraForLatLngBounds(bounds, intArrayOf(paddingPx, paddingPx, paddingPx, paddingPx)) ?: return false
            val target = CameraPosition.Builder(position).zoom(minOf(position.zoom, maxZoom)).build()
            if (animate) m.easeCamera(CameraUpdateFactory.newCameraPosition(target), 800)
            else m.moveCamera(CameraUpdateFactory.newCameraPosition(target))
            true
        }.getOrDefault(false)
    }

    fun resetNorth() {
        val m = map ?: return
        m.easeCamera(CameraUpdateFactory.bearingTo(0.0), 400)
    }

    fun zoomBy(delta: Double) { map?.easeCamera(CameraUpdateFactory.zoomBy(delta), 250) }
}


/**
 * Frames [points] once the map is attached and laid out, retrying briefly
 * until it succeeds. Re-frames whenever [key] changes.
 */
@androidx.compose.runtime.Composable
fun FitWhenReady(camera: MapCameraState, key: Any?, paddingPx: Int = 80, maxZoom: Double = 17.0, points: () -> List<GeoPoint>) {
    androidx.compose.runtime.LaunchedEffect(camera.isReady, key) {
        if (!camera.isReady) return@LaunchedEffect
        repeat(30) {
            val pts = points()
            if (pts.isNotEmpty() && camera.fit(pts, paddingPx, maxZoom, animate = false)) return@LaunchedEffect
            kotlinx.coroutines.delay(250)
        }
    }
}
