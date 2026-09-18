package com.oreki.stumpd.utils

interface FeatureFlagProvider {
    fun isEnabled(flag: FeatureFlag): Boolean
    fun setEnabled(flag: FeatureFlag, enabled: Boolean)
}
