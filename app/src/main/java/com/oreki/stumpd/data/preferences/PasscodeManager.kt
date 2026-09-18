package com.oreki.stumpd.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A passcode, stored as a salted SHA-256 digest.
 *
 * There are two of these, deliberately, and they share every line of this class:
 *
 * - **The app passcode** ([appLock]) guards destructive actions — deleting a match, editing or
 *   deleting a player, turning deletions on or off. It replaced two older mechanisms that held
 *   passwords in plain text; [migrateLegacyPasswords] folds those in on start-up.
 * - **The match-editing password** ([matchEditLock]) guards *correcting* a finished match. It's
 *   separate because a correction rewrites a result, a player's career figures and the group's
 *   rankings, where a delete just removes a row — so knowing the passcode that lets you delete a
 *   match shouldn't also let you rewrite one.
 *
 * Any non-empty passcode is accepted: no length or character rules. It's a guard against a
 * mis-tap, not against anyone holding the phone — the data it protects sits in a database they
 * could read anyway — so convenience wins. Stored hashed so it isn't in preferences in the clear,
 * which also means a forgotten passcode can't be read back; the match-editing one can be reset
 * with the app passcode, and the app passcode can only be changed with itself.
 */
class PasscodeManager internal constructor(
    private val context: Context,
    private val store: DataStore<Preferences>,
) {
    /** The app passcode, so existing call sites keep working unchanged. */
    constructor(context: Context) : this(context, storeNamed(context, APP_LOCK_STORE))

    private val keySalt = stringPreferencesKey("salt")
    private val keyDigest = stringPreferencesKey("digest")

    suspend fun isSet(): Boolean = withContext(Dispatchers.IO) {
        runCatching { store.data.first()[keyDigest] != null }
            .getOrDefault(false)
    }

    /** Replaces any existing passcode. Callers verify the old one first. Blank does nothing. */
    suspend fun setPasscode(passcode: String) = withContext(Dispatchers.IO) {
        if (passcode.isEmpty()) return@withContext
        val salt = newSalt()
        store.edit { prefs ->
            prefs[keySalt] = salt
            prefs[keyDigest] = digest(passcode, salt)
        }
    }

    suspend fun verify(passcode: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = store.data.first()
            val salt = prefs[keySalt] ?: return@runCatching false
            val stored = prefs[keyDigest] ?: return@runCatching false
            // Constant-time compare, so a wrong code can't be narrowed down by timing.
            MessageDigest.isEqual(stored.toByteArray(), digest(passcode, salt).toByteArray())
        }.getOrDefault(false)
    }

    /** Removes the passcode, so destructive actions only need their confirmation dialog. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        store.edit { it.clear() }
    }

    /**
     * Moves a password set by an older build into this store, then clears the plaintext copies.
     *
     * Does nothing once a passcode is set here, so it can run on every start-up. Only ever called
     * on the app lock: the old `edit_password` and `deletion_password` were both about deleting
     * things, and seeding the match-editing password from them would hand it a value the user
     * never chose for that purpose.
     */
    suspend fun migrateLegacyPasswords() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = LEGACY_KEYS.firstNotNullOfOrNull { key ->
            prefs.getString(key, null)?.takeIf { it.isNotEmpty() }
        }
        if (legacy != null && !isSet()) {
            setPasscode(legacy)
        }
        if (legacy != null) {
            // Don't leave a password sitting in preferences in the clear.
            prefs.edit().apply { LEGACY_KEYS.forEach { remove(it) } }.apply()
        }
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    companion object {
        private const val LEGACY_PREFS = "stumpd_prefs"

        /** Named for the delete lock it started as, so a passcode already set isn't lost. */
        private const val APP_LOCK_STORE = "delete_lock_v1"
        private const val MATCH_EDIT_LOCK_STORE = "match_edit_lock_v1"

        /**
         * One DataStore per file per process.
         *
         * `preferencesDataStore` is a property delegate, so its name is fixed at compile time and
         * can't be parameterised; and building a second DataStore over the same file throws. This
         * keeps one instance per name instead.
         */
        private val stores = mutableMapOf<String, DataStore<Preferences>>()

        @Synchronized
        private fun storeNamed(context: Context, name: String): DataStore<Preferences> =
            stores.getOrPut(name) {
                PreferenceDataStoreFactory.create(
                    produceFile = { context.applicationContext.preferencesDataStoreFile(name) }
                )
            }

        /** Guards deleting a match, editing or deleting a player, and the deletions toggle. */
        fun appLock(context: Context): PasscodeManager =
            PasscodeManager(context, storeNamed(context, APP_LOCK_STORE))

        /** Guards correcting a finished match. Separate from [appLock] on purpose. */
        fun matchEditLock(context: Context): PasscodeManager =
            PasscodeManager(context, storeNamed(context, MATCH_EDIT_LOCK_STORE))

        /** Player edits came first, so it wins if the two ever disagreed. */
        private val LEGACY_KEYS = listOf("edit_password", "deletion_password")

        internal fun digest(passcode: String, salt: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$salt:$passcode".toByteArray())
                .toHex()
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
