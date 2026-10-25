package app.fugitive.engine

import app.fugitive.engine.geo.Geo
import app.fugitive.engine.zone.ZoneMath
import app.fugitive.engine.zone.ZonePhase
import app.fugitive.engine.zone.ZonePlanner
import app.fugitive.engine.zone.ZoneSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class ZoneTest {
    private fun spec(staged: Boolean, shift: Boolean) = ZoneSpec(
        center = ORIGIN, startRadius = 1000.0, endRadius = 100.0, staged = staged, stages = 4, shift = shift,
        shrinkStartAt = 10_000, shrinkDurationMs = 400_000, warningMs = 30_000, appliesTo = listOf("runner"),
    )

    @Test fun stagedPlanShrinksMonotonicallyAndEndsAtFinalRadius() {
        val plan = ZonePlanner.plan(spec(staged = true, shift = true), Random(1))
        assertThat(plan.stages).hasSize(4)
        var prev = 1000.0
        plan.stages.forEach { s ->
            assertThat(s.to.radius).isLessThan(prev)
            // Each new circle lies fully inside the previous one.
            assertThat(Geo.distance(s.from.center, s.to.center) + s.to.radius).isAtMost(s.from.radius + 0.5)
            prev = s.to.radius
        }
        assertThat(plan.stages.last().to.radius).isWithin(0.01).of(100.0)
        assertThat(plan.stages.last().shrinkEndAt).isEqualTo(410_000)
    }

    @Test fun snapshotInterpolatesDuringShrink() {
        val plan = ZonePlanner.plan(spec(staged = false, shift = false), Random(1))
        val s = plan.stages.single()
        assertThat(ZoneMath.snapshot(plan, s.announceAt - 1)!!.phase).isEqualTo(ZonePhase.WAITING)
        assertThat(ZoneMath.snapshot(plan, s.shrinkStartAt - 1)!!.phase).isEqualTo(ZonePhase.ANNOUNCED)
        val mid = ZoneMath.snapshot(plan, (s.shrinkStartAt + s.shrinkEndAt) / 2)!!
        assertThat(mid.phase).isEqualTo(ZonePhase.SHRINKING)
        assertThat(mid.current.radius).isWithin(1.0).of(550.0)
        val end = ZoneMath.snapshot(plan, s.shrinkEndAt + 1)!!
        assertThat(end.phase).isEqualTo(ZonePhase.FINAL)
        assertThat(end.current.radius).isWithin(0.01).of(100.0)
    }
}
