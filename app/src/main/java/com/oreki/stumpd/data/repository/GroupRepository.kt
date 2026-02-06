package com.oreki.stumpd.data.repository

import android.util.Log
import com.google.gson.reflect.TypeToken
import com.oreki.stumpd.domain.model.GroupDefaultSettings
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.*
import com.oreki.stumpd.data.mappers.toEntityWithId
import com.oreki.stumpd.data.util.GsonProvider
import com.oreki.stumpd.data.util.InviteCodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Data class for returning group edit information
 */
data class GroupEditInfo(
    val entity: GroupEntity,
    val defaults: GroupDefaultEntity?,
    val memberIds: List<String>,
    val unavailablePlayerIds: List<String>
)

/**
 * Repository for managing player groups
 * Handles all database operations related to groups, members, and group settings
 */
class GroupRepository(private val db: StumpdDb) {
    
    private val gson = GsonProvider.get()
    
    private companion object {
        const val TAG = "GroupRepository"
    }
    /**
     * Retrieves all groups from database
     */
    suspend fun listGroups(): List<GroupEntity> = withContext(Dispatchers.IO) {
        db.groupDao().listGroups()
    }

    /**
     * Creates a new group with default settings
     * @param name The name of the group
     * @param defaults Default settings for the group
     * @return The ID of the created group
     */
    suspend fun createGroup(name: String, defaults: GroupDefaultSettings): String =
        withContext(Dispatchers.IO) {
            try {
                val id = UUID.randomUUID().toString()
                val defaultEntity = defaults.toEntityWithId(id)
                // Generate both invite code and claim code for new groups
                val inviteCode = InviteCodeManager.generateCode()
                val claimCode = InviteCodeManager.generateClaimCode()
                db.groupDao().upsertGroup(
                    GroupEntity(
                        id = id, 
                        name = name.trim(),
                        inviteCode = inviteCode,
                        claimCode = claimCode,
                        isOwner = true
                    )
                )
                db.groupDao().upsertDefaults(defaultEntity)
                Log.d(TAG, "Created group: $name with id: $id, inviteCode: $inviteCode")
                id
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create group: $name", e)
                throw e
            }
        }

    /**
     * Renames an existing group
     * @param id The ID of the group to rename
     * @param newName The new name for the group
     */
    suspend fun renameGroup(id: String, newName: String) = withContext(Dispatchers.IO) {
        try {
            db.groupDao().upsertGroup(GroupEntity(id = id, name = newName.trim()))
            Log.d(TAG, "Renamed group: $id to $newName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename group: $id", e)
            throw e
        }
    }

    /**
     * Deletes a group and its associated data
     * @param id The ID of the group to delete
     */
    suspend fun deleteGroup(id: String) = withContext(Dispatchers.IO) {
        try {
            db.groupDao().clearMembers(id)
            Log.d(TAG, "Deleted group: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete group: $id", e)
            throw e
        }
    }

    /**
     * Replaces all members of a group
     * @param groupId The ID of the group
     * @param playerIds List of player IDs to set as members
     */
    suspend fun replaceMembers(groupId: String, playerIds: List<String>) = withContext(Dispatchers.IO) {
        try {
            db.groupDao().clearMembers(groupId)
            val rows = playerIds.distinct().map { GroupMemberEntity(groupId, it) }
            if (rows.isNotEmpty()) {
                db.groupDao().upsertMembers(rows)
            }
            Log.d(TAG, "Replaced members for group: $groupId, count: ${rows.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace members for group: $groupId", e)
            throw e
        }
    }

    /**
     * Updates the default settings for a group
     * @param groupId The ID of the group
     * @param defaults The new default settings
     */
    suspend fun updateDefaults(groupId: String, defaults: GroupDefaultEntity) = withContext(Dispatchers.IO) {
        try {
            db.groupDao().upsertDefaults(defaults.copy(groupId = groupId))
            Log.d(TAG, "Updated defaults for group: $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update defaults for group: $groupId", e)
            throw e
        }
    }

    /**
     * Gets all members of a group
     * @param groupId The ID of the group
     * @return List of player entities that are members
     */
    suspend fun getMembers(groupId: String): List<PlayerEntity> = withContext(Dispatchers.IO) {
        db.groupDao().members(groupId)
    }

    /**
     * Saves the last selected teams for a group
     * @param groupId The ID of the group
     * @param team1Ids List of player IDs in team 1
     * @param team2Ids List of player IDs in team 2
     * @param team1Name Name of team 1
     * @param team2Name Name of team 2
     */
    suspend fun saveLastTeams(
        groupId: String,
        team1Ids: List<String>,
        team2Ids: List<String>,
        team1Name: String,
        team2Name: String
    ) = withContext(Dispatchers.IO) {
        try {
            val entity = GroupLastTeamsEntity(
                groupId = groupId,
                team1PlayerIdsJson = gson.toJson(team1Ids),
                team2PlayerIdsJson = gson.toJson(team2Ids),
                team1Name = team1Name,
                team2Name = team2Name
            )
            db.groupDao().upsertLastTeams(entity)
            Log.d(TAG, "Saved last teams for group: $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save last teams for group: $groupId", e)
            throw e
        }
    }

    /**
     * Loads the last selected teams for a group
     * @param groupId The ID of the group
     * @return Triple of (team1Ids, team2Ids, (team1Name, team2Name)) or null if not found
     */
    suspend fun loadLastTeams(groupId: String): Triple<List<String>, List<String>, Pair<String, String>>? =
        withContext(Dispatchers.IO) {
            try {
                val entity = db.groupDao().lastTeams(groupId) ?: return@withContext null
                
                val team1Ids: List<String> = gson.fromJson(
                    entity.team1PlayerIdsJson,
                    object : TypeToken<List<String>>() {}.type
                )
                val team2Ids: List<String> = gson.fromJson(
                    entity.team2PlayerIdsJson,
                    object : TypeToken<List<String>>() {}.type
                )
                val teamNames = Pair(entity.team1Name, entity.team2Name)

                Triple(team1Ids, team2Ids, teamNames)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load last teams for group: $groupId", e)
                null
            }
        }

    /**
     * Gets the default settings for a group
     * @param groupId The ID of the group
     * @return GroupDefaultEntity or null if not found
     */
    suspend fun getDefaults(groupId: String): GroupDefaultEntity? =
        withContext(Dispatchers.IO) {
            db.groupDao().getDefaults(groupId)
        }

    /**
     * Lists all groups with their summaries (defaults and member count)
     * @return List of triples containing (entity, defaults, member count)
     */
    suspend fun listGroupSummaries(): List<Triple<GroupEntity, GroupDefaultEntity?, Int>> =
        withContext(Dispatchers.IO) {
            try {
                val entities = db.groupDao().listGroups()
                entities.map { group ->
                    val defaults = db.groupDao().getDefaults(group.id)
                    val memberCount = db.groupDao().memberCount(group.id)
                    Triple(group, defaults, memberCount)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list group summaries", e)
                emptyList()
            }
        }

    /**
     * Gets group information for editing
     * @param groupId The ID of the group
     * @return GroupEditInfo containing entity, defaults, member IDs, and unavailable player IDs
     */
    suspend fun getGroupForEdit(groupId: String): GroupEditInfo =
        withContext(Dispatchers.IO) {
            try {
                GroupEditInfo(
                    entity = db.groupDao().listGroups().first { it.id == groupId },
                    defaults = db.groupDao().getDefaults(groupId),
                    memberIds = db.groupDao().memberIds(groupId),
                    unavailablePlayerIds = db.groupDao().getUnavailablePlayerIds(groupId)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get group for edit: $groupId", e)
                throw e
            }
        }
    
    /**
     * Gets all groups that a player belongs to
     * @param playerId The ID of the player
     * @return List of group entities the player belongs to
     */
    suspend fun getGroupsForPlayer(playerId: String): List<GroupEntity> = withContext(Dispatchers.IO) {
        try {
            db.groupDao().getGroupsForPlayer(playerId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get groups for player: $playerId", e)
            emptyList()
        }
    }
    
    /**
     * Updates the group memberships for a player
     * @param playerId The ID of the player
     * @param groupIds List of group IDs the player should belong to
     */
    suspend fun updatePlayerGroups(playerId: String, groupIds: List<String>) = withContext(Dispatchers.IO) {
        try {
            // Remove all existing memberships for this player
            val existingGroupIds = db.groupDao().getGroupIdsForPlayer(playerId)
            existingGroupIds.forEach { groupId ->
                db.groupDao().clearMembers(groupId)
                // Re-add members except this player
                val remainingMembers = db.groupDao().memberIds(groupId)
                    .filter { it != playerId }
                    .map { GroupMemberEntity(groupId, it) }
                if (remainingMembers.isNotEmpty()) {
                    db.groupDao().upsertMembers(remainingMembers)
                }
            }
            
            // Add new memberships
            val newMemberships = groupIds.map { GroupMemberEntity(it, playerId) }
            if (newMemberships.isNotEmpty()) {
                db.groupDao().upsertMembers(newMemberships)
            }
            Log.d(TAG, "Updated groups for player: $playerId, count: ${groupIds.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update groups for player: $playerId", e)
            throw e
        }
    }
    
    /**
     * Gets all players available for a specific group
     * Rule: Players with no group memberships are available everywhere
     *       Players with specific group memberships are only available in those groups
     *       Players marked as unavailable are filtered out
     * @param groupId The ID of the group
     * @return List of available players for this group
     */
    suspend fun getAvailablePlayersForGroup(groupId: String): List<com.oreki.stumpd.data.local.entity.PlayerEntity> =
        withContext(Dispatchers.IO) {
            try {
                val allPlayers = db.playerDao().list()
                val unavailableIds = db.groupDao().getUnavailablePlayerIds(groupId)
                val memberIds = db.groupDao().memberIds(groupId).toSet()
                allPlayers.filter { player ->
                    // Only show players who are explicit members of this group
                    val isMember = memberIds.contains(player.id)
                    // Check if player is not marked as unavailable
                    val isAvailable = !unavailableIds.contains(player.id)
                    isMember && isAvailable
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get available players for group: $groupId", e)
                emptyList()
            }
        }
    
    /**
     * Toggles the availability of a player in a group
     * @param groupId The ID of the group
     * @param playerId The ID of the player
     * @param isAvailable True to mark as available, false to mark as unavailable
     */
    suspend fun togglePlayerAvailability(groupId: String, playerId: String, isAvailable: Boolean) = 
        withContext(Dispatchers.IO) {
            try {
                if (isAvailable) {
                    // Mark as available (remove from unavailable list)
                    db.groupDao().markPlayerAvailable(groupId, playerId)
                    Log.d(TAG, "Marked player $playerId as available in group $groupId")
                } else {
                    // Mark as unavailable (add to unavailable list)
                    db.groupDao().markPlayerUnavailable(GroupUnavailablePlayerEntity(groupId, playerId))
                    Log.d(TAG, "Marked player $playerId as unavailable in group $groupId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle availability for player $playerId in group $groupId", e)
                throw e
            }
        }
    
    /**
     * Gets the list of unavailable player IDs for a group
     * @param groupId The ID of the group
     * @return List of player IDs that are marked as unavailable
     */
    suspend fun getUnavailablePlayerIds(groupId: String): List<String> = 
        withContext(Dispatchers.IO) {
            try {
                db.groupDao().getUnavailablePlayerIds(groupId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get unavailable players for group: $groupId", e)
                emptyList()
            }
        }
    
    /**
     * Replaces the unavailable player list for a group
     * @param groupId The ID of the group
     * @param unavailablePlayerIds List of player IDs to mark as unavailable
     */
    suspend fun replaceUnavailablePlayers(groupId: String, unavailablePlayerIds: List<String>) = 
        withContext(Dispatchers.IO) {
            try {
                // Clear existing unavailable players
                val currentUnavailable = db.groupDao().getUnavailablePlayerIds(groupId)
                currentUnavailable.forEach { playerId ->
                    db.groupDao().markPlayerAvailable(groupId, playerId)
                }
                
                // Add new unavailable players
                val entities = unavailablePlayerIds.distinct().map { 
                    GroupUnavailablePlayerEntity(groupId, it) 
                }
                if (entities.isNotEmpty()) {
                    entities.forEach { entity ->
                        db.groupDao().markPlayerUnavailable(entity)
                    }
                }
                Log.d(TAG, "Replaced unavailable players for group: $groupId, count: ${entities.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to replace unavailable players for group: $groupId", e)
                throw e
            }
        }
    
    /**
     * Get the default group ID from user preferences
     */
    suspend fun getDefaultGroupId(): String? = withContext(Dispatchers.IO) {
        db.userPreferencesDao().getValue("default_group_id")
    }
    
    /**
     * Set the default group ID in user preferences
     */
    suspend fun setDefaultGroupId(groupId: String) = withContext(Dispatchers.IO) {
        db.userPreferencesDao().upsert(UserPreferencesEntity("default_group_id", groupId))
    }
    
    /**
     * Clear the default group ID from user preferences
     */
    suspend fun clearDefaultGroupId() = withContext(Dispatchers.IO) {
        db.userPreferencesDao().delete("default_group_id")
    }
    
    // ========== Invite Code Methods ==========
    
    /**
     * Generates and saves a new invite code for a group
     * @param groupId The ID of the group
     * @return The generated invite code
     */
    suspend fun generateInviteCode(groupId: String): String = withContext(Dispatchers.IO) {
        try {
            val code = com.oreki.stumpd.data.util.InviteCodeManager.generateCode()
            db.groupDao().updateInviteCode(groupId, code)
            Log.d(TAG, "Generated invite code for group $groupId: $code")
            code
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate invite code for group: $groupId", e)
            throw e
        }
    }
    
    /**
     * Gets the invite code for a group
     * @param groupId The ID of the group
     * @return The invite code or null if not set
     */
    suspend fun getInviteCode(groupId: String): String? = withContext(Dispatchers.IO) {
        db.groupDao().getInviteCode(groupId)
    }
    
    /**
     * Gets or creates an invite code for a group
     * @param groupId The ID of the group
     * @return The existing or newly generated invite code
     */
    suspend fun getOrCreateInviteCode(groupId: String): String = withContext(Dispatchers.IO) {
        val existingCode = db.groupDao().getInviteCode(groupId)
        if (existingCode != null) {
            existingCode
        } else {
            generateInviteCode(groupId)
        }
    }
    
    /**
     * Finds a group by its invite code
     * @param inviteCode The invite code to search for
     * @return The group entity or null if not found
     */
    suspend fun findGroupByInviteCode(inviteCode: String): GroupEntity? = withContext(Dispatchers.IO) {
        val normalizedCode = com.oreki.stumpd.data.util.InviteCodeManager.normalizeCode(inviteCode)
        db.groupDao().getGroupByInviteCode(normalizedCode)
    }
    
    /**
     * Regenerates the invite code for a group (invalidates old code)
     * @param groupId The ID of the group
     * @return The new invite code
     */
    suspend fun regenerateInviteCode(groupId: String): String = withContext(Dispatchers.IO) {
        try {
            val newCode = com.oreki.stumpd.data.util.InviteCodeManager.generateCode()
            db.groupDao().updateInviteCode(groupId, newCode)
            Log.d(TAG, "Regenerated invite code for group $groupId: $newCode")
            newCode
        } catch (e: Exception) {
            Log.e(TAG, "Failed to regenerate invite code for group: $groupId", e)
            throw e
        }
    }
    
    // ========== Joined Groups Methods (for groups joined via invite codes) ==========
    
    /**
     * Joins a group using an invite code
     * @param inviteCode The invite code
     * @param remoteGroupId The remote group ID (from Firestore)
     * @param groupName The name of the group
     * @return true if joined successfully
     */
    suspend fun joinGroupWithCode(inviteCode: String, remoteGroupId: String, groupName: String): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                val normalizedCode = com.oreki.stumpd.data.util.InviteCodeManager.normalizeCode(inviteCode)
                val joinedGroup = JoinedGroupEntity(
                    groupId = remoteGroupId,
                    inviteCode = normalizedCode,
                    groupName = groupName,
                    joinedAt = System.currentTimeMillis()
                )
                db.groupDao().insertJoinedGroup(joinedGroup)
                Log.d(TAG, "Joined group $groupName with code $normalizedCode")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join group with code: $inviteCode", e)
                false
            }
        }
    
    /**
     * Gets all groups that have been joined via invite codes
     * @return List of joined groups
     */
    suspend fun getJoinedGroups(): List<JoinedGroupEntity> = withContext(Dispatchers.IO) {
        try {
            db.groupDao().getJoinedGroups()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get joined groups", e)
            emptyList()
        }
    }
    
    /**
     * Checks if a group has been joined via invite code
     * @param groupId The group ID to check
     * @return true if the group has been joined
     */
    suspend fun hasJoinedGroup(groupId: String): Boolean = withContext(Dispatchers.IO) {
        db.groupDao().getJoinedGroup(groupId) != null
    }
    
    /**
     * Checks if a group with the given invite code has already been joined
     * @param inviteCode The invite code to check
     * @return true if already joined
     */
    suspend fun hasJoinedGroupWithCode(inviteCode: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedCode = com.oreki.stumpd.data.util.InviteCodeManager.normalizeCode(inviteCode)
        db.groupDao().getJoinedGroupByCode(normalizedCode) != null
    }
    
    /**
     * Leaves a joined group
     * @param groupId The ID of the group to leave
     */
    suspend fun leaveJoinedGroup(groupId: String) = withContext(Dispatchers.IO) {
        try {
            db.groupDao().leaveJoinedGroup(groupId)
            Log.d(TAG, "Left joined group: $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave joined group: $groupId", e)
            throw e
        }
    }
    
    /**
     * Gets all joined group IDs (for syncing data from these groups)
     * @return List of group IDs
     */
    suspend fun getJoinedGroupIds(): List<String> = withContext(Dispatchers.IO) {
        try {
            db.groupDao().getJoinedGroupIds()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get joined group IDs", e)
            emptyList()
        }
    }
    
    /**
     * Gets a group by its ID
     * @param groupId The ID of the group
     * @return The group entity or null if not found
     */
    suspend fun getGroupById(groupId: String): GroupEntity? = withContext(Dispatchers.IO) {
        db.groupDao().getGroupById(groupId)
    }
    
    // ========== CLAIM CODE MANAGEMENT ==========
    
    /**
     * Gets the claim code for a group (only works for owned groups)
     * @param groupId The group ID
     * @return The claim code or null if not owner or not found
     */
    suspend fun getClaimCode(groupId: String): String? = withContext(Dispatchers.IO) {
        val group = db.groupDao().getGroupById(groupId)
        if (group?.isOwner == true) {
            group.claimCode
        } else {
            null // Non-owners shouldn't see the claim code
        }
    }
    
    /**
     * Gets or creates a claim code for a group
     * @param groupId The group ID
     * @return The claim code
     */
    suspend fun getOrCreateClaimCode(groupId: String): String? = withContext(Dispatchers.IO) {
        val group = db.groupDao().getGroupById(groupId) ?: return@withContext null
        
        // Only owners can have claim codes
        if (!group.isOwner) return@withContext null
        
        if (group.claimCode != null) {
            group.claimCode
        } else {
            // Generate new claim code
            val newCode = InviteCodeManager.generateClaimCode()
            db.groupDao().upsertGroup(group.copy(claimCode = newCode))
            Log.d(TAG, "Generated claim code for group $groupId")
            newCode
        }
    }
    
    /**
     * Regenerates the claim code for a group (invalidates old code)
     * @param groupId The group ID
     * @return The new claim code
     */
    suspend fun regenerateClaimCode(groupId: String): String? = withContext(Dispatchers.IO) {
        val group = db.groupDao().getGroupById(groupId) ?: return@withContext null
        
        // Only owners can regenerate claim codes
        if (!group.isOwner) return@withContext null
        
        val newCode = InviteCodeManager.generateClaimCode()
        db.groupDao().upsertGroup(group.copy(claimCode = newCode))
        Log.d(TAG, "Regenerated claim code for group $groupId")
        newCode
    }
    
    /**
     * Claims local ownership of a group after successful Firestore claim
     * @param groupId The group ID
     * @param claimCode The claim code used (to store locally)
     */
    suspend fun claimLocalOwnership(groupId: String, claimCode: String) = withContext(Dispatchers.IO) {
        val group = db.groupDao().getGroupById(groupId)
        if (group != null) {
            db.groupDao().upsertGroup(group.copy(isOwner = true, claimCode = claimCode))
            Log.d(TAG, "Claimed local ownership for group $groupId")
        }
    }
}
