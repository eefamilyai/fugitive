package app.fugitive.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import app.fugitive.model.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android's built-in LocationManager. Used on devices without Google Play
 * services. Combines GPS and network providers, preferring the more accurate.
 */
class PlatformLocationSource(private val context: Context) : LocationSource {
    override val id = ID
    override val displayName = "Device GPS"
    private val manager get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun isAvailable(): Boolean = manager.allProviders.any { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }

    @SuppressLint("MissingPermission")
    override fun fixes(spec: LocationRequestSpec): Flow<LocationFix> = callbackFlow {
        if (!LocationPermissions.hasLocation(context)) throw LocationPermissionException()
        var best: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val prev = best
                // Accept a network fix only if GPS has gone quiet or is worse.
                val accept = prev == null || location.provider == prev.provider ||
                    location.accuracy <= prev.accuracy || location.time - prev.time > spec.intervalMs * 3
                if (accept) {
                    best = location
                    trySend(location.toFix(context, if (location.provider == LocationManager.GPS_PROVIDER) "gps" else "network"))
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { best = it; trySend(it.toFix(context, "last")) }
        providers.forEach { p ->
            manager.requestLocationUpdates(p, spec.intervalMs, spec.minDistanceMeters, listener, Looper.getMainLooper())
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    companion object { const val ID = "platform" }
}
