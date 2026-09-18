package com.oreki.stumpd.domain.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BowlingCapacityTest {

    private fun problem(
        totalOvers: Int,
        maxOversPerBowler: Int,
        teamSize: Int,
        jokerEnabled: Boolean = false,
        jokerCanBowl: Boolean = false,
        jokerMaxOvers: Int = 1,
    ) = MatchSettings(
        totalOvers = totalOvers,
        maxOversPerBowler = maxOversPerBowler,
        jokerCanBatAndBowl = jokerEnabled,
        jokerCanBowl = jokerCanBowl,
        jokerMaxOvers = jokerMaxOvers,
    ).bowlingCapacityProblem(
        team1Name = "Team A",
        team1Size = teamSize,
        team2Name = "Team B",
        team2Size = teamSize,
    )

    @Test
    fun `enough capacity is accepted`() {
        // 5 players x 2 overs = 10 >= 5
        assertNull(problem(totalOvers = 5, maxOversPerBowler = 2, teamSize = 5))
    }

    @Test
    fun `exactly enough capacity is accepted`() {
        // 5 players x 1 over = 5 overs, exactly the innings length
        assertNull(problem(totalOvers = 5, maxOversPerBowler = 1, teamSize = 5))
    }

    @Test
    fun `too little capacity is refused`() {
        // This is the case that silently broke the cap: 4 players x 1 over cannot bowl 5 overs,
        // so the picker would run dry and let someone bowl a second over.
        val message = problem(totalOvers = 5, maxOversPerBowler = 1, teamSize = 4)

        assertNotNull(message)
        assertTrue(message!!.contains("4 of 5 overs"))
    }

    @Test
    fun `the refusal says how to fix it`() {
        val message = problem(totalOvers = 10, maxOversPerBowler = 1, teamSize = 4)!!

        // ceil(10 / 4) = 3 overs each
        assertTrue(message, message.contains("Raise max overs per bowler to 3"))
        assertTrue(message, message.contains("add players"))
        assertTrue(message, message.contains("reduce total overs"))
    }

    @Test
    fun `a bowling joker adds capacity`() {
        // 4 x 1 = 4, one short of 5 - the joker's over closes the gap.
        assertNotNull(problem(totalOvers = 5, maxOversPerBowler = 1, teamSize = 4))
        assertNull(
            problem(
                totalOvers = 5,
                maxOversPerBowler = 1,
                teamSize = 4,
                jokerEnabled = true,
                jokerCanBowl = true,
            )
        )
    }

    @Test
    fun `a joker who cannot bowl adds no capacity`() {
        assertNotNull(
            problem(
                totalOvers = 5,
                maxOversPerBowler = 1,
                teamSize = 4,
                jokerEnabled = true,
                jokerCanBowl = false,
            )
        )
    }

    @Test
    fun `a single bowler cannot cover a multi over innings`() {
        // No bowler may bowl consecutive overs, so one bowler can never finish 2+ overs
        // however high the cap is.
        val message = problem(totalOvers = 2, maxOversPerBowler = 10, teamSize = 1)

        assertNotNull(message)
        assertTrue(message!!, message.contains("at least 2 bowlers"))
    }

    @Test
    fun `a single over innings is fine with one bowler`() {
        assertNull(problem(totalOvers = 1, maxOversPerBowler = 1, teamSize = 1))
    }

    @Test
    fun `both sides are checked`() {
        // Team B is short even though Team A is fine.
        val message = MatchSettings(totalOvers = 6, maxOversPerBowler = 1)
            .bowlingCapacityProblem(
                team1Name = "Team A",
                team1Size = 6,
                team2Name = "Team B",
                team2Size = 3,
            )

        assertNotNull(message)
        assertTrue(message!!, message.startsWith("Team B"))
    }

    @Test
    fun `empty teams are not reported as a capacity problem`() {
        // Team size zero means the user has not picked players yet; other validation covers it.
        val message = MatchSettings(totalOvers = 5, maxOversPerBowler = 1)
            .bowlingCapacityProblem(
                team1Name = "Team A",
                team1Size = 0,
                team2Name = "Team B",
                team2Size = 0,
            )

        // Only the "needs 2 bowlers" rule should speak up here, not a "0 of 5 overs" message.
        assertTrue(message == null || message.contains("at least 2 bowlers"))
    }

    @Test
    fun `zero overs is not validated`() {
        assertNull(problem(totalOvers = 0, maxOversPerBowler = 1, teamSize = 1))
    }
}
