package app.fugitive.engine

import app.fugitive.engine.rules.ActionTypes
import app.fugitive.engine.rules.EndReasons
import app.fugitive.engine.visibility.VisibilityKind
import app.fugitive.engine.visibility.VisibilityRules
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RefereeTest {

    private fun classic(vararg extra: Pair<String, Any>) = Harness(
        config = config("classic", "zone_enabled" to false, "headstart_sec" to 60, "countdown_sec" to 5, *extra),
        ids = listOf("hank", "rita", "rob"),
        roster = mapOf("hank" to Role.HUNTER),
    )

    @Test fun manualRosterAssignsHunter() {
        val h = classic()
        assertThat(h.game.players["hank"]!!.role).isEqualTo(Role.HUNTER)
        assertThat(h.game.players["rita"]!!.role).isEqualTo(Role.RUNNER)
        assertThat(h.game.players.values.mapNotNull { it.catchCode }.toSet()).hasSize(3)
    }

    @Test fun catchBeforeReleaseIsRejected() {
        val h = classic()
        val t = h.game.startAt + 1000
        h.tick(t, h.action(ActionTypes.CATCH_CLAIM, "hank", "rita", t))
        assertThat(h.game.pendingCatches).isEmpty()
        assertThat(h.events.last().text).contains("aren't released")
    }

    @Test fun confirmFlowCatchesRunnerAndHuntersWinWhenAllCaught() {
        val h = classic()
        val t = h.active + 1000
        h.tick(t, h.action(ActionTypes.CATCH_CLAIM, "hank", "rita", t))
        val pending = h.game.pendingCatches.values.single()
        h.tick(t + 2000, h.action(ActionTypes.CATCH_RESPONSE, "rita", null, t + 2000, "catch" to pending.id, "accept" to true))
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.CAUGHT)
        assertThat(h.game.players["hank"]!!.catches).isEqualTo(1)
        assertThat(h.game.result).isNull()
        h.tick(t + 3000, h.action(ActionTypes.SELF_REPORT, "rob", "hank", t + 3000))
        assertThat(h.game.result!!.reason).isEqualTo(EndReasons.ALL_CAUGHT)
        assertThat(h.game.result!!.winningRole).isEqualTo(Role.HUNTER)
    }

    @Test fun unansweredClaimIsAcceptedAfterTimeout() {
        val h = classic("catch_confirm_timeout_sec" to 10)
        val t = h.active + 1000
        h.tick(t, h.action(ActionTypes.CATCH_CLAIM, "hank", "rita", t))
        h.tick(t + 11_000)
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.CAUGHT)
    }

    @Test fun proximityMethodRequiresRange() {
        val h = classic("catch_method" to "proximity", "catch_radius_m" to 10)
        val t = h.active + 1000
        h.move("hank", ORIGIN, t); h.move("rita", east(200.0), t)
        h.tick(t, h.action(ActionTypes.CATCH_CLAIM, "hank", "rita", t))
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.ACTIVE)
        h.move("rita", east(8.0), t + 1000)
        h.tick(t + 1000, h.action(ActionTypes.CATCH_CLAIM, "hank", "rita", t + 1000))
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.CAUGHT)
    }

    @Test fun codeCatchWorksWithCorrectCodeOnly() {
        val h = classic("catch_method" to "code")
        val t = h.active + 1000
        h.tick(t, h.action(ActionTypes.CODE_CATCH, "hank", null, t, "code" to "0000"))
        assertThat(h.game.players.values.count { it.status == PlayerStatus.CAUGHT }).isEqualTo(0)
        val code = h.game.players["rob"]!!.catchCode!!
        h.tick(t + 1, h.action(ActionTypes.CODE_CATCH, "hank", null, t + 1, "code" to code))
        assertThat(h.game.players["rob"]!!.status).isEqualTo(PlayerStatus.CAUGHT)
    }

    @Test fun autoCatchHonoursDwell() {
        val h = classic("catch_method" to "auto", "catch_radius_m" to 10, "auto_catch_dwell_sec" to 3)
        val t = h.active + 1000
        h.move("hank", ORIGIN, t); h.move("rita", east(5.0), t); h.move("rob", east(500.0), t)
        h.tick(t)
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.ACTIVE)
        listOf("hank", "rita", "rob").forEach { h.locations[it] = h.locations[it]!!.copy(time = t + 3500) }
        h.tick(t + 3500)
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.CAUGHT)
    }

    @Test fun runnersWinWhenTimeRunsOut() {
        val h = classic("duration_min" to 5)
        val end = h.game.endsAt!!
        listOf("hank", "rita", "rob").forEach { h.move(it, ORIGIN, end) }
        h.tick(end)
        assertThat(h.game.result!!.reason).isEqualTo(EndReasons.TIME_UP)
        assertThat(h.game.result!!.winnerIds).containsExactly("rita", "rob")
    }

    @Test fun zoneEliminatesRunnerOutsideAfterGrace() {
        val h = Harness(
            config = config("classic", "zone_start_radius_m" to 300, "zone_end_radius_m" to 50, "zone_grace_sec" to 5, "headstart_sec" to 0),
            ids = listOf("hank", "rita", "rob"), roster = mapOf("hank" to Role.HUNTER),
        )
        val t = h.active + 1000
        listOf("hank", "rob").forEach { h.move(it, ORIGIN, t) }
        h.move("rita", east(1000.0), t)
        h.tick(t)
        assertThat(h.game.players["rita"]!!.zoneExitAt).isEqualTo(t)
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.ACTIVE)
        listOf("hank", "rob", "rita").forEach { h.locations[it] = h.locations[it]!!.copy(time = t + 6000) }
        h.tick(t + 6000)
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.ELIMINATED)
        assertThat(h.game.players["rita"]!!.outReason).isEqualTo("zone")
    }

    @Test fun visibilityRespectsHeadstartPingsAndCloak() {
        val h = classic("runner_reveal" to "interval", "runner_reveal_interval_sec" to 60)
        val e = h.engine
        val g0 = h.game
        assertThat(VisibilityRules.decide(e, g0, "hank", "rita", g0.startAt + 1).kind).isEqualTo(VisibilityKind.HIDDEN)
        val v = VisibilityRules.decide(e, g0, "hank", "rita", g0.headstartEndsAt + 30_000)
        assertThat(v.kind).isEqualTo(VisibilityKind.SNAPSHOT)
        assertThat(v.snapshotAt).isEqualTo(g0.headstartEndsAt)
        assertThat(v.nextSnapshotAt).isEqualTo(g0.headstartEndsAt + 60_000)
        // Hunters are hidden from runners by default.
        assertThat(VisibilityRules.decide(e, g0, "rita", "hank", g0.headstartEndsAt + 1).kind).isEqualTo(VisibilityKind.HIDDEN)
        // Cloak hides the runner.
        val t = h.active + 1000
        h.tick(t, h.action(ActionTypes.USE_ABILITY, "rita", null, t, "ability" to "cloak"))
        assertThat(VisibilityRules.decide(e, h.game, "hank", "rita", t + 1000).reason).isEqualTo("Cloaked")
        assertThat(h.game.players["rita"]!!.abilityUses["cloak"]).isEqualTo(1)
    }

    @Test fun infectionConvertsCaughtRunners() {
        val h = Harness(
            config = config("infection", "zone_enabled" to false, "hunter_selection" to "manual"),
            ids = listOf("zed", "amy", "bo"), roster = mapOf("zed" to Role.HUNTER),
        )
        val t = h.active + 1000
        h.tick(t, h.action(ActionTypes.SELF_REPORT, "amy", "zed", t))
        val amy = h.game.players["amy"]!!
        assertThat(amy.role).isEqualTo(Role.HUNTER)
        assertThat(amy.status).isEqualTo(PlayerStatus.ACTIVE)
        assertThat(amy.frozenUntil).isGreaterThan(t)
        h.tick(t + 1000, h.action(ActionTypes.SELF_REPORT, "bo", "zed", t + 1000))
        assertThat(h.game.result!!.winningRole).isEqualTo(Role.HUNTER)
        assertThat(h.game.result!!.winnerIds).containsExactly("zed")
    }

    @Test fun assassinTargetsFormALoopAndAreInherited() {
        val h = Harness(config = config("assassin", "zone_enabled" to false), ids = listOf("a", "b", "c", "d"))
        val targets = h.game.players.mapValues { it.value.targetId!! }
        // Following targets from any player visits everyone exactly once.
        var cur = "a"; val seen = mutableSetOf<String>()
        repeat(4) { seen += cur; cur = targets.getValue(cur) }
        assertThat(seen).hasSize(4); assertThat(cur).isEqualTo("a")

        val killer = "a"; val victim = targets.getValue("a"); val victimsTarget = targets.getValue(victim)
        val t = h.active + 1000
        h.tick(t, h.action(ActionTypes.SELF_REPORT, victim, killer, t))
        assertThat(h.game.players[victim]!!.status).isEqualTo(PlayerStatus.CAUGHT)
        assertThat(h.game.players[killer]!!.targetId).isEqualTo(victimsTarget)
    }

    @Test fun extractionLetsRunnerEscapeAfterHold() {
        val h = Harness(
            config = config("extraction", "zone_enabled" to false, "extraction_hold_sec" to 10, "extraction_open_min" to 120, "headstart_sec" to 0),
            ids = listOf("hank", "rita", "rob"), roster = mapOf("hank" to Role.HUNTER),
        )
        val obj = h.game.objectives.values.single()
        val t = h.active + 1000
        h.move("hank", ORIGIN, t); h.move("rob", ORIGIN, t)
        h.move("rita", obj.point, t)
        h.tick(t)
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.ACTIVE)
        listOf("hank", "rob", "rita").forEach { h.locations[it] = h.locations[it]!!.copy(time = t + 11_000) }
        h.tick(t + 11_000)
        assertThat(h.game.players["rita"]!!.status).isEqualTo(PlayerStatus.ESCAPED)
        h.tick(t + 12_000, h.action(ActionTypes.SELF_REPORT, "rob", "hank", t + 12_000))
        assertThat(h.game.result!!.winnerIds).containsExactly("rita")
    }

    @Test fun lateJoinerBecomesHunterAndLeaverIsRemoved() {
        val h = classic()
        val t = h.active + 1000
        val members = h.members + ("neo" to member("neo", 9)) - "rob"
        val out = h.referee.tick(app.fugitive.engine.rules.RefereeInput(h.game, members, h.locations, emptyList(), "hank", t))
        assertThat(out.game.players["neo"]!!.role).isEqualTo(Role.HUNTER)
        assertThat(out.game.players["rob"]!!.status).isEqualTo(PlayerStatus.LEFT)
    }

    @Test fun settingsSchemaHasNoDuplicatesForAnyMode() {
        val engine = GameEngine()
        engine.modes.forEach { m -> assertThat(engine.schema(m.id).settings).isNotEmpty() }
        engine.presets.forEach { p -> engine.settings(engine.applyPreset(p)) }
    }
}
