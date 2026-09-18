package com.oreki.stumpd.viewmodel

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.gson.Gson
import com.oreki.stumpd.domain.model.ExtraType
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Player
import com.oreki.stumpd.domain.model.RunOutEnd
import com.oreki.stumpd.domain.model.RunOutInput
import com.oreki.stumpd.domain.model.WicketType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Every ball a batter faces must be accounted for exactly once: either a dot, or one of the
 * scoring buckets. The invariant is
 *
 *     ballsFaced == dots + singles + twos + threes + fours + sixes
 *
 * It was violated on every wicket ball, every bye/leg-bye and every no-ball with nothing off
 * the bat, because those paths incremented ballsFaced without touching dots. Run-outs also
 * added the completed runs without putting them in a bucket.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DotBallAccountingTest {

    private val gson = Gson()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun engine(): Pair<ScoringViewModel, ScoringEngine> {
        val app = RuntimeEnvironment.getApplication()
        val vm = ScoringViewModel(
            app,
            ScoringInitParams(
                matchId = "dots-test",
                team1PlayerNames = listOf("A", "B", "C", "D"),
                team2PlayerNames = listOf("BowlerA", "BowlerB"),
                matchSettingsJson = gson.toJson(
                    MatchSettings(totalOvers = 20, allowSingleSideBatting = true, wideRuns = 1, noballRuns = 1)
                ),
            ),
        )
        vm.battingTeamPlayers = listOf("A", "B", "C", "D").map { Player(name = it) }.toMutableList()
        vm.bowlingTeamPlayers = mutableListOf(Player(name = "BowlerA"), Player(name = "BowlerB"))
        vm.strikerIndex = 0
        vm.nonStrikerIndex = 1
        vm.bowlerIndex = 0
        vm.currentInnings = 1
        vm.currentPartnershipBatsman1Name = "A"
        vm.currentPartnershipBatsman2Name = "B"
        vm.deliveryHistory.clear()
        vm.allDeliveries.clear()
        return vm to ScoringEngine(vm)
    }

    /** ballsFaced must equal dots plus every scoring bucket. */
    private fun assertAccountsFor(p: Player, context: String) {
        val accounted = p.dots + p.singles + p.twos + p.threes + p.fours + p.sixes
        assertWithMessage(
            "$context: ${p.name} faced ${p.ballsFaced} balls but only $accounted are accounted " +
                "for (dots=${p.dots} 1s=${p.singles} 2s=${p.twos} 3s=${p.threes} " +
                "4s=${p.fours} 6s=${p.sixes})"
        ).that(accounted).isEqualTo(p.ballsFaced)
    }

    @Test
    fun `a dot ball off the bat counts as a dot`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val (vm, engine) = engine()

        engine.onRunScored(0)

        val p = vm.battingTeamPlayers[0]
        assertThat(p.ballsFaced).isEqualTo(1)
        assertThat(p.dots).isEqualTo(1)
        assertAccountsFor(p, "dot ball")
    }

    @Test
    fun `a wicket ball is a dot faced`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val (vm, engine) = engine()

        engine.onWicketTypeSelected(WicketType.BOWLED)

        val out = vm.battingTeamPlayers.first { it.isOut }
        assertThat(out.ballsFaced).isEqualTo(1)
        assertThat(out.dots).isEqualTo(1)
        assertAccountsFor(out, "wicket ball")
    }

    @Test
    fun `a leg bye is a dot for the batter`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val (vm, engine) = engine()

        engine.onExtraSelected(ExtraType.LEG_BYE, 1)

        val p = vm.battingTeamPlayers[0]
        assertThat(p.ballsFaced).isEqualTo(1)
        assertThat(p.runs).isEqualTo(0)
        assertThat(p.dots).isEqualTo(1)
        assertAccountsFor(p, "leg bye")
    }

    @Test
    fun `a no ball with nothing off the bat is a dot`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val (vm, engine) = engine()

        engine.onQuickNoBallConfirmed(vm.matchSettings.noballRuns)

        val p = vm.battingTeamPlayers[0]
        assertThat(p.ballsFaced).isEqualTo(1)
        assertThat(p.dots).isEqualTo(1)
        assertAccountsFor(p, "no ball, no runs off bat")
    }

    @Test
    fun `run out completed runs land in the shot buckets`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val (vm, engine) = engine()

        vm.pendingRunOutInput = RunOutInput(whoOut = "B", end = RunOutEnd.NON_STRIKER_END, runsCompleted = 2)
        engine.onFielderSelectedForRunOut(vm.bowlingTeamPlayers[1])

        // The striker faced the ball and the two completed runs are credited to them.
        val scorer = vm.battingTeamPlayers.first { it.name == "A" }
        assertThat(scorer.runs).isEqualTo(2)
        assertThat(scorer.twos).isEqualTo(1)
        assertThat(scorer.dots).isEqualTo(0)
        assertAccountsFor(scorer, "run out for two")
    }

    @Test
    fun `a run out with no completed runs is a dot`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val (vm, engine) = engine()

        vm.pendingRunOutInput = RunOutInput(whoOut = "B", end = RunOutEnd.NON_STRIKER_END, runsCompleted = 0)
        engine.onFielderSelectedForRunOut(vm.bowlingTeamPlayers[1])

        val scorer = vm.battingTeamPlayers.first { it.name == "A" }
        assertThat(scorer.runs).isEqualTo(0)
        assertThat(scorer.dots).isEqualTo(1)
        assertAccountsFor(scorer, "run out for nought")
    }

    @Test
    fun `a mixed over accounts for every ball faced`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val (vm, engine) = engine()

        // Deliberately mixes the paths that used to lose dots.
        engine.onRunScored(1)   // strike rotates
        engine.onRunScored(0)
        engine.onRunScored(4)
        engine.onExtraSelected(ExtraType.BYE, 1)
        engine.onQuickNoBallConfirmed(vm.matchSettings.noballRuns)
        engine.onRunScored(2)

        vm.battingTeamPlayers.filter { it.ballsFaced > 0 }.forEach {
            assertAccountsFor(it, "mixed over")
        }
    }
}
