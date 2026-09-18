package com.oreki.stumpd.data.sync

import android.content.SharedPreferences
import java.util.Calendar
import java.util.TimeZone

/**
 * Spreads uploading over multiple days so a large backlog can be synced entirely within the
 * Firestore free tier instead of dying at the daily cap.
 *
 * Firestore's free quota is 20,000 document writes and 50,000 reads per day, reset at midnight
 * US/Pacific. We deliberately budget well under that so ordinary app use (opening the app,
 * live-match sharing, spectating) still has room; running the cap dry would surface as errors
 * elsewhere in the app, not just in sync.
 *
 * Counters are keyed by the quota day, so the first sync after a reset gets a fresh allowance
 * and picks the backlog up where it left off.
 */
class SyncQuotaBudget(
    private val prefs: () -> SharedPreferences,
    private val writeAllowance: Int = DEFAULT_WRITE_ALLOWANCE,
    private val readAllowance: Int = DEFAULT_READ_ALLOWANCE,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** The quota day this instant falls in, in Firestore's reset timezone. */
    internal fun quotaDayKey(atMillis: Long = now()): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(QUOTA_RESET_ZONE))
        cal.timeInMillis = atMillis
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun spent(kind: String): Int {
        val p = prefs()
        val day = quotaDayKey()
        // A stale day key means the quota has reset since we last recorded anything.
        if (p.getString(KEY_DAY, null) != day) return 0
        return p.getInt(kind, 0)
    }

    fun writesSpentToday(): Int = spent(KEY_WRITES)

    fun readsSpentToday(): Int = spent(KEY_READS)

    fun remainingWrites(): Int = (writeAllowance - writesSpentToday()).coerceAtLeast(0)

    fun remainingReads(): Int = (readAllowance - readsSpentToday()).coerceAtLeast(0)

    /**
     * Whether [writes] more document writes fit in today's allowance.
     *
     * When nothing has been spent yet an oversized item is still allowed through: a single match
     * bigger than the whole daily allowance would otherwise block the queue forever.
     */
    fun canAffordWrites(writes: Int): Boolean {
        val remaining = remainingWrites()
        if (writes <= remaining) return true
        return writesSpentToday() == 0
    }

    fun recordWrites(writes: Int) = record(KEY_WRITES, writes)

    fun recordReads(reads: Int) = record(KEY_READS, reads)

    private fun record(kind: String, amount: Int) {
        if (amount <= 0) return
        val p = prefs()
        val day = quotaDayKey()
        val editor = p.edit()
        if (p.getString(KEY_DAY, null) != day) {
            // First activity of a new quota day: reset both counters together.
            editor.putString(KEY_DAY, day).putInt(KEY_WRITES, 0).putInt(KEY_READS, 0)
            editor.putInt(kind, amount)
        } else {
            editor.putInt(kind, p.getInt(kind, 0) + amount)
        }
        editor.apply()
    }

    /** Drops today's counters. Only for tests and an explicit user-triggered reset. */
    fun reset() {
        prefs().edit().remove(KEY_DAY).remove(KEY_WRITES).remove(KEY_READS).apply()
    }

    companion object {
        /** Firestore free-tier quotas reset at midnight US/Pacific. */
        private const val QUOTA_RESET_ZONE = "America/Los_Angeles"

        /** Of a 20,000/day write quota, leave headroom for live matches and normal use. */
        const val DEFAULT_WRITE_ALLOWANCE = 15_000

        /** Of a 50,000/day read quota. */
        const val DEFAULT_READ_ALLOWANCE = 30_000

        private const val KEY_DAY = "quota_day_key"
        private const val KEY_WRITES = "quota_writes_today"
        private const val KEY_READS = "quota_reads_today"
    }
}
