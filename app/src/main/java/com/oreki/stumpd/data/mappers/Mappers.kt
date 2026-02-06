package com.oreki.stumpd.data.mappers

import com.oreki.stumpd.domain.model.*
import android.util.Log
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.util.GsonProvider

/**
 * Converts a MatchEntity to domain MatchHistory model
 * Note: This is a lightweight conversion - stats and detailed information
 * should be loaded separately when needed
 * @return MatchHistory domain model
 */
fun MatchEntity.toDomain(): MatchHistory {
    return MatchHistory(
        id = id,
        team1Name = team1Name,
        team2Name = team2Name,
        jokerPlayerName = jokerPlayerName,
        team1CaptainName = team1CaptainName,
        team2CaptainName = team2CaptainName,
        firstInningsRuns = firstInningsRuns,
        firstInningsWickets = firstInningsWickets,
        secondInningsRuns = secondInningsRuns,
        secondInningsWickets = secondInningsWickets,
        winnerTeam = winnerTeam,
        winningMargin = winningMargin,
        matchDate = matchDate,
        groupId = groupId,
        groupName = groupName,
        shortPitch = shortPitch,
        // Lists/top performers can be loaded on the detail screen
        firstInningsBatting = emptyList(),
        firstInningsBowling = emptyList(),
        secondInningsBatting = emptyList(),
        secondInningsBowling = emptyList(),
        topBatsman = null,
        topBowler = null,
        matchSettings = parseMatchSettings(matchSettingsJson),
        playerOfTheMatchId = playerOfTheMatchId,
        playerOfTheMatchName = playerOfTheMatchName,
        playerOfTheMatchTeam = playerOfTheMatchTeam,
        playerOfTheMatchImpact = playerOfTheMatchImpact,
        playerOfTheMatchSummary = playerOfTheMatchSummary,
        playerImpacts = emptyList(),
        allDeliveries = parseDeliveries(allDeliveriesJson)
    )
}

/**
 * Safely parses match settings JSON
 * @param json JSON string representation of match settings
 * @return Parsed MatchSettings or null if parsing fails
 */
private fun parseMatchSettings(json: String?): MatchSettings? {
    return json?.let {
        try {
            GsonProvider.get().fromJson(it, MatchSettings::class.java)
        } catch (e: Exception) {
            Log.w("Mappers", "Failed to parse match settings JSON", e)
            null
        }
    }
}

/**
 * Safely parses deliveries JSON
 * @param json JSON string representation of deliveries
 * @return Parsed list of DeliveryUI or empty list if parsing fails
 */
private fun parseDeliveries(json: String?): List<DeliveryUI> {
    return json?.let {
        try {
            GsonProvider.get().fromJson(it, Array<DeliveryUI>::class.java).toList()
        } catch (e: Exception) {
            Log.w("Mappers", "Failed to parse deliveries JSON", e)
            emptyList()
        }
    } ?: emptyList()
}
