package com.oreki.stumpd.domain.model

// Enhanced MatchHistory with proper innings separation
data class MatchHistory(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val team1Name: String,
    val team2Name: String,
    val jokerPlayerName: String? = null,
    val team1CaptainName: String? = null,
    val team2CaptainName: String? = null,
    val firstInningsRuns: Int,
    val firstInningsWickets: Int,
    val secondInningsRuns: Int,
    val secondInningsWickets: Int,
    val winnerTeam: String,
    val winningMargin: String,
    val matchDate: Long = System.currentTimeMillis(),
    // Separate batting and bowling stats by innings
    val firstInningsBatting: List<PlayerMatchStats> = emptyList(), // Team1 batting in 1st innings
    val firstInningsBowling: List<PlayerMatchStats> = emptyList(), // Team2 bowling in 1st innings
    val secondInningsBatting: List<PlayerMatchStats> = emptyList(), // Team2 batting in 2nd innings
    val secondInningsBowling: List<PlayerMatchStats> = emptyList(), // Team1 bowling in 2nd innings
    // Keep for backward compatibility
    val team1Players: List<PlayerMatchStats> = emptyList(),
    val team2Players: List<PlayerMatchStats> = emptyList(),
    val topBatsman: PlayerMatchStats? = null,
    val topBowler: PlayerMatchStats? = null,
    val matchSettings: MatchSettings? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val shortPitch: Boolean = false,
    // New fields for partnerships and fall of wickets
    val firstInningsPartnerships: List<Partnership> = emptyList(),
    val secondInningsPartnerships: List<Partnership> = emptyList(),
    val firstInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    val secondInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    // NEW: Player of the Match (optional)
    val playerOfTheMatchId: String? = null,
    val playerOfTheMatchName: String? = null,
    val playerOfTheMatchTeam: String? = null,
    val playerOfTheMatchImpact: Double? = null,
    val playerOfTheMatchSummary: String? = null,
    // NEW: all players' impacts
    val playerImpacts: List<PlayerImpact> = emptyList(),
    // Ball-by-ball deliveries
    val allDeliveries: List<DeliveryUI> = emptyList(),
    /**
     * Who won the super over — a team name, or `"TIE"` if the scorer left it level.
     *
     * Null for the overwhelming majority of matches, and its non-nullness is the "a super over was
     * played" flag, so there is no separate boolean. Read by `resolveMatchResult` as an input, which
     * is what stops a later correction re-deriving the match back to a tie.
     */
    val superOverWinner: String? = null,
    /** One row per super-over innings, in order. Empty unless a super over was played. */
    val superOvers: List<SuperOverInnings> = emptyList(),
    /** The tournament fixture this match settled, if any. Null for an ordinary match. */
    val tournamentId: String? = null,
    val tournamentFixtureId: String? = null,
    /** The two sides as tournament teams, ordered to match [team1Name] and [team2Name]. */
    val team1Id: String? = null,
    val team2Id: String? = null,
)

/**
 * One innings of a super over.
 *
 * The batting side is stored rather than inferred, because [MatchHistory.team1Name] means "batted
 * first in the match" — which is precisely *not* who bats first in a super over.
 */
data class SuperOverInnings(
    val inning: Int,
    val battingTeam: String,
    val bowlingTeam: String,
    val runs: Int,
    val wickets: Int,
    val balls: Int,
)
