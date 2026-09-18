package com.oreki.stumpd.viewmodel

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

/**
 * The super over as the scorer experiences it: a tie, an offer, an over each, a winner.
 *
 * The important assertions are the quiet ones. The match's own second-innings figures must survive
 * an eliminator that resets exactly the counters they used to be read from, the second innings'
 * partnerships must not collect a super-over wicket, and nothing may be written until the result
 * is settled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SuperOverFlowTest {

    private val gson = Gson()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(superOver: Boolean): ScoringViewModel {
        val app = RuntimeEnvironment.getApplication()
        return ScoringViewModel(
            app,
            ScoringInitParams(
                matchId = "so-test",
                team1PlayerNames = listOf("Kushal", "Gokul", "Ajith"),
                team2PlayerNames = listOf("Muttu", "Madhu", "Sunil"),
                matchSettingsJson = gson.toJson(
                    MatchSettings(
                        totalOvers = 1,
                        maxPlayersPerTeam = 3,
                        allowSingleSideBatting = true,
                        enableSuperOver = superOver,
                    )
                ),
            ),
        )
    }

    private suspend fun TestScope.freshMatch(superOver: Boolean = true): Pair<ScoringViewModel, ScoringEngine> {
        val vm = createViewModel(superOver)
        repeat(400) {
            if (vm.idToNameLoaded) return@repeat
            advanceUntilIdle()
            withContext(Dispatchers.Default) { delay(5) }
        }
        vm.team1Players = mutableListOf(Player(name = "Kushal"), Player(name = "Gokul"), Player(name = "Ajith"))
        vm.team2Players = mutableListOf(Player(name = "Muttu"), Player(name = "Madhu"), Player(name = "Sunil"))
        vm.battingTeamPlayers = vm.team1Players
        vm.bowlingTeamPlayers = vm.team2Players
        vm.battingTeamName = "Strikers"
        vm.bowlingTeamName = "Chasers"
        vm.strikerIndex = 0
        vm.nonStrikerIndex = 1
        vm.bowlerIndex = 0
        vm.currentInnings = 1
        vm.currentOver = 0
        vm.ballsInOver = 0
        vm.totalExtras = 0
        vm.deliveryHistory.clear()
        vm.allDeliveries.clear()
        val engine = ScoringEngine(vm)
        engine.initPartnershipIfNeeded()
        return vm to engine
    }

    /** Scores [runs] off six legal balls, then ends the innings. */
    private fun ScoringEngine.bowlAnOverOf(runs: Int, vm: ScoringViewModel) {
        repeat(6) { ball -> onRunScored(if (ball == 0) runs else 0) }
        if (vm.isInningsComplete) onInningsComplete()
    }

    @Test
    fun `a tie offers a super over instead of finishing the match`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshMatch()

        engine.bowlAnOverOf(4, vm)
        assertThat(vm.showInningsBreakDialog).isTrue()
        engine.onStartSecondInnings()
        vm.strikerIndex = 0; vm.nonStrikerIndex = 1; vm.bowlerIndex = 0
        engine.initPartnershipIfNeeded()
        assertThat(vm.runsToChase).isEqualTo(4)

        engine.bowlAnOverOf(4, vm)

        // Nothing is written yet: the complete dialog saves the moment it appears.
        assertThat(vm.showSuperOverOfferDialog).isTrue()
        assertThat(vm.showMatchCompleteDialog).isFalse()
        // And the match's own second-innings figures are banked before anything resets them.
        assertThat(vm.secondInningsRuns).isEqualTo(4)
        assertThat(vm.firstInningsRuns).isEqualTo(4)
    }

    @Test
    fun `without the setting a tie finishes the match as it always did`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshMatch(superOver = false)

        engine.bowlAnOverOf(4, vm)
        engine.onStartSecondInnings()
        vm.strikerIndex = 0; vm.nonStrikerIndex = 1; vm.bowlerIndex = 0
        engine.initPartnershipIfNeeded()
        engine.bowlAnOverOf(4, vm)

        assertThat(vm.showSuperOverOfferDialog).isFalse()
        assertThat(vm.showMatchCompleteDialog).isTrue()
        assertThat(vm.superOverWinner).isNull()
    }

    @Test
    fun `the side that batted second bats the super over first, and the match figures survive it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshMatch()
        tieTheMatch(vm, engine)

        val chasedWith = vm.battingTeamName
        engine.startSuperOver()

        assertThat(vm.currentInnings).isEqualTo(3)
        // No swap entering an odd innings: whoever chased bats again.
        assertThat(vm.battingTeamName).isEqualTo(chasedWith)
        assertThat(vm.runsToChase).isNull()
        assertThat(vm.oversForCurrentInnings).isEqualTo(1)
        assertThat(vm.wicketCapForCurrentInnings).isEqualTo(2)
        // The figures the saved match will use are untouched by the reset.
        assertThat(vm.secondInningsRuns).isEqualTo(4)
        assertThat(vm.firstInningsRuns).isEqualTo(4)
    }

    @Test
    fun `a super over decides the match, and the balls are recorded as innings 3 and 4`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshMatch()
        tieTheMatch(vm, engine)

        engine.startSuperOver()
        vm.strikerIndex = 0; vm.nonStrikerIndex = 1; vm.bowlerIndex = 0
        engine.initPartnershipIfNeeded()
        val setter = vm.battingTeamName
        engine.bowlAnOverOf(6, vm)

        assertThat(vm.showSuperOverIntervalDialog).isTrue()
        assertThat(vm.superOvers.single().runs).isEqualTo(6)

        engine.startSuperOver()
        vm.strikerIndex = 0; vm.nonStrikerIndex = 1; vm.bowlerIndex = 0
        engine.initPartnershipIfNeeded()
        val chaser = vm.battingTeamName
        assertThat(chaser).isNotEqualTo(setter)
        assertThat(vm.runsToChase).isEqualTo(6)
        engine.bowlAnOverOf(2, vm)

        assertThat(vm.superOverWinner).isEqualTo(setter)
        assertThat(vm.showMatchCompleteDialog).isTrue()
        assertThat(vm.allDeliveries.map { it.inning }.distinct()).containsExactly(1, 2, 3, 4).inOrder()
    }

    @Test
    fun `a tied super over asks again rather than deciding`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshMatch()
        tieTheMatch(vm, engine)

        engine.startSuperOver()
        vm.strikerIndex = 0; vm.nonStrikerIndex = 1; vm.bowlerIndex = 0
        engine.initPartnershipIfNeeded()
        engine.bowlAnOverOf(3, vm)
        engine.startSuperOver()
        vm.strikerIndex = 0; vm.nonStrikerIndex = 1; vm.bowlerIndex = 0
        engine.initPartnershipIfNeeded()
        engine.bowlAnOverOf(3, vm)

        assertThat(vm.superOverWinner).isNull()
        assertThat(vm.showSuperOverOfferDialog).isTrue()
        assertThat(vm.showMatchCompleteDialog).isFalse()

        // Declining records the tie and finishes — the way out when the light goes.
        engine.declineSuperOver()
        assertThat(vm.superOverWinner).isEqualTo("TIE")
        assertThat(vm.showMatchCompleteDialog).isTrue()
    }

    @Test
    fun `declining the first offer leaves an ordinary tie`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshMatch()
        tieTheMatch(vm, engine)

        engine.declineSuperOver()

        // No super over was played, so this is a plain tie and the result derivation says so.
        assertThat(vm.superOverWinner).isNull()
        assertThat(vm.superOvers).isEmpty()
        assertThat(vm.showMatchCompleteDialog).isTrue()
    }

    @Test
    fun `a super-over wicket does not land in the second innings' fall of wickets`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine) = freshMatch()
        tieTheMatch(vm, engine)
        val secondInningsWickets = vm.secondInningsFallOfWickets.size

        engine.startSuperOver()
        vm.strikerIndex = 0; vm.nonStrikerIndex = 1; vm.bowlerIndex = 0
        engine.initPartnershipIfNeeded()
        engine.onWicketTypeSelected(com.oreki.stumpd.domain.model.WicketType.BOWLED)

        // The live list is where the eliminator's wicket goes, and the snapshot is what the match
        // saves — so the second innings keeps exactly what it had.
        assertThat(vm.secondInningsFallOfWickets).hasSize(secondInningsWickets)
    }

    /** Both sides make four off one over, leaving the match level and the offer on screen. */
    private fun tieTheMatch(vm: ScoringViewModel, engine: ScoringEngine) {
        engine.bowlAnOverOf(4, vm)
        engine.onStartSecondInnings()
        vm.strikerIndex = 0; vm.nonStrikerIndex = 1; vm.bowlerIndex = 0
        engine.initPartnershipIfNeeded()
        engine.bowlAnOverOf(4, vm)
    }
}
