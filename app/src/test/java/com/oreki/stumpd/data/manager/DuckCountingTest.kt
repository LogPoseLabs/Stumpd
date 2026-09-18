package com.oreki.stumpd.data.manager

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the duck definitions, which are easy to conflate:
 *  - duck         out for 0, any number of balls
 *  - golden duck  out for 0 on the first ball faced      (ballsFaced == 1)
 *  - diamond duck out for 0 without facing a ball at all (ballsFaced == 0)
 */
class DuckCountingTest {

    private fun perf(runs: Int, ballsFaced: Int, isOut: Boolean) = MatchPerformance(
        matchId = "m$runs-$ballsFaced-$isOut",
        matchDate = 0L,
        opposingTeam = "B",
        myTeam = "A",
        runs = runs,
        ballsFaced = ballsFaced,
        isOut = isOut,
    )

    private fun statsOf(vararg performances: MatchPerformance) = PlayerDetailedStats(
        playerId = "p1",
        name = "Player",
        totalMatches = performances.size,
        matchPerformances = performances.toMutableList(),
    )

    @Test
    fun `out for zero on the first ball is a golden duck`() {
        val stats = statsOf(perf(runs = 0, ballsFaced = 1, isOut = true))

        assertThat(stats.goldenDucks).isEqualTo(1)
        assertThat(stats.diamondDucks).isEqualTo(0)
        assertThat(stats.ducks).isEqualTo(1)
    }

    @Test
    fun `out for zero without facing a ball is a diamond duck, not golden`() {
        // Run out at the non-striker's end. The old implementation counted this as golden.
        val stats = statsOf(perf(runs = 0, ballsFaced = 0, isOut = true))

        assertThat(stats.goldenDucks).isEqualTo(0)
        assertThat(stats.diamondDucks).isEqualTo(1)
        assertThat(stats.ducks).isEqualTo(1)
    }

    @Test
    fun `out for zero after surviving a few balls is an ordinary duck`() {
        val stats = statsOf(perf(runs = 0, ballsFaced = 7, isOut = true))

        assertThat(stats.goldenDucks).isEqualTo(0)
        assertThat(stats.diamondDucks).isEqualTo(0)
        assertThat(stats.ducks).isEqualTo(1)
    }

    @Test
    fun `surviving a first-ball dot is not a duck`() {
        // Faced one ball, scored nothing, but was not dismissed.
        val stats = statsOf(perf(runs = 0, ballsFaced = 1, isOut = false))

        assertThat(stats.ducks).isEqualTo(0)
        assertThat(stats.goldenDucks).isEqualTo(0)
    }

    @Test
    fun `scoring runs off the first ball is never a duck`() {
        val stats = statsOf(perf(runs = 4, ballsFaced = 1, isOut = true))

        assertThat(stats.ducks).isEqualTo(0)
        assertThat(stats.goldenDucks).isEqualTo(0)
    }

    @Test
    fun `golden ducks are a subset of ducks across a mixed career`() {
        val stats = statsOf(
            perf(runs = 0, ballsFaced = 1, isOut = true),  // golden
            perf(runs = 0, ballsFaced = 1, isOut = true),  // golden
            perf(runs = 0, ballsFaced = 0, isOut = true),  // diamond
            perf(runs = 0, ballsFaced = 5, isOut = true),  // ordinary duck
            perf(runs = 0, ballsFaced = 3, isOut = false), // not out, not a duck
            perf(runs = 30, ballsFaced = 20, isOut = true),
        )

        assertThat(stats.ducks).isEqualTo(4)
        assertThat(stats.goldenDucks).isEqualTo(2)
        assertThat(stats.diamondDucks).isEqualTo(1)
        assertThat(stats.goldenDucks).isAtMost(stats.ducks)
    }
}
