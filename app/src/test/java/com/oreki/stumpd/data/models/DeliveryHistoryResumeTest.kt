package com.oreki.stumpd.data.models

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.DeliverySnapshot
import com.oreki.stumpd.domain.model.Player
import org.junit.Test

class DeliveryHistoryResumeTest {

    /** Snapshots differ by [strikerIndex] so truncation is observable. */
    private fun snap(strikerIdx: Int) = DeliverySnapshot(
        strikerIndex = strikerIdx,
        nonStrikerIndex = 1,
        bowlerIndex = 0,
        battingTeamPlayers = listOf(Player(name = "A")),
        bowlingTeamPlayers = listOf(Player(name = "B")),
        totalWickets = 0,
        currentOver = 0,
        ballsInOver = 0,
        runsConcededInCurrentOver = 0,
        totalExtras = 0,
        calculatedTotalRuns = 0,
        previousBowlerName = null,
        midOverReplacementDueToJoker = false,
        jokerBallsBowledInnings1 = 0,
        jokerBallsBowledInnings2 = 0,
        jokerOutInCurrentInnings = false,
        currentBowlerSpell = 1,
        powerplayDoublingDoneInnings1 = false,
        powerplayDoublingDoneInnings2 = false,
        isNoBallRunOut = false,
        completedBattersInnings1 = emptyList(),
        completedBattersInnings2 = emptyList(),
        completedBowlersInnings1 = emptyList(),
        completedBowlersInnings2 = emptyList(),
        currentPartnershipRuns = 0,
        currentPartnershipBalls = 0,
        currentPartnershipBatsman1Runs = 0,
        currentPartnershipBatsman2Runs = 0,
        currentPartnershipBatsman1Balls = 0,
        currentPartnershipBatsman2Balls = 0,
        currentPartnershipBatsman1Name = null,
        currentPartnershipBatsman2Name = null,
        partnerships = emptyList(),
        fallOfWickets = emptyList(),
    )

    @Test
    fun `returns all when counts match`() {
        val r = listOf(snap(0), snap(1))
        assertThat(deliverySnapshotsAlignedToDeliveryCount(r, 2)).isEqualTo(r)
    }

    @Test
    fun `returns empty when deliveries empty but history has snaps`() {
        val r = listOf(snap(0), snap(1))
        assertThat(deliverySnapshotsAlignedToDeliveryCount(r, 0)).isEmpty()
    }

    @Test
    fun `truncates when history longer than deliveries and deliveries positive`() {
        val r = listOf(snap(0), snap(1), snap(2))
        val out = deliverySnapshotsAlignedToDeliveryCount(r, 2)
        assertThat(out).hasSize(2)
        assertThat(out.map { it.strikerIndex }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `returns empty when history shorter than deliveries`() {
        val r = listOf(snap(0))
        assertThat(deliverySnapshotsAlignedToDeliveryCount(r, 3)).isEmpty()
    }

    @Test
    fun `both zero yields empty list`() {
        assertThat(deliverySnapshotsAlignedToDeliveryCount(emptyList(), 0)).isEmpty()
    }
}
