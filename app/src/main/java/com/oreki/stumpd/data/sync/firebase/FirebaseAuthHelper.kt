package com.oreki.stumpd.data.sync.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Firebase Authentication helper
 * 
 * Supports:
 * - Anonymous authentication (no login required)
 * - Google Sign-In (optional)
 * - Email/Password (optional)
 * 
 * For a simple scoring app, anonymous auth is sufficient.
 * Users can optionally upgrade to a full account later.
 */
class FirebaseAuthHelper(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    
    /**
     * Get current user if signed in
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser
    
    /**
     * Get current user ID (required for Firestore paths)
     */
    val currentUserId: String?
        get() = auth.currentUser?.uid
    
    /**
     * Sign in anonymously (no credentials required)
     * Perfect for simple offline-first apps
     */
    suspend fun signInAnonymously(): FirebaseUser? {
        return try {
            val result = auth.signInAnonymously().await()
            result.user
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * Sign out
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Ensure user is signed in (creates anonymous account if needed)
     */
    suspend fun ensureSignedIn(): String? {
        if (isSignedIn()) {
            return currentUserId
        }
        
        return signInAnonymously()?.uid
    }
}
