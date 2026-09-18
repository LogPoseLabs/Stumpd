package com.oreki.stumpd.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed flags and per-flag overrides for non-deletion features.
 * [FeatureFlag.DELETIONS_ENABLED] uses the legacy `enable_deletions` key for backward compatibility.
 */
@Singleton
class LocalFeatureFlagProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : FeatureFlagProvider {

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasLocalOverride(flag: FeatureFlag): Boolean =
        flag != FeatureFlag.DELETIONS_ENABLED && prefs.contains(overrideKey(flag))

    override fun isEnabled(flag: FeatureFlag): Boolean {
        return when (flag) {
            FeatureFlag.DELETIONS_ENABLED -> prefs.getBoolean(KEY_DELETIONS, false)
            else -> {
                val k = overrideKey(flag)
                if (prefs.contains(k)) prefs.getBoolean(k, false)
                else defaultFor(flag)
            }
        }
    }

    override fun setEnabled(flag: FeatureFlag, enabled: Boolean) {
        when (flag) {
            FeatureFlag.DELETIONS_ENABLED ->
                prefs.edit().putBoolean(KEY_DELETIONS, enabled).apply()
            else ->
                prefs.edit().putBoolean(overrideKey(flag), enabled).apply()
        }
    }

    private fun overrideKey(flag: FeatureFlag): String = "override_${flag.name}"

    private fun defaultFor(flag: FeatureFlag): Boolean = flag.defaultEnabled

    companion object {
        internal const val PREFS_NAME = "stumpd_prefs"
        internal const val KEY_DELETIONS = "enable_deletions"

        fun from(context: Context): LocalFeatureFlagProvider =
            LocalFeatureFlagProvider(context.applicationContext)
    }
}
