package com.oreki.stumpd.data.sync.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.data.sync.FirebaseConfig
import kotlinx.coroutines.tasks.await

/** Match payload plus root document [FirebaseConfig.FIELD_UPDATED_AT] for conflict resolution. */
data class CloudMatchDownload(
    val match: MatchHistory,
    val rootUpdatedAt: Long?
)

/** Root Firestore match document fields used for upload conflict policy. */
data class MatchRootMeta(
    val exists: Boolean,
    val ownerId: String?,
    val groupId: String?,
    val updatedAt: Long?,
)

/**
 * Number of deliveries the cloud copy of a match holds. Stored on the match root document so
 * an upload can tell whether the deliveries subcollection needs a cleanup sweep without
 * reading every delivery doc.
 */
private const val FIELD_DELIVERY_COUNT = "deliveryCount"

/** Same idea as [FIELD_DELIVERY_COUNT], for the fall-of-wickets subcollection. */
private const val FIELD_WICKET_COUNT = "wicketCount"

/** Same idea again, for the partnerships subcollection. */
private const val FIELD_PARTNERSHIP_COUNT = "partnershipCount"

/**
 * Complete Firebase Firestore data access layer for matches
 * Handles all match data including stats, partnerships, fall of wickets, and deliveries
 * 
 * DATA IS GLOBAL - All users can see all matches
 * ownerId field tracks who created the match for edit permissions
 */
class FirestoreMatchDao(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    /**
     * Upload a complete match with ALL related data
     * @param ownerId The user who created/owns this match
     * @param documentUpdatedAt Logical version timestamp (e.g. Room [com.oreki.stumpd.data.local.entity.MatchEntity.updatedAt]); defaults to now
     */
    suspend fun uploadCompleteMatch(ownerId: String, match: MatchHistory, documentUpdatedAt: Long? = null) {
        val ts = documentUpdatedAt ?: System.currentTimeMillis()
        uploadMatchMetadata(ownerId, match, ts)
        uploadMatchStats(match, ts)
        uploadPartnerships(match, ts)
        uploadFallOfWickets(match, ts)
        uploadDeliveries(match, ts)
        uploadPlayerImpacts(match, ts)
    }

    /**
     * Download a complete match with root doc [FirebaseConfig.FIELD_UPDATED_AT] for conflict resolution.
     */
    suspend fun downloadCompleteMatchWithSync(matchId: String): CloudMatchDownload? {
        val (matchMetadata, rootUpdatedAt) = downloadMatchRoot(matchId)
        if (matchMetadata == null) return null
        val full = assembleFullMatch(matchId, matchMetadata)
        return CloudMatchDownload(full, rootUpdatedAt)
    }
    
    /**
     * Download a complete match with ALL related data
     * @param matchId The match ID to download
     */
    suspend fun downloadCompleteMatch(matchId: String): MatchHistory? {
        return downloadCompleteMatchWithSync(matchId)?.match
    }

    private suspend fun assembleFullMatch(matchId: String, matchMetadata: MatchHistory): MatchHistory {
        val stats = downloadMatchStats(matchId)
        val (firstInningsPartnerships, secondInningsPartnerships) = downloadPartnerships(matchId)
        val (firstInningsFow, secondInningsFow) = downloadFallOfWickets(matchId)
        val deliveries = downloadDeliveries(matchId)
        val impacts = downloadPlayerImpacts(matchId)

        val partnershipCount = firstInningsPartnerships.size + secondInningsPartnerships.size
        android.util.Log.d("FirestoreMatchDao", "Downloaded match ${matchId}: " +
                "stats=${stats.size}, partnerships=${partnershipCount}, impacts=${impacts.size}, " +
                "team1=${matchMetadata.team1Name}, team2=${matchMetadata.team2Name}")

        if (stats.isNotEmpty()) {
            android.util.Log.d("FirestoreMatchDao", "Stats teams: ${stats.map { it.team }.distinct()}")
        }

        val hasRoles = stats.any { it.role.isNotEmpty() }

        val firstInningsBatting: List<PlayerMatchStats>
        val firstInningsBowling: List<PlayerMatchStats>
        val secondInningsBatting: List<PlayerMatchStats>
        val secondInningsBowling: List<PlayerMatchStats>

        if (hasRoles) {
            firstInningsBatting = stats.filter { it.team == matchMetadata.team1Name && it.role == "BAT" }
                .sortedBy { it.battingPosition }
            firstInningsBowling = stats.filter { it.team == matchMetadata.team2Name && it.role == "BOWL" }
                .sortedBy { it.bowlingPosition }
            secondInningsBatting = stats.filter { it.team == matchMetadata.team2Name && it.role == "BAT" }
                .sortedBy { it.battingPosition }
            secondInningsBowling = stats.filter { it.team == matchMetadata.team1Name && it.role == "BOWL" }
                .sortedBy { it.bowlingPosition }
        } else {
            firstInningsBatting = stats.filter { it.team == matchMetadata.team1Name }
            firstInningsBowling = stats.filter { it.team == matchMetadata.team2Name }
            secondInningsBatting = stats.filter { it.team == matchMetadata.team2Name }
            secondInningsBowling = stats.filter { it.team == matchMetadata.team1Name }
        }

        return matchMetadata.copy(
            firstInningsBatting = firstInningsBatting,
            firstInningsBowling = firstInningsBowling,
            secondInningsBatting = secondInningsBatting,
            secondInningsBowling = secondInningsBowling,
            firstInningsPartnerships = firstInningsPartnerships,
            secondInningsPartnerships = secondInningsPartnerships,
            firstInningsFallOfWickets = firstInningsFow,
            secondInningsFallOfWickets = secondInningsFow,
            allDeliveries = deliveries,
            playerImpacts = impacts,
            team1Players = firstInningsBatting + secondInningsBowling,
            team2Players = firstInningsBowling + secondInningsBatting
        )
    }
    
    /**
     * Download a complete match - legacy method for compatibility
     */
    suspend fun downloadCompleteMatch(userId: String, matchId: String): MatchHistory? {
        return downloadCompleteMatch(matchId)
    }
    
    /**
     * Download all matches (GLOBAL - returns all matches from all users)
     */
    /**
     * Get all match document IDs from Firestore
     */
    suspend fun getMatchDocIds(): List<String> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .get()
            .await()
        return querySnapshot.documents.map { it.id }
    }

    suspend fun getMatchRootMeta(matchId: String): MatchRootMeta {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            return MatchRootMeta(exists = false, ownerId = null, groupId = null, updatedAt = null)
        }
        return MatchRootMeta(
            exists = true,
            ownerId = snapshot.getString(FirebaseConfig.FIELD_OWNER_ID),
            groupId = snapshot.getString("groupId"),
            updatedAt = snapshot.getLong(FirebaseConfig.FIELD_UPDATED_AT),
        )
    }

    /**
     * Match ids in the given groups updated after [updatedAfterMillis].
     * Requires a Firestore composite index on (groupId, updatedAt) when using collection-group queries per group.
     */
    suspend fun listMatchIdsInGroupsUpdatedAfter(
        groupIds: List<String>,
        updatedAfterMillis: Long,
    ): List<String> {
        if (groupIds.isEmpty()) return emptyList()
        val ids = linkedSetOf<String>()
        for (groupId in groupIds) {
            val snap = firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .whereEqualTo("groupId", groupId)
                .whereGreaterThan(FirebaseConfig.FIELD_UPDATED_AT, updatedAfterMillis)
                .get()
                .await()
            ids.addAll(snap.documents.map { it.id })
        }
        return ids.toList()
    }

    suspend fun listMatchIdsInGroup(groupId: String): List<String> {
        val snap = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .whereEqualTo("groupId", groupId)
            .get()
            .await()
        return snap.documents.map { it.id }
    }

    suspend fun downloadAllMatches(): List<MatchHistory> {
        return getMatchDocIds().mapNotNull { matchId ->
            try {
                downloadCompleteMatch(matchId)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Download all matches - legacy method for compatibility
     */
    suspend fun downloadAllMatches(userId: String): List<MatchHistory> {
        return downloadAllMatches()
    }
    
    /**
     * Delete a match and all related data
     */
    suspend fun deleteMatch(matchId: String) {
        val batch = firestore.batch()
        
        // Delete main match
        val matchRef = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)
        batch.delete(matchRef)
        
        // Delete all subcollections (stats, partnerships, etc.)
        // Note: Firestore doesn't auto-delete subcollections, but for a free tier,
        // we'll leave them orphaned or delete manually if needed
        
        batch.commit().await()
    }
    
    /**
     * Delete a match - legacy method for compatibility
     */
    suspend fun deleteMatch(userId: String, matchId: String) {
        deleteMatch(matchId)
    }
    
    // ========== Private Helper Methods ==========
    
    private suspend fun uploadMatchMetadata(ownerId: String, match: MatchHistory, documentUpdatedAt: Long) {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(match.id)
        
        // Serialize match settings to JSON if available
        val matchSettingsJson = match.matchSettings?.let { Gson().toJson(it) }
        
        val data = mapOf(
            "id" to match.id,
            "team1Name" to match.team1Name,
            "team2Name" to match.team2Name,
            "jokerPlayerName" to match.jokerPlayerName,
            "team1CaptainName" to match.team1CaptainName,
            "team2CaptainName" to match.team2CaptainName,
            "firstInningsRuns" to match.firstInningsRuns,
            "firstInningsWickets" to match.firstInningsWickets,
            "secondInningsRuns" to match.secondInningsRuns,
            "secondInningsWickets" to match.secondInningsWickets,
            "winnerTeam" to match.winnerTeam,
            "winningMargin" to match.winningMargin,
            "matchDate" to match.matchDate,
            "groupId" to match.groupId,
            "groupName" to match.groupName,
            "shortPitch" to match.shortPitch,
            "playerOfTheMatchId" to match.playerOfTheMatchId,
            "playerOfTheMatchName" to match.playerOfTheMatchName,
            "playerOfTheMatchTeam" to match.playerOfTheMatchTeam,
            "playerOfTheMatchImpact" to match.playerOfTheMatchImpact,
            "playerOfTheMatchSummary" to match.playerOfTheMatchSummary,
            "matchSettingsJson" to matchSettingsJson,
            // The eliminator. Uploaded as a name plus a small JSON list rather than as extra
            // innings anywhere, because the deliveries subcollection already keys each ball on its
            // innings and carries super-over balls as-is.
            "superOverWinner" to match.superOverWinner,
            "superOversJson" to
                match.superOvers.takeIf { it.isNotEmpty() }?.let { Gson().toJson(it) },
            FirebaseConfig.FIELD_OWNER_ID to ownerId, // Track who created the match
            FirebaseConfig.FIELD_UPDATED_AT to documentUpdatedAt,
            FirebaseConfig.FIELD_CREATED_AT to match.matchDate,
            // Lets uploadDeliveries skip reading the whole deliveries subcollection just to
            // find out whether a cleanup sweep is needed. See uploadDeliveries.
            FIELD_DELIVERY_COUNT to match.allDeliveries.size,
            FIELD_WICKET_COUNT to (match.firstInningsFallOfWickets.size + match.secondInningsFallOfWickets.size),
            FIELD_PARTNERSHIP_COUNT to
                (match.firstInningsPartnerships.size + match.secondInningsPartnerships.size)
        )
        
        docRef.set(data, SetOptions.merge()).await()
    }
    
    private suspend fun uploadMatchStats(match: MatchHistory, documentUpdatedAt: Long) {
        val allStats = match.firstInningsBatting + match.firstInningsBowling +
                match.secondInningsBatting + match.secondInningsBowling
        
        val batch = firestore.batch()
        
        allStats.forEach { stat ->
            // Deterministic doc ID using role instead of index (no orphan docs)
            val docRef = firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .collection("stats")
                .document("${stat.id}_${stat.team}_${stat.role}")
            
            val data = mapOf(
                "playerId" to stat.id,
                "name" to stat.name,
                "team" to stat.team,
                "role" to stat.role,
                "runs" to stat.runs,
                "ballsFaced" to stat.ballsFaced,
                "dots" to stat.dots,
                "singles" to stat.singles,
                "twos" to stat.twos,
                "threes" to stat.threes,
                "fours" to stat.fours,
                "sixes" to stat.sixes,
                "wickets" to stat.wickets,
                "runsConceded" to stat.runsConceded,
                "oversBowled" to stat.oversBowled,
                "maidenOvers" to stat.maidenOvers,
                "isOut" to stat.isOut,
                "isRetired" to stat.isRetired,
                "isJoker" to stat.isJoker,
                "catches" to stat.catches,
                "runOuts" to stat.runOuts,
                "stumpings" to stat.stumpings,
                "dismissalType" to stat.dismissalType,
                "bowlerName" to stat.bowlerName,
                "fielderName" to stat.fielderName,
                "battingPosition" to stat.battingPosition,
                "bowlingPosition" to stat.bowlingPosition,
                FirebaseConfig.FIELD_UPDATED_AT to documentUpdatedAt
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    private suspend fun uploadPartnerships(match: MatchHistory, documentUpdatedAt: Long) {
        val firstInnings = match.firstInningsPartnerships.mapIndexed { i, p -> Triple(1, i, p) }
        val secondInnings = match.secondInningsPartnerships.mapIndexed { i, p -> Triple(2, i, p) }
        val allPartnerships = firstInnings + secondInnings
        
        if (allPartnerships.isEmpty()) return

        // Doc ids are innings-scoped (partnership_{innings}_{number}), the same scheme the
        // fall-of-wickets docs use, and swept the same way. They used to be a single index across
        // both innings, which meant a match whose *first* innings gained or lost a stand silently
        // re-keyed every second-innings document — exactly what a correction does. Sweeping when
        // the cloud holds more than we are about to write also clears the leftovers of the old
        // scheme, and of a match shortened by undo.
        val cloudPartnershipCount = runCatching {
            firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .get()
                .await()
                .getLong(FIELD_PARTNERSHIP_COUNT)
                ?.toInt()
        }.getOrNull()

        if (cloudPartnershipCount == null || cloudPartnershipCount > allPartnerships.size) {
            val existingDocs = firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .collection("partnerships")
                .get()
                .await()
            existingDocs.documents.chunked(FirebaseConfig.MAX_BATCH_OPERATIONS).forEach { chunk ->
                val deleteBatch = firestore.batch()
                chunk.forEach { deleteBatch.delete(it.reference) }
                deleteBatch.commit().await()
            }
        }

        val batch = firestore.batch()
        
        allPartnerships.forEachIndexed { index, (innings, partnershipNum, partnership) ->
            val docRef = firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .collection("partnerships")
                .document("partnership_${innings}_${partnershipNum + 1}")
            
            val data = mapOf(
                "batsman1Name" to partnership.batsman1Name,
                "batsman2Name" to partnership.batsman2Name,
                "runs" to partnership.runs,
                "balls" to partnership.balls,
                "batsman1Runs" to partnership.batsman1Runs,
                "batsman2Runs" to partnership.batsman2Runs,
                "isActive" to partnership.isActive,
                "innings" to innings,
                "partnershipNumber" to partnershipNum + 1,
                "index" to index,
                FirebaseConfig.FIELD_UPDATED_AT to documentUpdatedAt
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    private suspend fun uploadFallOfWickets(match: MatchHistory, documentUpdatedAt: Long) {
        val allWickets = match.firstInningsFallOfWickets.map { 1 to it } +
                match.secondInningsFallOfWickets.map { 2 to it }
        
        if (allWickets.isEmpty()) return
        
        // Doc ids are innings-scoped and deterministic (wicket_{innings}_{wicketNumber}) and are
        // written with set(merge), so re-uploading the same wickets needs no cleanup. Only sweep
        // when the cloud holds more wickets than we are about to write - leftovers from the old
        // wicket_{wicketNumber} scheme (where innings 2 overwrote innings 1), or a match that
        // shrank via undo. Reading the whole subcollection every time cost a read per wicket.
        val cloudWicketCount = runCatching {
            firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .get()
                .await()
                .getLong(FIELD_WICKET_COUNT)
                ?.toInt()
        }.getOrNull()

        if (cloudWicketCount == null || cloudWicketCount > allWickets.size) {
            val existingDocs = firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .collection("fall_of_wickets")
                .get()
                .await()
            if (existingDocs.documents.isNotEmpty()) {
                existingDocs.documents.chunked(FirebaseConfig.MAX_BATCH_OPERATIONS).forEach { chunk ->
                    val deleteBatch = firestore.batch()
                    chunk.forEach { deleteBatch.delete(it.reference) }
                    deleteBatch.commit().await()
                }
            }
        }

        val batch = firestore.batch()
        
        allWickets.forEach { (innings, wicket) ->
            val docRef = firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .collection("fall_of_wickets")
                .document("wicket_${innings}_${wicket.wicketNumber}")
            
            val data = mapOf(
                "batsmanName" to wicket.batsmanName,
                "runs" to wicket.runs,
                "overs" to wicket.overs,
                "innings" to innings,
                "wicketNumber" to wicket.wicketNumber,
                "dismissalType" to wicket.dismissalType,
                "bowlerName" to wicket.bowlerName,
                "fielderName" to wicket.fielderName,
                FirebaseConfig.FIELD_UPDATED_AT to documentUpdatedAt
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    private suspend fun uploadDeliveries(match: MatchHistory, documentUpdatedAt: Long) {
        if (match.allDeliveries.isEmpty()) return

        // Doc ids are deterministic (delivery_{inning}_{globalIndex}) and written with
        // set(merge), so re-uploading the same deliveries is idempotent and needs no cleanup.
        // A sweep is only required when the cloud holds MORE deliveries than we're about to
        // write - either leftovers from the old colliding id scheme
        // (delivery_{inning}_{over}_{ballInOver}) or a match that shrank via undo. Reading the
        // whole subcollection on every upload cost one read per delivery and was the single
        // biggest consumer of the daily quota.
        val cloudDeliveryCount = runCatching {
            firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .get()
                .await()
                .getLong(FIELD_DELIVERY_COUNT)
                ?.toInt()
        }.getOrNull()

        val needsSweep = cloudDeliveryCount == null || cloudDeliveryCount > match.allDeliveries.size
        if (needsSweep) {
            val existingDocs = firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .collection("deliveries")
                .get()
                .await()
            if (existingDocs.documents.isNotEmpty()) {
                existingDocs.documents.chunked(500).forEach { chunk ->
                    val deleteBatch = firestore.batch()
                    chunk.forEach { deleteBatch.delete(it.reference) }
                    deleteBatch.commit().await()
                }
            }
        }

        // For large delivery lists, use batches of 500 (Firestore limit)
        // Use global index for unique doc IDs (NB/WD share same ballInOver as the reattempt)
        var globalIndex = 0
        match.allDeliveries.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            
            chunk.forEach { delivery ->
                val docRef = firestore
                    .collection(FirebaseConfig.COLLECTION_MATCHES)
                    .document(match.id)
                    .collection("deliveries")
                    .document("delivery_${delivery.inning}_${globalIndex}")
                
                val data = mapOf(
                    "inning" to delivery.inning,
                    "over" to delivery.over,
                    "ballInOver" to delivery.ballInOver,
                    "outcome" to delivery.outcome,
                    "highlight" to delivery.highlight,
                    "strikerName" to delivery.strikerName,
                    "nonStrikerName" to delivery.nonStrikerName,
                    "bowlerName" to delivery.bowlerName,
                    "runs" to delivery.runs,
                    FirebaseConfig.FIELD_UPDATED_AT to documentUpdatedAt
                )
                
                batch.set(docRef, data, SetOptions.merge())
                globalIndex++
            }
            
            batch.commit().await()
        }
    }
    
    private suspend fun uploadPlayerImpacts(match: MatchHistory, documentUpdatedAt: Long) {
        if (match.playerImpacts.isEmpty()) return
        
        val batch = firestore.batch()
        
        match.playerImpacts.forEach { impact ->
            val docRef = firestore
                .collection(FirebaseConfig.COLLECTION_MATCHES)
                .document(match.id)
                .collection("impacts")
                .document(impact.id)
            
            val data = mapOf(
                "id" to impact.id,
                "name" to impact.name,
                "team" to impact.team,
                "impact" to impact.impact,
                "summary" to impact.summary,
                "isJoker" to impact.isJoker,
                "runs" to impact.runs,
                "balls" to impact.balls,
                "fours" to impact.fours,
                "sixes" to impact.sixes,
                "wickets" to impact.wickets,
                "runsConceded" to impact.runsConceded,
                "oversBowled" to impact.oversBowled,
                FirebaseConfig.FIELD_UPDATED_AT to documentUpdatedAt
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    // ========== Download Methods ==========
    
    private suspend fun downloadMatchRoot(matchId: String): Pair<MatchHistory?, Long?> {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)

        val snapshot = docRef.get().await()
        return if (snapshot.exists()) {
            firestoreToMatch(snapshot) to snapshot.getLong(FirebaseConfig.FIELD_UPDATED_AT)
        } else {
            Pair(null, null)
        }
    }
    
    private suspend fun downloadMatchStats(matchId: String): List<PlayerMatchStats> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)
            .collection("stats")
            .get()
            .await()
        
        return querySnapshot.documents.mapNotNull { doc ->
            try {
                firestoreToPlayerMatchStats(doc)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private suspend fun downloadPartnerships(matchId: String): Pair<List<Partnership>, List<Partnership>> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)
            .collection("partnerships")
            .get()
            .await()
        
        val rows = querySnapshot.documents.mapNotNull { doc ->
            try {
                InningsRow(
                    innings = doc.getLong("innings")?.toInt(),
                    order = doc.getLong("partnershipNumber")?.toInt() ?: 0,
                    value = Partnership(
                        batsman1Name = doc.getString("batsman1Name") ?: "",
                        batsman2Name = doc.getString("batsman2Name") ?: "",
                        runs = doc.getLong("runs")?.toInt() ?: 0,
                        balls = doc.getLong("balls")?.toInt() ?: 0,
                        batsman1Runs = doc.getLong("batsman1Runs")?.toInt() ?: 0,
                        batsman2Runs = doc.getLong("batsman2Runs")?.toInt() ?: 0,
                        isActive = doc.getBoolean("isActive") ?: false
                    )
                )
            } catch (e: Exception) {
                null
            }
        }
        return splitByInnings(rows)
    }
    
    private data class InningsRow<T>(val innings: Int?, val order: Int, val value: T)
    
    /**
     * Split rows into (first innings, second innings) using the persisted innings marker.
     *
     * [Partnership] and [FallOfWicket] carry no innings of their own, so it has to come off
     * the document. Docs written before innings was persisted have none; those fall back to
     * halving the list, which is the best guess available for legacy data.
     */
    private fun <T> splitByInnings(rows: List<InningsRow<T>>): Pair<List<T>, List<T>> {
        if (rows.isEmpty()) return emptyList<T>() to emptyList()
        if (rows.any { it.innings == null }) {
            val ordered = rows.sortedBy { it.order }.map { it.value }
            val halfPoint = ordered.size / 2
            return ordered.take(halfPoint) to ordered.drop(halfPoint)
        }
        val ordered = rows.sortedWith(compareBy({ it.innings }, { it.order }))
        return ordered.filter { it.innings == 1 }.map { it.value } to
                ordered.filter { it.innings == 2 }.map { it.value }
    }
    
    private suspend fun downloadFallOfWickets(matchId: String): Pair<List<FallOfWicket>, List<FallOfWicket>> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)
            .collection("fall_of_wickets")
            .get()
            .await()
        
        val rows = querySnapshot.documents.mapNotNull { doc ->
            try {
                val wicketNumber = doc.getLong("wicketNumber")?.toInt() ?: 0
                InningsRow(
                    innings = doc.getLong("innings")?.toInt(),
                    order = wicketNumber,
                    value = FallOfWicket(
                        batsmanName = doc.getString("batsmanName") ?: "",
                        runs = doc.getLong("runs")?.toInt() ?: 0,
                        overs = doc.getDouble("overs") ?: 0.0,
                        wicketNumber = wicketNumber,
                        dismissalType = doc.getString("dismissalType"),
                        bowlerName = doc.getString("bowlerName"),
                        fielderName = doc.getString("fielderName")
                    )
                )
            } catch (e: Exception) {
                null
            }
        }
        return splitByInnings(rows)
    }
    
    private suspend fun downloadDeliveries(matchId: String): List<DeliveryUI> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)
            .collection("deliveries")
            .get()
            .await()
        
        return querySnapshot.documents.mapNotNull { doc ->
            try {
                DeliveryUI(
                    inning = doc.getLong("inning")?.toInt() ?: 0,
                    over = doc.getLong("over")?.toInt() ?: 0,
                    ballInOver = doc.getLong("ballInOver")?.toInt() ?: 0,
                    outcome = doc.getString("outcome") ?: "",
                    highlight = doc.getBoolean("highlight") ?: false,
                    strikerName = doc.getString("strikerName") ?: "",
                    nonStrikerName = doc.getString("nonStrikerName") ?: "",
                    bowlerName = doc.getString("bowlerName") ?: "",
                    runs = doc.getLong("runs")?.toInt() ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.inning }, { it.over }, { it.ballInOver }))
    }
    
    private suspend fun downloadPlayerImpacts(matchId: String): List<PlayerImpact> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)
            .collection("impacts")
            .get()
            .await()
        
        return querySnapshot.documents.mapNotNull { doc ->
            try {
                PlayerImpact(
                    id = doc.getString("id") ?: "",
                    name = doc.getString("name") ?: "",
                    team = doc.getString("team") ?: "",
                    impact = doc.getDouble("impact") ?: 0.0,
                    summary = doc.getString("summary") ?: "",
                    isJoker = doc.getBoolean("isJoker") ?: false,
                    runs = doc.getLong("runs")?.toInt() ?: 0,
                    balls = doc.getLong("balls")?.toInt() ?: 0,
                    fours = doc.getLong("fours")?.toInt() ?: 0,
                    sixes = doc.getLong("sixes")?.toInt() ?: 0,
                    wickets = doc.getLong("wickets")?.toInt() ?: 0,
                    runsConceded = doc.getLong("runsConceded")?.toInt() ?: 0,
                    oversBowled = doc.getDouble("oversBowled") ?: 0.0
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun firestoreToMatch(doc: DocumentSnapshot): MatchHistory {
        // Deserialize match settings from JSON if available
        val matchSettings = try {
            doc.getString("matchSettingsJson")?.let { Gson().fromJson(it, MatchSettings::class.java) }
        } catch (_: Exception) { null }
        
        return MatchHistory(
            id = doc.getString("id") ?: doc.id,
            team1Name = doc.getString("team1Name") ?: "",
            team2Name = doc.getString("team2Name") ?: "",
            jokerPlayerName = doc.getString("jokerPlayerName"),
            team1CaptainName = doc.getString("team1CaptainName"),
            team2CaptainName = doc.getString("team2CaptainName"),
            firstInningsRuns = doc.getLong("firstInningsRuns")?.toInt() ?: 0,
            firstInningsWickets = doc.getLong("firstInningsWickets")?.toInt() ?: 0,
            secondInningsRuns = doc.getLong("secondInningsRuns")?.toInt() ?: 0,
            secondInningsWickets = doc.getLong("secondInningsWickets")?.toInt() ?: 0,
            winnerTeam = doc.getString("winnerTeam") ?: "",
            winningMargin = doc.getString("winningMargin") ?: "",
            matchDate = doc.getLong("matchDate") ?: System.currentTimeMillis(),
            matchSettings = matchSettings,
            groupId = doc.getString("groupId"),
            groupName = doc.getString("groupName"),
            shortPitch = doc.getBoolean("shortPitch") ?: false,
            playerOfTheMatchId = doc.getString("playerOfTheMatchId"),
            playerOfTheMatchName = doc.getString("playerOfTheMatchName"),
            playerOfTheMatchTeam = doc.getString("playerOfTheMatchTeam"),
            playerOfTheMatchImpact = doc.getDouble("playerOfTheMatchImpact"),
            playerOfTheMatchSummary = doc.getString("playerOfTheMatchSummary"),
            // Read back, or a re-download would quietly revert a super-over win to a tie the next
            // time anything saved this match.
            superOverWinner = doc.getString("superOverWinner"),
            superOvers = try {
                doc.getString("superOversJson")
                    ?.let { Gson().fromJson(it, Array<SuperOverInnings>::class.java).toList() }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        )
    }
    
    private fun firestoreToPlayerMatchStats(doc: DocumentSnapshot): PlayerMatchStats {
        return PlayerMatchStats(
            id = doc.getString("playerId") ?: "",
            name = doc.getString("name") ?: "",
            team = doc.getString("team") ?: "",
            runs = doc.getLong("runs")?.toInt() ?: 0,
            ballsFaced = doc.getLong("ballsFaced")?.toInt() ?: 0,
            dots = doc.getLong("dots")?.toInt() ?: 0,
            singles = doc.getLong("singles")?.toInt() ?: 0,
            twos = doc.getLong("twos")?.toInt() ?: 0,
            threes = doc.getLong("threes")?.toInt() ?: 0,
            fours = doc.getLong("fours")?.toInt() ?: 0,
            sixes = doc.getLong("sixes")?.toInt() ?: 0,
            wickets = doc.getLong("wickets")?.toInt() ?: 0,
            runsConceded = doc.getLong("runsConceded")?.toInt() ?: 0,
            oversBowled = doc.getDouble("oversBowled") ?: 0.0,
            maidenOvers = doc.getLong("maidenOvers")?.toInt() ?: 0,
            isOut = doc.getBoolean("isOut") ?: false,
            isRetired = doc.getBoolean("isRetired") ?: false,
            isJoker = doc.getBoolean("isJoker") ?: false,
            catches = doc.getLong("catches")?.toInt() ?: 0,
            runOuts = doc.getLong("runOuts")?.toInt() ?: 0,
            stumpings = doc.getLong("stumpings")?.toInt() ?: 0,
            dismissalType = doc.getString("dismissalType"),
            bowlerName = doc.getString("bowlerName"),
            fielderName = doc.getString("fielderName"),
            battingPosition = doc.getLong("battingPosition")?.toInt() ?: 0,
            bowlingPosition = doc.getLong("bowlingPosition")?.toInt() ?: 0,
            role = doc.getString("role") ?: "" // empty for legacy Firestore data
        )
    }
}
