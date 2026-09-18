package com.oreki.stumpd.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InProgressMatchFirestoreFieldsTest {

    @Test
    fun `parses deliveryHistoryJson and partnershipsStateJson`() {
        val hist = """[{"runsOffBat":1}]"""
        val partners = """{"innings1":[],"innings2":[]}"""
        val data = mapOf<String, Any?>(
            "matchId" to "m1",
            "team1Name" to "A",
            "team2Name" to "B",
            "deliveryHistoryJson" to hist,
            "partnershipsStateJson" to partners,
            "lastSavedAt" to 1000L,
            "startedAt" to 2000L,
        )

        val entity = inProgressMatchEntityFromFirestoreData(data, "fallback")

        assertThat(entity.matchId).isEqualTo("m1")
        assertThat(entity.deliveryHistoryJson).isEqualTo(hist)
        assertThat(entity.partnershipsStateJson).isEqualTo(partners)
        assertThat(entity.lastSavedAt).isEqualTo(1000L)
        assertThat(entity.startedAt).isEqualTo(2000L)
    }

    @Test
    fun `uses documentId when matchId missing`() {
        val entity = inProgressMatchEntityFromFirestoreData(emptyMap(), "doc-id")

        assertThat(entity.matchId).isEqualTo("doc-id")
        assertThat(entity.matchSettingsJson).isEqualTo("{}")
        assertThat(entity.team1PlayerIds).isEqualTo("[]")
        assertThat(entity.deliveryHistoryJson).isNull()
        assertThat(entity.partnershipsStateJson).isNull()
    }

    @Test
    fun `coerces Int and Long for numeric fields`() {
        val data = mapOf<String, Any?>(
            "matchId" to "x",
            "currentInnings" to 2,
            "currentOver" to 5L,
            "ballsInOver" to 3,
            "strikerIndex" to 1L,
            "jokerBallsBowledInnings1" to 7,
        )

        val entity = inProgressMatchEntityFromFirestoreData(data, "f")

        assertThat(entity.currentInnings).isEqualTo(2)
        assertThat(entity.currentOver).isEqualTo(5)
        assertThat(entity.ballsInOver).isEqualTo(3)
        assertThat(entity.strikerIndex).isEqualTo(1)
        assertThat(entity.jokerBallsBowledInnings1).isEqualTo(7)
    }
}
