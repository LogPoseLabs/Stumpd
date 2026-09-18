package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.SuperOverInnings

/**
 * Builds the finished match record from the innings' figures, deriving everything that is stored
 * but not entered: the top performers, every player's impact score and the Player of the Match.
 *
 * This used to be `saveMatchToHistory` in `ScoringActivity` — reachable only from the completion
 * dialog, and taking a `Context` it never read. Correcting a saved match needs the same
 * derivation re-run over amended figures, which is why [matchId] and [matchDate] are parameters:
 * a correction must rebuild the record in place, keeping its identity and its date, while a newly
 * completed match takes the defaults.
 */
fun assembleMatch(
    team1Name: String,
    team2Name: String,
    jokerPlayerName: String?,
    team1CaptainName: String? = null,
    team2CaptainName: String? = null,
    firstInningsRuns: Int,
    firstInningsWickets: Int,
    secondInningsRuns: Int,
    secondInningsWickets: Int,
    winnerTeam: String,
    winningMargin: String,
    firstInningsBattingStats: List<PlayerMatchStats> = emptyList(),
    firstInningsBowlingStats: List<PlayerMatchStats> = emptyList(),
    secondInningsBattingStats: List<PlayerMatchStats> = emptyList(),
    secondInningsBowlingStats: List<PlayerMatchStats> = emptyList(),
    firstInningsPartnerships: List<Partnership> = emptyList(),
    secondInningsPartnerships: List<Partnership> = emptyList(),
    firstInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    secondInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    matchSettings: MatchSettings,
    /** Null for an ungrouped match — distinct from an empty string, which the filters can't see. */
    groupId: String?,
    groupName: String?,
    allDeliveries: List<DeliveryUI> = emptyList(),
    /** Null mints a new id — pass the existing one when rebuilding a saved match. */
    matchId: String? = null,
    matchDate: Long = System.currentTimeMillis(),
    /**
     * The eliminator, when there was one. Defaulted so nothing else has to know about it — but the
     * correction path *must* pass both, or recomputing a match would drop its super over.
     */
    superOverWinner: String? = null,
    superOvers: List<SuperOverInnings> = emptyList(),
    /** The tournament fixture this match settled, when it was played as one. */
    tournamentId: String? = null,
    tournamentFixtureId: String? = null,
    team1Id: String? = null,
    team2Id: String? = null,
): MatchHistory {
    val allBatting = firstInningsBattingStats + secondInningsBattingStats
    val allBowling = firstInningsBowlingStats + secondInningsBowlingStats

    val topBatsman = allBatting.maxByOrNull { it.runs }
    val topBowler = allBowling
        .filter { it.oversBowled > 0 }
        .maxWithOrNull { a, b ->
            when {
                a.wickets != b.wickets -> a.wickets.compareTo(b.wickets)
                else -> {
                    val economyA = a.runsConceded.toDouble() / a.oversBowled
                    val economyB = b.runsConceded.toDouble() / b.oversBowled
                    economyB.compareTo(economyA)
                }
            }
        }

    val scored = MatchImpactScoring.compute(
        firstInningsBatting = firstInningsBattingStats,
        firstInningsBowling = firstInningsBowlingStats,
        secondInningsBatting = secondInningsBattingStats,
        secondInningsBowling = secondInningsBowlingStats,
        firstInningsRuns = firstInningsRuns,
        secondInningsRuns = secondInningsRuns,
        winnerTeam = winnerTeam,
        team2Name = team2Name,
        totalOvers = matchSettings.totalOvers,
    )
    val potm = scored.playerOfTheMatch

    return MatchHistory(
        id = matchId ?: java.util.UUID.randomUUID().toString(),
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
        firstInningsBatting = firstInningsBattingStats,
        firstInningsBowling = firstInningsBowlingStats,
        secondInningsBatting = secondInningsBattingStats,
        secondInningsBowling = secondInningsBowlingStats,
        team1Players = firstInningsBattingStats + secondInningsBowlingStats,
        team2Players = firstInningsBowlingStats + secondInningsBattingStats,
        topBatsman = topBatsman,
        topBowler = topBowler,
        matchDate = matchDate,
        matchSettings = matchSettings,
        groupId = groupId,
        groupName = groupName,
        shortPitch = matchSettings.shortPitch,
        firstInningsPartnerships = firstInningsPartnerships,
        secondInningsPartnerships = secondInningsPartnerships,
        firstInningsFallOfWickets = firstInningsFallOfWickets,
        secondInningsFallOfWickets = secondInningsFallOfWickets,
        playerOfTheMatchId = potm?.id,
        playerOfTheMatchName = potm?.name,
        playerOfTheMatchTeam = potm?.team,
        playerOfTheMatchImpact = potm?.impact,
        playerOfTheMatchSummary = potm?.summary,
        playerImpacts = scored.impacts,
        allDeliveries = allDeliveries,
        superOverWinner = superOverWinner,
        superOvers = superOvers,
        tournamentId = tournamentId,
        tournamentFixtureId = tournamentFixtureId,
        team1Id = team1Id,
        team2Id = team2Id,
    )
}
