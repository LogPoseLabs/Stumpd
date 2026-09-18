package com.oreki.stumpd.domain.match

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.SuperOverInnings
import org.junit.Test

/**
 * A correction must not quietly undo a super over.
 *
 * `MatchRecompute` rebuilds a whole match — result, impacts, awards — from its rows, and the two
 * innings totals of a match that went to an eliminator are *equal by definition*. So a recompute
 * that forgot the super over would hand back `"TIE"` and erase the result, weeks after the fact,
 * with nobody watching. That is the single most dangerous interaction in the feature, so it gets
 * its own test rather than being left to the threading being right.
 */
class MatchRecomputeSuperOverTest {

    private val settings = MatchSettings(totalOvers = 1, maxPlayersPerTeam = 3, allowSingleSideBatting = true)

    private fun match(
        firstRuns: Int = 4,
        secondRuns: Int = 4,
        superOverWinner: String? = "Chasers",
    ) = MatchHistory(
        id = "m-so",
        team1Name = "Strikers",
        team2Name = "Chasers",
        firstInningsRuns = firstRuns,
        firstInningsWickets = 0,
        secondInningsRuns = secondRuns,
        secondInningsWickets = 0,
        winnerTeam = superOverWinner ?: "TIE",
        winningMargin = if (superOverWinner != null) "Super Over" else "Scores level",
        firstInningsBatting = listOf(
            PlayerMatchStats(id = "kushal", name = "Kushal", team = "Strikers", role = "BAT", runs = firstRuns, ballsFaced = 6),
        ),
        firstInningsBowling = listOf(
            PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BOWL", runsConceded = firstRuns, oversBowled = 1.0),
        ),
        secondInningsBatting = listOf(
            PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BAT", runs = secondRuns, ballsFaced = 6),
        ),
        secondInningsBowling = listOf(
            PlayerMatchStats(id = "kushal", name = "Kushal", team = "Strikers", role = "BOWL", runsConceded = secondRuns, oversBowled = 1.0),
        ),
        firstInningsPartnerships = listOf(Partnership("Kushal", "Gokul", runs = firstRuns, balls = 6)),
        secondInningsPartnerships = listOf(Partnership("Muttu", "Madhu", runs = secondRuns, balls = 6)),
        allDeliveries = trail(firstRuns, secondRuns),
        matchSettings = settings,
        superOverWinner = superOverWinner,
        superOvers = if (superOverWinner == null) {
            emptyList()
        } else {
            listOf(
                SuperOverInnings(3, "Chasers", "Strikers", runs = 8, wickets = 0, balls = 6),
                SuperOverInnings(4, "Strikers", "Chasers", runs = 5, wickets = 0, balls = 6),
            )
        },
    )

    /** One over each for the match, then one each for the eliminator as innings 3 and 4. */
    private fun trail(firstRuns: Int, secondRuns: Int): List<DeliveryUI> = buildList {
        repeat(6) { ball ->
            add(ball(1, ball + 1, if (ball == 0) firstRuns else 0, "Kushal", "Muttu"))
        }
        repeat(6) { ball ->
            add(ball(2, ball + 1, if (ball == 0) secondRuns else 0, "Muttu", "Kushal"))
        }
        // The eliminator: eight off the first over, five off the second.
        repeat(6) { ball -> add(ball(3, ball + 1, if (ball == 0) 8 else 0, "Muttu", "Kushal")) }
        repeat(6) { ball -> add(ball(4, ball + 1, if (ball == 0) 5 else 0, "Kushal", "Muttu")) }
    }

    private fun ball(inning: Int, ballInOver: Int, runs: Int, striker: String, bowler: String) =
        DeliveryUI(
            inning = inning, over = 1, ballInOver = ballInOver,
            outcome = runs.toString(), runs = runs,
            strikerName = striker, nonStrikerName = "Gokul", bowlerName = bowler,
        )

    @Test
    fun `recomputing a super-over match keeps its winner`() {
        val after = MatchRecompute.recompute(match(), settings)

        assertThat(after.winnerTeam).isEqualTo("Chasers")
        assertThat(after.winningMargin).isEqualTo("Super Over")
        assertThat(after.superOverWinner).isEqualTo("Chasers")
        assertThat(after.superOvers.map { it.inning }).containsExactly(3, 4).inOrder()
    }

    @Test
    fun `recomputing twice is stable`() {
        val once = MatchRecompute.recompute(match(), settings)
        val twice = MatchRecompute.recompute(once, settings)

        assertThat(twice.winnerTeam).isEqualTo("Chasers")
        assertThat(twice.superOverWinner).isEqualTo("Chasers")
        assertThat(twice.superOvers).hasSize(2)
    }

    @Test
    fun `a tie with no super over still recomputes to a tie`() {
        val after = MatchRecompute.recompute(match(superOverWinner = null), settings)

        assertThat(after.winnerTeam).isEqualTo("TIE")
        assertThat(after.superOverWinner).isNull()
    }

    @Test
    fun `a correction that unlevels the scores retires the super over`() {
        // Someone fixes a mis-scored run: the match was never tied, so the eliminator is moot and
        // the ordinary margin must take over.
        val after = MatchRecompute.recompute(match(firstRuns = 6, secondRuns = 4), settings)

        assertThat(after.winnerTeam).isEqualTo("Strikers")
        assertThat(after.winningMargin).isEqualTo("2 runs")
        // The record of what was played is kept; it just no longer decides anything.
        assertThat(after.superOvers).hasSize(2)
    }

    @Test
    fun `a scoreless super over does not earn its bowler a maiden`() {
        val scoreless = match().copy(
            allDeliveries = buildList {
                repeat(6) { add(ball(1, it + 1, if (it == 0) 4 else 0, "Kushal", "Muttu")) }
                repeat(6) { add(ball(2, it + 1, if (it == 0) 4 else 0, "Muttu", "Kushal")) }
                // Six dots in the eliminator — a maiden, if anyone were counting it.
                repeat(6) { add(ball(3, it + 1, 0, "Muttu", "Kushal")) }
            },
        )

        val after = MatchRecompute.recompute(scoreless, settings)

        assertThat(after.firstInningsBowling.single { it.name == "Muttu" }.maidenOvers).isEqualTo(0)
        assertThat(after.secondInningsBowling.single { it.name == "Kushal" }.maidenOvers).isEqualTo(0)
    }

    @Test
    fun `innings totals are still repaired from the trail with super-over balls present`() {
        // The trail-based repair compares an innings' legal balls against the balls credited to its
        // bowlers. Super-over balls are in the trail but never credited, so the two still agree —
        // this pins that, because if it broke the repair would silently stop working.
        val after = MatchRecompute.recompute(match(), settings)

        assertThat(after.firstInningsRuns).isEqualTo(4)
        assertThat(after.secondInningsRuns).isEqualTo(4)
    }
}
