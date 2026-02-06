package com.oreki.stumpd.data.models

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oreki.stumpd.domain.model.*

/**
 * Helper functions to convert between match state and MatchInProgress
 */

/**
 * Serialize a list of Players to JSON
 */
fun List<Player>.toJson(gson: Gson): String {
    return gson.toJson(this)
}

/**
 * Deserialize a JSON string to a list of Players
 */
fun String.toPlayerList(gson: Gson): List<Player> {
    return try {
        val type = object : TypeToken<List<Player>>() {}.type
        gson.fromJson(this, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Create a MatchInProgress from current scoring activity state
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
    team1Players: List<Player>,
    team2Players: List<Player>,
    strikerIndex: Int?,
    nonStrikerIndex: Int?,
    bowlerIndex: Int?,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    firstInningsOvers: Int,
    firstInningsBalls: Int,
    bowlingTeamPlayers: List<Player>?,
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
    powerplayRunsInnings1: Int = 0,
    powerplayRunsInnings2: Int = 0,
    powerplayDoublingDoneInnings1: Boolean = false,
    powerplayDoublingDoneInnings2: Boolean = false,
    allDeliveries: List<DeliveryUI> = emptyList(),
    gson: Gson
): MatchInProgress {
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
        team1PlayersJson = team1Players.toJson(gson),
        team2PlayersJson = team2Players.toJson(gson),
        strikerIndex = strikerIndex,
        nonStrikerIndex = nonStrikerIndex,
        bowlerIndex = bowlerIndex,
        firstInningsRuns = firstInningsRuns,
        firstInningsWickets = firstInningsWickets,
        firstInningsOvers = firstInningsOvers,
        firstInningsBalls = firstInningsBalls,
        bowlingTeamPlayersJson = bowlingTeamPlayers?.toJson(gson),
        totalExtras = totalExtras,
        wides = wides,
        noBalls = noBalls,
        byes = byes,
        legByes = legByes,
        completedBattersInnings1Json = completedBattersInnings1.toJson(gson),
        completedBattersInnings2Json = completedBattersInnings2.toJson(gson),
        completedBowlersInnings1Json = completedBowlersInnings1.toJson(gson),
        completedBowlersInnings2Json = completedBowlersInnings2.toJson(gson),
        firstInningsBattingPlayersJson = firstInningsBattingPlayers.toJson(gson),
        firstInningsBowlingPlayersJson = firstInningsBowlingPlayers.toJson(gson),
        jokerOutInCurrentInnings = jokerOutInCurrentInnings,
        jokerBallsBowledInnings1 = jokerBallsBowledInnings1,
        jokerBallsBowledInnings2 = jokerBallsBowledInnings2,
        powerplayRunsInnings1 = powerplayRunsInnings1,
        powerplayRunsInnings2 = powerplayRunsInnings2,
        powerplayDoublingDoneInnings1 = powerplayDoublingDoneInnings1,
        powerplayDoublingDoneInnings2 = powerplayDoublingDoneInnings2,
        allDeliveriesJson = gson.toJson(allDeliveries),
        lastSavedAt = System.currentTimeMillis()
    )
}


