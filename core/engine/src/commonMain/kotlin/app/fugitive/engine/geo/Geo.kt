package app.fugitive.engine.geo

import app.fugitive.model.Circle
import app.fugitive.model.GeoPoint
import app.fugitive.model.LocationFix
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Spherical-earth helpers. Accurate to well under a metre at game scales. */
object Geo {
    private fun rad(d: Double) = d * kotlin.math.PI / 180.0
    private fun deg(r: Double) = r * 180.0 / kotlin.math.PI

    const val EARTH_RADIUS_M = 6_371_008.8

    fun distance(a: GeoPoint, b: GeoPoint): Double = distance(a.lat, a.lng, b.lat, b.lng)

    fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = rad(lat1)
        val p2 = rad(lat2)
        val dp = p2 - p1
        val dl = rad(lng2 - lng1)
        val h = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from [from] to [to] in degrees, 0..360, 0 = north. */
    fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val p1 = rad(from.lat)
        val p2 = rad(to.lat)
        val dl = rad(to.lng - from.lng)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (deg(atan2(y, x)) + 360.0) % 360.0
    }

    /** Point reached travelling [distance] metres from [origin] on [bearingDeg]. */
    fun destination(origin: GeoPoint, bearingDeg: Double, distance: Double): GeoPoint {
        val d = distance / EARTH_RADIUS_M
        val b = rad(bearingDeg)
        val p1 = rad(origin.lat)
        val l1 = rad(origin.lng)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return GeoPoint(deg(p2), ((deg(l2) + 540.0) % 360.0) - 180.0)
    }

    /** Uniformly distributed random point within [radius] metres of [center]. */
    fun randomPointInCircle(center: GeoPoint, radius: Double, random: Random): GeoPoint {
        if (radius <= 0.0) return center
        val r = radius * sqrt(random.nextDouble())
        return destination(center, random.nextDouble() * 360.0, r)
    }

    /** Linear interpolation; fine for the short distances a zone moves. */
    fun lerp(a: GeoPoint, b: GeoPoint, t: Double): GeoPoint =
        GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)

    fun lerp(a: Circle, b: Circle, t: Double): Circle {
        val f = t.coerceIn(0.0, 1.0)
        return Circle(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f, a.radius + (b.radius - a.radius) * f)
    }

    fun centroid(points: Collection<GeoPoint>): GeoPoint? {
        if (points.isEmpty()) return null
        // Average on the unit sphere so it behaves near the antimeridian too.
        var x = 0.0; var y = 0.0; var z = 0.0
        for (p in points) {
            val la = rad(p.lat); val lo = rad(p.lng)
            x += cos(la) * cos(lo); y += cos(la) * sin(lo); z += sin(la)
        }
        val n = points.size
        x /= n; y /= n; z /= n
        val lon = atan2(y, x)
        val lat = atan2(z, sqrt(x * x + y * y))
        return GeoPoint(deg(lat), deg(lon))
    }

    /** Polygon ring approximating a circle, closed (first == last). */
    fun circleRing(center: GeoPoint, radius: Double, segments: Int = 96): List<GeoPoint> =
        (0..segments).map { i -> destination(center, i * 360.0 / segments, radius) }

    /** Total path length of an ordered list of fixes, ignoring implausible GPS jumps. */
    fun pathLength(fixes: List<LocationFix>, maxSpeedMps: Double = 15.0): Double {
        var total = 0.0
        for (i in 1 until fixes.size) {
            val a = fixes[i - 1]; val b = fixes[i]
            val d = distance(a.lat, a.lng, b.lat, b.lng)
            val dt = ((b.time - a.time).coerceAtLeast(1)) / 1000.0
            if (d / dt <= maxSpeedMps) total += d
        }
        return total
    }

    fun isInside(point: GeoPoint, circle: Circle, tolerance: Double = 0.0): Boolean =
        distance(point, circle.center) <= circle.radius + tolerance

    /** Compass label for a bearing, e.g. "NE". */
    fun compass(bearing: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[(((bearing % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8]
    }
}
