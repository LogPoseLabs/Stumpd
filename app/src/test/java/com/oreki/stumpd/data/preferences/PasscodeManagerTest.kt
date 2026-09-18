package com.oreki.stumpd.data.preferences

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The passcodes guarding the app's destructive actions, and correcting a finished match.
 *
 * There are deliberately no length or character rules — a one-letter passcode is a valid choice —
 * and the two plaintext passwords older builds kept in SharedPreferences are folded in on
 * start-up so nothing has to be set again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PasscodeManagerTest {

    private val app = RuntimeEnvironment.getApplication()
    private val manager = PasscodeManager(app)

    private fun legacyPrefs() =
        app.getSharedPreferences("stumpd_prefs", Context.MODE_PRIVATE)

    @After
    fun tearDown() = runTest {
        manager.clear()
        PasscodeManager.matchEditLock(app).clear()
        legacyPrefs().edit().clear().apply()
    }

    @Test
    fun `no passcode is set to begin with`() = runTest {
        assertThat(manager.isSet()).isFalse()
    }

    @Test
    fun `the passcode that was set is accepted`() = runTest {
        manager.setPasscode("2468")

        assertThat(manager.isSet()).isTrue()
        assertThat(manager.verify("2468")).isTrue()
    }

    @Test
    fun `a single character is a valid passcode`() = runTest {
        // Deliberate: the user keeps a one-letter passcode for convenience.
        manager.setPasscode("q")

        assertThat(manager.isSet()).isTrue()
        assertThat(manager.verify("q")).isTrue()
        assertThat(manager.verify("Q")).isFalse()
    }

    @Test
    fun `letters, symbols and spaces all work`() = runTest {
        manager.setPasscode("hi there!")

        assertThat(manager.verify("hi there!")).isTrue()
        assertThat(manager.verify("hi there")).isFalse()
    }

    @Test
    fun `an empty passcode is not a passcode`() = runTest {
        manager.setPasscode("")

        assertThat(manager.isSet()).isFalse()
    }

    @Test
    fun `any other passcode is refused`() = runTest {
        manager.setPasscode("q")

        assertThat(manager.verify("w")).isFalse()
        assertThat(manager.verify("")).isFalse()
        assertThat(manager.verify("qq")).isFalse()
    }

    @Test
    fun `nothing verifies while no passcode is set`() = runTest {
        assertThat(manager.verify("")).isFalse()
        assertThat(manager.verify("q")).isFalse()
    }

    @Test
    fun `changing the passcode retires the old one`() = runTest {
        manager.setPasscode("1111")
        manager.setPasscode("2222")

        assertThat(manager.verify("1111")).isFalse()
        assertThat(manager.verify("2222")).isTrue()
    }

    @Test
    fun `clearing it takes the gate away`() = runTest {
        manager.setPasscode("q")
        manager.clear()

        assertThat(manager.isSet()).isFalse()
        assertThat(manager.verify("q")).isFalse()
    }

    @Test
    fun `a password from an older build is carried over`() = runTest {
        legacyPrefs().edit().putString("edit_password", "q").apply()

        manager.migrateLegacyPasswords()

        assertThat(manager.isSet()).isTrue()
        assertThat(manager.verify("q")).isTrue()
    }

    @Test
    fun `the deletions password is carried over too`() = runTest {
        legacyPrefs().edit().putString("deletion_password", "z").apply()

        manager.migrateLegacyPasswords()

        assertThat(manager.verify("z")).isTrue()
    }

    @Test
    fun `player edits win when the two old passwords disagree`() = runTest {
        legacyPrefs().edit()
            .putString("edit_password", "a")
            .putString("deletion_password", "b")
            .apply()

        manager.migrateLegacyPasswords()

        assertThat(manager.verify("a")).isTrue()
        assertThat(manager.verify("b")).isFalse()
    }

    @Test
    fun `migrating clears the plaintext copies`() = runTest {
        legacyPrefs().edit().putString("edit_password", "q").apply()

        manager.migrateLegacyPasswords()

        assertThat(legacyPrefs().getString("edit_password", null)).isNull()
        assertThat(legacyPrefs().getString("deletion_password", null)).isNull()
    }

    @Test
    fun `migrating leaves an already chosen passcode alone`() = runTest {
        manager.setPasscode("new")
        legacyPrefs().edit().putString("edit_password", "old").apply()

        manager.migrateLegacyPasswords()

        assertThat(manager.verify("new")).isTrue()
        assertThat(manager.verify("old")).isFalse()
    }

    @Test
    fun `migrating with nothing to migrate does nothing`() = runTest {
        manager.migrateLegacyPasswords()

        assertThat(manager.isSet()).isFalse()
    }

    @Test
    fun `the same passcode salts differently each time it is set`() = runTest {
        assertThat(PasscodeManager.digest("q", salt = "aaaa"))
            .isNotEqualTo(PasscodeManager.digest("q", salt = "bbbb"))
    }

    @Test
    fun `the digest is not the passcode`() = runTest {
        val digest = PasscodeManager.digest("hunter2", salt = "aaaa")

        assertThat(digest).doesNotContain("hunter2")
        assertThat(digest).hasLength(64)
    }

    // ── two locks, one implementation ───────────────────────────────────────────────────
    //
    // Correcting a finished match is guarded separately from deleting one: a correction rewrites
    // a result and a group's rankings, so the passcode that permits a delete shouldn't also
    // permit a rewrite. Both use this same class, so the tests that matter are about the two
    // stores staying independent.

    @Test
    fun `setting the app passcode leaves the match-editing one unset`() = runTest {
        PasscodeManager.appLock(app).setPasscode("a")

        assertThat(PasscodeManager.appLock(app).isSet()).isTrue()
        assertThat(PasscodeManager.matchEditLock(app).isSet()).isFalse()
    }

    @Test
    fun `each lock only accepts its own passcode`() = runTest {
        PasscodeManager.appLock(app).setPasscode("a")
        PasscodeManager.matchEditLock(app).setPasscode("b")

        assertThat(PasscodeManager.appLock(app).verify("a")).isTrue()
        assertThat(PasscodeManager.appLock(app).verify("b")).isFalse()
        assertThat(PasscodeManager.matchEditLock(app).verify("b")).isTrue()
        assertThat(PasscodeManager.matchEditLock(app).verify("a")).isFalse()
    }

    @Test
    fun `clearing one lock leaves the other alone`() = runTest {
        PasscodeManager.appLock(app).setPasscode("a")
        PasscodeManager.matchEditLock(app).setPasscode("b")

        PasscodeManager.matchEditLock(app).clear()

        assertThat(PasscodeManager.matchEditLock(app).isSet()).isFalse()
        assertThat(PasscodeManager.appLock(app).isSet()).isTrue()
        assertThat(PasscodeManager.appLock(app).verify("a")).isTrue()
    }

    @Test
    fun `the default constructor is the app lock, so existing callers are unaffected`() = runTest {
        PasscodeManager(app).setPasscode("a")

        assertThat(PasscodeManager.appLock(app).verify("a")).isTrue()
        assertThat(PasscodeManager.matchEditLock(app).isSet()).isFalse()
    }

    @Test
    fun `a legacy password is folded into the app lock only, never the editing one`() = runTest {
        legacyPrefs().edit().putString("edit_password", "old").apply()

        PasscodeManager.appLock(app).migrateLegacyPasswords()

        assertThat(PasscodeManager.appLock(app).verify("old")).isTrue()
        // Inheriting it here would hand the editing password a value chosen for deleting.
        assertThat(PasscodeManager.matchEditLock(app).isSet()).isFalse()
    }

}
