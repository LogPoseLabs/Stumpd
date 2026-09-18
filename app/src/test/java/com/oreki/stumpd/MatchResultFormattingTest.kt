package com.oreki.stumpd

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.PlayerMatchStats
import org.junit.Test

/**
 * "Won by N wickets" has to be measured against the side that actually played.
 *
 * Matches were saved with `maxPlayersPerTeam` floored at eleven whatever the turnout, so a
 * seven-a-side win with one wicket down was announced as "won by 9 wickets" — two more wickets
 * than the side had batters.
 */
class MatchResultFormattingTest {

    private fun stats(vararg names: String) =
        names.map { PlayerMatchStats(id = it, name = it) }

    private fun match(
        firstInningsRuns: Int = 18,
        firstInningsWickets: Int = 6,
        secondInningsRuns: Int = 19,
        secondInningsWickets: Int = 1,
        winnerTeam: String = "Wahid's Team",
        winningMargin: String = "9 wickets",
        secondInningsBatting: List<PlayerMatchStats> = stats("Wahid", "Samhith", "Gokul"),
        firstInningsBowling: List<PlayerMatchStats> = stats("Gokul", "Nithej", "Ajith", "Bharath"),
        allowSingleSideBatting: Boolean = true,
    ) = MatchHistory(
        team1Name = "Kushal's Team",
        team2Name = "Wahid's Team",
        firstInningsRuns = firstInningsRuns,
        firstInningsWickets = firstInningsWickets,
        secondInningsRuns = secondInningsRuns,
        secondInningsWickets = secondInningsWickets,
        winnerTeam = winnerTeam,
        winningMargin = winningMargin,
        secondInningsBatting = secondInningsBatting,
        firstInningsBowling = firstInningsBowling,
        matchSettings = MatchSettings(
            maxPlayersPerTeam = 11,
            allowSingleSideBatting = allowSingleSideBatting,
        ),
    )

    @Test
    fun `the squad includes players who bowled but never batted`() {
        // Three batted; Nithej, Ajith and Bharath only bowled. Gokul did both.
        assertThat(squadSizeForInnings(match(), innings = 2)).isEqualTo(6)
    }

    @Test
    fun `single-side batting means every player can be dismissed`() {
        assertThat(maxWicketsForSquad(squadSize = 7, allowSingleSideBatting = true)).isEqualTo(7)
    }

    @Test
    fun `without single-side batting the last batter cannot be out`() {
        assertThat(maxWicketsForSquad(squadSize = 7, allowSingleSideBatting = false)).isEqualTo(6)
    }

    @Test
    fun `an empty squad has no wickets to lose`() {
        assertThat(maxWicketsForSquad(squadSize = 0, allowSingleSideBatting = true)).isEqualTo(0)
        assertThat(maxWicketsForSquad(squadSize = 0, allowSingleSideBatting = false)).isEqualTo(0)
    }

    @Test
    fun `the chase margin counts the wickets the side really had left`() {
        // Six players, single-side batting, one out: five in hand — not the stored nine.
        assertThat(wicketsInHandForChase(match())).isEqualTo(5)
        assertThat(matchResultLine(match())).isEqualTo("Wahid's Team won by 5 wickets")
    }

    @Test
    fun `one wicket in hand is singular`() {
        val m = match(secondInningsWickets = 5)

        assertThat(matchResultLine(m)).isEqualTo("Wahid's Team won by 1 wicket")
    }

    @Test
    fun `a win by runs is reported in runs`() {
        val m = match(
            firstInningsRuns = 40,
            secondInningsRuns = 19,
            winnerTeam = "Kushal's Team",
            winningMargin = "21 runs",
        )

        assertThat(matchResultLine(m)).isEqualTo("Kushal's Team won by 21 runs")
    }

    @Test
    fun `a tie says so`() {
        val m = match(
            firstInningsRuns = 21,
            secondInningsRuns = 21,
            winnerTeam = "TIE",
            winningMargin = "Scores level",
        )

        assertThat(matchResultLine(m)).isEqualTo("Match tied • Scores level")
    }

    @Test
    fun `a match with no squad rows falls back to the stored margin`() {
        val m = match(secondInningsBatting = emptyList(), firstInningsBowling = emptyList())

        assertThat(wicketsInHandForChase(m)).isNull()
        assertThat(matchResultLine(m)).isEqualTo("Wahid's Team won by 9 wickets")
    }

    @Test
    fun `all out is judged against the squad, not against ten wickets`() {
        // Six players in the chasing side, all six out.
        val m = match(secondInningsWickets = 6)

        assertThat(wasAllOut(m, innings = 2)).isTrue()
        // One short of the squad is not all out.
        assertThat(wasAllOut(match(secondInningsWickets = 5), innings = 2)).isFalse()
    }

    @Test
    fun `a supplied squad size wins over the one derived from the rows`() {
        // The history list has no per-player stats loaded, so it passes the size in from a
        // grouped query; without it the margin would fall back to the bogus stored string.
        val noRows = match(secondInningsBatting = emptyList(), firstInningsBowling = emptyList())

        assertThat(wicketsInHandForChase(noRows, chasingSquadSize = 8)).isEqualTo(7)
        assertThat(matchResultLine(noRows, chasingSquadSize = 8))
            .isEqualTo("Wahid's Team won by 7 wickets")
    }

    @Test
    fun `a zero squad size is ignored rather than trusted`() {
        // An absent row in the grouped query must not read as "nobody played".
        assertThat(wicketsInHandForChase(match(), chasingSquadSize = 0)).isEqualTo(5)
    }

    @Test
    fun `wickets in hand never goes negative`() {
        // Defensive: a saved match whose wicket count exceeds the squad it lists.
        val m = match(secondInningsWickets = 99)

        assertThat(wicketsInHandForChase(m)).isEqualTo(0)
    }

    // ── Deciding the result, rather than re-reading a stored string ─────────────────────
    //
    // `resolveMatchResult` was lifted out of the match-completion dialog so a correction that
    // changes a score can work out who won. Its output is stored verbatim on the match row and
    // parsed by Records and the History strip, so the exact strings are the contract.

    @Test
    fun `a defended total is a win by runs`() {
        val result = resolveMatchResult(
            team1Name = "Strikers",
            team2Name = "Chasers",
            firstInningsRuns = 52,
            secondInningsRuns = 40,
            secondInningsWickets = 4,
            chasingSquadSize = 5,
            allowSingleSideBatting = true,
        )

        assertThat(result.winnerTeam).isEqualTo("Strikers")
        assertThat(result.winningMargin).isEqualTo("12 runs")
    }

    @Test
    fun `a successful chase is a win by the wickets still standing`() {
        val result = resolveMatchResult(
            team1Name = "Strikers",
            team2Name = "Chasers",
            firstInningsRuns = 40,
            secondInningsRuns = 41,
            secondInningsWickets = 2,
            chasingSquadSize = 5,
            allowSingleSideBatting = true,
        )

        // Five players, single-side batting, two out: three wickets in hand — not eight.
        assertThat(result.winnerTeam).isEqualTo("Chasers")
        assertThat(result.winningMargin).isEqualTo("3 wickets")
    }

    @Test
    fun `level scores are a tie, stored the way the rest of the app reads it`() {
        val result = resolveMatchResult(
            team1Name = "Strikers",
            team2Name = "Chasers",
            firstInningsRuns = 40,
            secondInningsRuns = 40,
            secondInningsWickets = 3,
            chasingSquadSize = 5,
            allowSingleSideBatting = true,
        )

        assertThat(result.winnerTeam).isEqualTo("TIE")
        assertThat(result.winningMargin).isEqualTo("Scores level")
    }

    @Test
    fun `without single-side batting the last batter cannot be dismissed`() {
        val result = resolveMatchResult(
            team1Name = "Strikers",
            team2Name = "Chasers",
            firstInningsRuns = 40,
            secondInningsRuns = 41,
            secondInningsWickets = 0,
            chasingSquadSize = 5,
            allowSingleSideBatting = false,
        )

        assertThat(result.winningMargin).isEqualTo("4 wickets")
    }

    @Test
    fun `resolving from a saved match takes the squad from its own rows`() {
        // Three batted, four bowled in the other innings, Gokul did both: a squad of six.
        val resolved = resolveMatchResult(
            match(firstInningsRuns = 18, secondInningsRuns = 19, secondInningsWickets = 1),
            MatchSettings(maxPlayersPerTeam = 11, allowSingleSideBatting = true),
        )

        assertThat(resolved.winnerTeam).isEqualTo("Wahid's Team")
        assertThat(resolved.winningMargin).isEqualTo("5 wickets")
    }

    @Test
    fun `an empty squad falls back to the configured team size rather than reporting zero`() {
        val resolved = resolveMatchResult(
            match(
                firstInningsRuns = 18,
                secondInningsRuns = 19,
                secondInningsWickets = 1,
                secondInningsBatting = emptyList(),
                firstInningsBowling = emptyList(),
            ),
            MatchSettings(maxPlayersPerTeam = 8, allowSingleSideBatting = true),
        )

        assertThat(resolved.winningMargin).isEqualTo("7 wickets")
    }

}
