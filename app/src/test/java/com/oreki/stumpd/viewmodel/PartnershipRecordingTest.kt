package com.oreki.stumpd.viewmodel

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
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
 * Guards the partnership / fall-of-wickets invariants:
 *  - exactly one partnership row per wicket, plus one for the unbeaten stand at innings end
 *  - wicketNumber is 1..N scoped to the innings, never repeated (a repeat would silently
 *    REPLACE the earlier row, since wicketNumber is part of the primary key)
 *
 * Deliberately does not wait on ScoringViewModel.idToNameLoaded: partnership recording works
 * off player names, not the id map, and that latch never settles in the JVM test environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PartnershipRecordingTest {

    private val gson = Gson()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun engineWith(batters: List<String>): Pair<ScoringViewModel, ScoringEngine> {
        val app = RuntimeEnvironment.getApplication()
        val vm = ScoringViewModel(
            app,
            ScoringInitParams(
                matchId = "test-match",
                team1PlayerNames = batters,
                team2PlayerNames = listOf("BowlerA", "BowlerB"),
                matchSettingsJson = gson.toJson(
                    MatchSettings(totalOvers = 20, allowSingleSideBatting = true, wideRuns = 1, noballRuns = 1)
                ),
            ),
        )
        vm.battingTeamPlayers = batters.map { Player(name = it) }.toMutableList()
        vm.bowlingTeamPlayers = mutableListOf(Player(name = "BowlerA"), Player(name = "BowlerB"))
        vm.strikerIndex = 0
        vm.nonStrikerIndex = 1
        vm.bowlerIndex = 0
        vm.currentInnings = 1
        vm.currentOver = 0
        vm.ballsInOver = 0
        vm.totalExtras = 0
        vm.deliveryHistory.clear()
        vm.allDeliveries.clear()
        vm.currentPartnershipBatsman1Name = batters[0]
        vm.currentPartnershipBatsman2Name = batters[1]
        return vm to ScoringEngine(vm)
    }

    /** Re-seats a fresh pair after a wicket, the way the batsman picker does in the UI. */
    private fun ScoringViewModel.seatPair(strikerIdx: Int, nonStrikerIdx: Int) {
        strikerIndex = strikerIdx
        nonStrikerIndex = nonStrikerIdx
        currentPartnershipBatsman1Name = battingTeamPlayers[strikerIdx].name
        currentPartnershipBatsman2Name = battingTeamPlayers[nonStrikerIdx].name
    }

    @Test
    fun `wicket on a scoreless stand still records a partnership row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C"))

        engine.onWicketTypeSelected(WicketType.BOWLED)

        assertThat(vm.partnerships).hasSize(1)
        assertThat(vm.partnerships[0].runs).isEqualTo(0)
        // The wicket delivery itself is a legal ball and belongs to the stand.
        assertThat(vm.partnerships[0].balls).isEqualTo(1)
        assertThat(vm.fallOfWickets).hasSize(1)
    }

    @Test
    fun `each wicket records one partnership and one fall of wicket numbered in sequence`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C", "D", "E", "F"))

        engine.onRunScored(1)
        engine.onWicketTypeSelected(WicketType.BOWLED)
        vm.seatPair(2, 1)

        engine.onRunScored(2)
        engine.onWicketTypeSelected(WicketType.BOWLED)
        vm.seatPair(3, 1)

        engine.onWicketTypeSelected(WicketType.BOWLED)

        assertThat(vm.totalWickets).isEqualTo(3)
        assertThat(vm.partnerships).hasSize(3)
        assertThat(vm.fallOfWickets).hasSize(3)
        assertThat(vm.fallOfWickets.map { it.wicketNumber }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `run out takes the next wicket number instead of colliding with the previous one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C", "D"))

        engine.onWicketTypeSelected(WicketType.BOWLED)
        vm.seatPair(2, 1)

        vm.pendingRunOutInput = RunOutInput(whoOut = "C", end = RunOutEnd.STRIKER_END, runsCompleted = 0)
        engine.onFielderSelectedForRunOut(vm.bowlingTeamPlayers[1])

        assertThat(vm.totalWickets).isEqualTo(2)
        // Previously the run-out path incremented totalWickets after recording, so it reused
        // the previous number and the earlier row was overwritten on save.
        assertThat(vm.fallOfWickets.map { it.wicketNumber }).containsExactly(1, 2).inOrder()
        assertThat(vm.partnerships).hasSize(2)
    }

    @Test
    fun `run out credits the completed runs to the partnership`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C"))

        vm.pendingRunOutInput = RunOutInput(whoOut = "A", end = RunOutEnd.STRIKER_END, runsCompleted = 2)
        engine.onFielderSelectedForRunOut(vm.bowlingTeamPlayers[1])

        assertThat(vm.partnerships).hasSize(1)
        assertThat(vm.partnerships[0].runs).isEqualTo(2)
    }

    @Test
    fun `innings end records the unbeaten stand as active`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C"))

        engine.onRunScored(4)
        engine.saveInningsPartnershipsAndWickets()

        assertThat(vm.firstInningsPartnerships).hasSize(1)
        assertThat(vm.firstInningsPartnerships[0].runs).isEqualTo(4)
        assertThat(vm.firstInningsPartnerships[0].isActive).isTrue()
    }

    @Test
    fun `closing the unbeaten stand twice does not duplicate it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C"))

        engine.onRunScored(3)
        engine.closeCurrentPartnershipIfAny()
        engine.closeCurrentPartnershipIfAny()

        assertThat(vm.partnerships).hasSize(1)
    }

    @Test
    fun `closing the second innings stand keeps the live lists for the match save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C"))
        vm.currentInnings = 2

        engine.onWicketTypeSelected(WicketType.BOWLED)
        vm.seatPair(2, 1)
        engine.onRunScored(5)
        engine.closeCurrentPartnershipIfAny()

        // The completed-match save reads these live lists for innings 2, so they must survive.
        assertThat(vm.partnerships).hasSize(2)
        assertThat(vm.fallOfWickets).hasSize(1)
    }

    @Test
    fun `a no ball does not add a ball to the partnership`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C"))

        engine.onQuickNoBallConfirmed(1)

        assertThat(vm.currentPartnershipBalls).isEqualTo(0)
        assertThat(vm.currentPartnershipRuns).isEqualTo(1)
    }

    @Test
    fun `undoing a wicket frees its number for reuse`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = engineWith(listOf("A", "B", "C", "D"))

        engine.onWicketTypeSelected(WicketType.BOWLED)
        engine.undoLastDelivery()
        vm.seatPair(0, 1)
        engine.onWicketTypeSelected(WicketType.BOWLED)

        assertThat(vm.fallOfWickets).hasSize(1)
        assertThat(vm.fallOfWickets[0].wicketNumber).isEqualTo(1)
        assertThat(vm.totalWickets).isEqualTo(1)
    }
}
