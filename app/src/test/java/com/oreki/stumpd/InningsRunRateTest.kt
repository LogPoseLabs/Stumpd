package com.oreki.stumpd

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import org.junit.Test

/**
 * Run rate must be measured against the overs actually faced.
 *
 * It previously divided by the match's *configured* overs, so a side bowled out in 3 of 5 overs
 * was divided by 5 and looked much slower than it really was.
 */
class InningsRunRateTest {

    private fun delivery(innings: Int, outcome: String) = DeliveryUI(
        inning = innings,
        over = 1,
        ballInOver = 1,
        outcome = outcome,
        highlight = false,
        strikerName = "A",
        nonStrikerName = "B",
        bowlerName = "C",
        runs = 0,
    )

    private fun match(
        deliveries: List<DeliveryUI>,
        configuredOvers: Int = 5,
    ) = MatchHistory(
        team1Name = "A",
        team2Name = "B",
        firstInningsRuns = 0,
        firstInningsWickets = 0,
        secondInningsRuns = 0,
        secondInningsWickets = 0,
        winnerTeam = "A",
        winningMargin = "",
        matchSettings = MatchSettings(totalOvers = configuredOvers),
        allDeliveries = deliveries,
    )

    @Test
    fun `uses the balls actually bowled, not the configured overs`() {
        // Bowled out after 3 overs (18 legal balls) for 36 in a 5-over match.
        val m = match(List(18) { delivery(1, "1") })

        // 36 off 18 balls = 12.00, not 36/5 = 7.20
        assertThat(inningsRunRate(m, innings = 1, runs = 36)).isWithin(0.01).of(12.0)
    }

    @Test
    fun `wides and no balls are not legal deliveries`() {
        val m = match(
            List(6) { delivery(1, "1") } + listOf(
                delivery(1, "Wd"),
                delivery(1, "Wd+2"),
                delivery(1, "Nb+1"),
            )
        )

        assertThat(legalBallsInInnings(m, innings = 1)).isEqualTo(6)
        // 12 off one legal over
        assertThat(inningsRunRate(m, innings = 1, runs = 12)).isWithin(0.01).of(12.0)
    }

    @Test
    fun `wickets byes and leg byes are legal deliveries`() {
        val m = match(
            listOf(
                delivery(1, "W"),
                delivery(1, "B+1"),
                delivery(1, "Lb+2"),
                delivery(1, "0"),
            )
        )

        assertThat(legalBallsInInnings(m, innings = 1)).isEqualTo(4)
    }

    @Test
    fun `each innings counts only its own deliveries`() {
        val m = match(List(6) { delivery(1, "1") } + List(12) { delivery(2, "1") })

        assertThat(legalBallsInInnings(m, innings = 1)).isEqualTo(6)
        assertThat(legalBallsInInnings(m, innings = 2)).isEqualTo(12)
    }

    @Test
    fun `falls back to configured overs when there is no delivery log`() {
        // Older saved matches have no ball-by-ball data to measure against.
        val m = match(deliveries = emptyList(), configuredOvers = 5)

        assertThat(inningsRunRate(m, innings = 1, runs = 40)).isWithin(0.01).of(8.0)
    }

    @Test
    fun `a scoreless innings has a zero run rate rather than dividing by zero`() {
        assertThat(inningsRunRate(match(emptyList()), innings = 1, runs = 0)).isEqualTo(0.0)
    }
}
