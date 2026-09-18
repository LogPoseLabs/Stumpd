package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.resolveMatchResult

/**
 * Re-derives everything a match stores but nobody enters.
 *
 * A correction changes figures; this puts the consequences right. Ten stored things are functions
 * of the figures — innings wickets, the maiden counts, the winner, the margin, every impact score,
 * the five Player-of-the-Match columns, the two top-performer rows and the fall-of-wickets
 * numbering — and all of them go stale the moment a row is edited.
 *
 * Order matters: the result has to be settled before impacts, because the winning side's players
 * get a 10% multiplier and a successful chase pays a bonus. Get that backwards and an edit which
 * flips the match leaves the awards pointing at the losing side.
 */
object MatchRecompute {

    fun recompute(match: MatchHistory, settings: MatchSettings): MatchHistory {
        // 1. Fall of wickets: dense 1..N in stored order. A repeated number silently replaces the
        //    earlier row on save, and a gap breaks the "Nth wicket" labels.
        val firstFow = renumber(match.firstInningsFallOfWickets)
        val secondFow = renumber(match.secondInningsFallOfWickets)

        // 2. Wickets per innings follow the batting rows, which are the source of truth for who
        //    was dismissed.
        val firstWickets = match.firstInningsBatting.count { it.isOut }
        val secondWickets = match.secondInningsBatting.count { it.isOut }

        // 3. Maidens, from the trail, keyed by bowler name as the existing repair sweep does.
        val maidens = maidensByBowlerName(match)
        val firstBowling = withMaidens(match.firstInningsBowling, maidens)
        val secondBowling = withMaidens(match.secondInningsBowling, maidens)

        // 4. Innings totals: the trail is authoritative when it's complete enough to trust.
        val firstRuns = inningsRunsFromTrail(match, innings = 1) ?: match.firstInningsRuns
        val secondRuns = inningsRunsFromTrail(match, innings = 2) ?: match.secondInningsRuns

        // 5. The result, before the impacts that depend on it.
        val result = resolveMatchResult(
            team1Name = match.team1Name,
            team2Name = match.team2Name,
            firstInningsRuns = firstRuns,
            secondInningsRuns = secondRuns,
            secondInningsWickets = secondWickets,
            chasingSquadSize = (match.secondInningsBatting + match.firstInningsBowling)
                .distinctBy { it.name }.size
                .takeIf { it > 0 } ?: settings.maxPlayersPerTeam,
            allowSingleSideBatting = settings.allowSingleSideBatting,
            // The eliminator is an input, not a derivation: the two totals are level when a super
            // over happened, so re-deriving without this would turn the win back into a tie.
            superOverWinner = match.superOverWinner,
        )

        // 6. Impacts, Player of the Match and the top performers, via the same assembly the
        //    completion path uses — so a corrected match is scored exactly like a fresh one.
        return assembleMatch(
            team1Name = match.team1Name,
            team2Name = match.team2Name,
            jokerPlayerName = match.jokerPlayerName,
            team1CaptainName = match.team1CaptainName,
            team2CaptainName = match.team2CaptainName,
            firstInningsRuns = firstRuns,
            firstInningsWickets = firstWickets,
            secondInningsRuns = secondRuns,
            secondInningsWickets = secondWickets,
            winnerTeam = result.winnerTeam,
            winningMargin = result.winningMargin,
            firstInningsBattingStats = match.firstInningsBatting,
            firstInningsBowlingStats = firstBowling,
            secondInningsBattingStats = match.secondInningsBatting,
            secondInningsBowlingStats = secondBowling,
            firstInningsPartnerships = match.firstInningsPartnerships,
            secondInningsPartnerships = match.secondInningsPartnerships,
            firstInningsFallOfWickets = firstFow,
            secondInningsFallOfWickets = secondFow,
            matchSettings = settings,
            groupId = match.groupId,
            groupName = match.groupName,
            allDeliveries = match.allDeliveries,
            matchId = match.id,
            matchDate = match.matchDate,
            superOverWinner = match.superOverWinner,
            superOvers = match.superOvers,
            // Or a corrected match would forget which fixture it settled.
            tournamentId = match.tournamentId,
            tournamentFixtureId = match.tournamentFixtureId,
            team1Id = match.team1Id,
            team2Id = match.team2Id,
        )
    }

    /** Wicket numbers 1..N in stored order — never re-sorted, since two can fall at one score. */
    fun renumber(fow: List<FallOfWicket>): List<FallOfWicket> =
        fow.mapIndexed { index, row -> row.copy(wicketNumber = index + 1) }

    /**
     * Maiden overs per bowler, by lowercased name.
     *
     * Deliberately the same rule as the existing repair sweep: a completed over with no runs at
     * all, byes included. The live scorer excludes byes from its own running total, so the two
     * disagree on a bye-only over — changing that here would silently rewrite every historical
     * maiden count, which is a separate decision from correcting a match.
     */
    fun maidensByBowlerName(match: MatchHistory): Map<String, Int> {
        val maidens = mutableMapOf<String, Int>()
        // The eliminator is excluded, or a scoreless super over would award a phantom maiden
        // against the bowler's match figures.
        match.allDeliveries
            .mainMatchDeliveries()
            .groupBy { it.inning to it.over }
            .forEach { (_, over) ->
                val legalBalls = over.count { it.isLegalBall() }
                if (legalBalls < 6) return@forEach
                if (over.sumOf { it.effectiveRuns() } != 0) return@forEach
                val bowler = over.firstOrNull()?.bowlerName?.lowercase()?.trim() ?: return@forEach
                if (bowler.isBlank()) return@forEach
                maidens[bowler] = (maidens[bowler] ?: 0) + 1
            }
        return maidens
    }

    private fun withMaidens(
        bowling: List<PlayerMatchStats>,
        maidens: Map<String, Int>,
    ): List<PlayerMatchStats> = bowling.map { row ->
        val recomputed = maidens[row.name.lowercase().trim()] ?: 0
        if (recomputed == row.maidenOvers) row else row.copy(maidenOvers = recomputed)
    }

    /**
     * An innings' total from the trail, or null when the trail can't be trusted for it.
     *
     * Matches imported or scored before the trail was complete would otherwise have their totals
     * rewritten to nonsense, so the stored value stands unless the trail agrees with the bowling
     * figures about how many balls were bowled.
     */
    fun inningsRunsFromTrail(match: MatchHistory, innings: Int): Int? {
        val deliveries = match.allDeliveries.filter { it.inning == innings }
        if (deliveries.isEmpty()) return null
        val bowling = if (innings == 1) match.firstInningsBowling else match.secondInningsBowling
        val trailLegalBalls = deliveries.count { it.isLegalBall() }
        val creditedBalls = bowling.sumOf { ballsFromOvers(it.oversBowled) }
        if (creditedBalls != trailLegalBalls) return null
        return deliveries.sumOf { it.effectiveRuns() }
    }

    /** Overs stored as `O.B` (3.4 = 22 balls) back to a ball count. */
    fun ballsFromOvers(overs: Double): Int {
        if (overs <= 0.0) return 0
        val completed = kotlin.math.floor(overs + 1e-6).toInt()
        val balls = ((overs - completed) * 10).let { Math.round(it).toInt() }.coerceIn(0, 5)
        return completed * 6 + balls
    }
}
