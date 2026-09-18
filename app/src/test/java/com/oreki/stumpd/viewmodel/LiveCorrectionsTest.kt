package com.oreki.stumpd.viewmodel

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Player
import com.oreki.stumpd.domain.model.WicketType
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The three mid-match fixes, and the promise they make: after one, every figure agrees, and Undo
 * still takes you back.
 *
 * These are the corrections made under pressure — someone is waiting to bowl — so the tests care
 * most about what *doesn't* move. A dismissal reassigned must not shift a run or a ball faced; an
 * over reassigned must not change the team total. And each must refuse, with a reason, rather than
 * approximate its way through a state it can't handle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LiveCorrectionsTest {

    private val gson = Gson()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ScoringViewModel {
        val app = RuntimeEnvironment.getApplication()
        return ScoringViewModel(
            app,
            ScoringInitParams(
                matchId = "fix-test",
                team1PlayerNames = listOf("Kushal", "Gokul", "Ajith"),
                team2PlayerNames = listOf("Muttu", "Madhu"),
                matchSettingsJson = gson.toJson(
                    MatchSettings(totalOvers = 5, maxPlayersPerTeam = 3, allowSingleSideBatting = true)
                ),
            ),
        )
    }

    private suspend fun TestScope.awaitIdMapLoaded(vm: ScoringViewModel) {
        repeat(400) {
            if (vm.idToNameLoaded) return
            advanceUntilIdle()
            withContext(Dispatchers.Default) { delay(5) }
        }
    }

    /** Kushal and Gokul at the crease, Ajith to come; Muttu bowling. */
    private suspend fun TestScope.freshMatch(): Triple<ScoringViewModel, ScoringEngine, LiveCorrections> {
        val vm = createViewModel()
        awaitIdMapLoaded(vm)
        vm.battingTeamPlayers = mutableListOf(
            Player(name = "Kushal"), Player(name = "Gokul"), Player(name = "Ajith"),
        )
        vm.bowlingTeamPlayers = mutableListOf(Player(name = "Muttu"), Player(name = "Madhu"))
        vm.strikerIndex = 0
        vm.nonStrikerIndex = 1
        vm.bowlerIndex = 0
        vm.currentInnings = 1
        vm.currentOver = 0
        vm.ballsInOver = 0
        vm.totalExtras = 0
        vm.deliveryHistory.clear()
        vm.allDeliveries.clear()
        vm.partnerships = emptyList()
        vm.fallOfWickets = emptyList()
        val engine = ScoringEngine(vm)
        engine.initPartnershipIfNeeded()
        return Triple(vm, engine, LiveCorrections(vm, engine))
    }

    /**
     * The striker bowled by Muttu, with Ajith in to replace them.
     *
     * Returns who went and who stayed, rather than naming them: the strike rotates on odd runs,
     * so which of the openers is facing depends on the balls scored before this, and a test that
     * hard-codes the name is testing its own arithmetic.
     */
    private fun ScoringEngine.dismissStriker(vm: ScoringViewModel): Pair<String, String> {
        val out = vm.striker!!.name
        val partner = vm.nonStriker!!.name
        onWicketTypeSelected(WicketType.BOWLED)
        if (vm.showBatsmanDialog) {
            vm.selection.onBatsmanSelected(vm.battingTeamPlayers.first { it.name == "Ajith" })
        }
        return out to partner
    }

    // ── Wrong batter given out ───────────────────────────────────────────────────────

    @Test
    fun `the dismissal moves to the other batter and takes nothing with it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(2)
        engine.onRunScored(1)
        val (wronglyOut, partner) = engine.dismissStriker(vm)

        val runsBefore = vm.battingTeamPlayers.associate { it.name to it.runs }
        val ballsBefore = vm.battingTeamPlayers.associate { it.name to it.ballsFaced }
        val wicketsBefore = vm.bowlingTeamPlayers.single { it.name == "Muttu" }.wickets

        assertThat(fixes.swapCandidatesForLastWicket().map { it.name }).containsExactly(partner)
        assertThat(fixes.reassignLastWicket(partner)).isNull()

        val backIn = vm.battingTeamPlayers.single { it.name == wronglyOut }
        val nowOut = vm.battingTeamPlayers.single { it.name == partner }
        assertThat(backIn.isOut).isFalse()
        assertThat(backIn.dismissalType).isNull()
        assertThat(nowOut.isOut).isTrue()
        assertThat(nowOut.dismissalType).isEqualTo(WicketType.BOWLED)
        assertThat(nowOut.bowlerName).isEqualTo("Muttu")

        // Nothing else moved: runs, balls faced, the bowler's wicket and the team's total.
        assertThat(vm.battingTeamPlayers.associate { it.name to it.runs }).isEqualTo(runsBefore)
        assertThat(vm.battingTeamPlayers.associate { it.name to it.ballsFaced }).isEqualTo(ballsBefore)
        assertThat(vm.bowlingTeamPlayers.single { it.name == "Muttu" }.wickets).isEqualTo(wicketsBefore)
        assertThat(vm.totalWickets).isEqualTo(1)
    }

    @Test
    fun `the fall-of-wickets row and the crease follow the dismissal`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(1)
        val (wronglyOut, partner) = engine.dismissStriker(vm)
        val scoreAtWicket = vm.fallOfWickets.single().runs

        fixes.reassignLastWicket(partner)

        val fow = vm.fallOfWickets.single()
        assertThat(fow.batsmanName).isEqualTo(partner)
        assertThat(fow.runs).isEqualTo(scoreAtWicket)
        assertThat(fow.wicketNumber).isEqualTo(1)

        // The wrongly-out batter is back in, the other is gone, and the scorecard's archive of
        // finished batters agrees with both.
        val atCrease = listOfNotNull(vm.striker?.name, vm.nonStriker?.name)
        assertThat(atCrease).contains(wronglyOut)
        assertThat(atCrease).doesNotContain(partner)
        assertThat(vm.completedBattersInnings1.map { it.name }).containsExactly(partner)
    }

    @Test
    fun `a correction is itself undoable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(1)
        val (wronglyOut, partner) = engine.dismissStriker(vm)
        assertThat(fixes.reassignLastWicket(partner)).isNull()

        engine.undoLastDelivery()

        assertThat(vm.fallOfWickets.single().batsmanName).isEqualTo(wronglyOut)
        assertThat(vm.battingTeamPlayers.single { it.name == wronglyOut }.isOut).isTrue()
        assertThat(vm.battingTeamPlayers.single { it.name == partner }.isOut).isFalse()
    }

    @Test
    fun `a batter who was not in that stand is refused, with a reason`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(1)
        val (wronglyOut, _) = engine.dismissStriker(vm)

        // Ajith came in *after* the wicket, so giving it to him would falsify the partnership
        // and the score it fell at — that needs the post-match editor, which recomputes both.
        val problem = fixes.reassignLastWicket("Ajith")

        assertThat(problem).contains("full editor")
        assertThat(vm.fallOfWickets.single().batsmanName).isEqualTo(wronglyOut)
    }

    @Test
    fun `nothing to fix before a wicket falls`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (_, engine, fixes) = freshMatch()
        engine.onRunScored(1)

        assertThat(fixes.lastWicket()).isNull()
        assertThat(fixes.swapCandidatesForLastWicket()).isEmpty()
        assertThat(fixes.reassignLastWicket("Gokul")).contains("No wicket")
    }

    // ── Wrong bowler credited ────────────────────────────────────────────────────────

    @Test
    fun `an over moves to another bowler with its balls, runs and wickets`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(4)
        engine.onRunScored(0)
        engine.dismissStriker(vm)

        assertThat(fixes.reassignableOver()).isEqualTo(1)
        assertThat(fixes.bowlerOfOver(1)).isEqualTo("Muttu")
        assertThat(fixes.reassignOverBowler(1, "Madhu")).isNull()

        val muttu = vm.bowlingTeamPlayers.single { it.name == "Muttu" }
        val madhu = vm.bowlingTeamPlayers.single { it.name == "Madhu" }
        assertThat(muttu.ballsBowled).isEqualTo(0)
        assertThat(muttu.runsConceded).isEqualTo(0)
        assertThat(muttu.wickets).isEqualTo(0)
        assertThat(madhu.ballsBowled).isEqualTo(3)
        assertThat(madhu.runsConceded).isEqualTo(4)
        assertThat(madhu.wickets).isEqualTo(1)

        // The trail, the fall-of-wickets row and the batter's own row all name the new bowler.
        assertThat(vm.allDeliveries.map { it.bowlerName }.distinct()).containsExactly("Madhu")
        assertThat(vm.fallOfWickets.single().bowlerName).isEqualTo("Madhu")
        assertThat(vm.completedBattersInnings1.single().bowlerName).isEqualTo("Madhu")
        // And the team total is none of the bowler's business.
        assertThat(vm.calculatedTotalRuns).isEqualTo(4)
        assertThat(vm.bowlerIndex).isEqualTo(vm.bowlingTeamPlayers.indexOfFirst { it.name == "Madhu" })
    }

    @Test
    fun `a run out does not move a wicket to the new bowler`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(1)
        // Run outs are credited to nobody's bowling figures, here or on the scorecard.
        vm.fallOfWickets = listOf(
            com.oreki.stumpd.domain.model.FallOfWicket(
                batsmanName = "Kushal", runs = 1, overs = 0.2, wicketNumber = 1,
                dismissalType = WicketType.RUN_OUT.name, bowlerName = "Muttu", fielderName = "Madhu",
            )
        )

        assertThat(fixes.reassignOverBowler(1, "Madhu")).isNull()

        assertThat(vm.bowlingTeamPlayers.single { it.name == "Madhu" }.wickets).isEqualTo(0)
        assertThat(vm.bowlingTeamPlayers.single { it.name == "Muttu" }.wickets).isEqualTo(0)
    }

    @Test
    fun `an over with two bowlers in it is refused rather than guessed at`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(1)
        vm.allDeliveries[0] = vm.allDeliveries[0].copy(bowlerName = "Madhu")
        engine.onRunScored(1)

        assertThat(fixes.bowlerOfOver(1)).isNull()
        assertThat(fixes.reassignOverBowler(1, "Madhu")).contains("more than one bowler")
    }

    @Test
    fun `an earlier over can be fixed from a later one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        // Over 1 by Muttu, then over 2 by Madhu — the mistake noticed one over late.
        repeat(6) { engine.onRunScored(1) }
        vm.bowlerIndex = vm.bowlingTeamPlayers.indexOfFirst { it.name == "Madhu" }
        engine.onRunScored(1)

        assertThat(fixes.reassignableOvers()).containsExactly(1, 2).inOrder()
        assertThat(fixes.bowlerOfOver(1)).isEqualTo("Muttu")

        // Over 1 was really Madhu's: move it, while over 2 is in progress.
        assertThat(fixes.reassignOverBowler(1, "Madhu")).isNull()

        val muttu = vm.bowlingTeamPlayers.single { it.name == "Muttu" }
        val madhu = vm.bowlingTeamPlayers.single { it.name == "Madhu" }
        assertThat(muttu.ballsBowled).isEqualTo(0)
        assertThat(muttu.runsConceded).isEqualTo(0)
        // Madhu now has over 1's six balls plus the one she has bowled of over 2.
        assertThat(madhu.ballsBowled).isEqualTo(7)
        assertThat(vm.allDeliveries.filter { it.over == 1 }.map { it.bowlerName }.distinct())
            .containsExactly("Madhu")
        // And the over in progress still belongs to whoever is bowling it.
        assertThat(vm.bowler?.name).isEqualTo("Madhu")
    }

    @Test
    fun `with nothing bowled in the new over the fix reaches back to the last one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        repeat(6) { engine.onRunScored(1) }

        // The over has ended, so `currentOver` has moved on but no ball of it exists yet.
        assertThat(vm.currentOverNumber).isEqualTo(2)
        assertThat(fixes.reassignableOver()).isEqualTo(1)
    }

    // ── Wrong runs on the last ball ──────────────────────────────────────────────────

    @Test
    fun `restating the last ball replaces the runs everywhere at once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(1)
        engine.onRunScored(4)   // meant to be 2

        assertThat(fixes.restateLastDelivery(2)).isNull()

        assertThat(vm.calculatedTotalRuns).isEqualTo(3)
        assertThat(vm.allDeliveries).hasSize(2)
        assertThat(vm.allDeliveries.last().outcome).isEqualTo("2")
        assertThat(vm.bowlingTeamPlayers.single { it.name == "Muttu" }.runsConceded).isEqualTo(3)
        assertThat(vm.bowlingTeamPlayers.single { it.name == "Muttu" }.ballsBowled).isEqualTo(2)
        assertThat(vm.battingTeamPlayers.sumOf { it.fours }).isEqualTo(0)
        assertThat(vm.ballsInOver).isEqualTo(2)
    }

    @Test
    fun `clearing the last ball leaves the over one ball shorter`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, fixes) = freshMatch()
        engine.onRunScored(1)
        engine.onRunScored(1)

        assertThat(fixes.clearLastDelivery()).isNull()

        assertThat(vm.allDeliveries).hasSize(1)
        assertThat(vm.ballsInOver).isEqualTo(1)
        assertThat(vm.calculatedTotalRuns).isEqualTo(1)
    }

    @Test
    fun `undoing the ball that ended an over takes it out of the trail too`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, engine, _) = freshMatch()
        repeat(6) { engine.onRunScored(1) }
        assertThat(vm.allDeliveries).hasSize(6)

        engine.undoLastDelivery()

        // The old guard compared the trail's over against a `currentOver` that had already
        // advanced, so the score rolled back while the sixth ball stayed on the Overs tab.
        assertThat(vm.allDeliveries).hasSize(5)
        assertThat(vm.ballsInOver).isEqualTo(5)
        assertThat(vm.currentOver).isEqualTo(0)
        assertThat(vm.calculatedTotalRuns).isEqualTo(5)
    }
}
