package com.oreki.stumpd.domain.match

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.DeliveryUI
import org.junit.Test

/**
 * The arithmetic a super over rests on.
 *
 * The first block matters most: [inningsComplete] is about to replace the expression the scoring
 * screen has used since the app was written, so the ordinary two-innings cases are pinned here
 * *before* anything starts calling it. If those stay green through the engine change, no existing
 * match behaves differently.
 */
class SuperOverTest {

    // ── The two-innings behaviour, pinned before it is generalised ──────────────────────

    @Test
    fun `an innings ends when its overs run out, exactly as before`() {
        assertThat(complete(currentOver = 5, oversAllotted = 5)).isTrue()
        assertThat(complete(currentOver = 4, oversAllotted = 5)).isFalse()
    }

    @Test
    fun `a chase ends the moment the target is passed, not when it is levelled`() {
        // Levelling the scores is a tie, and the innings plays on — this is what makes a super
        // over reachable at all.
        assertThat(complete(runs = 20, runsToChase = 20)).isFalse()
        assertThat(complete(runs = 21, runsToChase = 20)).isTrue()
    }

    @Test
    fun `the side setting a target is never ended by the score`() {
        // runsToChase is null in the first innings of a pair, which switches the clause off.
        assertThat(complete(runs = 999, runsToChase = null)).isFalse()
    }

    @Test
    fun `running out of batters ends an innings, and single-side batting changes where that is`() {
        assertThat(complete(availableBatsmen = 1, allowSingleSideBatting = true)).isFalse()
        assertThat(complete(availableBatsmen = 0, allowSingleSideBatting = true)).isTrue()

        assertThat(complete(availableBatsmen = 2, allowSingleSideBatting = false)).isFalse()
        assertThat(complete(availableBatsmen = 1, allowSingleSideBatting = false)).isTrue()
    }

    @Test
    fun `no wicket cap applies outside a super over`() {
        assertThat(complete(totalWickets = 9, wicketCap = null)).isFalse()
    }

    // ── What the super over adds ────────────────────────────────────────────────────────

    @Test
    fun `a super-over innings ends at two wickets`() {
        assertThat(complete(totalWickets = 1, wicketCap = 2)).isFalse()
        assertThat(complete(totalWickets = 2, wicketCap = 2)).isTrue()
    }

    @Test
    fun `a super over is one over long`() {
        assertThat(complete(currentOver = 0, oversAllotted = SUPER_OVER_OVERS)).isFalse()
        assertThat(complete(currentOver = 1, oversAllotted = SUPER_OVER_OVERS)).isTrue()
    }

    // ── Which innings is which ──────────────────────────────────────────────────────────

    @Test
    fun `innings 1 and 2 are the match, 3 onwards is the eliminator`() {
        assertThat(isSuperOverInnings(1)).isFalse()
        assertThat(isSuperOverInnings(2)).isFalse()
        assertThat(isSuperOverInnings(3)).isTrue()
        assertThat(isSuperOverInnings(6)).isTrue()
    }

    @Test
    fun `sides change ends entering an even innings only`() {
        // 2 -> the chase swaps. 3 -> the side that batted second bats the super over first, so no
        // swap. 4 -> swap back. 5 -> no swap again.
        assertThat(swapSidesEnteringInnings(2)).isTrue()
        assertThat(swapSidesEnteringInnings(3)).isFalse()
        assertThat(swapSidesEnteringInnings(4)).isTrue()
        assertThat(swapSidesEnteringInnings(5)).isFalse()
    }

    @Test
    fun `who is batting is not the parity of the innings number`() {
        // The naive `innings % 2 == 1` gets innings 1 and 2 backwards, which is the whole reason
        // this is a function and not an expression at the call site.
        assertThat(battingSideIsFirstInningsSide(1)).isTrue()
        assertThat(battingSideIsFirstInningsSide(2)).isFalse()
        assertThat(battingSideIsFirstInningsSide(3)).isFalse()
        assertThat(battingSideIsFirstInningsSide(4)).isTrue()
        assertThat(battingSideIsFirstInningsSide(5)).isFalse()
        assertThat(battingSideIsFirstInningsSide(6)).isTrue()
    }

    @Test
    fun `innings labels read the way a scorer would say them`() {
        assertThat(inningsLabel(1)).isEqualTo("1st Innings")
        assertThat(inningsLabel(2)).isEqualTo("2nd Innings")
        assertThat(inningsLabel(3)).isEqualTo("Super Over")
        assertThat(inningsLabel(4)).isEqualTo("Super Over")
        assertThat(inningsLabel(5)).isEqualTo("Super Over 2")
        assertThat(inningsLabel(6)).isEqualTo("Super Over 2")
        assertThat(inningsLabel(7)).isEqualTo("Super Over 3")
    }

    // ── Keeping super-over balls out of the figures ─────────────────────────────────────

    @Test
    fun `the trail splits into the match and the eliminator`() {
        val trail = listOf(ball(1), ball(2), ball(3), ball(4), ball(5))

        assertThat(trail.mainMatchDeliveries().map { it.inning }).containsExactly(1, 2).inOrder()
        assertThat(trail.superOverDeliveries().map { it.inning }).containsExactly(3, 4, 5).inOrder()
        assertThat(ball(2).isSuperOver()).isFalse()
        assertThat(ball(3).isSuperOver()).isTrue()
    }

    @Test
    fun `a match that never went to a super over is unchanged by the filter`() {
        val trail = listOf(ball(1), ball(1), ball(2), ball(2))

        assertThat(trail.mainMatchDeliveries()).isEqualTo(trail)
        assertThat(trail.superOverDeliveries()).isEmpty()
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────

    /** Defaults chosen so each test can vary one thing and leave the innings otherwise alive. */
    private fun complete(
        currentOver: Int = 0,
        oversAllotted: Int = 5,
        totalWickets: Int = 0,
        wicketCap: Int? = null,
        runs: Int = 0,
        runsToChase: Int? = null,
        availableBatsmen: Int = 5,
        allowSingleSideBatting: Boolean = true,
    ) = inningsComplete(
        currentOver = currentOver,
        oversAllotted = oversAllotted,
        totalWickets = totalWickets,
        wicketCap = wicketCap,
        runs = runs,
        runsToChase = runsToChase,
        availableBatsmen = availableBatsmen,
        allowSingleSideBatting = allowSingleSideBatting,
    )

    private fun ball(inning: Int) = DeliveryUI(
        inning = inning, over = 1, ballInOver = 1, outcome = "1", runs = 1,
        strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu",
    )
}
