package com.oreki.stumpd.domain.model

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

/**
 * The preset is the whole contract between a fixture and the scoring screens, and it travels as
 * JSON through an intent — so the cases worth pinning are the ones where it arrives damaged.
 */
class TeamSetupPresetTest {

    private fun complete() = TeamSetupPreset(
        tournamentId = "t1",
        tournamentName = "Sunday Cup",
        fixtureId = "t1:LEAGUE:1:0:1",
        fixtureLabel = "Match 1",
        groupId = "g1",
        homeTeamId = "t1:t1",
        homeTeamName = "Warriors",
        homeCaptainPlayerId = "p1",
        homePlayerIds = listOf("p1", "p2"),
        awayTeamId = "t1:t2",
        awayTeamName = "Strikers",
        awayCaptainPlayerId = "p3",
        awayPlayerIds = listOf("p3", "p4"),
    )

    @Test
    fun `a complete preset is usable`() {
        assertThat(complete().isUsable).isTrue()
    }

    @Test
    fun `a preset with an empty squad is not usable`() {
        // Half a preset is worse than none: team setup would open with one side blank and the
        // scorer could start a match that didn't match its fixture.
        assertThat(complete().copy(awayPlayerIds = emptyList()).isUsable).isFalse()
        assertThat(complete().copy(homePlayerIds = null).isUsable).isFalse()
    }

    @Test
    fun `a preset missing its fixture or group is not usable`() {
        assertThat(complete().copy(fixtureId = "").isUsable).isFalse()
        assertThat(complete().copy(groupId = "").isUsable).isFalse()
        assertThat(complete().copy(tournamentId = " ").isUsable).isFalse()
    }

    @Test
    fun `team ids are looked up by the name the side is playing under`() {
        // The saved match stores "batted first" as team1, which may be either side — so the ids
        // have to be resolvable from the name rather than assumed to be in setup order.
        val preset = complete()
        assertThat(preset.teamIdFor("Strikers")).isEqualTo("t1:t2")
        assertThat(preset.teamIdFor("warriors")).isEqualTo("t1:t1")
        assertThat(preset.teamIdFor("Somebody Else")).isNull()
    }

    @Test
    fun `a squad list missing from the JSON reads as empty rather than throwing`() {
        // Gson bypasses Kotlin constructors, so an absent key is null and not the declared
        // default. Reading through the accessors is what keeps that from being a crash.
        val json = """{"tournamentId":"t1","fixtureId":"f1","groupId":"g1"}"""
        val parsed = Gson().fromJson(json, TeamSetupPreset::class.java)

        assertThat(parsed.homeSquad).isEmpty()
        assertThat(parsed.awaySquad).isEmpty()
        assertThat(parsed.isUsable).isFalse()
    }

    @Test
    fun `a preset survives a round trip through JSON`() {
        val gson = Gson()
        val restored = gson.fromJson(gson.toJson(complete()), TeamSetupPreset::class.java)

        assertThat(restored).isEqualTo(complete())
        assertThat(restored.isUsable).isTrue()
    }
}
