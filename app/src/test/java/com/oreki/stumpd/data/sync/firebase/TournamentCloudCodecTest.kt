package com.oreki.stumpd.data.sync.firebase

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.data.local.entity.TournamentFixtureEntity
import com.oreki.stumpd.data.local.entity.TournamentSquadPlayerEntity
import com.oreki.stumpd.data.local.entity.TournamentTeamEntity
import org.junit.Test

/**
 * The upload and the download have to agree, field for field.
 *
 * This is the test the feature most needs, because the failure it catches is silent: a field
 * written under one name and read under another uploads without error, downloads without error,
 * and quietly drops part of the tournament on the next sync. Nothing crashes and nothing logs.
 */
class TournamentCloudCodecTest {

    private val tournament = TournamentEntity(
        tournamentId = "t1",
        groupId = "g1",
        name = "Sunday Cup",
        format = "GROUPS_KNOCKOUT",
        teamCount = 4,
        squadSize = 6,
        poolCount = 2,
        advancePerPool = 1,
        pointsWin = 3,
        pointsTie = 2,
        pointsLoss = 1,
        pointsNoResult = 2,
        status = "ACTIVE",
        matchSettingsJson = """{"totalOvers":5}""",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_500_000,
    )

    private val teams = listOf(
        TournamentTeamEntity(
            teamId = "t1:t1",
            tournamentId = "t1",
            name = "Warriors",
            shortName = "WAR",
            captainPlayerId = "p1",
            captainName = "Ann",
            seed = 1,
            poolOrdinal = 1,
            updatedAt = 1_700_000_100_000,
        ),
        TournamentTeamEntity(
            teamId = "t1:t2",
            tournamentId = "t1",
            name = "Strikers",
            seed = 2,
            poolOrdinal = 2,
            updatedAt = 1_700_000_200_000,
        ),
    )

    private val squads = listOf(
        TournamentSquadPlayerEntity("t1", "t1:t1", "p1", battingOrder = 0),
        TournamentSquadPlayerEntity("t1", "t1:t1", "p2", battingOrder = 1),
        TournamentSquadPlayerEntity("t1", "t1:t2", "p3", battingOrder = 0),
    )

    private val fixtures = listOf(
        TournamentFixtureEntity(
            fixtureId = "t1:POOL:1:0:1",
            tournamentId = "t1",
            stage = "POOL",
            poolOrdinal = 1,
            round = 1,
            slot = 0,
            leg = 1,
            homeTeamId = "t1:t1",
            awayTeamId = "t1:t2",
            label = "Group A · Match 1",
            status = "COMPLETED",
            matchId = "m1",
            winnerTeamId = "t1:t1",
            updatedAt = 1_700_000_300_000,
        ),
        TournamentFixtureEntity(
            fixtureId = "t1:KNOCKOUT:1:0:1",
            tournamentId = "t1",
            stage = "KNOCKOUT",
            round = 1,
            slot = 0,
            homeSourceRef = "P:1:1",
            awaySourceRef = "P:2:1",
            label = "Final",
            status = "AWAITING_TEAMS",
            updatedAt = 1_700_000_400_000,
        ),
    )

    private fun roundTrip() = TournamentCloudCodec.decode(
        documentId = "t1",
        groupId = "g1",
        data = TournamentCloudCodec.encode("owner-1", tournament, teams, squads, fixtures),
    )

    @Test
    fun `a tournament survives the round trip unchanged`() {
        assertThat(roundTrip().tournament).isEqualTo(tournament)
    }

    @Test
    fun `teams and their squads survive the round trip unchanged`() {
        val decoded = roundTrip()

        assertThat(decoded.teams).containsExactlyElementsIn(teams)
        // Batting order is implied by position in the uploaded array, so it has to come back the
        // same — the order is the batting order the scorer chose.
        assertThat(decoded.squads).containsExactlyElementsIn(squads).inOrder()
    }

    @Test
    fun `fixtures survive the round trip unchanged, including unresolved slots`() {
        // The knockout fixture carries source refs and no teams: that is what a bracket looks
        // like before the groups finish, and losing those refs would strand the final.
        assertThat(roundTrip().fixtures).containsExactlyElementsIn(fixtures)
    }

    @Test
    fun `a document written by an older version decodes with sensible defaults`() {
        val sparse = TournamentCloudCodec.decode(
            documentId = "t9",
            groupId = "g9",
            data = mapOf("name" to "Old Cup"),
        )

        assertThat(sparse.tournament.tournamentId).isEqualTo("t9")
        assertThat(sparse.tournament.groupId).isEqualTo("g9")
        assertThat(sparse.tournament.format).isEqualTo("SINGLE_ROUND_ROBIN")
        assertThat(sparse.tournament.status).isEqualTo("DRAFT")
        // The defaults the entity declares, not zero — points are what the table is made of.
        assertThat(sparse.tournament.pointsWin).isEqualTo(2)
        assertThat(sparse.tournament.pointsTie).isEqualTo(1)
        assertThat(sparse.tournament.pointsNoResult).isEqualTo(1)
        assertThat(sparse.teams).isEmpty()
        assertThat(sparse.fixtures).isEmpty()
    }

    @Test
    fun `numbers arriving as doubles still decode`() {
        // Firestore is free to hand an integer back as a Double, and a hard cast would throw
        // halfway through a download.
        val decoded = TournamentCloudCodec.decode(
            documentId = "t1",
            groupId = "g1",
            data = mapOf(
                "teamCount" to 6.0,
                "squadSize" to 7.0,
                "updatedAt" to 1_700_000_000_000.0,
                "fixtures" to listOf(
                    mapOf(
                        "fixtureId" to "f1",
                        "round" to 2.0,
                        "slot" to 1.0,
                        "label" to "Match 2",
                        "status" to "PENDING",
                    )
                ),
            ),
        )

        assertThat(decoded.tournament.teamCount).isEqualTo(6)
        assertThat(decoded.tournament.squadSize).isEqualTo(7)
        assertThat(decoded.tournament.updatedAt).isEqualTo(1_700_000_000_000)
        assertThat(decoded.fixtures.single().round).isEqualTo(2)
    }

    @Test
    fun `entries with no id are dropped rather than stored as blanks`() {
        // A blank team id would collide with any other blank one on insert, and a blank fixture
        // id is not addressable at all.
        val decoded = TournamentCloudCodec.decode(
            documentId = "t1",
            groupId = "g1",
            data = mapOf(
                "teams" to listOf(mapOf("name" to "Nameless"), mapOf("teamId" to "")),
                "fixtures" to listOf(mapOf("label" to "Orphan")),
            ),
        )

        assertThat(decoded.teams).isEmpty()
        assertThat(decoded.squads).isEmpty()
        assertThat(decoded.fixtures).isEmpty()
    }

    @Test
    fun `the owner id and update time are written where the sync expects them`() {
        // The sync filters and the Firestore rules read these two by name.
        val encoded = TournamentCloudCodec.encode("owner-1", tournament, teams, squads, fixtures)

        assertThat(encoded["ownerId"]).isEqualTo("owner-1")
        assertThat(encoded["updatedAt"]).isEqualTo(tournament.updatedAt)
    }
}
