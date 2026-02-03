package com.oreki.stumpd.utils

import android.content.Context

/**
 * Feature flags for enabling/disabling app features
 * These flags can be controlled via the app settings
 */
object FeatureFlags {
    /**
     * Enable/disable delete buttons across the app
     * - When true: Shows delete buttons for groups and players
     * - When false: Hides delete buttons for groups and players
     * 
     * This is controlled via Settings (Data Management screen)
     * and requires password authentication to change
     */
    fun isDeletionsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("stumpd_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("enable_deletions", false) // Default: disabled
    }
    
    fun setDeletionsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("stumpd_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enable_deletions", enabled).apply()
    }
    
    /**
     * Enable/disable unlimited undo in scoring
     * - When true: Can undo all the way back to match start
     * - When false: Can only undo last 2 balls
     * 
     * This is controlled via the Scoring screen
     * and requires password authentication to change
     */
    fun isUnlimitedUndoEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("stumpd_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("unlimited_undo", false) // Default: disabled (limited to 2)
    }
    
    fun setUnlimitedUndoEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("stumpd_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("unlimited_undo", enabled).apply()
    }
}

