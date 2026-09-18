package com.oreki.stumpd.data.mappers

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.match.MatchRecompute
import com.oreki.stumpd.ui.scoring.ballsFromOversNotation
import com.oreki.stumpd.ui.scoring.formatBallsAsOvers
import org.junit.Test

/**
 * An over is six balls, and "1.3 overs" means nine of them.
 *
 * Stored bowling workload is cricket notation, where the digit after the point counts balls out of
 * six — so it can be neither multiplied nor subtracted as a decimal. `overs * 6` turns 1.3 into
 * seven balls, and `1 - 0.1` reads as nine tenths of an over when it should be five balls. Both
 * mistakes were live: the first in career ball counts, the second on the scoring screen, where a
 * bowler one ball into a one-over quota was told he had "0.9 of 1 left".
 */
class OversNotationTest {

    @Test
    fun `the fractional digit counts balls, not tenths`() {
        assertThat(0.0.oversToBalls()).isEqualTo(0)
        assertThat(0.1.oversToBalls()).isEqualTo(1)
        assertThat(0.3.oversToBalls()).isEqualTo(3)
        assertThat(0.5.oversToBalls()).isEqualTo(5)
        assertThat(1.0.oversToBalls()).isEqualTo(6)
        // The one that used to come back as seven.
        assertThat(1.3.oversToBalls()).isEqualTo(9)
        assertThat(2.0.oversToBalls()).isEqualTo(12)
        assertThat(4.5.oversToBalls()).isEqualTo(29)
    }

    @Test
    fun `floating point noise from the live scorer survives the trip`() {
        // Player.oversBowled builds the figure arithmetically, so nine balls can arrive as
        // 1.3000000000000003 and three as 0.30000000000000004 — both seen in real saved matches.
        assertThat(1.3000000000000003.oversToBalls()).isEqualTo(9)
        assertThat(0.30000000000000004.oversToBalls()).isEqualTo(3)
    }

    @Test
    fun `balls and overs round-trip`() {
        (0..60).forEach { balls ->
            val overs = balls.ballsToOvers()
            assertThat(overs.oversToBalls()).isEqualTo(balls)
        }
    }

    @Test
    fun `the three converters in the codebase agree`() {
        // They exist separately for historical reasons; they must at least not disagree.
        listOf(0.0, 0.1, 0.5, 1.0, 1.3, 2.4, 5.0, 1.3000000000000003).forEach { overs ->
            assertThat(overs.oversToBalls()).isEqualTo(ballsFromOversNotation(overs))
            assertThat(overs.oversToBalls()).isEqualTo(MatchRecompute.ballsFromOvers(overs))
        }
    }

    @Test
    fun `a quota left over reads as balls remaining, not as a decimal`() {
        // The scoring-screen bug, as arithmetic: one ball into a one-over quota.
        val quotaBalls = 1 * 6
        assertThat(formatBallsAsOvers(quotaBalls - 1)).isEqualTo("0.5")
        // And one ball into a two-over quota is 1.5, not 1.9.
        assertThat(formatBallsAsOvers(2 * 6 - 1)).isEqualTo("1.5")
        assertThat(formatBallsAsOvers(2 * 6)).isEqualTo("2.0")
        assertThat(formatBallsAsOvers(0)).isEqualTo("0.0")
    }
}
