package com.oreki.stumpd.utils

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletions follow the user-controlled local preference only.
 * Other flags use Remote Config, with optional local overrides from [LocalFeatureFlagProvider].
 */
@Singleton
class CompositeFeatureFlagProvider @Inject constructor(
    private val local: LocalFeatureFlagProvider,
    private val remote: RemoteFeatureFlagProvider,
) : FeatureFlagProvider {

    override fun isEnabled(flag: FeatureFlag): Boolean {
        if (flag == FeatureFlag.DELETIONS_ENABLED) {
            return local.isEnabled(flag)
        }
        if (local.hasLocalOverride(flag)) {
            return local.isEnabled(flag)
        }
        return remote.isEnabled(flag)
    }

    override fun setEnabled(flag: FeatureFlag, enabled: Boolean) {
        local.setEnabled(flag, enabled)
    }
}
