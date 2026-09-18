package com.oreki.stumpd

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import org.junit.Test

/**
 * A super-over result has to be an *input* to the result derivation, not a decoration on top of it.
 *
 * `resolveMatchResult` is pure and is called twice: once when the match finishes, and again every
 * time a correction recomputes the match. Since the two innings totals are level by definition when
 * a super over happens, a derivation that only looked at those totals would hand back `"TIE"` on
 * that second call and quietly erase the eliminator. These tests pin both halves: that passing the
 * winner in produces the right result, and that leaving it out changes nothing for every match that
 * never went to one.
 */
class MatchResultSuperOverTest {

    private val settings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 5)

    // ── The existing behaviour, unchanged ───────────────────────────────────────────────

    @Test
    fun `without a super over the three ordinary outcomes are exactly as before`() {
        assertThat(resolve(first = 20, second = 20))
            .isEqualTo(MatchResult("TIE", "Scores level"))
        // Five a side with single-side batting off is a four-wicket cap, so two down leaves two.
        assertThat(resolve(first = 20, second = 25, secondWickets = 2))
            .isEqualTo(MatchResult("Chasers", "2 wickets"))
        assertThat(resolve(first = 30, second = 22))
            .isEqualTo(MatchResult("Strikers", "8 runs"))
    }

    // ── What a super over adds ──────────────────────────────────────────────────────────

    @Test
    fun `a super-over winner becomes the match winner`() {
        assertThat(resolve(first = 20, second = 20, superOverWinner = "Chasers"))
            .isEqualTo(MatchResult("Chasers", "Super Over"))
    }

    @Test
    fun `a super over the scorer left level is still a tie, and says so`() {
        assertThat(resolve(first = 20, second = 20, superOverWinner = "TIE"))
            .isEqualTo(MatchResult("TIE", "Tied after Super Over"))
    }

    @Test
    fun `a correction that unlevels the scores makes the super over moot`() {
        // The case that matters most: someone fixes a mis-scored run in the first innings, so the
        // match was never actually tied. The ordinary margin must win, not the eliminator.
        assertThat(resolve(first = 19, second = 20, secondWickets = 1, superOverWinner = "Chasers"))
            .isEqualTo(MatchResult("Chasers", "3 wickets"))
        assertThat(resolve(first = 24, second = 20, superOverWinner = "Chasers"))
            .isEqualTo(MatchResult("Strikers", "4 runs"))
    }

    @Test
    fun `the saved-match overload carries the stored winner through`() {
        // This overload is what the correction path calls, so it is the one that must not forget.
        val match = match(first = 20, second = 20, superOverWinner = "Chasers")

        assertThat(resolveMatchResult(match, settings))
            .isEqualTo(MatchResult("Chasers", "Super Over"))
    }

    @Test
    fun `a saved tie with no super over still resolves to a tie`() {
        assertThat(resolveMatchResult(match(first = 20, second = 20), settings))
            .isEqualTo(MatchResult("TIE", "Scores level"))
    }

    // ── How it reads on screen ──────────────────────────────────────────────────────────

    @Test
    fun `the result line says won the Super Over, not won by Super Over`() {
        val won = match(first = 20, second = 20, superOverWinner = "Chasers")
            .copy(winnerTeam = "Chasers", winningMargin = "Super Over")

        assertThat(matchResultLine(won)).isEqualTo("Chasers won the Super Over")
    }

    @Test
    fun `a tied super over reads as tied after the Super Over`() {
        val tied = match(first = 20, second = 20, superOverWinner = "TIE")
            .copy(winnerTeam = "TIE", winningMargin = "Tied after Super Over")

        assertThat(matchResultLine(tied)).isEqualTo("Match tied after the Super Over")
    }

    @Test
    fun `an ordinary tie is untouched by the new branch`() {
        val tie = match(first = 20, second = 20)
            .copy(winnerTeam = "TIE", winningMargin = "Scores level")

        assertThat(matchResultLine(tie)).isEqualTo("Match tied • Scores level")
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────

    private fun resolve(
        first: Int,
        second: Int,
        secondWickets: Int = 0,
        superOverWinner: String? = null,
    ) = resolveMatchResult(
        team1Name = "Strikers",
        team2Name = "Chasers",
        firstInningsRuns = first,
        secondInningsRuns = second,
        secondInningsWickets = secondWickets,
        chasingSquadSize = 5,
        allowSingleSideBatting = false,
        superOverWinner = superOverWinner,
    )

    private fun match(first: Int, second: Int, superOverWinner: String? = null) = MatchHistory(
        team1Name = "Strikers",
        team2Name = "Chasers",
        firstInningsRuns = first,
        firstInningsWickets = 3,
        secondInningsRuns = second,
        secondInningsWickets = 0,
        winnerTeam = "TIE",
        winningMargin = "Scores level",
        matchSettings = settings,
        superOverWinner = superOverWinner,
    )
}
