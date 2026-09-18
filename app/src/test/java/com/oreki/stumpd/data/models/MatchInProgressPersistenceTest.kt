package com.oreki.stumpd.data.models

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.oreki.stumpd.domain.model.DeliverySnapshot
import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PartnershipsPersistenceState
import com.oreki.stumpd.domain.model.Player
import org.junit.Test

/**
 * Verifies Gson helpers and [createMatchInProgress] output for undo history + partnership blobs.
 */
class MatchInProgressPersistenceTest {

    private val gson = Gson()

    @Test
    fun `parseDeliverySnapshotList returns empty for null or blank`() {
        assertThat(null.parseDeliverySnapshotList(gson)).isEmpty()
        assertThat("".parseDeliverySnapshotList(gson)).isEmpty()
        assertThat("   ".parseDeliverySnapshotList(gson)).isEmpty()
    }

    @Test
    fun `parseDeliverySnapshotList returns empty for invalid JSON`() {
        assertThat("{not an array".parseDeliverySnapshotList(gson)).isEmpty()
    }

    @Test
    fun `parseDeliverySnapshotList round trips list of DeliverySnapshot`() {
        val snaps = listOf(
            minimalSnapshot(totalWickets = 0, currentOver = 0, ballsInOver = 0),
            minimalSnapshot(totalWickets = 1, currentOver = 0, ballsInOver = 3),
        )
        val json = gson.toJson(snaps)
        val parsed = json.parseDeliverySnapshotList(gson)
        assertThat(parsed).hasSize(2)
        assertThat(parsed[0].totalWickets).isEqualTo(0)
        assertThat(parsed[0].battingTeamPlayers.map { it.name }).containsExactly("A", "B")
        assertThat(parsed[1].totalWickets).isEqualTo(1)
        assertThat(parsed[1].ballsInOver).isEqualTo(3)
        assertThat(parsed[1].partnerships).isEmpty()
        assertThat(parsed[1].fallOfWickets).isEmpty()
    }

    @Test
    fun `parsePartnershipsPersistenceState returns null for blank`() {
        assertThat(null.parsePartnershipsPersistenceState(gson)).isNull()
        assertThat("".parsePartnershipsPersistenceState(gson)).isNull()
    }

    @Test
    fun `parsePartnershipsPersistenceState returns null for invalid JSON`() {
        assertThat("{not json".parsePartnershipsPersistenceState(gson)).isNull()
        assertThat("[]".parsePartnershipsPersistenceState(gson)).isNull()
    }

    @Test
    fun `parsePartnershipsPersistenceState round trips`() {
        val state = PartnershipsPersistenceState(
            currentPartnershipRuns = 42,
            currentPartnershipBalls = 20,
            currentPartnershipBatsman1Runs = 25,
            currentPartnershipBatsman2Runs = 17,
            currentPartnershipBatsman1Balls = 14,
            currentPartnershipBatsman2Balls = 6,
            currentPartnershipBatsman1Name = "Opener",
            currentPartnershipBatsman2Name = "No2",
            partnerships = listOf(
                Partnership("Opener", "No2", 42, 20, 25, 17, true)
            ),
            fallOfWickets = listOf(
                FallOfWicket("Prev", 100, 12.4, 3, "CAUGHT", "Bowler1", "F1")
            ),
            firstInningsPartnerships = listOf(
                Partnership("X", "Y", 55, 40, 30, 25, false)
            ),
            firstInningsFallOfWickets = listOf(
                FallOfWicket("X", 55, 8.0, 1, "BOWLED", "Z", null)
            ),
        )
        val json = gson.toJson(state)
        val parsed = json.parsePartnershipsPersistenceState(gson)!!
        assertThat(parsed.currentPartnershipRuns).isEqualTo(42)
        assertThat(parsed.partnerships.single().runs).isEqualTo(42)
        assertThat(parsed.fallOfWickets.single().batsmanName).isEqualTo("Prev")
        assertThat(parsed.firstInningsPartnerships.single().batsman1Name).isEqualTo("X")
        assertThat(parsed.firstInningsFallOfWickets.single().wicketNumber).isEqualTo(1)
    }

    @Test
    fun `createMatchInProgress serializes deliveryHistory and partnershipsState`() {
        val batters = listOf(Player(name = "A"), Player(name = "B"))
        val bowlers = listOf(Player(name = "C"))
        val deliveries = listOf(
            DeliveryUI(1, 1, 1, "1", false, "A", "B", "C", runs = 1)
        )
        val history = listOf(
            minimalSnapshot(0, 0, 0).copy(
                battingTeamPlayers = batters,
                bowlingTeamPlayers = bowlers
            )
        )
        val pState = PartnershipsPersistenceState(
            currentPartnershipRuns = 8,
            currentPartnershipBatsman1Name = "A",
            currentPartnershipBatsman2Name = "B",
        )
        val progress = createMatchInProgress(
            matchId = "m1",
            team1Name = "T1",
            team2Name = "T2",
            jokerName = "",
            team1PlayerIds = listOf("1"),
            team2PlayerIds = listOf("2"),
            team1PlayerNames = listOf("A"),
            team2PlayerNames = listOf("B"),
            matchSettingsJson = "{}",
            groupId = null,
            groupName = null,
            tossWinner = null,
            tossChoice = null,
            currentInnings = 1,
            currentOver = 0,
            ballsInOver = 1,
            team1Players = batters,
            team2Players = bowlers,
            strikerIndex = 0,
            nonStrikerIndex = 1,
            bowlerIndex = 0,
            firstInningsRuns = 0,
            firstInningsWickets = 0,
            firstInningsOvers = 0,
            firstInningsBalls = 0,
            bowlingTeamPlayers = bowlers,
            totalExtras = 0,
            wides = 0,
            noBalls = 0,
            byes = 0,
            legByes = 0,
            completedBattersInnings1 = emptyList(),
            completedBattersInnings2 = emptyList(),
            completedBowlersInnings1 = emptyList(),
            completedBowlersInnings2 = emptyList(),
            firstInningsBattingPlayers = emptyList(),
            firstInningsBowlingPlayers = emptyList(),
            jokerOutInCurrentInnings = false,
            jokerBallsBowledInnings1 = 0,
            jokerBallsBowledInnings2 = 0,
            powerplayRunsInnings1 = 0,
            powerplayRunsInnings2 = 0,
            powerplayDoublingDoneInnings1 = false,
            powerplayDoublingDoneInnings2 = false,
            allDeliveries = deliveries,
            totalWickets = 2,
            calculatedTotalRuns = 120,
            deliveryHistory = history,
            partnershipsState = pState,
            gson = gson,
        )

        assertThat(progress.totalWickets).isEqualTo(2)
        assertThat(progress.calculatedTotalRuns).isEqualTo(120)
        assertThat(progress.deliveryHistoryJson).isNotNull()
        assertThat(progress.partnershipsStateJson).isNotNull()

        val snaps = progress.deliveryHistoryJson!!.parseDeliverySnapshotList(gson)
        assertThat(snaps).hasSize(1)
        assertThat(snaps.single().totalWickets).isEqualTo(0)

        val ps = progress.partnershipsStateJson!!.parsePartnershipsPersistenceState(gson)!!
        assertThat(ps.currentPartnershipRuns).isEqualTo(8)
        assertThat(ps.currentPartnershipBatsman1Name).isEqualTo("A")
    }

    @Test
    fun `createMatchInProgress leaves deliveryHistoryJson null when history empty`() {
        val batters = listOf(Player(name = "A"), Player(name = "B"))
        val bowlers = listOf(Player(name = "C"))
        val progress = createMatchInProgress(
            matchId = "m1",
            team1Name = "T1",
            team2Name = "T2",
            jokerName = "",
            team1PlayerIds = listOf("1"),
            team2PlayerIds = listOf("2"),
            team1PlayerNames = listOf("A"),
            team2PlayerNames = listOf("B"),
            matchSettingsJson = "{}",
            groupId = null,
            groupName = null,
            tossWinner = null,
            tossChoice = null,
            currentInnings = 1,
            currentOver = 0,
            ballsInOver = 0,
            team1Players = batters,
            team2Players = bowlers,
            strikerIndex = 0,
            nonStrikerIndex = 1,
            bowlerIndex = 0,
            firstInningsRuns = 0,
            firstInningsWickets = 0,
            firstInningsOvers = 0,
            firstInningsBalls = 0,
            bowlingTeamPlayers = bowlers,
            totalExtras = 0,
            wides = 0,
            noBalls = 0,
            byes = 0,
            legByes = 0,
            completedBattersInnings1 = emptyList(),
            completedBattersInnings2 = emptyList(),
            completedBowlersInnings1 = emptyList(),
            completedBowlersInnings2 = emptyList(),
            firstInningsBattingPlayers = emptyList(),
            firstInningsBowlingPlayers = emptyList(),
            jokerOutInCurrentInnings = false,
            jokerBallsBowledInnings1 = 0,
            jokerBallsBowledInnings2 = 0,
            powerplayRunsInnings1 = 0,
            powerplayRunsInnings2 = 0,
            powerplayDoublingDoneInnings1 = false,
            powerplayDoublingDoneInnings2 = false,
            allDeliveries = emptyList(),
            totalWickets = 0,
            calculatedTotalRuns = 0,
            deliveryHistory = emptyList(),
            partnershipsState = PartnershipsPersistenceState(),
            gson = gson,
        )
        assertThat(progress.deliveryHistoryJson).isNull()
        assertThat(progress.partnershipsStateJson).isNotNull()
    }

    @Test
    fun `toPlayerList returns empty for invalid JSON`() {
        assertThat("{".toPlayerList(gson)).isEmpty()
    }

    private fun minimalSnapshot(totalWickets: Int, currentOver: Int, ballsInOver: Int) = DeliverySnapshot(
        strikerIndex = 0,
        nonStrikerIndex = 1,
        bowlerIndex = 0,
        battingTeamPlayers = listOf(Player(name = "A"), Player(name = "B")),
        bowlingTeamPlayers = listOf(Player(name = "C")),
        totalWickets = totalWickets,
        currentOver = currentOver,
        ballsInOver = ballsInOver,
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
        currentPartnershipRuns = 12,
        currentPartnershipBalls = 8,
        currentPartnershipBatsman1Runs = 7,
        currentPartnershipBatsman2Runs = 5,
        currentPartnershipBatsman1Balls = 5,
        currentPartnershipBatsman2Balls = 3,
        currentPartnershipBatsman1Name = "A",
        currentPartnershipBatsman2Name = "B",
        partnerships = emptyList(),
        fallOfWickets = emptyList(),
    )
}
