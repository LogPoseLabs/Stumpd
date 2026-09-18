package com.oreki.stumpd.data.sync.firebase

import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.data.local.entity.TournamentFixtureEntity
import com.oreki.stumpd.data.local.entity.TournamentSquadPlayerEntity
import com.oreki.stumpd.data.local.entity.TournamentTeamEntity
import com.oreki.stumpd.data.sync.FirebaseConfig

/** A tournament as the cloud holds it: the whole aggregate, ready for Room. */
data class TournamentCloudData(
    val tournament: TournamentEntity,
    val teams: List<TournamentTeamEntity>,
    val squads: List<TournamentSquadPlayerEntity>,
    val fixtures: List<TournamentFixtureEntity>,
)

/**
 * The wire format for a tournament document, both ways, with no Firestore types in sight.
 *
 * Kept pure and in one file on purpose. The failure this guards against is silent: a field written
 * under one name and read under another uploads cleanly, downloads cleanly, and quietly reverts
 * part of the tournament on the next sync — no error anywhere. Encoding and decoding side by side,
 * with a round-trip test over them, is the only way to notice.
 *
 * Every read coerces rather than casts: Firestore hands numbers back as `Long` or `Double`, and any
 * field may be missing from a document written by an older version of the app.
 */
object TournamentCloudCodec {

    fun encode(
        ownerId: String,
        tournament: TournamentEntity,
        teams: List<TournamentTeamEntity>,
        squads: List<TournamentSquadPlayerEntity>,
        fixtures: List<TournamentFixtureEntity>,
    ): Map<String, Any?> = mapOf(
        "tournamentId" to tournament.tournamentId,
        "groupId" to tournament.groupId,
        "name" to tournament.name,
        "format" to tournament.format,
        "teamCount" to tournament.teamCount,
        "squadSize" to tournament.squadSize,
        "poolCount" to tournament.poolCount,
        "advancePerPool" to tournament.advancePerPool,
        "pointsWin" to tournament.pointsWin,
        "pointsTie" to tournament.pointsTie,
        "pointsLoss" to tournament.pointsLoss,
        "pointsNoResult" to tournament.pointsNoResult,
        "status" to tournament.status,
        "matchSettingsJson" to tournament.matchSettingsJson,
        "teams" to teams.map { team ->
            mapOf(
                "teamId" to team.teamId,
                "name" to team.name,
                "shortName" to team.shortName,
                "captainPlayerId" to team.captainPlayerId,
                "captainName" to team.captainName,
                "seed" to team.seed,
                "poolOrdinal" to team.poolOrdinal,
                "updatedAt" to team.updatedAt,
                // A squad travels inside its team: nothing needs it separately, and this way a
                // team and its players cannot arrive apart.
                "playerIds" to squads
                    .filter { it.teamId == team.teamId }
                    .sortedBy { it.battingOrder }
                    .map { it.playerId },
            )
        },
        "fixtures" to fixtures.map { fixture ->
            mapOf(
                "fixtureId" to fixture.fixtureId,
                "stage" to fixture.stage,
                "poolOrdinal" to fixture.poolOrdinal,
                "round" to fixture.round,
                "slot" to fixture.slot,
                "leg" to fixture.leg,
                "homeTeamId" to fixture.homeTeamId,
                "awayTeamId" to fixture.awayTeamId,
                "homeSourceRef" to fixture.homeSourceRef,
                "awaySourceRef" to fixture.awaySourceRef,
                "label" to fixture.label,
                "status" to fixture.status,
                "matchId" to fixture.matchId,
                "winnerTeamId" to fixture.winnerTeamId,
                "updatedAt" to fixture.updatedAt,
            )
        },
        "createdAt" to tournament.createdAt,
        FirebaseConfig.FIELD_OWNER_ID to ownerId,
        FirebaseConfig.FIELD_UPDATED_AT to tournament.updatedAt,
    )

    /**
     * Reads a document back.
     *
     * [documentId] and [groupId] come from the document's own path, so a tournament whose stored
     * ids are missing still lands in the right place.
     */
    fun decode(
        documentId: String,
        groupId: String,
        data: Map<String, Any?>,
    ): TournamentCloudData {
        val tournamentId = data.str("tournamentId")?.takeIf { it.isNotBlank() } ?: documentId

        val tournament = TournamentEntity(
            tournamentId = tournamentId,
            groupId = data.str("groupId")?.takeIf { it.isNotBlank() } ?: groupId,
            name = data.str("name").orEmpty(),
            format = data.str("format") ?: "SINGLE_ROUND_ROBIN",
            teamCount = data.num("teamCount").toInt(),
            squadSize = data.num("squadSize").toInt(),
            poolCount = data.num("poolCount").toInt(),
            advancePerPool = data.num("advancePerPool").toInt(),
            pointsWin = data.num("pointsWin", default = 2).toInt(),
            pointsTie = data.num("pointsTie", default = 1).toInt(),
            pointsLoss = data.num("pointsLoss").toInt(),
            pointsNoResult = data.num("pointsNoResult", default = 1).toInt(),
            status = data.str("status") ?: "DRAFT",
            matchSettingsJson = data.str("matchSettingsJson"),
            createdAt = data.num("createdAt"),
            updatedAt = data.num(FirebaseConfig.FIELD_UPDATED_AT),
        )

        val teamMaps = (data["teams"] as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .filter { it.str("teamId")?.isNotBlank() == true }

        val teams = teamMaps.map { m ->
            TournamentTeamEntity(
                teamId = m.str("teamId").orEmpty(),
                tournamentId = tournamentId,
                name = m.str("name").orEmpty(),
                shortName = m.str("shortName"),
                captainPlayerId = m.str("captainPlayerId"),
                captainName = m.str("captainName"),
                seed = m.num("seed").toInt(),
                poolOrdinal = m.num("poolOrdinal").toInt(),
                updatedAt = m.num("updatedAt"),
            )
        }

        val squads = teamMaps.flatMap { m ->
            val teamId = m.str("teamId").orEmpty()
            (m["playerIds"] as? List<*>).orEmpty()
                .filterIsInstance<String>()
                .mapIndexed { index, playerId ->
                    TournamentSquadPlayerEntity(
                        tournamentId = tournamentId,
                        teamId = teamId,
                        playerId = playerId,
                        battingOrder = index,
                    )
                }
        }

        val fixtures = (data["fixtures"] as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .filter { it.str("fixtureId")?.isNotBlank() == true }
            .map { m ->
                TournamentFixtureEntity(
                    fixtureId = m.str("fixtureId").orEmpty(),
                    tournamentId = tournamentId,
                    stage = m.str("stage") ?: "LEAGUE",
                    poolOrdinal = m.num("poolOrdinal").toInt(),
                    round = m.num("round").toInt(),
                    slot = m.num("slot").toInt(),
                    leg = m.num("leg", default = 1).toInt(),
                    homeTeamId = m.str("homeTeamId"),
                    awayTeamId = m.str("awayTeamId"),
                    homeSourceRef = m.str("homeSourceRef"),
                    awaySourceRef = m.str("awaySourceRef"),
                    label = m.str("label").orEmpty(),
                    status = m.str("status") ?: "PENDING",
                    matchId = m.str("matchId"),
                    winnerTeamId = m.str("winnerTeamId"),
                    updatedAt = m.num("updatedAt"),
                )
            }

        return TournamentCloudData(tournament, teams, squads, fixtures)
    }

    private fun Map<*, *>.str(field: String): String? = this[field] as? String

    private fun Map<*, *>.num(field: String, default: Long = 0L): Long =
        (this[field] as? Number)?.toLong() ?: default
}
