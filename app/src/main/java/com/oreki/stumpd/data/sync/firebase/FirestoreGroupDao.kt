package com.oreki.stumpd.data.sync.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oreki.stumpd.data.local.entity.GroupDefaultEntity
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.local.entity.GroupMemberEntity
import com.oreki.stumpd.data.local.entity.GroupUnavailablePlayerEntity
import com.oreki.stumpd.data.sync.FirebaseConfig
import kotlinx.coroutines.tasks.await

/**
 * Firebase Firestore data access layer for groups
 * 
 * ACCESS IS RESTRICTED - Only members who joined via invite code can see group data
 * memberDeviceIds array tracks which device IDs have access to this group
 */
class FirestoreGroupDao(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    companion object {
        private const val TAG = "FirestoreGroupDao"
    }
    
    /**
     * Upload a group with all its members and settings
     * @param ownerId The user who created this group (automatically added to memberDeviceIds)
     */
    suspend fun uploadGroup(
        ownerId: String,
        group: GroupEntity,
        members: List<GroupMemberEntity>,
        unavailable: List<GroupUnavailablePlayerEntity>,
        defaults: GroupDefaultEntity?
    ) {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .document(group.id)
        
        // Check if group exists to preserve existing memberDeviceIds
        val existingDoc = docRef.get().await()
        val existingMemberDeviceIds = if (existingDoc.exists()) {
            (existingDoc.get("memberDeviceIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        } else {
            emptyList()
        }
        
        // Ensure owner is always in memberDeviceIds
        val memberDeviceIds = (existingMemberDeviceIds + ownerId).distinct()
        
        val data = mutableMapOf<String, Any?>(
            "id" to group.id,
            "name" to group.name,
            "inviteCode" to group.inviteCode,
            "claimCode" to group.claimCode, // Secret recovery code for ownership transfer
            "isOwner" to group.isOwner,
            "memberIds" to members.map { it.playerId }, // Player IDs (for cricket players)
            "memberDeviceIds" to memberDeviceIds, // Device IDs (for access control)
            "unavailablePlayerIds" to unavailable.map { it.playerId },
            FirebaseConfig.FIELD_OWNER_ID to ownerId,
            FirebaseConfig.FIELD_UPDATED_AT to System.currentTimeMillis()
        )
        
        // Add defaults if present
        defaults?.let {
            data["defaults"] = mapOf(
                "groundName" to it.groundName,
                "format" to it.format,
                "shortPitch" to it.shortPitch,
                "matchSettingsJson" to it.matchSettingsJson
            )
        }
        
        docRef.set(data, SetOptions.merge()).await()
        Log.d(TAG, "Uploaded group ${group.name} with ${memberDeviceIds.size} device members")
    }
    
    /**
     * Join a group using an invite code
     * Adds the device ID to the group's memberDeviceIds array
     * 
     * @param inviteCode The invite code to join with
     * @param deviceId The current device's user ID
     * @return GroupData if successful, null if code not found
     */
    suspend fun joinGroupWithInviteCode(inviteCode: String, deviceId: String): GroupData? {
        Log.d(TAG, "Attempting to join group with code: $inviteCode")
        
        // Find the group by invite code
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .whereEqualTo("inviteCode", inviteCode.uppercase())
            .limit(1)
            .get()
            .await()
        
        val doc = querySnapshot.documents.firstOrNull() ?: run {
            Log.w(TAG, "No group found with invite code: $inviteCode")
            return null
        }
        
        // Add device ID to memberDeviceIds array (atomic operation)
        doc.reference.update("memberDeviceIds", FieldValue.arrayUnion(deviceId)).await()
        Log.d(TAG, "Successfully joined group: ${doc.getString("name")}")
        
        // Return the group data
        return firestoreToGroupData(doc)
    }
    
    /**
     * Leave a group (remove device ID from memberDeviceIds)
     * @param groupId The group to leave
     * @param deviceId The device ID to remove
     */
    suspend fun leaveGroup(groupId: String, deviceId: String) {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .document(groupId)
        
        docRef.update("memberDeviceIds", FieldValue.arrayRemove(deviceId)).await()
        Log.d(TAG, "Left group: $groupId")
    }
    
    /**
     * Transfer ownership of a group to another member
     * Only the current owner can do this
     * 
     * @param groupId The group to transfer
     * @param newOwnerId The device ID of the new owner (must be a member)
     * @return true if successful, false if new owner is not a member
     */
    suspend fun transferOwnership(groupId: String, newOwnerId: String): Boolean {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .document(groupId)
        
        val doc = docRef.get().await()
        if (!doc.exists()) {
            Log.w(TAG, "Cannot transfer ownership - group not found: $groupId")
            return false
        }
        
        val memberDeviceIds = (doc.get("memberDeviceIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        
        // New owner must be a member
        if (newOwnerId !in memberDeviceIds) {
            Log.w(TAG, "Cannot transfer ownership - new owner is not a member")
            return false
        }
        
        docRef.update(FirebaseConfig.FIELD_OWNER_ID, newOwnerId).await()
        Log.d(TAG, "Transferred ownership of $groupId to $newOwnerId")
        return true
    }
    
    /**
     * Claim ownership of an orphaned group (owner device lost/deleted)
     * This requires special handling - a member can claim if they have the secret claim code
     * 
     * @param groupId The group to claim
     * @param claimCode A special code that was generated when the group was created
     * @param newOwnerId The device ID claiming ownership
     * @return true if successful
     */
    suspend fun claimOwnership(groupId: String, claimCode: String, newOwnerId: String): Boolean {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .document(groupId)
        
        val doc = docRef.get().await()
        if (!doc.exists()) {
            Log.w(TAG, "Cannot claim ownership - group not found: $groupId")
            return false
        }
        
        val storedClaimCode = doc.getString("claimCode")
        if (storedClaimCode == null || storedClaimCode != claimCode.uppercase()) {
            Log.w(TAG, "Cannot claim ownership - invalid claim code")
            return false
        }
        
        // Update owner and ensure claimer is in memberDeviceIds
        docRef.update(
            mapOf(
                FirebaseConfig.FIELD_OWNER_ID to newOwnerId,
                "memberDeviceIds" to FieldValue.arrayUnion(newOwnerId)
            )
        ).await()
        
        Log.d(TAG, "Claimed ownership of $groupId by $newOwnerId")
        return true
    }
    
    /**
     * Get list of member device IDs for a group
     */
    suspend fun getMemberDeviceIds(groupId: String): List<String> {
        val doc = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .document(groupId)
            .get()
            .await()
        
        return if (doc.exists()) {
            (doc.get("memberDeviceIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * Check if a device is a member of a group
     * @param groupId The group ID
     * @param deviceId The device ID to check
     * @return true if the device is a member
     */
    suspend fun isDeviceMember(groupId: String, deviceId: String): Boolean {
        val doc = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .document(groupId)
            .get()
            .await()
        
        if (!doc.exists()) return false
        
        val memberDeviceIds = (doc.get("memberDeviceIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return deviceId in memberDeviceIds
    }
    
    /**
     * Download only groups where this device is a member
     * @param deviceId The current device's user ID
     * @return List of groups the device has access to
     */
    suspend fun downloadMyGroups(deviceId: String): List<GroupData> {
        Log.d(TAG, "Downloading groups for device: $deviceId")
        
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .whereArrayContains("memberDeviceIds", deviceId)
            .get()
            .await()
        
        val groups = querySnapshot.documents.mapNotNull { doc ->
            try {
                firestoreToGroupData(doc)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse group: ${doc.id}", e)
                null
            }
        }
        
        Log.d(TAG, "Downloaded ${groups.size} groups for this device")
        return groups
    }
    
    /**
     * Download all groups - legacy method (returns only groups user is member of)
     */
    suspend fun downloadAllGroups(userId: String): List<GroupData> {
        return downloadMyGroups(userId)
    }
    
    /**
     * @deprecated Use downloadMyGroups(deviceId) instead
     */
    @Deprecated("Use downloadMyGroups(deviceId) instead", ReplaceWith("downloadMyGroups(deviceId)"))
    suspend fun downloadAllGroups(): List<GroupData> {
        // Without a device ID, we can't filter - return empty
        Log.w(TAG, "downloadAllGroups() called without deviceId - returning empty list")
        return emptyList()
    }
    
    /**
     * Delete a group from Firestore
     */
    suspend fun deleteGroup(groupId: String) {
        firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .document(groupId)
            .delete()
            .await()
    }
    
    /**
     * Delete a group - legacy method for compatibility
     */
    suspend fun deleteGroup(userId: String, groupId: String) {
        deleteGroup(groupId)
    }
    
    /**
     * Find a group by its invite code (GLOBAL search)
     * @param inviteCode The invite code to search for
     * @return GroupData or null if not found
     */
    suspend fun findGroupByInviteCode(inviteCode: String): GroupData? {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .whereEqualTo("inviteCode", inviteCode.uppercase())
            .limit(1)
            .get()
            .await()
        
        return querySnapshot.documents.firstOrNull()?.let { doc ->
            try {
                firestoreToGroupData(doc)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Get a group by its ID
     * @param groupId The group ID
     * @return GroupData or null if not found
     */
    suspend fun getGroupById(groupId: String): GroupData? {
        val doc = firestore
            .collection(FirebaseConfig.COLLECTION_GROUPS)
            .document(groupId)
            .get()
            .await()
        
        return if (doc.exists()) {
            try {
                firestoreToGroupData(doc)
            } catch (e: Exception) {
                null
            }
        } else null
    }
    
    private fun firestoreToGroupData(doc: DocumentSnapshot): GroupData {
        val groupEntity = GroupEntity(
            id = doc.getString("id") ?: doc.id,
            name = doc.getString("name") ?: "",
            inviteCode = doc.getString("inviteCode"),
            claimCode = null, // Claim code is secret - don't expose to non-owners
            isOwner = doc.getBoolean("isOwner") ?: false // When downloaded, it's not owned by this device
        )
        
        val memberIds = doc.get("memberIds") as? List<*>
        val members = memberIds?.mapNotNull { id ->
            (id as? String)?.let { GroupMemberEntity(groupEntity.id, it) }
        } ?: emptyList()
        
        val unavailableIds = doc.get("unavailablePlayerIds") as? List<*>
        val unavailable = unavailableIds?.mapNotNull { id ->
            (id as? String)?.let { GroupUnavailablePlayerEntity(groupEntity.id, it) }
        } ?: emptyList()
        
        val defaultsMap = doc.get("defaults") as? Map<*, *>
        val defaults = defaultsMap?.let {
            GroupDefaultEntity(
                groupId = groupEntity.id,
                groundName = it["groundName"] as? String ?: "",
                format = it["format"] as? String ?: "",
                shortPitch = it["shortPitch"] as? Boolean ?: false,
                matchSettingsJson = it["matchSettingsJson"] as? String
            )
        }
        
        return GroupData(groupEntity, members, unavailable, defaults)
    }
}

/**
 * Container for group data with all related entities
 */
data class GroupData(
    val group: GroupEntity,
    val members: List<GroupMemberEntity>,
    val unavailable: List<GroupUnavailablePlayerEntity>,
    val defaults: GroupDefaultEntity?
)
