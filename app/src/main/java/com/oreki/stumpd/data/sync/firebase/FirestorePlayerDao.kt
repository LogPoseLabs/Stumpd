package com.oreki.stumpd.data.sync.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oreki.stumpd.data.local.entity.PlayerEntity
import com.oreki.stumpd.data.sync.FirebaseConfig
import kotlinx.coroutines.tasks.await

/**
 * Firebase Firestore data access layer for players
 * 
 * DATA IS GLOBAL - All users can see all players
 */
class FirestorePlayerDao(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    /**
     * Upload a player to Firestore (GLOBAL)
     * @param ownerId The user who created this player
     */
    suspend fun uploadPlayer(ownerId: String, player: PlayerEntity) {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_PLAYERS)
            .document(player.id)
        
        val data = mapOf(
            "id" to player.id,
            "name" to player.name,
            "isJoker" to player.isJoker,
            FirebaseConfig.FIELD_OWNER_ID to ownerId,
            FirebaseConfig.FIELD_UPDATED_AT to System.currentTimeMillis()
        )
        
        docRef.set(data, SetOptions.merge()).await()
    }
    
    /**
     * Upload multiple players (GLOBAL)
     * @param ownerId The user who created these players
     */
    suspend fun uploadPlayers(ownerId: String, players: List<PlayerEntity>) {
        val batch = firestore.batch()
        
        players.forEach { player ->
            val docRef = firestore
                .collection(FirebaseConfig.COLLECTION_PLAYERS)
                .document(player.id)
            
            val data = mapOf(
                "id" to player.id,
                "name" to player.name,
                "isJoker" to player.isJoker,
                FirebaseConfig.FIELD_OWNER_ID to ownerId,
                FirebaseConfig.FIELD_UPDATED_AT to System.currentTimeMillis()
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    /**
     * Download all players (GLOBAL - returns all players from all users)
     */
    suspend fun downloadAllPlayers(): List<PlayerEntity> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_PLAYERS)
            .get()
            .await()
        
        return querySnapshot.documents.mapNotNull { doc ->
            try {
                firestoreToPlayer(doc)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Download all players - legacy method for compatibility
     */
    suspend fun downloadAllPlayers(userId: String): List<PlayerEntity> {
        return downloadAllPlayers()
    }
    
    /**
     * Delete a player from Firestore
     */
    suspend fun deletePlayer(playerId: String) {
        firestore
            .collection(FirebaseConfig.COLLECTION_PLAYERS)
            .document(playerId)
            .delete()
            .await()
    }
    
    /**
     * Delete a player - legacy method for compatibility
     */
    suspend fun deletePlayer(userId: String, playerId: String) {
        deletePlayer(playerId)
    }
    
    private fun firestoreToPlayer(doc: DocumentSnapshot): PlayerEntity {
        return PlayerEntity(
            id = doc.getString("id") ?: doc.id,
            name = doc.getString("name") ?: "",
            isJoker = doc.getBoolean("isJoker") ?: false
        )
    }
}
