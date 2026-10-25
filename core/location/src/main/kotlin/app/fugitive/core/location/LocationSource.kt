package app.fugitive.core.location

import app.fugitive.model.LocationFix
import kotlinx.coroutines.flow.Flow

/** How often and how precisely a source should deliver fixes. */
data class LocationRequestSpec(
    val intervalMs: Long = 2_000,
    val highAccuracy: Boolean = true,
    /** Fixes closer than this to the previous one may be skipped by the provider. 0 = deliver all. */
    val minDistanceMeters: Float = 0f,
)

/**
 * A stream of device locations. Fix times are *device* epoch millis; the
 * session layer converts them to server time before sharing.
 *
 * Implementations: [FusedLocationSource] (Google Play services),
 * [PlatformLocationSource] (Android LocationManager fallback) and
 * [SimulatedLocationSource] (virtual joystick for practice and testing).
 * Add new sources (e.g. external GPS over Bluetooth) by implementing this.
 */
interface LocationSource {
    val id: String
    val displayName: String
    fun isAvailable(): Boolean
    /** Cold flow; collection starts updates and cancellation stops them. */
    fun fixes(spec: LocationRequestSpec): Flow<LocationFix>
}

class LocationPermissionException : SecurityException("Location permission not granted")
