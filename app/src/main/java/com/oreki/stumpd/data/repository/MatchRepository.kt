package com.oreki.stumpd.data.repository

import com.oreki.stumpd.domain.model.*
import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.FallOfWicketEntity
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.local.entity.PartnershipEntity
import com.oreki.stumpd.data.local.entity.PlayerImpactEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import com.oreki.stumpd.data.local.entity.PlayerEntity
import com.oreki.stumpd.data.local.entity.GroupMemberEntity
import com.oreki.stumpd.data.sync.MatchAdoptionResult
import com.oreki.stumpd.data.sync.MatchGroupAdoption
import com.oreki.stumpd.domain.match.mainMatchDeliveries
import com.oreki.stumpd.data.sync.MergeDestPlayer
import com.oreki.stumpd.data.sync.PlayerMergeMapping
import java.util.UUID
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.data.util.Constants
import com.oreki.stumpd.data.util.GsonProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing match data
 * Handles all database operations related to matches, stats, and impacts
 */
class MatchRepository(
    private val db: StumpdDb,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = GsonProvider.get()
    
    private companion object {
        const val TAG = "MatchRepository"
    }

    /**
     * Saves a complete match with all related stats and impacts
     * @param match The match history to save
     */
    /**
     * @param persistedUpdatedAt If non-null (e.g. from cloud download), stored on the row for sync/conflict resolution; otherwise [System.currentTimeMillis].
     * @param replaceChildren Clear this match's existing stats, impacts, partnerships and
     *   fall-of-wickets rows before inserting, so the saved graph is exactly what was passed.
     *   Off by default: an insert-only save merges, which is what an import or a cloud download
     *   wants (a download cut short by the sync quota must not delete local rows). Callers holding
     *   the complete, authoritative graph — adopt, merge, corrections — pass true, otherwise a row
     *   whose key moved is left behind and read back by [getMatchWithStats].
     */
    suspend fun saveMatch(
        match: MatchHistory,
        persistedUpdatedAt: Long? = null,
        replaceChildren: Boolean = false,
    ): Unit = withContext(ioDispatcher) {
        try {
            val matchEntity = convertMatchToEntity(match, persistedUpdatedAt ?: System.currentTimeMillis())
            val statsEntities = convertStatsToEntities(match)
            val impactEntities = convertImpactsToEntities(match)
            val partnershipEntities = convertPartnershipsToEntities(match)
            val fowEntities = convertFallOfWicketsToEntities(match)
            
            Log.d(TAG, "Saving match: ${match.team1Name} vs ${match.team2Name}, " +
                    "stats: ${statsEntities.size}, impacts: ${impactEntities.size}, " +
                    "partnerships: ${partnershipEntities.size}, fow: ${fowEntities.size}, " +
                    "batting1: ${match.firstInningsBatting.size}, bowling1: ${match.firstInningsBowling.size}, " +
                    "batting2: ${match.secondInningsBatting.size}, bowling2: ${match.secondInningsBowling.size}")

            db.withTransaction {
                if (replaceChildren) {
                    db.partnershipDao().deleteForMatch(match.id)
                    db.fallOfWicketDao().deleteForMatch(match.id)
                    db.matchDao().replaceFullMatch(matchEntity, statsEntities, impactEntities)
                } else {
                    db.matchDao().insertFullMatch(matchEntity, statsEntities, impactEntities)
                }
                if (partnershipEntities.isNotEmpty()) {
                    db.partnershipDao().insertPartnerships(partnershipEntities)
                }
                if (fowEntities.isNotEmpty()) {
                    db.fallOfWicketDao().insertFallOfWickets(fowEntities)
                }
            }
            
            Log.d(TAG, "Saved match: ${match.team1Name} vs ${match.team2Name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save match: ${match.id}", e)
            throw e
        }
    }

    /**
     * Saves a match as the complete truth for its id, stamping a fresh `updatedAt` so the change
     * is picked up for upload. The entry point for anything that *changes* an existing match.
     */
    suspend fun replaceMatchGraph(match: MatchHistory) {
        saveMatch(match, persistedUpdatedAt = null, replaceChildren = true)
    }

    /**
     * Carries a renamed player through every match they played in.
     *
     * Renaming used to update `player_match_stats.name` and nothing else — which left the old
     * name in the bowler and fielder columns, both partnership names, the fall-of-wickets rows,
     * all three names on every delivery, the joker, both captains, the Player of the Match and
     * the impact rows. The scorecard then showed a batter dismissed by a bowler who, by name, no
     * longer existed, and head-to-head and partnership records silently split one player in two.
     *
     * The traversal is the same pure rewrite that adopting and merging a match already use, so
     * there is one inventory of "everywhere a player appears" rather than two that drift. Each
     * match is written back as a whole, which stamps `updatedAt` and so re-uploads it.
     *
     * Returns the number of matches changed.
     */
    suspend fun renamePlayerAcrossMatches(playerId: String, newName: String): Int =
        withContext(ioDispatcher) {
            if (newName.isBlank()) return@withContext 0
            val matchIds = db.matchDao().matchIdsNaming(playerId, newName)
            var changed = 0
            matchIds.forEach { matchId ->
                runCatching {
                    val match = getMatchWithStats(matchId) ?: return@runCatching
                    // The name to replace comes from the match, not from the players table: that
                    // way a rename made while this match was in progress — or one that an older
                    // version of the app half-applied — is still repaired by renaming again.
                    val stale = (
                        match.firstInningsBatting + match.firstInningsBowling +
                            match.secondInningsBatting + match.secondInningsBowling +
                            match.team1Players + match.team2Players
                        )
                        .filter { it.id == playerId }
                        .map { it.name }
                        .filter { it.isNotBlank() && !it.equals(newName, ignoreCase = true) }
                        .distinct()
                    if (stale.isEmpty()) return@runCatching
                    val renamed = MatchGroupAdoption.remapNames(match) { name ->
                        // Null means "leave this one alone" — every other player in the match.
                        if (stale.any { it.equals(name, ignoreCase = true) })
                            MergeDestPlayer(id = playerId, name = newName)
                        else null
                    }
                    replaceMatchGraph(renamed)
                    changed++
                }.onFailure { Log.e(TAG, "Couldn't carry the rename into match $matchId", it) }
            }
            if (matchIds.isNotEmpty()) {
                Log.d(TAG, "Renamed to $newName across $changed of ${matchIds.size} match(es)")
            }
            changed
        }

    /**
     * Converts partnerships from MatchHistory to PartnershipEntity list
     */
    private fun convertPartnershipsToEntities(match: MatchHistory): List<PartnershipEntity> {
        val entities = mutableListOf<PartnershipEntity>()
        match.firstInningsPartnerships.forEachIndexed { index, p ->
            entities.add(PartnershipEntity(
                matchId = match.id,
                innings = 1,
                partnershipNumber = index + 1,
                batsman1Name = p.batsman1Name,
                batsman2Name = p.batsman2Name,
                runs = p.runs,
                balls = p.balls,
                batsman1Runs = p.batsman1Runs,
                batsman2Runs = p.batsman2Runs,
                isActive = p.isActive
            ))
        }
        match.secondInningsPartnerships.forEachIndexed { index, p ->
            entities.add(PartnershipEntity(
                matchId = match.id,
                innings = 2,
                partnershipNumber = index + 1,
                batsman1Name = p.batsman1Name,
                batsman2Name = p.batsman2Name,
                runs = p.runs,
                balls = p.balls,
                batsman1Runs = p.batsman1Runs,
                batsman2Runs = p.batsman2Runs,
                isActive = p.isActive
            ))
        }
        return entities
    }

    /**
     * Converts fall of wickets from MatchHistory to FallOfWicketEntity list
     */
    private fun convertFallOfWicketsToEntities(match: MatchHistory): List<FallOfWicketEntity> {
        val entities = mutableListOf<FallOfWicketEntity>()
        match.firstInningsFallOfWickets.forEach { fow ->
            entities.add(FallOfWicketEntity(
                matchId = match.id,
                innings = 1,
                wicketNumber = fow.wicketNumber,
                batsmanName = fow.batsmanName,
                runs = fow.runs,
                overs = fow.overs,
                dismissalType = fow.dismissalType,
                bowlerName = fow.bowlerName,
                fielderName = fow.fielderName
            ))
        }
        match.secondInningsFallOfWickets.forEach { fow ->
            entities.add(FallOfWicketEntity(
                matchId = match.id,
                innings = 2,
                wicketNumber = fow.wicketNumber,
                batsmanName = fow.batsmanName,
                runs = fow.runs,
                overs = fow.overs,
                dismissalType = fow.dismissalType,
                bowlerName = fow.bowlerName,
                fielderName = fow.fielderName
            ))
        }
        return entities
    }

    /**
     * Converts MatchHistory domain model to MatchEntity for database storage
     */
    private fun convertMatchToEntity(match: MatchHistory, updatedAt: Long): MatchEntity {
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
            allDeliveriesJson = if (match.allDeliveries.isNotEmpty()) gson.toJson(match.allDeliveries) else null,
            superOverWinner = match.superOverWinner,
            superOversJson = if (match.superOvers.isNotEmpty()) gson.toJson(match.superOvers) else null,
            tournamentId = match.tournamentId,
            tournamentFixtureId = match.tournamentFixtureId,
            team1Id = match.team1Id,
            team2Id = match.team2Id,
            updatedAt = updatedAt
        )
    }

    /**
     * Converts player match stats to entities for database storage.
     * Each list maps directly to role-based rows: batting lists → "BAT", bowling lists → "BOWL".
     *
     * Legacy compat: an old backup can arrive with the two roles merged into one list — a batting
     * entry carrying bowling figures, or the reverse — and those figures would otherwise be lost.
     * So for a match that has *no* list for the other role at all, the missing rows are
     * synthesised from the ones present.
     *
     * That gate matters, and it used to be per player: "this player isn't in a bowling list, so
     * rescue their bowling figures". Which is right for a legacy import and catastrophic for a
     * correction — reassigning an over leaves the previous bowler out of the bowling list *on
     * purpose*, and the per-player rescue read that as missing data and put the row back, with the
     * pre-correction figures. The next correction then added to those, so a bowler who really sent
     * down three balls ended up with nine. A match that has role-separated lists is telling us
     * exactly who bowled; absence from them is a statement, not a gap.
     */
    private fun convertStatsToEntities(match: MatchHistory): List<PlayerMatchStatsEntity> {
        fun toEntity(stat: PlayerMatchStats, matchId: String, role: String, position: Int): PlayerMatchStatsEntity {
            return PlayerMatchStatsEntity(
                matchId = matchId,
                playerId = stat.id,
                name = stat.name,
                team = stat.team,
                role = role,
                runs = stat.runs,
                ballsFaced = stat.ballsFaced,
                dots = stat.dots,
                singles = stat.singles,
                twos = stat.twos,
                threes = stat.threes,
                fours = stat.fours,
                sixes = stat.sixes,
                wickets = stat.wickets,
                runsConceded = stat.runsConceded,
                oversBowled = stat.oversBowled,
                maidenOvers = stat.maidenOvers,
                isOut = stat.isOut,
                isRetired = stat.isRetired,
                isJoker = stat.isJoker,
                catches = stat.catches,
                runOuts = stat.runOuts,
                stumpings = stat.stumpings,
                dismissalType = stat.dismissalType,
                bowlerName = stat.bowlerName,
                fielderName = stat.fielderName,
                battingPosition = if (role == "BAT") position else 0,
                bowlingPosition = if (role == "BOWL") position else 0
            )
        }

        fun hasBowlingActivity(stat: PlayerMatchStats): Boolean =
            stat.wickets > 0 || stat.oversBowled > 0.0 || stat.runsConceded > 0

        fun hasBattingActivity(stat: PlayerMatchStats): Boolean =
            stat.runs > 0 || stat.ballsFaced > 0 || stat.fours > 0 || stat.sixes > 0 || stat.isOut || stat.isRetired

        // Whether this match keeps the two roles in separate lists at all. Only a match missing a
        // whole side of that split is a legacy merge worth rescuing.
        val hasBowlingLists =
            match.firstInningsBowling.isNotEmpty() || match.secondInningsBowling.isNotEmpty()
        val hasBattingLists =
            match.firstInningsBatting.isNotEmpty() || match.secondInningsBatting.isNotEmpty()

        val entities = mutableListOf<PlayerMatchStatsEntity>()

        // Batting rows (1-indexed positions)
        match.firstInningsBatting.forEachIndexed { index, stat ->
            entities.add(toEntity(stat, match.id, "BAT", index + 1))
            if (!hasBowlingLists && hasBowlingActivity(stat)) {
                entities.add(toEntity(stat, match.id, "BOWL", 0))
            }
        }
        match.secondInningsBatting.forEachIndexed { index, stat ->
            entities.add(toEntity(stat, match.id, "BAT", index + 1))
            if (!hasBowlingLists && hasBowlingActivity(stat)) {
                entities.add(toEntity(stat, match.id, "BOWL", 0))
            }
        }

        // Bowling rows (1-indexed positions)
        match.firstInningsBowling.forEachIndexed { index, stat ->
            entities.add(toEntity(stat, match.id, "BOWL", index + 1))
            if (!hasBattingLists && hasBattingActivity(stat)) {
                entities.add(toEntity(stat, match.id, "BAT", 0))
            }
        }
        match.secondInningsBowling.forEachIndexed { index, stat ->
            entities.add(toEntity(stat, match.id, "BOWL", index + 1))
            if (!hasBattingLists && hasBattingActivity(stat)) {
                entities.add(toEntity(stat, match.id, "BAT", 0))
            }
        }

        return entities
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
    ): List<MatchHistory> = withContext(ioDispatcher) {
        try {
            db.matchDao().list(groupId, limit).map { it.toDomain() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all matches", e)
            emptyList()
        }
    }

    /**
     * Squad size per (matchId, team), for showing a wicket margin the chasing side could actually
     * have lost. Cheap enough to load for a whole history screen.
     */
    suspend fun squadSizesByMatchAndTeam(): Map<Pair<String, String>, Int> =
        withContext(ioDispatcher) {
            try {
                db.matchDao().squadSizes().associate { (it.matchId to it.team) to it.squadSize }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load squad sizes", e)
                emptyMap()
            }
        }

    /**
     * Deletes a match and its associated data
     * @param matchId The ID of the match to delete
     */
    suspend fun deleteMatch(matchId: String) = withContext(ioDispatcher) {
        try {
            // The four child tables have no foreign keys, so deleting the header alone left their
            // rows behind — and `playerCareerSummaries()` / `squadSizes()` read
            // `player_match_stats` without joining `matches`, so a deleted match went on counting
            // toward career totals and wicket margins.
            db.withTransaction {
                db.matchDao().deleteStatsForMatch(matchId)
                db.matchDao().deleteImpactsForMatch(matchId)
                db.partnershipDao().deleteForMatch(matchId)
                db.fallOfWicketDao().deleteForMatch(matchId)
                db.matchCorrectionLogDao().deleteForMatch(matchId)
                db.matchDao().deleteMatch(matchId)
            }
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
    ): String? = withContext(ioDispatcher) {
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
    ): String? = withContext(ioDispatcher) {
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
    suspend fun importMatches(filePath: String): Boolean = withContext(ioDispatcher) {
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
                        val nowPlayers = System.currentTimeMillis()
                        db.playerDao().upsert(backup.players.map { p ->
                            if (p.updatedAt == 0L) p.copy(updatedAt = nowPlayers) else p
                        })
                    }
                    if (backup.groups.isNotEmpty()) {
                        val nowGroups = System.currentTimeMillis()
                        db.groupDao().insertGroups(backup.groups.map { g ->
                            if (g.updatedAt == 0L) g.copy(updatedAt = nowGroups) else g
                        })
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
                        // Only keep rows for players who are actually members, otherwise
                        // Available = members - unavailable goes negative on the Groups screen.
                        val memberKeys = db.groupDao().getAllGroupMembers()
                            .map { it.groupId to it.playerId }
                            .toSet()
                        db.groupDao().insertGroupUnavailablePlayers(
                            backup.groupUnavailablePlayers.filter {
                                (it.groupId to it.playerId) in memberKeys
                            }
                        )
                    }
                    if (backup.userPreferences.isNotEmpty()) {
                        db.userPreferencesDao().upsertAll(backup.userPreferences)
                    }
                    if (backup.matches.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        backup.matches.forEach { m ->
                            val entity = if (m.updatedAt == 0L) m.copy(updatedAt = now) else m
                            db.matchDao().insertMatch(entity)
                        }
                    }
                    if (backup.matchStats.isNotEmpty()) {
                        db.matchDao().insertStats(backup.matchStats)
                    }
                    if (backup.playerImpacts.isNotEmpty()) {
                        db.matchDao().insertImpacts(backup.playerImpacts)
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
    suspend fun getMatchById(id: String): MatchHistory? = withContext(ioDispatcher) {
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
    suspend fun getMatchWithStats(id: String): MatchHistory? = withContext(ioDispatcher) {
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

            // Partition stats by team and role, sorted by position
            val firstBat = stats.filter { 
                it.team == team1 && it.role == "BAT"
            }.sortedBy { it.battingPosition }.map { it.toPlayerMatchStats() }
            val firstBowl = stats.filter { 
                it.team == team2 && it.role == "BOWL"
            }.sortedBy { it.bowlingPosition }.map { it.toPlayerMatchStats() }
            
            val secondBat = stats.filter { 
                it.team == team2 && it.role == "BAT"
            }.sortedBy { it.battingPosition }.map { it.toPlayerMatchStats() }
            val secondBowl = stats.filter { 
                it.team == team1 && it.role == "BOWL"
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
     * Converts PlayerMatchStatsEntity to domain model
     */
    private fun PlayerMatchStatsEntity.toPlayerMatchStats(): PlayerMatchStats {
        return PlayerMatchStats(
            id = playerId,
            name = name,
            team = team,
            role = role,
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
    ): List<MatchHistory> = withContext(ioDispatcher) {
        try {
            val matches = db.matchDao().list(groupId, limit)

            matches.map { matchEntity ->
                val stats = db.matchDao().statsForMatch(matchEntity.id)
                val firstInningsPartnerships = db.partnershipDao().getPartnershipsForInnings(matchEntity.id, 1)
                val secondInningsPartnerships = db.partnershipDao().getPartnershipsForInnings(matchEntity.id, 2)
                val firstInningsFOW = db.fallOfWicketDao().getFallOfWicketsForInnings(matchEntity.id, 1)
                val secondInningsFOW = db.fallOfWicketDao().getFallOfWicketsForInnings(matchEntity.id, 2)

                if (stats.isNotEmpty()) {
                    val team1 = matchEntity.team1Name
                    val team2 = matchEntity.team2Name

                    val firstBat = stats.filter {
                        it.team == team1 && it.role == "BAT"
                    }.sortedBy { it.battingPosition }.map { it.toPlayerMatchStats() }
                    val firstBowl = stats.filter {
                        it.team == team2 && it.role == "BOWL"
                    }.sortedBy { it.bowlingPosition }.map { it.toPlayerMatchStats() }

                    val secondBat = stats.filter {
                        it.team == team2 && it.role == "BAT"
                    }.sortedBy { it.battingPosition }.map { it.toPlayerMatchStats() }
                    val secondBowl = stats.filter {
                        it.team == team1 && it.role == "BOWL"
                    }.sortedBy { it.bowlingPosition }.map { it.toPlayerMatchStats() }

                    matchEntity.toDomain().copy(
                        firstInningsBatting = firstBat,
                        firstInningsBowling = firstBowl,
                        secondInningsBatting = secondBat,
                        secondInningsBowling = secondBowl,
                        firstInningsPartnerships = firstInningsPartnerships.map { it.toPartnership() },
                        secondInningsPartnerships = secondInningsPartnerships.map { it.toPartnership() },
                        firstInningsFallOfWickets = firstInningsFOW.map { it.toFallOfWicket() },
                        secondInningsFallOfWickets = secondInningsFOW.map { it.toFallOfWicket() }
                    )
                } else {
                    matchEntity.toDomain().copy(
                        firstInningsPartnerships = firstInningsPartnerships.map { it.toPartnership() },
                        secondInningsPartnerships = secondInningsPartnerships.map { it.toPartnership() },
                        firstInningsFallOfWickets = firstInningsFOW.map { it.toFallOfWicket() },
                        secondInningsFallOfWickets = secondInningsFOW.map { it.toFallOfWicket() }
                    )
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
    suspend fun importLegacyMatches(filePath: String): Boolean = withContext(ioDispatcher) {
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
                    name = groupName,
                    updatedAt = System.currentTimeMillis()
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
                        isJoker = false,
                        updatedAt = System.currentTimeMillis()
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

    /**
     * Recalculate maidens and wicket counts from delivery data for all matches.
     * Fixes two historical bugs:
     *   1. Maidens were never recorded (ballsInOver was reset before the check)
     *   2. Wickets on the 6th ball of an over may have been missed in the innings count
     * Updates the local Room DB; the next sync will push corrected data to Firestore.
     */
    suspend fun recalculateDerivedStats(): Int = withContext(ioDispatcher) {
        var fixedCount = 0
        try {
            val allMatches = db.matchDao().list(null, 9999)
            
            for (matchEntity in allMatches) {
                // The match proper only: a scoreless super over would otherwise be counted as a
                // maiden here and written back, across every match on the phone.
                val deliveries = matchEntity.toDomain().allDeliveries.mainMatchDeliveries()
                if (deliveries.isEmpty()) continue
                
                val stats = db.matchDao().statsForMatch(matchEntity.id).toMutableList()
                if (stats.isEmpty()) continue
                
                var changed = false
                
                // --- Recalculate maidens per bowler ---
                // Group deliveries by (innings, over, bowlerName)
                // A maiden = a completed over (6 legal balls) with 0 runs conceded
                val byInningsOver = deliveries.groupBy { Pair(it.inning, it.over) }
                val bowlerMaidens = mutableMapOf<String, Int>() // "bowlerName_team_BOWL" -> maiden count
                
                for ((_, overDeliveries) in byInningsOver) {
                    // Count legal deliveries (not wides or no-balls)
                    val legalBalls = overDeliveries.filter { d ->
                        val o = d.outcome.uppercase()
                        !o.startsWith("WD") && !o.contains("WIDE") &&
                        !o.startsWith("NB") && !o.contains("NO BALL") && !o.contains("NOBALL")
                    }
                    if (legalBalls.size < 6) continue // incomplete over
                    
                    // Check if any runs were conceded in this over (including extras)
                    val totalRunsInOver = overDeliveries.sumOf { it.runs }
                    if (totalRunsInOver == 0) {
                        val bowlerName = overDeliveries.firstOrNull()?.bowlerName ?: continue
                        val key = bowlerName.lowercase().trim()
                        bowlerMaidens[key] = (bowlerMaidens[key] ?: 0) + 1
                    }
                }
                
                // Update bowling stats with recalculated maidens
                for (i in stats.indices) {
                    val stat = stats[i]
                    if (stat.role != "BOWL") continue
                    val key = stat.name.lowercase().trim()
                    val newMaidens = bowlerMaidens[key] ?: 0
                    if (newMaidens != stat.maidenOvers) {
                        stats[i] = stat.copy(maidenOvers = newMaidens)
                        changed = true
                    }
                }
                
                // --- Recalculate wicket counts per innings ---
                val team1 = matchEntity.team1Name
                val team2 = matchEntity.team2Name
                val firstInningsWickets = stats.count { it.team == team1 && it.role == "BAT" && it.isOut }
                val secondInningsWickets = stats.count { it.team == team2 && it.role == "BAT" && it.isOut }
                
                var matchChanged = false
                if (firstInningsWickets != matchEntity.firstInningsWickets ||
                    secondInningsWickets != matchEntity.secondInningsWickets) {
                    val updatedMatch = matchEntity.copy(
                        firstInningsWickets = firstInningsWickets,
                        secondInningsWickets = secondInningsWickets,
                        updatedAt = System.currentTimeMillis()
                    )
                    db.matchDao().update(updatedMatch)
                    matchChanged = true
                }
                
                // Persist updated stats
                if (changed) {
                    db.withTransaction {
                        stats.forEach { stat ->
                            db.matchDao().updateStat(stat)
                        }
                        // Sync pending-ness is keyed on the *match* row's updatedAt, so a repair
                        // that only rewrites stat rows would never reach the cloud without this.
                        if (!matchChanged) {
                            db.matchDao().update(
                                matchEntity.copy(updatedAt = System.currentTimeMillis())
                            )
                        }
                    }
                }
                
                if (changed || matchChanged) {
                    fixedCount++
                    Log.d(TAG, "Recalculated stats for match ${matchEntity.id}: " +
                        "maidens=${bowlerMaidens.values.sum()}, " +
                        "wickets=${firstInningsWickets}/${secondInningsWickets}")
                }
            }
            
            Log.d(TAG, "Recalculation complete: $fixedCount matches updated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recalculate derived stats", e)
        }
        fixedCount
    }

    /**
     * Reassigns matches (e.g. scored on another phone / wrong group) into [destinationGroupId].
     * Only the local group owner should call this. Player ids are remapped to the destination
     * group's roster by player name; missing names are added to the group automatically.
     *
     * @param matchIds null = all matches whose groupId is not already [destinationGroupId]
     */
    suspend fun adoptMatchesIntoOwnedGroup(
        destinationGroupId: String,
        matchIds: List<String>? = null,
    ): MatchAdoptionResult = withContext(ioDispatcher) {
        val group = db.groupDao().getGroupById(destinationGroupId)
            ?: return@withContext MatchAdoptionResult(0, 0, 0, listOf("Group not found"))
        if (!group.isOwner) {
            return@withContext MatchAdoptionResult(
                0,
                0,
                0,
                listOf("Only the destination group owner can adopt matches"),
            )
        }

        val targets = if (matchIds != null) {
            matchIds.mapNotNull { db.matchDao().getById(it)?.id }
        } else {
            db.matchDao().list(null, Constants.MAX_MATCHES_STORED)
                .filter { it.groupId != destinationGroupId }
                .map { it.id }
        }

        if (targets.isEmpty()) {
            return@withContext MatchAdoptionResult(0, 0, 0, emptyList())
        }

        var adopted = 0
        var skipped = 0
        var playersAdded = 0
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()

        for (matchId in targets) {
            try {
                val match = getMatchWithStats(matchId)
                if (match == null) {
                    skipped++
                } else {
                    val (idMap, added) = ensureGroupPlayerIdsForMatch(destinationGroupId, match)
                    playersAdded += added
                    val remapped = MatchGroupAdoption.remapMatchIntoGroup(
                        match = match,
                        destinationGroupId = destinationGroupId,
                        destinationGroupName = group.name,
                        playerIdByNormalizedName = idMap,
                    )
                    saveMatch(remapped, persistedUpdatedAt = now, replaceChildren = true)
                    adopted++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to adopt match $matchId into $destinationGroupId", e)
                errors.add("$matchId: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        MatchAdoptionResult(adopted, skipped, playersAdded, errors)
    }

    /**
     * Writes selected foreign matches into an owned group using an explicit player map.
     * New match ids are generated so the friend's cloud document is not overwritten.
     */
    suspend fun mergeMatchesIntoOwnedGroup(
        destinationGroupId: String,
        matches: List<MatchHistory>,
        mappings: List<PlayerMergeMapping>,
    ): MatchAdoptionResult = withContext(ioDispatcher) {
        val group = db.groupDao().getGroupById(destinationGroupId)
            ?: return@withContext MatchAdoptionResult(0, 0, 0, listOf("Group not found"))
        if (!group.isOwner) {
            return@withContext MatchAdoptionResult(
                0,
                0,
                0,
                listOf("Only the destination group owner can merge matches"),
            )
        }
        if (matches.isEmpty()) {
            return@withContext MatchAdoptionResult(0, 0, 0, emptyList())
        }

        val roster = db.groupDao().members(destinationGroupId)
        val rosterById = roster.associateBy { it.id }
        val destByNormalized = mutableMapOf<String, MergeDestPlayer>()
        var playersAdded = 0
        val now = System.currentTimeMillis()

        for (mapping in mappings) {
            val key = MatchGroupAdoption.normalizePlayerName(mapping.sourceNormalizedName)
            if (key.isEmpty()) continue
            if (mapping.createNew) {
                val sourceName = mapping.sourceNormalizedName
                val displayName = matches
                    .flatMap { MatchGroupAdoption.collectPlayerNames(it) }
                    .firstOrNull { MatchGroupAdoption.normalizePlayerName(it) == key }
                    ?.trim()
                    ?: sourceName
                val newId = UUID.randomUUID().toString()
                db.playerDao().upsert(
                    listOf(
                        PlayerEntity(
                            id = newId,
                            name = displayName,
                            isJoker = false,
                            updatedAt = now,
                        ),
                    ),
                )
                db.groupDao().upsertMembers(listOf(GroupMemberEntity(destinationGroupId, newId)))
                destByNormalized[key] = MergeDestPlayer(newId, displayName)
                playersAdded++
            } else {
                val destId = mapping.destinationPlayerId
                    ?: return@withContext MatchAdoptionResult(
                        0,
                        0,
                        0,
                        listOf("Missing destination player for $key"),
                    )
                val destPlayer = rosterById[destId]
                    ?: db.playerDao().list().firstOrNull { it.id == destId }
                    ?: return@withContext MatchAdoptionResult(
                        0,
                        0,
                        0,
                        listOf("Destination player not found: $destId"),
                    )
                if (rosterById[destId] == null) {
                    db.groupDao().upsertMembers(listOf(GroupMemberEntity(destinationGroupId, destId)))
                }
                destByNormalized[key] = MergeDestPlayer(destPlayer.id, destPlayer.name)
            }
        }

        db.groupDao().upsertGroup(group.copy(updatedAt = now))

        var adopted = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        for (match in matches) {
            try {
                val remapped = MatchGroupAdoption.remapMatchForMerge(
                    match = match,
                    destinationGroupId = destinationGroupId,
                    destinationGroupName = group.name,
                    newMatchId = UUID.randomUUID().toString(),
                    destByNormalizedSourceName = destByNormalized,
                )
                saveMatch(remapped, persistedUpdatedAt = now, replaceChildren = true)
                adopted++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to merge match ${match.id}", e)
                errors.add("${match.team1Name} vs ${match.team2Name}: ${e.message ?: e.javaClass.simpleName}")
                skipped++
            }
        }
        MatchAdoptionResult(adopted, skipped, playersAdded, errors)
    }

    private suspend fun ensureGroupPlayerIdsForMatch(
        groupId: String,
        match: MatchHistory,
    ): Pair<Map<String, String>, Int> {
        val roster = db.groupDao().members(groupId)
        val idByName = MatchGroupAdoption.buildPlayerIdByNormalizedName(
            roster.map { it.id to it.name },
        ).toMutableMap()
        var added = 0
        val neededNames = MatchGroupAdoption.collectPlayerNames(match)
        for (name in neededNames) {
            val key = MatchGroupAdoption.normalizePlayerName(name)
            if (key.isEmpty() || key in idByName) continue
            val newId = java.util.UUID.randomUUID().toString()
            val isJoker = match.jokerPlayerName?.equals(name, ignoreCase = true) == true
            db.playerDao().upsert(
                listOf(
                    PlayerEntity(
                        id = newId,
                        name = name.trim(),
                        isJoker = isJoker,
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )
            db.groupDao().upsertMembers(listOf(GroupMemberEntity(groupId = groupId, playerId = newId)))
            idByName[key] = newId
            added++
        }
        return idByName to added
    }
}

