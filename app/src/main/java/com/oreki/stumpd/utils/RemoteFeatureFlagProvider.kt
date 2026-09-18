package com.oreki.stumpd.utils

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production rollout via Firebase Remote Config. Client cannot publish config changes;
 * [setEnabled] is a no-op.
 */
@Singleton
class RemoteFeatureFlagProvider @Inject constructor() : FeatureFlagProvider {

    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

    init {
        remoteConfig.setDefaultsAsync(REMOTE_DEFAULTS)
        remoteConfig.fetchAndActivate()
    }

    /**
     * The server's answer where there is one, and the built-in default where there isn't.
     *
     * The fallback is the whole point. `setDefaultsAsync` is asynchronous, so for a moment after
     * launch Remote Config holds **no** value for the key and `getBoolean` returns `false` — not
     * the default that was just handed to it. Any flag meant to ship on therefore read as off
     * during the first seconds of a cold start, which is exactly long enough for the home screen
     * to compose without it. Asking for the value's *source* tells the two cases apart.
     */
    override fun isEnabled(flag: FeatureFlag): Boolean {
        val value = runCatching { remoteConfig.getValue(flag.remoteConfigKey) }.getOrNull()
        return when (value?.source) {
            FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT ->
                runCatching { value.asBoolean() }.getOrDefault(flag.defaultEnabled)

            // SOURCE_STATIC means Remote Config has nothing for this key, from the server or
            // from the defaults it hasn't finished applying.
            else -> flag.defaultEnabled
        }
    }

    override fun setEnabled(flag: FeatureFlag, enabled: Boolean) {
        // Remote Config is server-controlled; local/debug overrides live in [LocalFeatureFlagProvider].
    }

    private companion object {
        /** Built from the shared defaults, so the two can't drift apart. */
        val REMOTE_DEFAULTS: Map<String, Any> =
            FeatureFlag.entries.associate { it.remoteConfigKey to it.defaultEnabled }
    }
}

private val FeatureFlag.remoteConfigKey: String
    get() = when (this) {
        FeatureFlag.DELETIONS_ENABLED -> "feature_deletions_enabled"
        FeatureFlag.LIVE_SCORING -> "feature_live_scoring"
        FeatureFlag.CLOUD_SYNC -> "feature_cloud_sync"
        FeatureFlag.ANALYTICS -> "feature_analytics"
        FeatureFlag.TOURNAMENTS -> "feature_tournaments"
    }
