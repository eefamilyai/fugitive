package app.fugitive.core.location

import android.os.SystemClock
import app.fugitive.model.GeoPoint
import app.fugitive.model.LocationFix
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A virtual position steered by an on-screen joystick. Lets you try the game
 * (or demo it) without walking around, and makes emulator testing possible.
 */
class SimulatedLocationSource : LocationSource {
    override val id = ID
    override val displayName = "Virtual joystick"
    override fun isAvailable() = true

    private val _position = MutableStateFlow<GeoPoint?>(null)
    val position: StateFlow<GeoPoint?> = _position.asStateFlow()

    /** Joystick deflection: x = east, y = north, each -1..1. */
    @Volatile private var stickX = 0.0
    @Volatile private var stickY = 0.0
    /** Top speed at full deflection, m/s (default: a brisk run). */
    @Volatile var maxSpeed = 5.0

    fun setStick(x: Float, y: Float) { stickX = x.toDouble().coerceIn(-1.0, 1.0); stickY = y.toDouble().coerceIn(-1.0, 1.0) }

    /** Place the virtual player somewhere (e.g. at the real last-known location). */
    fun teleport(point: GeoPoint) { _position.value = point }

    fun ensureStart(fallback: GeoPoint) { if (_position.value == null) _position.value = fallback }

    override fun fixes(spec: LocationRequestSpec): Flow<LocationFix> = flow {
        val step = 200L
        var last = SystemClock.elapsedRealtime()
        var lastEmit = 0L
        var bearing = 0f
        var speed = 0f
        while (currentCoroutineContextActive()) {
            delay(step)
            val now = SystemClock.elapsedRealtime()
            val dt = (now - last) / 1000.0
            last = now
            val pos = _position.value ?: continue
            val mag = hypot(stickX, stickY).coerceAtMost(1.0)
            if (mag > 0.05) {
                val v = maxSpeed * mag
                val dNorth = stickY / hypot(stickX, stickY) * v * dt
                val dEast = stickX / hypot(stickX, stickY) * v * dt
                val lat = pos.lat + Math.toDegrees(dNorth / 6_371_008.8)
                val lng = pos.lng + Math.toDegrees(dEast / (6_371_008.8 * cos(Math.toRadians(pos.lat))))
                _position.value = GeoPoint(lat, lng)
                bearing = ((Math.toDegrees(atan2(stickX, stickY)) + 360) % 360).toFloat()
                speed = v.toFloat()
            } else speed = 0f
            if (now - lastEmit >= spec.intervalMs.coerceAtMost(1000)) {
                lastEmit = now
                val p = _position.value ?: continue
                emit(LocationFix(p.lat, p.lng, accuracy = 4f, bearing = bearing, speed = speed, time = System.currentTimeMillis(), source = ID))
            }
        }
    }

    private suspend fun currentCoroutineContextActive(): Boolean = kotlinx.coroutines.currentCoroutineContext().isActive

    companion object { const val ID = "sim" }
}
