package app.fugitive.core.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import app.fugitive.model.LocationFix

internal fun Location.toFix(context: Context, source: String): LocationFix = LocationFix(
    lat = latitude,
    lng = longitude,
    accuracy = if (hasAccuracy()) accuracy else 50f,
    bearing = if (hasBearing() && hasSpeed() && speed > 0.5f) bearing else null,
    speed = if (hasSpeed()) speed else null,
    altitude = if (hasAltitude()) altitude else null,
    time = System.currentTimeMillis(),
    battery = Battery.level(context),
    source = source,
)

object Battery {
    private var cached: Int? = null
    private var cachedAt = 0L

    /** Battery percentage, cached for a minute (it is attached to every fix). */
    fun level(context: Context): Int? {
        val now = System.currentTimeMillis()
        if (cached != null && now - cachedAt < 60_000) return cached
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        cached = if (level >= 0 && scale > 0) level * 100 / scale else null
        cachedAt = now
        return cached
    }
}
