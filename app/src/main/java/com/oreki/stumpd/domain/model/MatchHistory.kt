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
    val allDeliveries: List<DeliveryUI> = emptyList()
)
