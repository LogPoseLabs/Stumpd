package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.*

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
