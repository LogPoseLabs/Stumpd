package com.oreki.stumpd

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.oreki.stumpd.data.sync.CompleteSyncManager
import com.oreki.stumpd.data.sync.SyncScheduler
import android.app.UiModeManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.oreki.stumpd.data.preferences.DarkModeOption
import com.oreki.stumpd.data.preferences.ThemePreferencesManager
import com.oreki.stumpd.data.preferences.PasscodeManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StumpdApplication : Application() {

    @Inject lateinit var syncManager: CompleteSyncManager
    @Inject lateinit var syncScheduler: SyncScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Folds the old plaintext `edit_password` / `deletion_password` into the single passcode. */
    private val passcodeManager by lazy { PasscodeManager(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Tells the platform which night mode the app considers itself to be in, so the `-night`
     * resource qualifier follows the user's choice rather than the system's.
     *
     * Dark mode here is a DataStore setting ([DarkModeOption]), but resource qualifiers key off
     * the system's mode. Forcing dark in-app on a light-system phone therefore resolved the
     * *light* launch window for all 19 activities — a white flash on every screen open. The
     * Compose side already honours the setting; this brings the window in line.
     */
    private fun applyStoredNightMode() {
        ThemePreferencesManager.ensureLoaded(this)
        applicationScope.launch {
            ThemePreferencesManager.settingsFlow.collect { settings ->
                val mode = when (settings.darkMode) {
                    DarkModeOption.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    DarkModeOption.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    DarkModeOption.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                mainHandler.post { AppCompatDelegate.setDefaultNightMode(mode) }

                // Activities here are ComponentActivity, not AppCompatActivity, so the call above
                // doesn't move the resource qualifier on its own. This does, from API 31.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val uiModeManager = getSystemService(UiModeManager::class.java)
                    uiModeManager?.setApplicationNightMode(
                        when (settings.darkMode) {
                            DarkModeOption.LIGHT -> UiModeManager.MODE_NIGHT_NO
                            DarkModeOption.DARK -> UiModeManager.MODE_NIGHT_YES
                            DarkModeOption.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                        }
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { passcodeManager.migrateLegacyPasswords() }
        applyStoredNightMode()

        Log.d(TAG, "StumpdApplication starting...")

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase - sync will not work", e)
            Log.e(TAG, "Make sure you have added google-services.json to your app folder")
        }

        applicationScope.launch {
            try {
                syncManager.initialize()
                if (syncManager.isAutoSyncEnabled()) {
                    syncScheduler.schedulePeriodicIncrementalSync()
                } else {
                    syncScheduler.cancelPeriodicIncrementalSync()
                }
                Log.d(TAG, "Sync manager initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize sync manager", e)
            }
        }

        applicationScope.launch {
            syncManager.toastEvents.collect { message ->
                mainHandler.post {
                    Toast.makeText(this@StumpdApplication, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val TAG = "StumpdApplication"
    }
}
