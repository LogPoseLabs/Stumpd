package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String, // PlayerId.value
    val name: String,
    val isJoker: Boolean
)

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val name: String
)

@Entity(
    tableName = "team_players",
    primaryKeys = ["teamName", "playerId"],
    indices = [Index("playerId")]
)
data class TeamPlayerX(
    val teamName: String,
    val playerId: String
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String? = null, // 6-character alphanumeric invite code (for joining)
    val claimCode: String? = null, // Secret recovery code (for ownership recovery)
    val isOwner: Boolean = true // True if this device created the group
)

/**
 * Tracks groups that this device has joined via invite codes.
 * Used to sync data only for groups the user is a member of.
 */
@Entity(
    tableName = "joined_groups",
    indices = [Index("inviteCode")]
)
data class JoinedGroupEntity(
    @PrimaryKey val groupId: String, // The remote group ID
    val inviteCode: String, // The code used to join
    val groupName: String, // Cached group name
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(
    tableName = "partnerships",
    primaryKeys = ["matchId", "innings", "partnershipNumber"],
    indices = [Index("matchId")]
)
data class PartnershipEntity(
    val matchId: String,
    val innings: Int, // 1 or 2
    val partnershipNumber: Int, // Sequential number (1st partnership, 2nd, etc.)
    val batsman1Name: String,
    val batsman2Name: String,
    val runs: Int,
    val balls: Int,
    val batsman1Runs: Int,
    val batsman2Runs: Int,
    val isActive: Boolean
)

@Entity(
    tableName = "fall_of_wickets",
    primaryKeys = ["matchId", "innings", "wicketNumber"],
    indices = [Index("matchId")]
)
data class FallOfWicketEntity(
    val matchId: String,
    val innings: Int, // 1 or 2
    val wicketNumber: Int, // 1st wicket, 2nd wicket, etc.
    val batsmanName: String,
    val runs: Int, // Team score when wicket fell
    val overs: Double, // Overs when wicket fell
    val dismissalType: String? = null,
    val bowlerName: String? = null,
    val fielderName: String? = null
)

@Entity(tableName = "group_defaults")
data class GroupDefaultEntity(
    @PrimaryKey val groupId: String,
    val groundName: String,
    val format: String, // BallFormat.name
    val shortPitch: Boolean,
    val matchSettingsJson: String? // serialized MatchSettings
)

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val team1Name: String,
    val team2Name: String,
    val jokerPlayerName: String?,
    val team1CaptainName: String?,
    val team2CaptainName: String?,
    val firstInningsRuns: Int,
    val firstInningsWickets: Int,
    val secondInningsRuns: Int,
    val secondInningsWickets: Int,
    val winnerTeam: String,
    val winningMargin: String,
    val matchDate: Long,
    val groupId: String?,
    val groupName: String?,
    val shortPitch: Boolean,
    val playerOfTheMatchId: String?,
    val playerOfTheMatchName: String?,
    val playerOfTheMatchTeam: String?,
    val playerOfTheMatchImpact: Double?,
    val playerOfTheMatchSummary: String?,
    val matchSettingsJson: String?,
    val allDeliveriesJson: String? // Ball-by-ball data
)

@Entity(
    tableName = "player_match_stats",
    primaryKeys = ["matchId", "playerId", "team"],
    indices = [Index("matchId"), Index("playerId"), Index("team")]
)
data class PlayerMatchStatsEntity(
    val matchId: String,
    val playerId: String,
    val name: String,
    val team: String,
    val runs: Int,
    val ballsFaced: Int,
    val dots: Int = 0,
    val singles: Int = 0,
    val twos: Int = 0,
    val threes: Int = 0,
    val fours: Int,
    val sixes: Int,
    val wickets: Int,
    val runsConceded: Int,
    val oversBowled: Double,
    val maidenOvers: Int = 0,
    val isOut: Boolean,
    val isRetired: Boolean = false,
    val isJoker: Boolean,
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
    val dismissalType: String? = null,
    val bowlerName: String? = null,
    val fielderName: String? = null
)

@Entity(
    tableName = "player_impacts",
    indices = [Index("matchId"), Index("playerId")]
)
data class PlayerImpactEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val matchId: String,
    val playerId: String,
    val name: String,
    val team: String,
    val impact: Double,
    val summary: String,
    val isJoker: Boolean,
    val runs: Int,
    val balls: Int,
    val dots: Int = 0,
    val singles: Int = 0,
    val twos: Int = 0,
    val threes: Int = 0,
    val fours: Int,
    val sixes: Int,
    val wickets: Int,
    val runsConceded: Int,
    val oversBowled: Double
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId","playerId"],
    indices = [Index("playerId")]
)
data class GroupMemberEntity(
    val groupId: String,
    val playerId: String
)

@Entity(
    tableName = "group_unavailable_players",
    primaryKeys = ["groupId","playerId"],
    indices = [Index("playerId")]
)
data class GroupUnavailablePlayerEntity(
    val groupId: String,
    val playerId: String
)

@Entity(tableName = "group_last_teams")
data class GroupLastTeamsEntity(
    @PrimaryKey val groupId: String,
    val team1PlayerIdsJson: String,
    val team2PlayerIdsJson: String,
    val team1Name: String,
    val team2Name: String
)

@Entity(tableName = "in_progress_matches")
data class InProgressMatchEntity(
    @PrimaryKey val matchId: String,
    val team1Name: String,
    val team2Name: String,
    val jokerName: String,
    val groupId: String?,
    val groupName: String?,
    val tossWinner: String?,
    val tossChoice: String?,
    val matchSettingsJson: String,
    
    // Player info
    val team1PlayerIds: String, // JSON array
    val team2PlayerIds: String, // JSON array
    val team1PlayerNames: String, // JSON array
    val team2PlayerNames: String, // JSON array
    
    // Current match state
    val currentInnings: Int,
    val currentOver: Int,
    val ballsInOver: Int,
    val totalWickets: Int,
    
    // Team players state (serialized as JSON)
    val team1PlayersJson: String,
    val team2PlayersJson: String,
    
    // Current roles
    val strikerIndex: Int?,
    val nonStrikerIndex: Int?,
    val bowlerIndex: Int?,
    
    // Innings data
    val firstInningsRuns: Int,
    val firstInningsWickets: Int,
    val firstInningsOvers: Int,
    val firstInningsBalls: Int,
    
    // Extras
    val totalExtras: Int,
    val calculatedTotalRuns: Int,
    
    // Completed players lists (serialized as JSON)
    val completedBattersInnings1Json: String?,
    val completedBattersInnings2Json: String?,
    val completedBowlersInnings1Json: String?,
    val completedBowlersInnings2Json: String?,
    
    // First innings stats (serialized as JSON)
    val firstInningsBattingPlayersJson: String?,
    val firstInningsBowlingPlayersJson: String?,
    
    // Joker tracking
    val jokerOutInCurrentInnings: Boolean,
    val jokerBallsBowledInnings1: Int,
    val jokerBallsBowledInnings2: Int,
    
    // Powerplay tracking
    val powerplayRunsInnings1: Int = 0,
    val powerplayRunsInnings2: Int = 0,
    val powerplayDoublingDoneInnings1: Boolean = false,
    val powerplayDoublingDoneInnings2: Boolean = false,
    
    // Deliveries (ball-by-ball data, serialized as JSON)
    val allDeliveriesJson: String?,
    
    // Timestamps
    val lastSavedAt: Long,
    val startedAt: Long
)

