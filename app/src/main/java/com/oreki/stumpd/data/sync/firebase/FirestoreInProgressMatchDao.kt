package com.oreki.stumpd.data.sync.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oreki.stumpd.data.local.entity.InProgressMatchEntity
import com.oreki.stumpd.data.sync.FirebaseConfig
import com.oreki.stumpd.data.sync.inProgressMatchEntityFromFirestoreData
import com.oreki.stumpd.data.sync.inProgressMatchEntityToFirestoreUploadMap
import kotlinx.coroutines.tasks.await

/**
 * Firebase Firestore data access layer for in-progress matches
 * Syncs ongoing matches that haven't been completed yet
 * 
 * DATA IS GLOBAL - All users can see all in-progress matches for live spectating
 * ownerId field tracks who is scoring the match
 */
class FirestoreInProgressMatchDao(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    /**
     * Upload an in-progress match to Firestore (GLOBAL)
     * This allows live spectating and resuming matches
     * @param ownerId The user who is scoring this match
     */
    suspend fun uploadInProgressMatch(ownerId: String, match: InProgressMatchEntity) {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .document(match.matchId)
        
        val data = inProgressMatchEntityToFirestoreUploadMap(match, ownerId)
        
        docRef.set(data, SetOptions.merge()).await()
    }
    
    /**
     * Download the latest in-progress match (GLOBAL)
     */
    suspend fun downloadLatestInProgressMatch(): InProgressMatchEntity? {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .orderBy("lastSavedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        
        return querySnapshot.documents.firstOrNull()?.let { firestoreToInProgressMatch(it) }
    }
    
    /**
     * Download the latest in-progress match - legacy method for compatibility
     */
    suspend fun downloadLatestInProgressMatch(userId: String): InProgressMatchEntity? {
        return downloadLatestInProgressMatch()
    }
    
    /**
     * Download all in-progress matches (GLOBAL - returns all live matches)
     */
    suspend fun downloadAllInProgressMatches(): List<InProgressMatchEntity> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .get()
            .await()
        
        return querySnapshot.documents.mapNotNull { doc ->
            try {
                firestoreToInProgressMatch(doc)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Download all in-progress matches - legacy method for compatibility
     */
    suspend fun downloadAllInProgressMatches(userId: String): List<InProgressMatchEntity> {
        return downloadAllInProgressMatches()
    }
    
    /**
     * Delete an in-progress match from Firestore
     */
    suspend fun deleteInProgressMatch(matchId: String) {
        firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .document(matchId)
            .delete()
            .await()
    }
    
    /**
     * Delete an in-progress match - legacy method for compatibility
     */
    suspend fun deleteInProgressMatch(userId: String, matchId: String) {
        deleteInProgressMatch(matchId)
    }
    
    private fun firestoreToInProgressMatch(doc: DocumentSnapshot): InProgressMatchEntity =
        inProgressMatchEntityFromFirestoreData(doc.data ?: emptyMap(), doc.id)
}
