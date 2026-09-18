package com.oreki.stumpd.domain.match

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.DeliveryUI
import org.junit.Test

/**
 * The outcome grammar, pinned against the strings `ScoringEngine` actually writes.
 *
 * Every case below is a real format from a call site in `ScoringEngine.addDelivery`, not an
 * invented one. Four screens used to parse these independently; this is the contract they now
 * share, so the parsing has to be right for the awkward ones — a wide that also took a wicket, a
 * no-ball run-out with a name inside it, a bye that counts as a ball but not against the bowler.
 */
class DeliveryOutcomeTest {

    private fun delivery(outcome: String, runs: Int = 0) =
        DeliveryUI(inning = 1, over = 1, ballInOver = 1, outcome = outcome, runs = runs)

    @Test
    fun `runs off the bat are the outcome itself`() {
        assertThat(delivery("0", runs = 0).effectiveRuns()).isEqualTo(0)
        assertThat(delivery("4", runs = 4).effectiveRuns()).isEqualTo(4)
        assertThat(delivery("6", runs = 6).effectiveRuns()).isEqualTo(6)
    }

    @Test
    fun `extras carry their total, which is what the old upper-case parser missed`() {
        // "Wd+2".contains("WD") is false, so the live Overs tab scored these as zero.
        assertThat(delivery("Wd", runs = 1).effectiveRuns()).isEqualTo(1)
        assertThat(delivery("Wd+2", runs = 2).effectiveRuns()).isEqualTo(2)
        assertThat(delivery("Nb+3", runs = 3).effectiveRuns()).isEqualTo(3)
        assertThat(delivery("B+4", runs = 4).effectiveRuns()).isEqualTo(4)
        assertThat(delivery("Lb+1", runs = 1).effectiveRuns()).isEqualTo(1)
    }

    @Test
    fun `a leg bye is not read as a bye`() {
        assertThat(DeliveryOutcome.kindOf("Lb+2")).isEqualTo(DeliveryOutcome.Kind.LEG_BYE)
        assertThat(DeliveryOutcome.kindOf("B+2")).isEqualTo(DeliveryOutcome.Kind.BYE)
    }

    @Test
    fun `only wides and no-balls fail to count towards the over`() {
        // The scorer's convention, not the laws': a bye costs the batting side a ball.
        assertThat(delivery("4").isLegalBall()).isTrue()
        assertThat(delivery("W").isLegalBall()).isTrue()
        assertThat(delivery("B+1").isLegalBall()).isTrue()
        assertThat(delivery("Lb+1").isLegalBall()).isTrue()
        assertThat(delivery("Wd+1").isLegalBall()).isFalse()
        assertThat(delivery("Nb+2").isLegalBall()).isFalse()
    }

    @Test
    fun `byes are not charged to the bowler`() {
        assertThat(DeliveryOutcome.runsChargedToBowler("B+4", 4)).isEqualTo(0)
        assertThat(DeliveryOutcome.runsChargedToBowler("Lb+2", 2)).isEqualTo(0)
        assertThat(DeliveryOutcome.runsChargedToBowler("Wd+2", 2)).isEqualTo(2)
        assertThat(DeliveryOutcome.runsChargedToBowler("Nb+3", 3)).isEqualTo(3)
        assertThat(DeliveryOutcome.runsChargedToBowler("4", 4)).isEqualTo(4)
    }

    @Test
    fun `the batter keeps only what came off the bat on a no-ball`() {
        // Group penalty of one, four runs total: three were struck.
        assertThat(DeliveryOutcome.batterRuns("Nb+4", 4, noBallRuns = 1)).isEqualTo(3)
        assertThat(DeliveryOutcome.batterRuns("Nb+1", 1, noBallRuns = 1)).isEqualTo(0)
        assertThat(DeliveryOutcome.batterRuns("Wd+2", 2, noBallRuns = 1)).isEqualTo(0)
        assertThat(DeliveryOutcome.batterRuns("B+3", 3, noBallRuns = 1)).isEqualTo(0)
        assertThat(DeliveryOutcome.batterRuns("2", 2, noBallRuns = 1)).isEqualTo(2)
    }

    @Test
    fun `every shape of wicket ball is recognised`() {
        assertThat(delivery("W").isWicket()).isTrue()
        assertThat(delivery("Wd+1 W").isWicket()).isTrue()            // stumped off a wide
        assertThat(delivery("2 + RO (Kushal @ NS)").isWicket()).isTrue()
        assertThat(delivery("Nb+4; BO(Kushal)").isWicket()).isTrue()
        assertThat(delivery("Nb+1; RO(Kushal @ S)").isWicket()).isTrue()
        assertThat(delivery("4").isWicket()).isFalse()
        assertThat(delivery("Wd+1").isWicket()).isFalse()
    }

    @Test
    fun `the dismissed batter's name is found inside the outcome, and can be rewritten`() {
        // This is what adopt, merge and a player rename all fail to update today.
        assertThat(DeliveryOutcome.embeddedOutName("2 + RO (Kushal @ NS)")).isEqualTo("Kushal")
        assertThat(DeliveryOutcome.embeddedOutName("Nb+4; BO(Kushal)")).isEqualTo("Kushal")
        assertThat(DeliveryOutcome.embeddedOutName("Nb+1; RO(Kushal @ S)")).isEqualTo("Kushal")
        assertThat(DeliveryOutcome.embeddedOutName("W")).isNull()
        assertThat(DeliveryOutcome.embeddedOutName("4")).isNull()

        assertThat(DeliveryOutcome.withEmbeddedOutName("2 + RO (Kushal @ NS)", "Prasanna"))
            .isEqualTo("2 + RO (Prasanna @ NS)")
        assertThat(DeliveryOutcome.withEmbeddedOutName("Nb+4; BO(Kushal)", "Prasanna"))
            .isEqualTo("Nb+4; BO(Prasanna)")
        assertThat(DeliveryOutcome.withEmbeddedOutName("W", "Prasanna")).isEqualTo("W")
    }

    @Test
    fun `rendering produces the strings the scorer writes, so the trail stays uniform`() {
        assertThat(DeliveryOutcome.render(DeliveryOutcome.Kind.OFF_THE_BAT, 4)).isEqualTo("4")
        assertThat(DeliveryOutcome.render(DeliveryOutcome.Kind.WIDE, 0)).isEqualTo("Wd")
        assertThat(DeliveryOutcome.render(DeliveryOutcome.Kind.WIDE, 2)).isEqualTo("Wd+2")
        assertThat(DeliveryOutcome.render(DeliveryOutcome.Kind.NO_BALL, 3)).isEqualTo("Nb+3")
        assertThat(DeliveryOutcome.render(DeliveryOutcome.Kind.BYE, 1)).isEqualTo("B+1")
        assertThat(DeliveryOutcome.render(DeliveryOutcome.Kind.LEG_BYE, 4)).isEqualTo("Lb+4")
    }

    @Test
    fun `rendering round-trips through parsing`() {
        listOf(
            DeliveryOutcome.Kind.OFF_THE_BAT to 3,
            DeliveryOutcome.Kind.WIDE to 2,
            DeliveryOutcome.Kind.NO_BALL to 1,
            DeliveryOutcome.Kind.BYE to 2,
            DeliveryOutcome.Kind.LEG_BYE to 1,
        ).forEach { (kind, runs) ->
            val rendered = DeliveryOutcome.render(kind, runs)
            assertThat(DeliveryOutcome.kindOf(rendered)).isEqualTo(kind)
            assertThat(DeliveryOutcome.totalRuns(rendered, storedRuns = 0)).isEqualTo(runs)
        }
    }

    @Test
    fun `a boundary is highlighted only when it came off the bat`() {
        assertThat(DeliveryOutcome.isHighlight(DeliveryOutcome.Kind.OFF_THE_BAT, 4)).isTrue()
        assertThat(DeliveryOutcome.isHighlight(DeliveryOutcome.Kind.OFF_THE_BAT, 6)).isTrue()
        assertThat(DeliveryOutcome.isHighlight(DeliveryOutcome.Kind.OFF_THE_BAT, 2)).isFalse()
        assertThat(DeliveryOutcome.isHighlight(DeliveryOutcome.Kind.BYE, 4)).isFalse()
    }

    @Test
    fun `the stored total wins over the string, since the string is only a label`() {
        // A no-ball penalty of two would make "Nb+3" ambiguous; the stored runs settle it.
        assertThat(delivery("Nb+3", runs = 3).effectiveRuns()).isEqualTo(3)
        // Legacy rows with no stored runs still parse.
        assertThat(delivery("Nb+3", runs = 0).effectiveRuns()).isEqualTo(3)
        assertThat(delivery("W", runs = 0).effectiveRuns()).isEqualTo(0)
    }
}
