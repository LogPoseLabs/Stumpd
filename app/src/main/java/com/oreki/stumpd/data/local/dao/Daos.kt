package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.*

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(players: List<PlayerEntity>)

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun get(id: String): PlayerEntity?

    @Query("SELECT * FROM players ORDER BY name")
    suspend fun list(): List<PlayerEntity>
    
    @Query("DELETE FROM players WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TeamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTeams(teams: List<TeamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinks(links: List<TeamPlayerX>)

    @Query("DELETE FROM team_players WHERE teamName = :teamName")
    suspend fun clearTeamPlayers(teamName: String)
}

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(m: MatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(rows: List<PlayerMatchStatsEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchStats(rows: List<PlayerMatchStatsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImpacts(rows: List<PlayerImpactEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayerImpacts(rows: List<PlayerImpactEntity>)

    @Transaction
    suspend fun insertFullMatch(
        m: MatchEntity,
        stats: List<PlayerMatchStatsEntity>,
        impacts: List<PlayerImpactEntity>
    ) {
        insertMatch(m)
        if (stats.isNotEmpty()) insertStats(stats)
        if (impacts.isNotEmpty()) insertImpacts(impacts)
    }

    @Query("""
        SELECT * FROM matches 
        WHERE (:groupId IS NULL OR groupId = :groupId)
        ORDER BY matchDate DESC
        LIMIT :limit
    """)
    suspend fun list(groupId: String?, limit: Int = 500): List<MatchEntity>

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteMatch(matchId: String)

    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MatchEntity?

    @Query("SELECT * FROM player_match_stats WHERE matchId = :matchId")
    suspend fun statsForMatch(matchId: String): List<PlayerMatchStatsEntity>
    
    @Query("SELECT * FROM player_match_stats WHERE matchId IN (:matchIds)")
    suspend fun getStatsForMatches(matchIds: List<String>): List<PlayerMatchStatsEntity>

    @Query("SELECT * FROM player_impacts WHERE matchId = :matchId ORDER BY impact DESC")
    suspend fun impactsForMatch(matchId: String): List<PlayerImpactEntity>
    
    @Query("SELECT * FROM player_impacts WHERE matchId IN (:matchIds) ORDER BY impact DESC")
    suspend fun getImpactsForMatches(matchIds: List<String>): List<PlayerImpactEntity>
    
    @Query("UPDATE player_match_stats SET name = :newName WHERE playerId = :playerId")
    suspend fun updatePlayerNameInStats(playerId: String, newName: String): Int
}

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(g: GroupEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDefaults(d: GroupDefaultEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupDefaults(defaults: List<GroupDefaultEntity>)

    // New membership table (see Entity below)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(rows: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)
    
    @Query("DELETE FROM group_members WHERE playerId = :playerId")
    suspend fun removePlayerFromAllGroups(playerId: String)
    
    @Query("DELETE FROM group_unavailable_players WHERE playerId = :playerId")
    suspend fun removePlayerFromUnavailableLists(playerId: String)

    @Query("SELECT * FROM groups ORDER BY name")
    suspend fun listGroups(): List<GroupEntity>
    
    @Query("SELECT * FROM groups ORDER BY name")
    suspend fun getAllGroups(): List<GroupEntity>

    @Query("SELECT * FROM group_defaults WHERE groupId = :groupId LIMIT 1")
    suspend fun getDefaults(groupId: String): GroupDefaultEntity?
    
    @Query("SELECT * FROM group_defaults")
    suspend fun getAllGroupDefaults(): List<GroupDefaultEntity>
    
    @Query("SELECT * FROM group_members")
    suspend fun getAllGroupMembers(): List<GroupMemberEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(members: List<GroupMemberEntity>)
    
    @Query("SELECT * FROM group_last_teams")
    suspend fun getAllGroupLastTeams(): List<GroupLastTeamsEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupLastTeams(lastTeams: List<GroupLastTeamsEntity>)

    @Query("""
    SELECT p.* FROM group_members gm
    JOIN players p ON p.id = gm.playerId
    WHERE gm.groupId = :groupId
    ORDER BY p.name
  """)
    suspend fun members(groupId: String): List<PlayerEntity>

    // Persist and read last selected teams per group as JSON of ids
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLastTeams(e: GroupLastTeamsEntity)

    @Query("SELECT * FROM group_last_teams WHERE groupId = :groupId LIMIT 1")
    suspend fun lastTeams(groupId: String): GroupLastTeamsEntity?

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId")
    suspend fun memberCount(groupId: String): Int // fast COUNT(*) [9][11]

    @Query("SELECT playerId FROM group_members WHERE groupId = :groupId ORDER BY playerId")
    suspend fun memberIds(groupId: String): List<String> // used to pre-fill Edit dialog [11]
    
    @Query("SELECT groupId FROM group_members WHERE playerId = :playerId")
    suspend fun getGroupIdsForPlayer(playerId: String): List<String>
    
    @Query("""
    SELECT g.* FROM groups g
    JOIN group_members gm ON g.id = gm.groupId
    WHERE gm.playerId = :playerId
    ORDER BY g.name
    """)
    suspend fun getGroupsForPlayer(playerId: String): List<GroupEntity>
    
    // Unavailable players management
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markPlayerUnavailable(entity: GroupUnavailablePlayerEntity)
    
    @Query("DELETE FROM group_unavailable_players WHERE groupId = :groupId AND playerId = :playerId")
    suspend fun markPlayerAvailable(groupId: String, playerId: String)
    
    @Query("SELECT playerId FROM group_unavailable_players WHERE groupId = :groupId")
    suspend fun getUnavailablePlayerIds(groupId: String): List<String>
    
    @Query("SELECT * FROM group_unavailable_players")
    suspend fun getAllGroupUnavailablePlayers(): List<GroupUnavailablePlayerEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupUnavailablePlayers(unavailable: List<GroupUnavailablePlayerEntity>)
    
    // ========== Invite Code Methods ==========
    
    @Query("UPDATE groups SET inviteCode = :inviteCode WHERE id = :groupId")
    suspend fun updateInviteCode(groupId: String, inviteCode: String)
    
    @Query("SELECT * FROM groups WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getGroupByInviteCode(inviteCode: String): GroupEntity?
    
    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupEntity?
    
    @Query("SELECT inviteCode FROM groups WHERE id = :groupId LIMIT 1")
    suspend fun getInviteCode(groupId: String): String?
    
    // ========== Joined Groups Methods (for groups joined via invite codes) ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJoinedGroup(joinedGroup: JoinedGroupEntity)
    
    @Query("SELECT * FROM joined_groups ORDER BY joinedAt DESC")
    suspend fun getJoinedGroups(): List<JoinedGroupEntity>
    
    @Query("SELECT * FROM joined_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getJoinedGroup(groupId: String): JoinedGroupEntity?
    
    @Query("SELECT * FROM joined_groups WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getJoinedGroupByCode(inviteCode: String): JoinedGroupEntity?
    
    @Query("DELETE FROM joined_groups WHERE groupId = :groupId")
    suspend fun leaveJoinedGroup(groupId: String)
    
    @Query("SELECT groupId FROM joined_groups")
    suspend fun getJoinedGroupIds(): List<String>
    
    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)
}

@Dao
interface InProgressMatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(match: InProgressMatchEntity)
    
    @Query("SELECT * FROM in_progress_matches ORDER BY lastSavedAt DESC LIMIT 1")
    suspend fun getLatest(): InProgressMatchEntity?
    
    @Query("SELECT * FROM in_progress_matches WHERE matchId = :matchId LIMIT 1")
    suspend fun getById(matchId: String): InProgressMatchEntity?
    
    @Query("DELETE FROM in_progress_matches WHERE matchId = :matchId")
    suspend fun delete(matchId: String)
    
    @Query("DELETE FROM in_progress_matches")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM in_progress_matches")
    suspend fun count(): Int
}

@Dao
interface UserPreferencesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: UserPreferencesEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(prefs: List<UserPreferencesEntity>)
    
    @Query("SELECT value FROM user_preferences WHERE key = :key LIMIT 1")
    suspend fun getValue(key: String): String?
    
    @Query("SELECT * FROM user_preferences")
    suspend fun getAll(): List<UserPreferencesEntity>
    
    @Query("DELETE FROM user_preferences WHERE key = :key")
    suspend fun delete(key: String)
}

@Dao
interface PartnershipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartnerships(partnerships: List<PartnershipEntity>)
    
    @Query("SELECT * FROM partnerships WHERE matchId = :matchId AND innings = :innings ORDER BY partnershipNumber")
    suspend fun getPartnershipsForInnings(matchId: String, innings: Int): List<PartnershipEntity>
    
    @Query("SELECT * FROM partnerships WHERE matchId = :matchId ORDER BY innings, partnershipNumber")
    suspend fun getPartnershipsForMatch(matchId: String): List<PartnershipEntity>
    
    @Query("SELECT * FROM partnerships WHERE matchId IN (:matchIds)")
    suspend fun getPartnershipsForMatches(matchIds: List<String>): List<PartnershipEntity>
    
    @Query("SELECT * FROM partnerships")
    suspend fun getAllPartnerships(): List<PartnershipEntity>
    
    @Query("DELETE FROM partnerships WHERE matchId = :matchId")
    suspend fun deleteForMatch(matchId: String)
}

@Dao
interface FallOfWicketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFallOfWickets(fows: List<FallOfWicketEntity>)
    
    @Query("SELECT * FROM fall_of_wickets WHERE matchId = :matchId AND innings = :innings ORDER BY wicketNumber")
    suspend fun getFallOfWicketsForInnings(matchId: String, innings: Int): List<FallOfWicketEntity>
    
    @Query("SELECT * FROM fall_of_wickets WHERE matchId = :matchId ORDER BY innings, wicketNumber")
    suspend fun getFallOfWicketsForMatch(matchId: String): List<FallOfWicketEntity>
    
    @Query("SELECT * FROM fall_of_wickets WHERE matchId IN (:matchIds)")
    suspend fun getFallOfWicketsForMatches(matchIds: List<String>): List<FallOfWicketEntity>
    
    @Query("SELECT * FROM fall_of_wickets")
    suspend fun getAllFallOfWickets(): List<FallOfWicketEntity>
    
    @Query("DELETE FROM fall_of_wickets WHERE matchId = :matchId")
    suspend fun deleteForMatch(matchId: String)
}
