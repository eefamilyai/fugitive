package app.fugitive.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lng: Double) {
    override fun toString(): String = "${Fmt.fixed(lat, 6)},${Fmt.fixed(lng, 6)}"

    companion object {
        fun parse(raw: String?): GeoPoint? {
            val parts = raw?.split(',')?.map { it.trim().toDoubleOrNull() } ?: return null
            if (parts.size != 2 || parts.any { it == null }) return null
            return GeoPoint(parts[0]!!, parts[1]!!)
        }
    }
}

/**
 * One GPS/fused location sample as shared with other players.
 * [time] is epoch millis on the *server* clock so every device agrees on age.
 */
@Serializable
data class LocationFix(
    val lat: Double,
    val lng: Double,
    /** Horizontal accuracy radius in metres (68% confidence). */
    val accuracy: Float = 0f,
    /** Direction of travel in degrees, 0 = north. */
    val bearing: Float? = null,
    /** Ground speed in metres per second. */
    val speed: Float? = null,
    val altitude: Double? = null,
    val time: Long = 0L,
    /** Device battery percentage, useful to spot players about to drop out. */
    val battery: Int? = null,
    /** Which source produced the fix: "fused", "gps", "network", "sim", "bot". */
    val source: String? = null,
) {
    val point: GeoPoint get() = GeoPoint(lat, lng)
}
