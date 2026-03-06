package com.oreki.stumpd.data.manager

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores temporary scoring access per group (local to this device only).
 * Used when a member enters a valid OTP to gain time-limited ability to start/score matches.
 */
class ScoringAccessManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setTemporaryAccess(groupId: String, expiresAtMillis: Long) {
        prefs.edit().putLong(key(groupId), expiresAtMillis).apply()
    }

    fun hasTemporaryScoringAccess(groupId: String?): Boolean {
        if (groupId == null) return false
        val expiry = prefs.getLong(key(groupId), 0L)
        return expiry > System.currentTimeMillis()
    }

    fun clearAccess(groupId: String) {
        prefs.edit().remove(key(groupId)).apply()
    }

    private fun key(groupId: String) = "scoring_access_$groupId"

    companion object {
        private const val PREFS_NAME = "scoring_access"
    }
}
