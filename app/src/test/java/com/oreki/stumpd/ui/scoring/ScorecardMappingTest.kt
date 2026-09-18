package com.oreki.stumpd.ui.scoring

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.Player
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.WicketType
import org.junit.Test

/**
 * Saved matches keep a bowler's workload as "overs" in cricket notation (1.3 = one over and three
 * balls). The scorecard used to add those figures up as if they were decimals — `oversBowled * 6`
 * — which inflated the innings length for any bowler mid-over, and divided runs by them to get an
 * economy that was wrong for the same reason.
 */
class ScorecardMappingTest {

    @Test
    fun `overs in cricket notation convert back to balls`() {
        assertThat(ballsFromOversNotation(0.0)).isEqualTo(0)
        assertThat(ballsFromOversNotation(1.0)).isEqualTo(6)
        assertThat(ballsFromOversNotation(1.3)).isEqualTo(9)
        assertThat(ballsFromOversNotation(2.5)).isEqualTo(17)
        assertThat(ballsFromOversNotation(5.0)).isEqualTo(30)
    }

    @Test
    fun `the floating point figures the app actually stores round-trip exactly`() {
        // Player.oversBowled is built with arithmetic that leaves 9 balls as 1.3000000000000003.
        for (balls in 0..120) {
            val stored = Player(ballsBowled = balls).oversBowled
            assertThat(ballsFromOversNotation(stored)).isEqualTo(balls)
        }
    }

    @Test
    fun `a nonsense ball figure is clamped rather than counted twice`() {
        // 1.7 can't happen from a real innings; treat the tail as at most five balls.
        assertThat(ballsFromOversNotation(1.7)).isEqualTo(11)
    }

    @Test
    fun `balls are shown the way a scoreboard shows them`() {
        assertThat(formatBallsAsOvers(0)).isEqualTo("0.0")
        assertThat(formatBallsAsOvers(5)).isEqualTo("0.5")
        assertThat(formatBallsAsOvers(6)).isEqualTo("1.0")
        assertThat(formatBallsAsOvers(19)).isEqualTo("3.1")
    }

    @Test
    fun `run rate is per six balls, not per stored over figure`() {
        assertThat(runRatePerOver(36, 18)).isWithin(0.001).of(12.0)
        assertThat(runRatePerOver(19, 19)).isWithin(0.001).of(6.0)
    }

    @Test
    fun `a scoreless innings has a zero run rate rather than dividing by zero`() {
        assertThat(runRatePerOver(0, 0)).isEqualTo(0.0)
    }

    @Test
    fun `a saved batting row maps to the row the scorecard renders`() {
        val stats = PlayerMatchStats(
            id = "p1",
            name = "Samhith",
            runs = 17,
            ballsFaced = 15,
            fours = 0,
            sixes = 2,
            isOut = false,
        )

        val player = stats.toScorecardPlayer()

        assertThat(player.name).isEqualTo("Samhith")
        assertThat(player.runs).isEqualTo(17)
        assertThat(player.ballsFaced).isEqualTo(15)
        assertThat(player.sixes).isEqualTo(2)
        assertThat(player.id.value).isEqualTo("p1")
        assertThat(player.getDismissalText()).isEqualTo("not out")
    }

    @Test
    fun `a dismissal stored as text maps back to its wicket type`() {
        val stats = PlayerMatchStats(
            id = "p2",
            name = "Wahid",
            runs = 1,
            ballsFaced = 3,
            isOut = true,
            dismissalType = "BOWLED",
            bowlerName = "Pavan",
        )

        val player = stats.toScorecardPlayer()

        assertThat(player.dismissalType).isEqualTo(WicketType.BOWLED)
        assertThat(player.getDismissalText()).isEqualTo("b Pavan")
    }

    @Test
    fun `an unrecognised dismissal type is dropped rather than crashing`() {
        val stats = PlayerMatchStats(id = "p3", name = "X", isOut = true, dismissalType = "MANKADED")

        val player = stats.toScorecardPlayer()

        assertThat(player.dismissalType).isNull()
        assertThat(player.getDismissalText()).isEqualTo("not out")
    }

    @Test
    fun `bowling figures survive the round trip and economy is per over`() {
        val stats = PlayerMatchStats(
            id = "p4",
            name = "Bharath",
            wickets = 2,
            runsConceded = 10,
            oversBowled = 1.3,
            maidenOvers = 0,
        )

        val player = stats.toScorecardPlayer()

        assertThat(player.ballsBowled).isEqualTo(9)
        assertThat(player.wickets).isEqualTo(2)
        // 10 runs off 9 balls is 6.67 an over. Dividing by the stored 1.3 gave 7.7.
        assertThat(player.economy).isWithin(0.01).of(6.67)
    }

    @Test
    fun `an innings total is the sum of the bowlers' balls, not of their over figures`() {
        val bowlers = listOf(
            PlayerMatchStats(id = "a", name = "A", oversBowled = 1.3),
            PlayerMatchStats(id = "b", name = "B", oversBowled = 2.5),
        ).map { it.toScorecardPlayer() }

        // 9 + 17 balls = 26 = 4.2 overs. Summing 1.3 + 2.5 as decimals would say 3.8.
        assertThat(bowlers.sumOf { it.ballsBowled }).isEqualTo(26)
        assertThat(formatBallsAsOvers(bowlers.sumOf { it.ballsBowled })).isEqualTo("4.2")
    }
}
