package com.oreki.stumpd.domain.tournament

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.MatchHistory
import org.junit.Test

class TournamentStatsTest {

    private fun match(potmId: String?, potmName: String?) = MatchHistory(
        id = "m-${potmId ?: "none"}-${potmName ?: ""}-${System.nanoTime()}",
        team1Name = "Warriors",
        team2Name = "Strikers",
        firstInningsRuns = 40,
        firstInningsWickets = 1,
        secondInningsRuns = 25,
        secondInningsWickets = 3,
        winnerTeam = "Warriors",
        winningMargin = "15 runs",
        playerOfTheMatchId = potmId,
        playerOfTheMatchName = potmName,
    )

    @Test
    fun `players are ranked by how often they won it`() {
        val tally = potmTally(
            listOf(
                match("p1", "Ann"),
                match("p1", "Ann"),
                match("p2", "Bo"),
            ),
        )

        assertThat(tally.map { it.playerId to it.count }).containsExactly("p1" to 2, "p2" to 1).inOrder()
        assertThat(tally.first().name).isEqualTo("Ann")
    }

    @Test
    fun `a match with no Player of the Match is skipped, not counted as Unknown`() {
        val tally = potmTally(listOf(match(null, null), match("p1", "Ann")))

        assertThat(tally).hasSize(1)
        assertThat(tally.single().playerId).isEqualTo("p1")
    }

    @Test
    fun `no matches means no tally`() {
        assertThat(potmTally(emptyList())).isEmpty()
    }
}
