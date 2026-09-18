package com.oreki.stumpd.data.sync

import com.oreki.stumpd.data.local.entity.InProgressMatchEntity

/**
 * Converts a Firestore document data map to [InProgressMatchEntity].
 * Shared by [com.oreki.stumpd.data.sync.firebase.FirestoreInProgressMatchDao] and
 * [com.oreki.stumpd.data.sync.realtime.RealTimeMatchListener].
 *
 * When a key is **absent** (null in the map), JSON-oriented fields use safe defaults so
 * Gson / list parsing does not see empty strings: [InProgressMatchEntity.matchSettingsJson]
 * defaults to `"{}"`, and player id/name JSON strings default to `"[]"`.
 */
fun inProgressMatchEntityFromFirestoreData(
    data: Map<String, Any?>,
    documentIdFallback: String,
): InProgressMatchEntity {
    return InProgressMatchEntity(
        matchId = data.fireString("matchId") ?: documentIdFallback,
        team1Name = data.fireString("team1Name").orEmpty(),
        team2Name = data.fireString("team2Name").orEmpty(),
        jokerName = data.fireString("jokerName").orEmpty(),
        groupId = data.fireString("groupId"),
        groupName = data.fireString("groupName"),
        tossWinner = data.fireString("tossWinner"),
        tossChoice = data.fireString("tossChoice"),
        matchSettingsJson = data.fireString("matchSettingsJson") ?: "{}",
        team1PlayerIds = data.fireString("team1PlayerIds") ?: "[]",
        team2PlayerIds = data.fireString("team2PlayerIds") ?: "[]",
        team1PlayerNames = data.fireString("team1PlayerNames") ?: "[]",
        team2PlayerNames = data.fireString("team2PlayerNames") ?: "[]",
        currentInnings = data.fireInt("currentInnings", 1),
        currentOver = data.fireInt("currentOver", 0),
        ballsInOver = data.fireInt("ballsInOver", 0),
        totalWickets = data.fireInt("totalWickets", 0),
        team1PlayersJson = data.fireString("team1PlayersJson") ?: "[]",
        team2PlayersJson = data.fireString("team2PlayersJson") ?: "[]",
        strikerIndex = data.fireIntOrNull("strikerIndex"),
        nonStrikerIndex = data.fireIntOrNull("nonStrikerIndex"),
        bowlerIndex = data.fireIntOrNull("bowlerIndex"),
        firstInningsRuns = data.fireInt("firstInningsRuns", 0),
        firstInningsWickets = data.fireInt("firstInningsWickets", 0),
        firstInningsOvers = data.fireInt("firstInningsOvers", 0),
        firstInningsBalls = data.fireInt("firstInningsBalls", 0),
        totalExtras = data.fireInt("totalExtras", 0),
        calculatedTotalRuns = data.fireInt("calculatedTotalRuns", 0),
        completedBattersInnings1Json = data.fireString("completedBattersInnings1Json"),
        completedBattersInnings2Json = data.fireString("completedBattersInnings2Json"),
        completedBowlersInnings1Json = data.fireString("completedBowlersInnings1Json"),
        completedBowlersInnings2Json = data.fireString("completedBowlersInnings2Json"),
        firstInningsBattingPlayersJson = data.fireString("firstInningsBattingPlayersJson"),
        firstInningsBowlingPlayersJson = data.fireString("firstInningsBowlingPlayersJson"),
        jokerOutInCurrentInnings = data.fireBoolean("jokerOutInCurrentInnings", false),
        jokerBallsBowledInnings1 = data.fireInt("jokerBallsBowledInnings1", 0),
        jokerBallsBowledInnings2 = data.fireInt("jokerBallsBowledInnings2", 0),
        powerplayRunsInnings1 = data.fireInt("powerplayRunsInnings1", 0),
        powerplayRunsInnings2 = data.fireInt("powerplayRunsInnings2", 0),
        powerplayDoublingDoneInnings1 = data.fireBoolean("powerplayDoublingDoneInnings1", false),
        powerplayDoublingDoneInnings2 = data.fireBoolean("powerplayDoublingDoneInnings2", false),
        allDeliveriesJson = data.fireString("allDeliveriesJson"),
        deliveryHistoryJson = data.fireString("deliveryHistoryJson"),
        partnershipsStateJson = data.fireString("partnershipsStateJson"),
        superOverStateJson = data.fireString("superOverStateJson"),
        lastSavedAt = data.fireLong("lastSavedAt") ?: System.currentTimeMillis(),
        startedAt = data.fireLong("startedAt") ?: System.currentTimeMillis(),
    )
}

/**
 * Firestore document fields for [com.oreki.stumpd.data.sync.firebase.FirestoreInProgressMatchDao.uploadInProgressMatch].
 */
fun inProgressMatchEntityToFirestoreUploadMap(
    match: InProgressMatchEntity,
    ownerId: String,
    updatedAtMillis: Long = System.currentTimeMillis(),
): Map<String, Any?> = mapOf(
    "matchId" to match.matchId,
    "team1Name" to match.team1Name,
    "team2Name" to match.team2Name,
    "jokerName" to match.jokerName,
    "groupId" to match.groupId,
    "groupName" to match.groupName,
    "tossWinner" to match.tossWinner,
    "tossChoice" to match.tossChoice,
    "matchSettingsJson" to match.matchSettingsJson,
    "team1PlayerIds" to match.team1PlayerIds,
    "team2PlayerIds" to match.team2PlayerIds,
    "team1PlayerNames" to match.team1PlayerNames,
    "team2PlayerNames" to match.team2PlayerNames,
    "currentInnings" to match.currentInnings,
    "currentOver" to match.currentOver,
    "ballsInOver" to match.ballsInOver,
    "totalWickets" to match.totalWickets,
    "team1PlayersJson" to match.team1PlayersJson,
    "team2PlayersJson" to match.team2PlayersJson,
    "strikerIndex" to match.strikerIndex,
    "nonStrikerIndex" to match.nonStrikerIndex,
    "bowlerIndex" to match.bowlerIndex,
    "firstInningsRuns" to match.firstInningsRuns,
    "firstInningsWickets" to match.firstInningsWickets,
    "firstInningsOvers" to match.firstInningsOvers,
    "firstInningsBalls" to match.firstInningsBalls,
    "totalExtras" to match.totalExtras,
    "calculatedTotalRuns" to match.calculatedTotalRuns,
    "completedBattersInnings1Json" to match.completedBattersInnings1Json,
    "completedBattersInnings2Json" to match.completedBattersInnings2Json,
    "completedBowlersInnings1Json" to match.completedBowlersInnings1Json,
    "completedBowlersInnings2Json" to match.completedBowlersInnings2Json,
    "firstInningsBattingPlayersJson" to match.firstInningsBattingPlayersJson,
    "firstInningsBowlingPlayersJson" to match.firstInningsBowlingPlayersJson,
    "jokerOutInCurrentInnings" to match.jokerOutInCurrentInnings,
    "jokerBallsBowledInnings1" to match.jokerBallsBowledInnings1,
    "jokerBallsBowledInnings2" to match.jokerBallsBowledInnings2,
    "powerplayRunsInnings1" to match.powerplayRunsInnings1,
    "powerplayRunsInnings2" to match.powerplayRunsInnings2,
    "powerplayDoublingDoneInnings1" to match.powerplayDoublingDoneInnings1,
    "powerplayDoublingDoneInnings2" to match.powerplayDoublingDoneInnings2,
    "allDeliveriesJson" to match.allDeliveriesJson,
    "deliveryHistoryJson" to match.deliveryHistoryJson,
    "partnershipsStateJson" to match.partnershipsStateJson,
    "superOverStateJson" to match.superOverStateJson,
    "lastSavedAt" to match.lastSavedAt,
    "startedAt" to match.startedAt,
    FirebaseConfig.FIELD_OWNER_ID to ownerId,
    FirebaseConfig.FIELD_UPDATED_AT to updatedAtMillis,
)

private fun Map<String, Any?>.fireString(key: String): String? =
    this[key] as? String

private fun Map<String, Any?>.fireInt(key: String, default: Int): Int {
    val v = this[key] ?: return default
    return when (v) {
        is Long -> v.toInt()
        is Int -> v
        is Double -> v.toInt()
        else -> default
    }
}

private fun Map<String, Any?>.fireIntOrNull(key: String): Int? {
    val v = this[key] ?: return null
    return when (v) {
        is Long -> v.toInt()
        is Int -> v
        is Double -> v.toInt()
        else -> null
    }
}

private fun Map<String, Any?>.fireBoolean(key: String, default: Boolean): Boolean {
    val v = this[key] ?: return default
    return v as? Boolean ?: default
}

private fun Map<String, Any?>.fireLong(key: String): Long? {
    val v = this[key] ?: return null
    return when (v) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> v.toLong()
        else -> null
    }
}
