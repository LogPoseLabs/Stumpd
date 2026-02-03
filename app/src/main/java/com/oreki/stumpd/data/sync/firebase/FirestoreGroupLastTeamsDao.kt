package com.oreki.stumpd.data.sync.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oreki.stumpd.data.local.entity.GroupLastTeamsEntity
import com.oreki.stumpd.data.sync.FirebaseConfig
import kotlinx.coroutines.tasks.await

/**
 * Firebase Firestore data access layer for group last teams
 * Syncs last selected team configurations for each group
 */
class FirestoreGroupLastTeamsDao(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    /**
     * Upload group last teams configurations
     * This stores the last team selection for each group
     */
    suspend fun uploadGroupLastTeams(userId: String, lastTeams: List<GroupLastTeamsEntity>) {
        if (lastTeams.isEmpty()) return
        
        val batch = firestore.batch()
        
        lastTeams.forEach { config ->
            val docRef = firestore
                .collection(FirebaseConfig.COLLECTION_USERS)
                .document(userId)
                .collection("group_last_teams")
                .document(config.groupId)
            
            val data = mapOf(
                "groupId" to config.groupId,
                "team1PlayerIdsJson" to config.team1PlayerIdsJson,
                "team2PlayerIdsJson" to config.team2PlayerIdsJson,
                "team1Name" to config.team1Name,
                "team2Name" to config.team2Name,
                FirebaseConfig.FIELD_UPDATED_AT to System.currentTimeMillis()
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    /**
     * Download all group last teams configurations
     */
    suspend fun downloadAllGroupLastTeams(userId: String): List<GroupLastTeamsEntity> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_USERS)
            .document(userId)
            .collection("group_last_teams")
            .get()
            .await()
        
        return querySnapshot.documents.mapNotNull { doc ->
            try {
                firestoreToGroupLastTeams(doc)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Delete a group's last teams configuration
     */
    suspend fun deleteGroupLastTeams(userId: String, groupId: String) {
        firestore
            .collection(FirebaseConfig.COLLECTION_USERS)
            .document(userId)
            .collection("group_last_teams")
            .document(groupId)
            .delete()
            .await()
    }
    
    private fun firestoreToGroupLastTeams(doc: DocumentSnapshot): GroupLastTeamsEntity {
        return GroupLastTeamsEntity(
            groupId = doc.getString("groupId") ?: doc.id,
            team1PlayerIdsJson = doc.getString("team1PlayerIdsJson") ?: "",
            team2PlayerIdsJson = doc.getString("team2PlayerIdsJson") ?: "",
            team1Name = doc.getString("team1Name") ?: "",
            team2Name = doc.getString("team2Name") ?: ""
        )
    }
}
