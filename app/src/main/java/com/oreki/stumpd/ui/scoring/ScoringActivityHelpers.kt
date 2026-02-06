package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*

import com.oreki.stumpd.domain.model.*
import com.google.gson.Gson
import com.oreki.stumpd.data.models.MatchInProgress

/**
 * Helper functions for ScoringActivity to support auto-save and resume functionality
 */

/**
 * Convert a List<Player> to JSON string
 */
fun List<Player>.toJsonString(gson: Gson): String {
    return gson.toJson(this)
}

/**
 * Convert a JSON string to List<Player>
 */
fun String.toPlayerList(gson: Gson): List<Player> {
    return try {
        gson.fromJson(this, Array<Player>::class.java).toList()
    } catch (e: Exception) {
        android.util.Log.e("ScoringActivityHelpers", "Failed to parse player list", e)
        emptyList()
    }
}

/**
 * Create a MatchInProgress object from the current scoring state
 */
fun createMatchInProgress(
    matchId: String,
    team1Name: String,
    team2Name: String,
    jokerName: String,
    team1PlayerIds: List<String>,
    team2PlayerIds: List<String>,
    team1PlayerNames: List<String>,
    team2PlayerNames: List<String>,
    matchSettingsJson: String,
    groupId: String?,
    groupName: String?,
    tossWinner: String?,
    tossChoice: String?,
    currentInnings: Int,
    currentOver: Int,
    ballsInOver: Int,
    totalWickets: Int,
    team1Players: List<Player>,
    team2Players: List<Player>,
    strikerIndex: Int?,
    nonStrikerIndex: Int?,
    bowlerIndex: Int?,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    firstInningsOvers: Int,
    firstInningsBalls: Int,
    bowlingTeamPlayers: List<Player>,
    totalExtras: Int,
    wides: Int,
    noBalls: Int,
    byes: Int,
    legByes: Int,
    completedBattersInnings1: List<Player>,
    completedBattersInnings2: List<Player>,
    completedBowlersInnings1: List<Player>,
    completedBowlersInnings2: List<Player>,
    firstInningsBattingPlayers: List<Player>,
    firstInningsBowlingPlayers: List<Player>,
    jokerOutInCurrentInnings: Boolean,
    jokerBallsBowledInnings1: Int,
    jokerBallsBowledInnings2: Int,
    allDeliveries: List<DeliveryUI>,
    gson: Gson
): MatchInProgress {
    val battingPlayers = if (currentInnings == 1) team1Players else team2Players
    val calculatedTotalRuns = battingPlayers.sumOf { it.runs } + totalExtras
    
    return MatchInProgress(
        matchId = matchId,
        team1Name = team1Name,
        team2Name = team2Name,
        jokerName = jokerName,
        groupId = groupId,
        groupName = groupName,
        tossWinner = tossWinner,
        tossChoice = tossChoice,
        matchSettingsJson = matchSettingsJson,
        team1PlayerIds = team1PlayerIds,
        team2PlayerIds = team2PlayerIds,
        team1PlayerNames = team1PlayerNames,
        team2PlayerNames = team2PlayerNames,
        currentInnings = currentInnings,
        currentOver = currentOver,
        ballsInOver = ballsInOver,
        totalWickets = totalWickets,
        team1PlayersJson = team1Players.toJsonString(gson),
        team2PlayersJson = team2Players.toJsonString(gson),
        strikerIndex = strikerIndex,
        nonStrikerIndex = nonStrikerIndex,
        bowlerIndex = bowlerIndex,
        firstInningsRuns = firstInningsRuns,
        firstInningsWickets = firstInningsWickets,
        firstInningsOvers = firstInningsOvers,
        firstInningsBalls = firstInningsBalls,
        totalExtras = totalExtras,
        calculatedTotalRuns = calculatedTotalRuns,
        wides = wides,
        noBalls = noBalls,
        byes = byes,
        legByes = legByes,
        completedBattersInnings1Json = completedBattersInnings1.toJsonString(gson),
        completedBattersInnings2Json = completedBattersInnings2.toJsonString(gson),
        completedBowlersInnings1Json = completedBowlersInnings1.toJsonString(gson),
        completedBowlersInnings2Json = completedBowlersInnings2.toJsonString(gson),
        firstInningsBattingPlayersJson = firstInningsBattingPlayers.toJsonString(gson),
        firstInningsBowlingPlayersJson = firstInningsBowlingPlayers.toJsonString(gson),
        jokerOutInCurrentInnings = jokerOutInCurrentInnings,
        jokerBallsBowledInnings1 = jokerBallsBowledInnings1,
        jokerBallsBowledInnings2 = jokerBallsBowledInnings2,
        allDeliveriesJson = gson.toJson(allDeliveries),
        lastSavedAt = System.currentTimeMillis(),
        startedAt = System.currentTimeMillis()
    )
}

