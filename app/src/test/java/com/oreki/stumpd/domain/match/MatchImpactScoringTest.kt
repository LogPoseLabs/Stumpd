package com.oreki.stumpd.domain.match

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.PlayerMatchStats
import org.junit.Test

/**
 * A golden test for the impact scores and Player of the Match.
 *
 * The formula behind these numbers is moving out of `ScoringActivity` so that a match correction
 * can re-run it. Nobody can eyeball whether a 280-line move altered a weighting, so the numbers
 * are the contract: the values asserted here were captured from the implementation *before* the
 * move, and the move is correct exactly when they don't shift.
 */
class MatchImpactScoringTest {

    private val settings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 5)

    private fun bat(
        name: String,
        team: String,
        runs: Int,
        balls: Int,
        fours: Int = 0,
        sixes: Int = 0,
        isOut: Boolean = true,
        catches: Int = 0,
        runOuts: Int = 0,
        stumpings: Int = 0,
        isJoker: Boolean = false,
    ) = PlayerMatchStats(
        id = name.lowercase(), name = name, team = team, role = "BAT",
        runs = runs, ballsFaced = balls, fours = fours, sixes = sixes, isOut = isOut,
        catches = catches, runOuts = runOuts, stumpings = stumpings, isJoker = isJoker,
    )

    private fun bowl(
        name: String,
        team: String,
        wickets: Int,
        runsConceded: Int,
        oversBowled: Double,
        catches: Int = 0,
        isJoker: Boolean = false,
    ) = PlayerMatchStats(
        id = name.lowercase(), name = name, team = team, role = "BOWL",
        wickets = wickets, runsConceded = runsConceded, oversBowled = oversBowled,
        catches = catches, isJoker = isJoker,
    )

    /** Strikers bat first and win by 12; Chasers fall short. */
    private fun fixture(): MatchHistory = assembleMatch(
        team1Name = "Strikers",
        team2Name = "Chasers",
        jokerPlayerName = "Joker",
        team1CaptainName = "Kushal",
        team2CaptainName = "Ravi",
        firstInningsRuns = 52,
        firstInningsWickets = 3,
        secondInningsRuns = 40,
        secondInningsWickets = 4,
        winnerTeam = "Strikers",
        winningMargin = "12 runs",
        firstInningsBattingStats = listOf(
            bat("Kushal", "Strikers", runs = 27, balls = 18, fours = 2, sixes = 1, isOut = false),
            bat("Gokul", "Strikers", runs = 15, balls = 12, fours = 1),
            bat("Joker", "Strikers", runs = 8, balls = 4, sixes = 1, isJoker = true),
        ),
        firstInningsBowlingStats = listOf(
            bowl("Muttu", "Chasers", wickets = 2, runsConceded = 18, oversBowled = 2.0),
            bowl("Ravi", "Chasers", wickets = 1, runsConceded = 30, oversBowled = 3.0, catches = 1),
        ),
        secondInningsBattingStats = listOf(
            bat("Ravi", "Chasers", runs = 22, balls = 20, fours = 3),
            bat("Muttu", "Chasers", runs = 10, balls = 9, isOut = false),
        ),
        secondInningsBowlingStats = listOf(
            bowl("Gokul", "Strikers", wickets = 3, runsConceded = 12, oversBowled = 2.0, catches = 2),
            bowl("Kushal", "Strikers", wickets = 1, runsConceded = 24, oversBowled = 3.0),
        ),
        matchSettings = settings,
        groupId = "g1",
        groupName = "Test",
    )

    @Test
    fun `impact scores are unchanged by the move out of the scoring screen`() {
        val match = fixture()

        val byName = match.playerImpacts.associate { it.name to it.impact }
        assertThat(byName).containsExactly(
            "Gokul", 68.6,
            "Kushal", 59.8,
            "Ravi", 44.0,
            "Muttu", 26.0,
        )
    }

    @Test
    fun `impacts come back sorted best first, which is what makes the top one the award`() {
        val match = fixture()

        assertThat(match.playerImpacts.map { it.name })
            .containsExactly("Gokul", "Kushal", "Ravi", "Muttu")
            .inOrder()
    }

    @Test
    fun `player of the match is the top of the impact list`() {
        val match = fixture()

        assertThat(match.playerOfTheMatchName).isEqualTo("Gokul")
        assertThat(match.playerOfTheMatchTeam).isEqualTo("Strikers")
        assertThat(match.playerOfTheMatchImpact).isEqualTo(68.6)
    }

    @Test
    fun `the summary reads as a sentence about what the player did`() {
        val match = fixture()

        val summaries = match.playerImpacts.associate { it.name to it.summary }
        assertThat(summaries["Gokul"]).isEqualTo("15(12) and 3/12 and 2 ct")
        assertThat(summaries["Kushal"]).isEqualTo("27*(18) and 1/24")
        assertThat(summaries["Ravi"]).isEqualTo("22(20) and 1/30 and 1 ct")
        assertThat(summaries["Muttu"]).isEqualTo("10*(9) and 2/18")
    }

    @Test
    fun `the joker is left out of the stored impacts entirely, not merely scored zero`() {
        // The joker plays for both sides, so ranking them against the teams they helped and hurt
        // is meaningless — the scoring function returns 0.0 and the list then drops them.
        val match = fixture()

        assertThat(match.playerImpacts.map { it.name }).doesNotContain("Joker")
        assertThat(match.playerOfTheMatchName).isNotEqualTo("Joker")
    }
}
