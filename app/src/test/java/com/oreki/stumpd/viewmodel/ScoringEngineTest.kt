package com.oreki.stumpd.viewmodel

import com.google.gson.Gson
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Player
import com.oreki.stumpd.domain.model.WicketType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.google.common.truth.Truth.assertThat

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScoringEngineTest {

    private val gson = Gson()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun matchSettingsJson(wideRuns: Int = 1, noballRuns: Int = 1, allowSingleSide: Boolean = true) =
        gson.toJson(
            MatchSettings(
                totalOvers = 20,
                allowSingleSideBatting = allowSingleSide,
                wideRuns = wideRuns,
                noballRuns = noballRuns,
            )
        )

    private fun createViewModel(): ScoringViewModel {
        val app = RuntimeEnvironment.getApplication()
        return ScoringViewModel(
            app,
            ScoringInitParams(
                matchId = "test-match",
                team1PlayerNames = listOf("Striker", "NonStriker"),
                team2PlayerNames = listOf("BowlerA", "BowlerB"),
                matchSettingsJson = matchSettingsJson(),
            ),
        )
    }

    private suspend fun TestScope.awaitIdMapLoaded(vm: ScoringViewModel) {
        repeat(400) {
            if (vm.idToNameLoaded) return
            advanceUntilIdle()
            // The player load runs on Dispatchers.IO, so it needs real elapsed time. A plain
            // delay() here is virtual under runTest and completes instantly, so the loop spun
            // 400 times without ever letting the IO thread finish and the flag never flipped.
            withContext(Dispatchers.Default) { delay(5) }
        }
        assertThat(vm.idToNameLoaded).isTrue()
    }

    private suspend fun TestScope.freshEngine(): Pair<ScoringViewModel, ScoringEngine> {
        val vm = createViewModel()
        awaitIdMapLoaded(vm)
        vm.battingTeamPlayers = mutableListOf(
            Player(name = "Striker"),
            Player(name = "NonStriker"),
        )
        vm.bowlingTeamPlayers = mutableListOf(
            Player(name = "BowlerA"),
            Player(name = "BowlerB"),
        )
        vm.strikerIndex = 0
        vm.nonStrikerIndex = 1
        vm.bowlerIndex = 0
        vm.currentPartnershipBatsman1Name = "Striker"
        vm.currentPartnershipBatsman2Name = "NonStriker"
        vm.currentInnings = 1
        vm.currentOver = 0
        vm.ballsInOver = 0
        vm.totalExtras = 0
        vm.deliveryHistory.clear()
        vm.allDeliveries.clear()
        return vm to ScoringEngine(vm)
    }

    @Test
    fun onRunScored_updatesStrikerBowlerAndTotal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshEngine()
        engine.onRunScored(4)
        val striker = vm.battingTeamPlayers[vm.strikerIndex!!]
        assertThat(striker.runs).isEqualTo(4)
        assertThat(striker.ballsFaced).isEqualTo(1)
        assertThat(striker.fours).isEqualTo(1)
        val bowler = vm.bowlingTeamPlayers[vm.bowlerIndex!!]
        assertThat(bowler.runsConceded).isEqualTo(4)
        assertThat(bowler.ballsBowled).isEqualTo(1)
        assertThat(vm.calculatedTotalRuns).isEqualTo(4)
        assertThat(vm.ballsInOver).isEqualTo(1)
    }

    @Test
    fun onQuickWide_addsExtrasAndBowlingConceded_withoutBattingRuns() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshEngine()
        val wideRuns = vm.matchSettings.wideRuns
        engine.onQuickWide()
        assertThat(vm.totalExtras).isEqualTo(wideRuns)
        assertThat(vm.battingTeamPlayers[0].runs).isEqualTo(0)
        assertThat(vm.bowlingTeamPlayers[0].runsConceded).isEqualTo(wideRuns)
        assertThat(vm.runsConcededInCurrentOver).isEqualTo(wideRuns)
        assertThat(vm.calculatedTotalRuns).isEqualTo(wideRuns)
    }

    @Test
    fun onQuickNoBallConfirmed_addsNoballExtraAndStrikerRunsFromAdditional() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshEngine()
        val base = vm.matchSettings.noballRuns
        val totalNb = base + 3
        engine.onQuickNoBallConfirmed(totalNb)
        assertThat(vm.totalExtras).isEqualTo(base)
        assertThat(vm.battingTeamPlayers[0].runs).isEqualTo(3)
        assertThat(vm.battingTeamPlayers[0].ballsFaced).isEqualTo(1)
        assertThat(vm.bowlingTeamPlayers[0].runsConceded).isEqualTo(totalNb)
        assertThat(vm.calculatedTotalRuns).isEqualTo(base + 3)
    }

    @Test
    fun undoLastDelivery_restoresStateAfterOnRunScored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshEngine()
        val beforeStriker = vm.battingTeamPlayers[0].copy()
        val beforeBowler = vm.bowlingTeamPlayers[0].copy()
        val beforeTotal = vm.calculatedTotalRuns
        val beforeBalls = vm.ballsInOver
        engine.onRunScored(2)
        assertThat(vm.calculatedTotalRuns).isEqualTo(beforeTotal + 2)
        engine.undoLastDelivery()
        assertThat(vm.battingTeamPlayers[0]).isEqualTo(beforeStriker)
        assertThat(vm.bowlingTeamPlayers[0]).isEqualTo(beforeBowler)
        assertThat(vm.calculatedTotalRuns).isEqualTo(beforeTotal)
        assertThat(vm.ballsInOver).isEqualTo(beforeBalls)
        assertThat(vm.deliveryHistory).isEmpty()
    }

    @Test
    fun onRunScored_oddRuns_swapsStrikeWhenTwoBatters() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshEngine()
        assertThat(vm.strikerIndex).isEqualTo(0)
        engine.onRunScored(1)
        assertThat(vm.strikerIndex).isEqualTo(1)
        assertThat(vm.nonStrikerIndex).isEqualTo(0)
    }
    @Test
    fun `a joker's second catch is counted, not overwritten`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshEngine()
        // The joker fields for whichever side is bowling and isn't in its list until they do
        // something, so the add-on path used to set the counter to 1 every time.
        val joker = Player(name = "Guest", isJoker = true)

        vm.pendingWicketType = WicketType.CAUGHT
        engine.onFielderSelectedForCaughtStumped(joker)
        val afterFirst = vm.bowlingTeamPlayers.last { it.isJoker }
        assertThat(afterFirst.catches).isEqualTo(1)

        vm.pendingWicketType = WicketType.CAUGHT
        engine.onFielderSelectedForCaughtStumped(afterFirst)
        assertThat(vm.bowlingTeamPlayers.last { it.isJoker }.catches).isEqualTo(2)
    }

}
