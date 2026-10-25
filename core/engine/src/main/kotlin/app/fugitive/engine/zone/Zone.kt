package app.fugitive.engine.zone

import app.fugitive.engine.geo.Geo
import app.fugitive.model.Circle
import app.fugitive.model.GeoPoint
import app.fugitive.model.LocationFix
import app.fugitive.model.ZonePlan
import app.fugitive.model.ZoneStage
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

enum class ZonePhase {
    /** Zone is static; nothing announced yet. */
    WAITING,
    /** Next circle announced; shrinking soon. */
    ANNOUNCED,
    /** Zone is actively closing in. */
    SHRINKING,
    /** Zone reached its final size. */
    FINAL,
}

/** Zone as seen at a specific instant. */
data class ZoneSnapshot(
    val current: Circle,
    /** The circle the zone is closing to (shown as an outline), if announced. */
    val next: Circle?,
    val phase: ZonePhase,
    /** Zero-based stage index; equals the stage count when final. */
    val stageIndex: Int,
    val stageCount: Int,
    /** When the current phase ends (shrink starts / shrink ends), if it does. */
    val phaseEndsAt: Long?,
)

object ZoneMath {
    fun snapshot(plan: ZonePlan, now: Long): ZoneSnapshot? {
        val stages = plan.stages
        if (stages.isEmpty()) return null
        for (s in stages) {
            if (now < s.shrinkStartAt) {
                val announced = now >= s.announceAt
                return ZoneSnapshot(
                    current = s.from,
                    next = if (announced) s.to else null,
                    phase = if (announced) ZonePhase.ANNOUNCED else ZonePhase.WAITING,
                    stageIndex = s.index,
                    stageCount = stages.size,
                    phaseEndsAt = if (announced) s.shrinkStartAt else s.announceAt,
                )
            }
            if (now < s.shrinkEndAt) {
                val span = (s.shrinkEndAt - s.shrinkStartAt).coerceAtLeast(1)
                val t = (now - s.shrinkStartAt).toDouble() / span
                return ZoneSnapshot(Geo.lerp(s.from, s.to, t), s.to, ZonePhase.SHRINKING, s.index, stages.size, s.shrinkEndAt)
            }
        }
        val last = stages.last()
        return ZoneSnapshot(last.to, null, ZonePhase.FINAL, stages.size, stages.size, null)
    }

    /** Tolerance in metres granted to a fix when GPS forgiveness is on. */
    fun tolerance(fix: LocationFix, forgiveGps: Boolean): Double =
        if (forgiveGps) min(fix.accuracy.toDouble().coerceAtLeast(0.0), 25.0) else 0.0

    fun isInside(fix: LocationFix, circle: Circle, forgiveGps: Boolean): Boolean =
        Geo.isInside(fix.point, circle, tolerance(fix, forgiveGps))

    /** Signed distance to the edge: positive = metres inside, negative = metres outside. */
    fun marginToEdge(point: GeoPoint, circle: Circle): Double = circle.radius - Geo.distance(point, circle.center)
}

/** Parameters for [ZonePlanner.plan], decoupled from Settings for testability. */
data class ZoneSpec(
    val center: GeoPoint,
    val startRadius: Double,
    val endRadius: Double,
    val staged: Boolean,
    val stages: Int,
    val shift: Boolean,
    val shrinkStartAt: Long,
    val shrinkDurationMs: Long,
    val warningMs: Long,
    val appliesTo: List<String>,
)

object ZonePlanner {
    /**
     * Builds a deterministic zone timeline. Staged zones shrink geometrically
     * (each circle a constant fraction of the last) which feels even at every
     * scale; each stage spends 60% holding and 40% shrinking.
     */
    fun plan(spec: ZoneSpec, random: Random): ZonePlan {
        val startR = spec.startRadius.coerceAtLeast(10.0)
        val endR = spec.endRadius.coerceIn(5.0, startR)
        val duration = spec.shrinkDurationMs.coerceAtLeast(10_000)
        val initial = Circle(spec.center.lat, spec.center.lng, startR)

        if (!spec.staged) {
            val target = nextCircle(initial, endR, spec.shift, random)
            val stage = ZoneStage(
                index = 0,
                announceAt = spec.shrinkStartAt - spec.warningMs,
                shrinkStartAt = spec.shrinkStartAt,
                shrinkEndAt = spec.shrinkStartAt + duration,
                from = initial, to = target,
            )
            return ZonePlan(listOf(stage), spec.appliesTo)
        }

        val n = spec.stages.coerceIn(1, 20)
        val segment = duration / n
        val hold = (segment * 0.6).toLong()
        val ratio = (endR / startR).pow(1.0 / n)
        val stages = ArrayList<ZoneStage>(n)
        var from = initial
        for (i in 0 until n) {
            val segStart = spec.shrinkStartAt + i * segment
            val shrinkStart = segStart + hold
            val shrinkEnd = segStart + segment
            val r = if (i == n - 1) endR else startR * ratio.pow(i + 1)
            val to = nextCircle(from, r, spec.shift, random)
            val announce = shrinkStart - min(spec.warningMs, if (i == 0) spec.warningMs else hold)
            stages += ZoneStage(i, announce, shrinkStart, shrinkEnd, from, to)
            from = to
        }
        return ZonePlan(stages, spec.appliesTo)
    }

    /** A circle of [radius] fully contained in [outer]. */
    private fun nextCircle(outer: Circle, radius: Double, shift: Boolean, random: Random): Circle {
        if (!shift) return Circle(outer.lat, outer.lng, radius)
        // Keep the new circle inside the old one, biased away from the extreme edge.
        val slack = (outer.radius - radius).coerceAtLeast(0.0) * 0.85
        val c = Geo.randomPointInCircle(outer.center, slack, random)
        return Circle(c.lat, c.lng, radius)
    }
}
