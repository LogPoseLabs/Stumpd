package com.oreki.stumpd.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.oreki.stumpd.BallFormat
import com.oreki.stumpd.FallOfWicket
import com.oreki.stumpd.MatchHistory
import com.oreki.stumpd.Partnership
import com.oreki.stumpd.PlayerImpact
import com.oreki.stumpd.PlayerMatchStats
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.FallOfWicketEntity
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.local.entity.PartnershipEntity
import com.oreki.stumpd.data.local.entity.PlayerImpactEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.data.util.Constants
import com.oreki.stumpd.data.util.GsonProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing match data
 * Handles all database operations related to matches, stats, and impacts
 */
class MatchRepository(
    private val db: StumpdDb,
    private val context: Context
) {
    private val gson = GsonProvider.get()

    private companion object {
        const val TAG = "MatchRepository"
    }

    /**
     * Saves a complete match with all related stats and impacts
     * @param match The match history to save
     */
    suspend fun saveMatch(match: MatchHistory) = withContext(Dispatchers.IO) {
        try {
            val matchEntity = convertMatchToEntity(match)
            val statsEntities = convertStatsToEntities(match)
            val impactEntities = convertImpactsToEntities(match)

            Log.d(TAG, "Saving match: ${match.team1Name} vs ${match.team2Name}, " +
                    "stats: ${statsEntities.size}, impacts: ${impactEntities.size}, " +
                    "batting1: ${match.firstInningsBatting.size}, bowling1: ${match.firstInningsBowling.size}, " +
                    "batting2: ${match.secondInningsBatting.size}, bowling2: ${match.secondInningsBowling.size}")

            db.withTransaction {
                db.matchDao().insertFullMatch(matchEntity, statsEntities, impactEntities)
            }

            Log.d(TAG, "Saved match: ${match.team1Name} vs ${match.team2Name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save match: ${match.id}", e)
            throw e
        }
    }

    /**
     * Converts MatchHistory domain model to MatchEntity for database storage
     */
    private fun convertMatchToEntity(match: MatchHistory): MatchEntity {
        return MatchEntity(
            id = match.id,
            team1Name = match.team1Name,
            team2Name = match.team2Name,
            jokerPlayerName = match.jokerPlayerName,
            team1CaptainName = match.team1CaptainName,
            team2CaptainName = match.team2CaptainName,
            firstInningsRuns = match.firstInningsRuns,
            firstInningsWickets = match.firstInningsWickets,
            secondInningsRuns = match.secondInningsRuns,
            secondInningsWickets = match.secondInningsWickets,
            winnerTeam = match.winnerTeam,
            winningMargin = match.winningMargin,
            matchDate = match.matchDate,
            groupId = match.groupId,
            groupName = match.groupName,
            shortPitch = match.shortPitch,
            playerOfTheMatchId = match.playerOfTheMatchId,
            playerOfTheMatchName = match.playerOfTheMatchName,
            playerOfTheMatchTeam = match.playerOfTheMatchTeam,
            playerOfTheMatchImpact = match.playerOfTheMatchImpact,
            playerOfTheMatchSummary = match.playerOfTheMatchSummary,
            matchSettingsJson = match.matchSettings?.let { gson.toJson(it) },
            allDeliveriesJson = if (match.allDeliveries.isNotEmpty()) gson.toJson(match.allDeliveries) else null
        )
    }

    /**
     * Converts player match stats to entities for database storage.
     * Merges batting + bowling stats for the same player into a single entity
     * to avoid data overwrite (since primary key is matchId+playerId+team).
     * Also assigns battingPosition/bowlingPosition to preserve list order.
     */
    private fun convertStatsToEntities(match: MatchHistory): List<PlayerMatchStatsEntity> {
        data class StatsKey(val playerId: String, val team: String)

        // Track batting positions (1-indexed; 0 = did not bat)
        val battingPositions = mutableMapOf<StatsKey, Int>()
        match.firstInningsBatting.forEachIndexed { index, stat ->
            battingPositions[StatsKey(stat.id, stat.team)] = index + 1
        }
        match.secondInningsBatting.forEachIndexed { index, stat ->
            battingPositions[StatsKey(stat.id, stat.team)] = index + 1
        }

        // Track bowling positions (1-indexed; 0 = did not bowl)
        val bowlingPositions = mutableMapOf<StatsKey, Int>()
        match.firstInningsBowling.forEachIndexed { index, stat ->
            bowlingPositions[StatsKey(stat.id, stat.team)] = index + 1
        }
        match.secondInningsBowling.forEachIndexed { index, stat ->
            bowlingPositions[StatsKey(stat.id, stat.team)] = index + 1
        }

        // Concatenate all stats and group by (playerId, team) to merge duplicates
        val allStats = match.firstInningsBatting + match.firstInningsBowling +
                match.secondInningsBatting + match.secondInningsBowling
        val grouped = allStats.groupBy { StatsKey(it.id, it.team) }

        return grouped.map { (key, playerStats) ->
            // Merge all entries for this player: take max of numerics, OR of booleans,
            // first non-null of nullable strings
            val merged = playerStats.reduce { acc, stat ->
                acc.copy(
                    runs = maxOf(acc.runs, stat.runs),
                    ballsFaced = maxOf(acc.ballsFaced, stat.ballsFaced),
                    dots = maxOf(acc.dots, stat.dots),
                    singles = maxOf(acc.singles, stat.singles),
                    twos = maxOf(acc.twos, stat.twos),
                    threes = maxOf(acc.threes, stat.threes),
                    fours = maxOf(acc.fours, stat.fours),
                    sixes = maxOf(acc.sixes, stat.sixes),
                    wickets = maxOf(acc.wickets, stat.wickets),
                    runsConceded = maxOf(acc.runsConceded, stat.runsConceded),
                    oversBowled = maxOf(acc.oversBowled, stat.oversBowled),
                    maidenOvers = maxOf(acc.maidenOvers, stat.maidenOvers),
                    isOut = acc.isOut || stat.isOut,
                    isRetired = acc.isRetired || stat.isRetired,
                    isJoker = acc.isJoker || stat.isJoker,
                    catches = maxOf(acc.catches, stat.catches),
                    runOuts = maxOf(acc.runOuts, stat.runOuts),
                    stumpings = maxOf(acc.stumpings, stat.stumpings),
                    dismissalType = acc.dismissalType ?: stat.dismissalType,
                    bowlerName = acc.bowlerName ?: stat.bowlerName,
                    fielderName = acc.fielderName ?: stat.fielderName
                )
            }

            PlayerMatchStatsEntity(
                matchId = match.id,
                playerId = merged.id,
                name = merged.name,
                team = merged.team,
                runs = merged.runs,
                ballsFaced = merged.ballsFaced,
                dots = merged.dots,
                singles = merged.singles,
                twos = merged.twos,
                threes = merged.threes,
                fours = merged.fours,
                sixes = merged.sixes,
                wickets = merged.wickets,
                runsConceded = merged.runsConceded,
                oversBowled = merged.oversBowled,
                maidenOvers = merged.maidenOvers,
                isOut = merged.isOut,
                isRetired = merged.isRetired,
                isJoker = merged.isJoker,
                catches = merged.catches,
                runOuts = merged.runOuts,
                stumpings = merged.stumpings,
                dismissalType = merged.dismissalType,
                bowlerName = merged.bowlerName,
                fielderName = merged.fielderName,
                battingPosition = battingPositions[key] ?: 0,
                bowlingPosition = bowlingPositions[key] ?: 0
            )
        }
    }

    /**
     * Converts player impacts to entities for database storage
     */
    private fun convertImpactsToEntities(match: MatchHistory): List<PlayerImpactEntity> {
        return match.playerImpacts.map { impact ->
            PlayerImpactEntity(
                matchId = match.id,
                playerId = impact.id,
                name = impact.name,
                team = impact.team,
                impact = impact.impact,
                summary = impact.summary,
                isJoker = impact.isJoker,
                runs = impact.runs,
                balls = impact.balls,
                fours = impact.fours,
                sixes = impact.sixes,
                wickets = impact.wickets,
                runsConceded = impact.runsConceded,
                oversBowled = impact.oversBowled
            )
        }
    }

    /**
     * Retrieves all matches, optionally filtered by group
     * @param groupId Optional group ID to filter by
     * @param limit Maximum number of matches to return
     * @return List of match histories
     */
    suspend fun getAllMatches(
        groupId: String? = null,
        limit: Int = Constants.MAX_MATCHES_STORED
    ): List<MatchHistory> = withContext(Dispatchers.IO) {
        try {
            db.matchDao().list(groupId, limit).map { it.toDomain() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all matches", e)
            emptyList()
        }
    }

    /**
     * Deletes a match and its associated data
     * @param matchId The ID of the match to delete
     */
    suspend fun deleteMatch(matchId: String) = withContext(Dispatchers.IO) {
        try {
            db.matchDao().deleteMatch(matchId)
            Log.d(TAG, "Deleted match: $matchId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete match: $matchId", e)
            throw e
        }
    }

    /**
     * Exports matches to a JSON file
     * @param fileName Name of the export file (default includes timestamp)
     * @return Absolute path to the exported file, or null if export failed
     */
    data class CompleteBackup(
        val matches: List<MatchEntity>,
        val matchStats: List<PlayerMatchStatsEntity>,
        val playerImpacts: List<com.oreki.stumpd.data.local.entity.PlayerImpactEntity>,
        val players: List<com.oreki.stumpd.data.local.entity.PlayerEntity>,
        val groups: List<com.oreki.stumpd.data.local.entity.GroupEntity>,
        val groupDefaults: List<com.oreki.stumpd.data.local.entity.GroupDefaultEntity>,
        val groupMembers: List<com.oreki.stumpd.data.local.entity.GroupMemberEntity>,
        val groupLastTeams: List<com.oreki.stumpd.data.local.entity.GroupLastTeamsEntity>,
        val groupUnavailablePlayers: List<com.oreki.stumpd.data.local.entity.GroupUnavailablePlayerEntity> = emptyList(),
        val userPreferences: List<com.oreki.stumpd.data.local.entity.UserPreferencesEntity> = emptyList(),
        val partnerships: List<com.oreki.stumpd.data.local.entity.PartnershipEntity> = emptyList(),
        val fallOfWickets: List<com.oreki.stumpd.data.local.entity.FallOfWicketEntity> = emptyList(),
        val exportDate: Long = System.currentTimeMillis(),
        val version: Int = 3
    )

    suspend fun exportMatches(
        fileName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.getDefault())
        val timestamp = dateFormat.format(java.util.Date())
        val finalFileName = fileName ?: "stumpd_backup_all_$timestamp.json"
        try {
            // Gather all data
            val matches = db.matchDao().list(null, Constants.MAX_MATCHES_EXPORT)
            val matchIds = matches.map { it.id }
            val matchStats = db.matchDao().getStatsForMatches(matchIds)
            val playerImpacts = db.matchDao().getImpactsForMatches(matchIds)
            val players = db.playerDao().list()
            val groups = db.groupDao().getAllGroups()
            val groupDefaults = db.groupDao().getAllGroupDefaults()
            val groupMembers = db.groupDao().getAllGroupMembers()
            val groupLastTeams = db.groupDao().getAllGroupLastTeams()
            val groupUnavailablePlayers = db.groupDao().getAllGroupUnavailablePlayers()
            val userPreferences = db.userPreferencesDao().getAll()
            val partnerships = db.partnershipDao().getAllPartnerships()
            val fallOfWickets = db.fallOfWicketDao().getAllFallOfWickets()

            val backup = CompleteBackup(
                matches = matches,
                matchStats = matchStats,
                playerImpacts = playerImpacts,
                players = players,
                groups = groups,
                groupDefaults = groupDefaults,
                groupMembers = groupMembers,
                groupLastTeams = groupLastTeams,
                groupUnavailablePlayers = groupUnavailablePlayers,
                userPreferences = userPreferences,
                partnerships = partnerships,
                fallOfWickets = fallOfWickets
            )

            // Try to save to Downloads folder for easier access
            val downloadsPath = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )

            val path = if (downloadsPath != null && downloadsPath.exists()) {
                downloadsPath
            } else {
                // Fallback to app's external files directory
                context.getExternalFilesDir(null)
            }

            if (path == null) {
                Log.e(Constants.LOG_TAG_EXPORT, "External storage not available")
                return@withContext null
            }

            val file = java.io.File(path, finalFileName)
            file.writeText(gson.toJson(backup))

            Log.d(Constants.LOG_TAG_EXPORT, "Exported complete backup to ${file.absolutePath}:\n" +
                    "- ${backup.matches.size} matches\n" +
                    "- ${backup.matchStats.size} player stats\n" +
                    "- ${backup.playerImpacts.size} player impacts\n" +
                    "- ${backup.players.size} players\n" +
                    "- ${backup.groups.size} groups\n" +
                    "- ${backup.groupMembers.size} group members\n" +
                    "- ${backup.groupLastTeams.size} last teams")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_EXPORT, "Failed to export matches", e)
            null
        }
    }

    /**
     * Exports data for a specific group only
     * @param groupId ID of the group to export
     * @param groupName Name of the group (for filename)
     * @param fileName Name of the output file (optional)
     * @return Absolute path of the exported file, or null if failed
     */
    suspend fun exportGroupData(
        groupId: String,
        groupName: String? = null,
        fileName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.getDefault())
        val timestamp = dateFormat.format(java.util.Date())
        val sanitizedGroupName = groupName?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: "group"
        val finalFileName = fileName ?: "stumpd_backup_${sanitizedGroupName}_$timestamp.json"
        try {
            // Get only matches for this group
            val matches = db.matchDao().list(groupId, Constants.MAX_MATCHES_EXPORT)
            val matchIds = matches.map { it.id }

            // Get stats and impacts only for these matches
            val matchStats = db.matchDao().getStatsForMatches(matchIds)
            val playerImpacts = db.matchDao().getImpactsForMatches(matchIds)

            // Get only players who participated in these matches
            val playerIds = matchStats.map { it.playerId }.distinct()
            val players = db.playerDao().list().filter { it.id in playerIds }

            // Get group and its related data
            val groups = db.groupDao().getAllGroups().filter { it.id == groupId }
            val groupDefaults = db.groupDao().getAllGroupDefaults().filter { it.groupId == groupId }
            val groupMembers = db.groupDao().getAllGroupMembers().filter { it.groupId == groupId }
            val groupLastTeams = db.groupDao().getAllGroupLastTeams().filter { it.groupId == groupId }
            val groupUnavailablePlayers = db.groupDao().getAllGroupUnavailablePlayers().filter { it.groupId == groupId }

            // User preferences are global, include all for group backup too
            val userPreferences = db.userPreferencesDao().getAll()

            // Get partnerships and fall of wickets for these matches
            val partnerships = db.partnershipDao().getPartnershipsForMatches(matchIds)
            val fallOfWickets = db.fallOfWicketDao().getFallOfWicketsForMatches(matchIds)

            val backup = CompleteBackup(
                matches = matches,
                matchStats = matchStats,
                playerImpacts = playerImpacts,
                players = players,
                groups = groups,
                groupDefaults = groupDefaults,
                groupMembers = groupMembers,
                groupLastTeams = groupLastTeams,
                groupUnavailablePlayers = groupUnavailablePlayers,
                userPreferences = userPreferences,
                partnerships = partnerships,
                fallOfWickets = fallOfWickets
            )

            // Try to save to Downloads folder
            val downloadsPath = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )

            val path = if (downloadsPath != null && downloadsPath.exists()) {
                downloadsPath
            } else {
                context.getExternalFilesDir(null)
            }

            if (path == null) {
                Log.e(Constants.LOG_TAG_EXPORT, "External storage not available")
                return@withContext null
            }

            val file = java.io.File(path, finalFileName)
            file.writeText(gson.toJson(backup))

            Log.d(Constants.LOG_TAG_EXPORT, "Exported group backup to ${file.absolutePath}:\n" +
                    "- ${backup.matches.size} matches\n" +
                    "- ${backup.matchStats.size} player stats\n" +
                    "- ${backup.players.size} players\n" +
                    "- ${backup.groups.size} groups")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_EXPORT, "Failed to export group data", e)
            null
        }
    }


    /**
     * Imports complete backup from a JSON file
     * @param filePath Path to the file to import from
     * @return true if import was successful, false otherwise
     */
    suspend fun importMatches(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                Log.e(Constants.LOG_TAG_IMPORT, "File does not exist: $filePath")
                return@withContext false
            }

            val json = file.readText()

            // Try to parse as complete backup first
            try {
                val backup = gson.fromJson(json, CompleteBackup::class.java)

                db.withTransaction {
                    // Import all data
                    if (backup.players.isNotEmpty()) {
                        db.playerDao().upsert(backup.players)
                    }
                    if (backup.groups.isNotEmpty()) {
                        db.groupDao().insertGroups(backup.groups)
                    }
                    if (backup.groupDefaults.isNotEmpty()) {
                        db.groupDao().insertGroupDefaults(backup.groupDefaults)
                    }
                    if (backup.groupMembers.isNotEmpty()) {
                        db.groupDao().insertGroupMembers(backup.groupMembers)
                    }
                    if (backup.groupLastTeams.isNotEmpty()) {
                        db.groupDao().insertGroupLastTeams(backup.groupLastTeams)
                    }
                    if (backup.groupUnavailablePlayers.isNotEmpty()) {
                        db.groupDao().insertGroupUnavailablePlayers(backup.groupUnavailablePlayers)
                    }
                    if (backup.userPreferences.isNotEmpty()) {
                        db.userPreferencesDao().upsertAll(backup.userPreferences)
                    }
                    if (backup.matches.isNotEmpty()) {
                        backup.matches.forEach { db.matchDao().insertMatch(it) }
                    }
                    if (backup.matchStats.isNotEmpty()) {
                        db.matchDao().insertMatchStats(backup.matchStats)
                    }
                    if (backup.playerImpacts.isNotEmpty()) {
                        db.matchDao().insertPlayerImpacts(backup.playerImpacts)
                    }
                    if (backup.partnerships.isNotEmpty()) {
                        db.partnershipDao().insertPartnerships(backup.partnerships)
                    }
                    if (backup.fallOfWickets.isNotEmpty()) {
                        db.fallOfWicketDao().insertFallOfWickets(backup.fallOfWickets)
                    }
                }

                Log.d(Constants.LOG_TAG_IMPORT, "Imported complete backup: ${backup.matches.size} matches, ${backup.matchStats.size} stats, ${backup.players.size} players, ${backup.groups.size} groups, ${backup.groupMembers.size} group members, ${backup.groupUnavailablePlayers.size} unavailable players, ${backup.userPreferences.size} preferences, ${backup.partnerships.size} partnerships, ${backup.fallOfWickets.size} fall of wickets")
                return@withContext true
            } catch (e: Exception) {
                // Fallback: try to parse as legacy format (just matches)
                Log.d(Constants.LOG_TAG_IMPORT, "Trying legacy format...")
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java, MatchEntity::class.java
                ).type

                val matches: List<MatchEntity> = gson.fromJson(json, type)

                Log.d(Constants.LOG_TAG_IMPORT, "Imported ${matches.size} matches (legacy format with reconstruction)")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_IMPORT, "Failed to import backup", e)
            false
        }
    }

    /**
     * Retrieves a single match by ID (lightweight, without stats)
     * @param id The match ID
     * @return MatchHistory or null if not found
     */
    suspend fun getMatchById(id: String): MatchHistory? = withContext(Dispatchers.IO) {
        try {
            db.matchDao().getById(id)?.toDomain()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get match by ID: $id", e)
            null
        }
    }

    /**
     * Retrieves a match with all its stats and impacts
     * @param id The match ID
     * @return Complete MatchHistory with all details, or null if not found
     */
    suspend fun getMatchWithStats(id: String): MatchHistory? = withContext(Dispatchers.IO) {
        try {
            val matchEntity = db.matchDao().getById(id) ?: return@withContext null
            val stats = db.matchDao().statsForMatch(id)
            val impacts = db.matchDao().impactsForMatch(id)

            // Load partnerships and fall of wickets
            val firstInningsPartnerships = db.partnershipDao().getPartnershipsForInnings(id, 1)
            val secondInningsPartnerships = db.partnershipDao().getPartnershipsForInnings(id, 2)
            val firstInningsFOW = db.fallOfWicketDao().getFallOfWicketsForInnings(id, 1)
            val secondInningsFOW = db.fallOfWicketDao().getFallOfWicketsForInnings(id, 2)

            val team1 = matchEntity.team1Name
            val team2 = matchEntity.team2Name

            // Partition stats by team and activity type, sorted by position
            val firstBat = stats.filter {
                it.team == team1 && hasBattingStats(it)
            }.sortedBy { it.battingPosition }.map { it.toPlayerMatchStats() }
            val firstBowl = stats.filter {
                it.team == team2 && hasBowlingStats(it)
            }.sortedBy { it.bowlingPosition }.map { it.toPlayerMatchStats() }

            val secondBat = stats.filter {
                it.team == team2 && hasBattingStats(it)
            }.sortedBy { it.battingPosition }.map { it.toPlayerMatchStats() }
            val secondBowl = stats.filter {
                it.team == team1 && hasBowlingStats(it)
            }.sortedBy { it.bowlingPosition }.map { it.toPlayerMatchStats() }

            matchEntity.toDomain().copy(
                firstInningsBatting = firstBat,
                firstInningsBowling = firstBowl,
                secondInningsBatting = secondBat,
                secondInningsBowling = secondBowl,
                playerImpacts = impacts.map { it.toPlayerImpact() },
                firstInningsPartnerships = firstInningsPartnerships.map { it.toPartnership() },
                secondInningsPartnerships = secondInningsPartnerships.map { it.toPartnership() },
                firstInningsFallOfWickets = firstInningsFOW.map { it.toFallOfWicket() },
                secondInningsFallOfWickets = secondInningsFOW.map { it.toFallOfWicket() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get match with stats: $id", e)
            null
        }
    }

    /**
     * Checks if a player stats entity has batting activity
     */
    private fun hasBattingStats(stats: PlayerMatchStatsEntity): Boolean {
        return stats.runs > 0 || stats.ballsFaced > 0 || stats.fours > 0 || stats.sixes > 0 || stats.isOut
    }

    /**
     * Checks if a player stats entity has bowling activity
     */
    private fun hasBowlingStats(stats: PlayerMatchStatsEntity): Boolean {
        return stats.wickets > 0 || stats.oversBowled > 0.0 || stats.runsConceded > 0
    }

    /**
     * Converts PlayerMatchStatsEntity to domain model
     */
    private fun PlayerMatchStatsEntity.toPlayerMatchStats(): PlayerMatchStats {
        return PlayerMatchStats(
            id = playerId,
            name = name,
            team = team,
            runs = runs,
            ballsFaced = ballsFaced,
            dots = dots,
            singles = singles,
            twos = twos,
            threes = threes,
            fours = fours,
            sixes = sixes,
            wickets = wickets,
            runsConceded = runsConceded,
            oversBowled = oversBowled,
            maidenOvers = maidenOvers,
            isOut = isOut,
            isRetired = isRetired,
            isJoker = isJoker,
            catches = catches,
            runOuts = runOuts,
            stumpings = stumpings,
            dismissalType = dismissalType,
            bowlerName = bowlerName,
            fielderName = fielderName,
            battingPosition = battingPosition,
            bowlingPosition = bowlingPosition
        )
    }

    /**
     * Converts PlayerImpactEntity to domain model
     */
    private fun PlayerImpactEntity.toPlayerImpact(): PlayerImpact {
        return PlayerImpact(
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
            oversBowled = oversBowled
        )
    }

    /**
     * Converts PartnershipEntity to domain model
     */
    private fun PartnershipEntity.toPartnership(): Partnership {
        return Partnership(
            batsman1Name = batsman1Name,
            batsman2Name = batsman2Name,
            runs = runs,
            balls = balls,
            batsman1Runs = batsman1Runs,
            batsman2Runs = batsman2Runs,
            isActive = isActive
        )
    }

    /**
     * Converts FallOfWicketEntity to domain model
     */
    private fun FallOfWicketEntity.toFallOfWicket(): FallOfWicket {
        return FallOfWicket(
            batsmanName = batsmanName,
            runs = runs,
            overs = overs,
            wicketNumber = wicketNumber,
            dismissalType = dismissalType,
            bowlerName = bowlerName,
            fielderName = fielderName
        )
    }

    /**
     * Retrieves all matches with their stats properly separated by innings
     * @param groupId Optional group ID to filter by
     * @param limit Maximum number of matches to return
     * @return List of matches with stats included
     */
    suspend fun getAllMatchesWithStats(
        groupId: String? = null,
        limit: Int = Constants.MAX_MATCHES_STORED
    ): List<MatchHistory> = withContext(Dispatchers.IO) {
        try {
            val matches = db.matchDao().list(groupId, limit)

            matches.map { matchEntity ->
                val stats = db.matchDao().statsForMatch(matchEntity.id)

                if (stats.isNotEmpty()) {
                    val team1 = matchEntity.team1Name
                    val team2 = matchEntity.team2Name

                    val firstBat = stats.filter {
                        it.team == team1 && hasBattingStats(it)
                    }.sortedBy { it.battingPosition }.map { it.toPlayerMatchStats() }
                    val firstBowl = stats.filter {
                        it.team == team2 && hasBowlingStats(it)
                    }.sortedBy { it.bowlingPosition }.map { it.toPlayerMatchStats() }

                    val secondBat = stats.filter {
                        it.team == team2 && hasBattingStats(it)
                    }.sortedBy { it.battingPosition }.map { it.toPlayerMatchStats() }
                    val secondBowl = stats.filter {
                        it.team == team1 && hasBowlingStats(it)
                    }.sortedBy { it.bowlingPosition }.map { it.toPlayerMatchStats() }

                    matchEntity.toDomain().copy(
                        firstInningsBatting = firstBat,
                        firstInningsBowling = firstBowl,
                        secondInningsBatting = secondBat,
                        secondInningsBowling = secondBowl
                    )
                } else {
                    matchEntity.toDomain()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all matches with stats", e)
            emptyList()
        }
    }

    /**
     * Imports legacy matches from SharedPreferences format
     * Also creates associated groups and players
     * @param filePath Path to the legacy backup file
     * @return true if import was successful, false otherwise
     */
    suspend fun importLegacyMatches(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                Log.e(Constants.LOG_TAG_IMPORT, "Legacy file does not exist: $filePath")
                return@withContext false
            }

            val json = file.readText()
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java, MatchHistory::class.java
            ).type

            val legacyMatches: List<MatchHistory> = gson.fromJson(json, type)

            if (legacyMatches.isEmpty()) {
                Log.w(Constants.LOG_TAG_IMPORT, "No matches found in backup file")
                return@withContext false
            }

            val (uniqueGroups, uniquePlayerNames) = collectGroupsAndPlayers(legacyMatches)

            db.withTransaction {
                createGroupsFromLegacy(uniqueGroups)
                createPlayersFromLegacy(uniquePlayerNames)
                val successCount = importLegacyMatchData(legacyMatches)

                Log.d(Constants.LOG_TAG_IMPORT,
                    "Imported $successCount/${legacyMatches.size} legacy matches, " +
                            "${uniqueGroups.size} groups, ${uniquePlayerNames.size} players"
                )
            }

            true
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG_IMPORT, "Legacy import failed", e)
            false
        }
    }

    /**
     * Collects unique groups and players from legacy matches
     */
    private fun collectGroupsAndPlayers(
        matches: List<MatchHistory>
    ): Pair<Map<String, Pair<String, com.oreki.stumpd.data.local.entity.GroupDefaultEntity?>>, Set<String>> {
        val uniqueGroups = mutableMapOf<String, Pair<String, com.oreki.stumpd.data.local.entity.GroupDefaultEntity?>>()
        val uniquePlayerNames = mutableSetOf<String>()

        matches.forEach { match ->
            // Collect groups
            if (!match.groupId.isNullOrEmpty() && !match.groupName.isNullOrEmpty()) {
                if (!uniqueGroups.containsKey(match.groupId)) {
                    val groupDefaults = match.matchSettings?.let { settings ->
                        com.oreki.stumpd.data.local.entity.GroupDefaultEntity(
                            groupId = match.groupId,
                            groundName = Constants.DEFAULT_GROUND_NAME,
                            format = BallFormat.WHITE_BALL.toString(),
                            shortPitch = match.shortPitch,
                            matchSettingsJson = gson.toJson(settings)
                        )
                    }
                    uniqueGroups[match.groupId] = Pair(match.groupName, groupDefaults)
                }
            }

            // Collect players
            (match.firstInningsBatting + match.firstInningsBowling +
                    match.secondInningsBatting + match.secondInningsBowling).forEach { player ->
                uniquePlayerNames.add(player.name)
            }
        }

        return Pair(uniqueGroups, uniquePlayerNames)
    }

    /**
     * Creates groups from legacy import data
     */
    private suspend fun createGroupsFromLegacy(
        groups: Map<String, Pair<String, com.oreki.stumpd.data.local.entity.GroupDefaultEntity?>>
    ) {
        groups.forEach { (groupId, groupData) ->
            try {
                val (groupName, groupDefaults) = groupData
                val groupEntity = com.oreki.stumpd.data.local.entity.GroupEntity(
                    id = groupId,
                    name = groupName
                )
                db.groupDao().upsertGroup(groupEntity)
                groupDefaults?.let { db.groupDao().upsertDefaults(it) }
                Log.d(Constants.LOG_TAG_IMPORT, "Created group: $groupName")
            } catch (e: Exception) {
                Log.e(Constants.LOG_TAG_IMPORT, "Failed to create group $groupId", e)
            }
        }
    }

    /**
     * Creates players from legacy import data
     */
    private suspend fun createPlayersFromLegacy(playerNames: Set<String>) {
        val existingPlayers = db.playerDao().list()
        playerNames.forEach { playerName ->
            try {
                val playerExists = existingPlayers.any {
                    it.name.equals(playerName, ignoreCase = true)
                }

                if (!playerExists) {
                    val playerEntity = com.oreki.stumpd.data.local.entity.PlayerEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        name = playerName,
                        isJoker = false
                    )
                    db.playerDao().upsert(listOf(playerEntity))
                    Log.d(Constants.LOG_TAG_IMPORT, "Created player: $playerName")
                }
            } catch (e: Exception) {
                Log.e(Constants.LOG_TAG_IMPORT, "Failed to create player $playerName", e)
            }
        }
    }

    /**
     * Imports the actual match data
     * @return Number of successfully imported matches
     */
    private suspend fun importLegacyMatchData(matches: List<MatchHistory>): Int {
        var successCount = 0
        matches.forEach { match ->
            try {
                saveMatch(match)
                successCount++
            } catch (e: Exception) {
                Log.e(Constants.LOG_TAG_IMPORT, "Failed to import match ${match.id}", e)
            }
        }
        return successCount
    }
}
