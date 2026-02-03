package com.oreki.stumpd.data.sync.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oreki.stumpd.data.local.entity.UserPreferencesEntity
import com.oreki.stumpd.data.sync.FirebaseConfig
import kotlinx.coroutines.tasks.await

/**
 * Firebase Firestore data access layer for user preferences
 * Syncs app settings across devices
 */
class FirestoreUserPreferencesDao(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    /**
     * Upload user preferences to Firestore
     */
    suspend fun uploadPreferences(userId: String, preferences: List<UserPreferencesEntity>) {
        if (preferences.isEmpty()) return
        
        val batch = firestore.batch()
        
        preferences.forEach { pref ->
            val docRef = firestore
                .collection(FirebaseConfig.COLLECTION_USERS)
                .document(userId)
                .collection("preferences")
                .document(pref.key)
            
            val data = mapOf(
                "key" to pref.key,
                "value" to pref.value,
                FirebaseConfig.FIELD_UPDATED_AT to System.currentTimeMillis()
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    /**
     * Download all user preferences from Firestore
     */
    suspend fun downloadAllPreferences(userId: String): List<UserPreferencesEntity> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_USERS)
            .document(userId)
            .collection("preferences")
            .get()
            .await()
        
        return querySnapshot.documents.mapNotNull { doc ->
            try {
                UserPreferencesEntity(
                    key = doc.getString("key") ?: doc.id,
                    value = doc.getString("value") ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Delete all preferences from Firestore
     */
    suspend fun deleteAllPreferences(userId: String) {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_USERS)
            .document(userId)
            .collection("preferences")
            .get()
            .await()
        
        val batch = firestore.batch()
        querySnapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }
}
