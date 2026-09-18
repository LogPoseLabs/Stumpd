package com.oreki.stumpd.utils

import android.content.Context

/**
 * Feature flags for enabling/disabling app features.
 * Deletions are controlled via Settings (Data Management) with password auth.
 *
 * For Hilt injection, use [FeatureFlagProvider] ([CompositeFeatureFlagProvider]).
 */
object FeatureFlags {

    fun isDeletionsEnabled(context: Context): Boolean {
        return LocalFeatureFlagProvider.from(context).isEnabled(FeatureFlag.DELETIONS_ENABLED)
    }

    fun setDeletionsEnabled(context: Context, enabled: Boolean) {
        LocalFeatureFlagProvider.from(context).setEnabled(FeatureFlag.DELETIONS_ENABLED, enabled)
        StumpdAnalytics.featureFlagToggled(FeatureFlag.DELETIONS_ENABLED.name, enabled)
    }
}
