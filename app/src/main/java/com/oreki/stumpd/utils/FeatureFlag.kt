package com.oreki.stumpd.utils

enum class FeatureFlag {
    DELETIONS_ENABLED,
    LIVE_SCORING,
    CLOUD_SYNC,
    ANALYTICS,

    /** Tournaments: persistent teams, fixtures and a table. */
    TOURNAMENTS,
}

/**
 * What a flag is worth when nobody has said otherwise — no local override, and nothing from
 * Remote Config.
 *
 * One list, read by both providers. It used to be two, and they could disagree: Remote Config's
 * defaults are applied *asynchronously*, so a read taken moments after launch found no value at
 * all and returned `false` — with no fallback, a flag meant to ship on was off for the first
 * seconds of every cold start. That is how the tournaments card failed to appear on a device
 * where everything else was right.
 */
val FeatureFlag.defaultEnabled: Boolean
    get() = when (this) {
        FeatureFlag.DELETIONS_ENABLED -> false
        FeatureFlag.LIVE_SCORING -> true
        FeatureFlag.CLOUD_SYNC -> true
        FeatureFlag.ANALYTICS -> false
        FeatureFlag.TOURNAMENTS -> true
    }
