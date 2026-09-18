package com.oreki.stumpd.data.sync

import com.google.gson.JsonParser
import com.oreki.stumpd.data.local.entity.FallOfWicketEntity
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.local.entity.PartnershipEntity
import com.oreki.stumpd.data.local.entity.PlayerImpactEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.data.util.GsonProvider
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerImpact

/**
 * Reads a Stumpd backup JSON (or a match array) into [MatchHistory] without writing to the database.
 */
object MatchMergeParser {

    fun parseMatchesFromJson(json: String): List<MatchHistory> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        val gson = GsonProvider.get()
        val element = JsonParser.parseString(trimmed)
        if (element.isJsonArray) {
            return gson.fromJson(trimmed, Array<MatchEntity>::class.java)
                .map { it.toDomain() }
        }
        val backup = gson.fromJson(trimmed, BackupFile::class.java)
            ?: return emptyList()
        return assembleMatches(backup)
    }

    internal data class BackupFile(
        val matches: List<MatchEntity> = emptyList(),
        val matchStats: List<PlayerMatchStatsEntity> = emptyList(),
        val playerImpacts: List<PlayerImpactEntity> = emptyList(),
        val partnerships: List<PartnershipEntity> = emptyList(),
        val fallOfWickets: List<FallOfWicketEntity> = emptyList(),
    )

    internal fun assembleMatches(backup: BackupFile): List<MatchHistory> {
        val statsByMatch = backup.matchStats.groupBy { it.matchId }
        val impactsByMatch = backup.playerImpacts.groupBy { it.matchId }
        val partnershipsByMatch = backup.partnerships.groupBy { it.matchId }
        val fowByMatch = backup.fallOfWickets.groupBy { it.matchId }
        return backup.matches.map { entity ->
            assembleMatch(
                entity = entity,
                stats = statsByMatch[entity.id].orEmpty(),
                impacts = impactsByMatch[entity.id].orEmpty(),
                partnerships = partnershipsByMatch[entity.id].orEmpty(),
                fallOfWickets = fowByMatch[entity.id].orEmpty(),
            )
        }
    }

    private fun assembleMatch(
        entity: MatchEntity,
        stats: List<PlayerMatchStatsEntity>,
        impacts: List<PlayerImpactEntity>,
        partnerships: List<PartnershipEntity>,
        fallOfWickets: List<FallOfWicketEntity>,
    ): MatchHistory {
        val team1 = entity.team1Name
        val team2 = entity.team2Name
        val firstBat = stats.filter { it.team == team1 && it.role == "BAT" }
            .sortedBy { it.battingPosition }
            .map { it.toDomain() }
        val firstBowl = stats.filter { it.team == team2 && it.role == "BOWL" }
            .sortedBy { it.bowlingPosition }
            .map { it.toDomain() }
        val secondBat = stats.filter { it.team == team2 && it.role == "BAT" }
            .sortedBy { it.battingPosition }
            .map { it.toDomain() }
        val secondBowl = stats.filter { it.team == team1 && it.role == "BOWL" }
            .sortedBy { it.bowlingPosition }
            .map { it.toDomain() }
        return entity.toDomain().copy(
            firstInningsBatting = firstBat,
            firstInningsBowling = firstBowl,
            secondInningsBatting = secondBat,
            secondInningsBowling = secondBowl,
            playerImpacts = impacts.map { it.toPlayerImpact() },
            firstInningsPartnerships = partnerships.filter { it.innings == 1 }.map { it.toPartnership() },
            secondInningsPartnerships = partnerships.filter { it.innings == 2 }.map { it.toPartnership() },
            firstInningsFallOfWickets = fallOfWickets.filter { it.innings == 1 }.map { it.toFallOfWicket() },
            secondInningsFallOfWickets = fallOfWickets.filter { it.innings == 2 }.map { it.toFallOfWicket() },
        )
    }

    private fun PlayerImpactEntity.toPlayerImpact(): PlayerImpact = PlayerImpact(
        id = playerId,
        name = name,
        team = team,
        impact = impact,
        summary = summary,
        isJoker = isJoker,
        runs = runs,
        balls = balls,
        fours = fours,
        sixes = sixes,
        wickets = wickets,
        runsConceded = runsConceded,
        oversBowled = oversBowled,
    )

    private fun PartnershipEntity.toPartnership(): Partnership = Partnership(
        batsman1Name = batsman1Name,
        batsman2Name = batsman2Name,
        runs = runs,
        balls = balls,
        batsman1Runs = batsman1Runs,
        batsman2Runs = batsman2Runs,
        isActive = isActive,
    )

    private fun FallOfWicketEntity.toFallOfWicket(): FallOfWicket = FallOfWicket(
        batsmanName = batsmanName,
        runs = runs,
        overs = overs,
        wicketNumber = wicketNumber,
        dismissalType = dismissalType,
        bowlerName = bowlerName,
        fielderName = fielderName,
    )
}
