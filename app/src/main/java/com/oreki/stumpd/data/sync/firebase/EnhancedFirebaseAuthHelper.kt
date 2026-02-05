package com.oreki.stumpd.data.sync.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.oreki.stumpd.R
import kotlinx.coroutines.tasks.await

/**
 * Enhanced Firebase Authentication helper
 * 
 * Supports:
 * - Anonymous authentication (no login)
 * - Google Sign-In (for multi-device sync)
 * - Account switching
 * - Persistent user state
 */
class EnhancedFirebaseAuthHelper(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    
    companion object {
        private const val TAG = "EnhancedAuthHelper"
        const val RC_SIGN_IN = 9001
    }
    
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    
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
     * Check if user is signed in with Google
     */
    fun isGoogleSignedIn(): Boolean {
        return auth.currentUser?.providerData?.any { 
            it.providerId == GoogleAuthProvider.PROVIDER_ID 
        } == true
    }
    
    /**
     * Check if user is anonymous
     */
    fun isAnonymous(): Boolean {
        return auth.currentUser?.isAnonymous == true
    }
    
    /**
     * Get user display name (Google name or "Anonymous")
     */
    fun getUserDisplayName(): String {
        return auth.currentUser?.displayName ?: "Anonymous User"
    }
    
    /**
     * Get user email (Google email or null)
     */
    fun getUserEmail(): String? {
        return auth.currentUser?.email
    }
    
    /**
     * Sign in anonymously (no credentials required)
     */
    suspend fun signInAnonymously(): FirebaseUser? {
        return try {
            val result = auth.signInAnonymously().await()
            Log.d(TAG, "Anonymous sign-in successful: ${result.user?.uid}")
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed", e)
            null
        }
    }
    
    /**
     * Get Google Sign-In intent
     * Call this to start Google Sign-In flow
     */
    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    /**
     * Handle Google Sign-In result from Activity
     * Call this in onActivityResult with the data Intent
     */
    suspend fun handleGoogleSignInResult(data: Intent?): Result<FirebaseUser> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.await()
            
            // Link or sign in with Firebase
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            
            // If already signed in anonymously, link accounts
            val currentUser = auth.currentUser
            val authResult = if (currentUser?.isAnonymous == true) {
                Log.d(TAG, "Linking anonymous account with Google")
                currentUser.linkWithCredential(credential).await()
            } else {
                Log.d(TAG, "Signing in with Google")
                auth.signInWithCredential(credential).await()
            }
            
            val user = authResult.user
            if (user != null) {
                Log.d(TAG, "Google sign-in successful: ${user.email}")
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to get user after sign-in"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sign out (Google and Firebase)
     */
    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
            auth.signOut()
            Log.d(TAG, "Sign out successful")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
        }
    }
    
    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean {
        return auth.currentUser != null
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
    
    /**
     * Upgrade from anonymous to Google account
     * This preserves all anonymous user data
     */
    suspend fun upgradeAnonymousToGoogle(googleAccount: GoogleSignInAccount): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(googleAccount.idToken, null)
            val currentUser = auth.currentUser
            
            if (currentUser?.isAnonymous == true) {
                Log.d(TAG, "Upgrading anonymous account to Google")
                val result = currentUser.linkWithCredential(credential).await()
                Result.success(result.user!!)
            } else {
                Log.d(TAG, "User not anonymous, signing in with Google")
                val result = auth.signInWithCredential(credential).await()
                Result.success(result.user!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account upgrade failed", e)
            Result.failure(e)
        }
    }
}
