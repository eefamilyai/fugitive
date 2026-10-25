package app.fugitive.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import app.fugitive.model.LocationFix
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Google Play services fused provider: best accuracy and battery life when available. */
class FusedLocationSource(private val context: Context) : LocationSource {
    override val id = ID
    override val displayName = "Google fused location"
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun isAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    @SuppressLint("MissingPermission")
    override fun fixes(spec: LocationRequestSpec): Flow<LocationFix> = callbackFlow {
        if (!LocationPermissions.hasLocation(context)) throw LocationPermissionException()
        val request = LocationRequest.Builder(
            if (spec.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            spec.intervalMs,
        )
            .setMinUpdateIntervalMillis((spec.intervalMs / 2).coerceAtLeast(500))
            .setMinUpdateDistanceMeters(spec.minDistanceMeters)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(spec.intervalMs) // no batching: we want it live
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix(context, ID)) }
            }
        }
        // Emit the last known position immediately so the map isn't empty while GPS warms up.
        runCatching { client.lastLocation.await() }.getOrNull()?.let { trySend(it.toFix(context, ID)) }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    companion object { const val ID = "fused" }
}
