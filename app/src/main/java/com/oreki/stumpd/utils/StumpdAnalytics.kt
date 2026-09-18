package com.oreki.stumpd.utils

import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * Firebase Analytics + Crashlytics helpers. Safe to call off the main thread for log/record calls.
 */
object StumpdAnalytics {

    fun matchStarted(matchId: String? = null) {
        Firebase.analytics.logEvent("match_started") {
            matchId?.let { param("match_id", it) }
        }
        Firebase.crashlytics.log("match_started matchId=$matchId")
    }

    fun matchCompleted(matchId: String? = null) {
        Firebase.analytics.logEvent("match_completed") {
            matchId?.let { param("match_id", it) }
        }
        Firebase.crashlytics.log("match_completed matchId=$matchId")
    }

    fun syncTriggered(kind: String) {
        Firebase.analytics.logEvent("sync_triggered") {
            param("sync_kind", kind)
        }
        Firebase.crashlytics.log("sync_triggered kind=$kind")
    }

    fun syncSuccess(kind: String, itemCount: Int? = null, hadErrors: Boolean = false) {
        Firebase.analytics.logEvent("sync_success") {
            param("sync_kind", kind)
            itemCount?.let { param("item_count", it.toLong()) }
            param("had_errors", if (hadErrors) 1L else 0L)
        }
        Firebase.crashlytics.log("sync_success kind=$kind items=$itemCount hadErrors=$hadErrors")
    }

    fun syncError(kind: String, e: Throwable? = null, detail: String? = null) {
        val msg = detail ?: e?.message ?: "unknown"
        Firebase.analytics.logEvent("sync_error") {
            param("sync_kind", kind)
            param("detail", msg.take(100))
        }
        Firebase.crashlytics.log("sync_error kind=$kind detail=$msg")
        e?.let { Firebase.crashlytics.recordException(it) }
    }

    fun featureFlagToggled(flagName: String, enabled: Boolean) {
        Firebase.analytics.logEvent("feature_flag_toggled") {
            param("flag_name", flagName)
            param("enabled", if (enabled) 1L else 0L)
        }
        Firebase.crashlytics.log("feature_flag_toggled flag=$flagName enabled=$enabled")
    }

    fun recordNonFatal(e: Throwable) {
        Firebase.crashlytics.recordException(e)
    }
}
