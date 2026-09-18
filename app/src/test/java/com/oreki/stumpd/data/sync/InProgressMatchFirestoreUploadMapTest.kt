package com.oreki.stumpd.data.sync

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.data.local.entity.InProgressMatchEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InProgressMatchFirestoreUploadMapTest {

    @Test
    fun `upload map includes owner updatedAt and persistence blobs`() {
        val entity = sampleEntity(
            deliveryHistoryJson = "[{}]",
            partnershipsStateJson = """{"innings1":[],"innings2":[]}""",
        )
        val map = inProgressMatchEntityToFirestoreUploadMap(entity, "user-99", 9_000L)

        assertThat(map[FirebaseConfig.FIELD_OWNER_ID]).isEqualTo("user-99")
        assertThat(map[FirebaseConfig.FIELD_UPDATED_AT]).isEqualTo(9_000L)
        assertThat(map["deliveryHistoryJson"]).isEqualTo("[{}]")
        assertThat(map["partnershipsStateJson"]).isEqualTo("""{"innings1":[],"innings2":[]}""")
        assertThat(map["matchId"]).isEqualTo("mid")
    }

    @Test
    fun `upload map without sync fields round trips through parser`() {
        val entity = sampleEntity(
            deliveryHistoryJson = null,
            partnershipsStateJson = null,
            strikerIndex = 2,
            nonStrikerIndex = null,
        )
        val map = inProgressMatchEntityToFirestoreUploadMap(entity, "o", 1L)
        val core = map.filterKeys {
            it != FirebaseConfig.FIELD_OWNER_ID && it != FirebaseConfig.FIELD_UPDATED_AT
        }
        val parsed = inProgressMatchEntityFromFirestoreData(core, entity.matchId)
        assertThat(parsed).isEqualTo(entity)
    }

    private fun sampleEntity(
        deliveryHistoryJson: String?,
        partnershipsStateJson: String?,
        strikerIndex: Int? = 0,
        nonStrikerIndex: Int? = 1,
    ) = InProgressMatchEntity(
        matchId = "mid",
        team1Name = "A",
        team2Name = "B",
        jokerName = "J",
        groupId = "g",
        groupName = "G",
        tossWinner = "A",
        tossChoice = "bat",
        matchSettingsJson = "{}",
        team1PlayerIds = "[]",
        team2PlayerIds = "[]",
        team1PlayerNames = "[]",
        team2PlayerNames = "[]",
        currentInnings = 1,
        currentOver = 3,
        ballsInOver = 2,
        totalWickets = 4,
        team1PlayersJson = "[]",
        team2PlayersJson = "[]",
        firstInningsRuns = 10,
        firstInningsWickets = 1,
        firstInningsOvers = 2,
        firstInningsBalls = 3,
        totalExtras = 1,
        calculatedTotalRuns = 11,
        completedBattersInnings1Json = null,
        completedBattersInnings2Json = null,
        completedBowlersInnings1Json = null,
        completedBowlersInnings2Json = null,
        firstInningsBattingPlayersJson = null,
        firstInningsBowlingPlayersJson = null,
        jokerOutInCurrentInnings = false,
        jokerBallsBowledInnings1 = 0,
        jokerBallsBowledInnings2 = 0,
        powerplayRunsInnings1 = 0,
        powerplayRunsInnings2 = 0,
        powerplayDoublingDoneInnings1 = false,
        powerplayDoublingDoneInnings2 = false,
        allDeliveriesJson = "[]",
        deliveryHistoryJson = deliveryHistoryJson,
        partnershipsStateJson = partnershipsStateJson,
        lastSavedAt = 100L,
        startedAt = 200L,
        strikerIndex = strikerIndex,
        nonStrikerIndex = nonStrikerIndex,
        bowlerIndex = 0,
    )
}
