package com.oreki.stumpd.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.oreki.stumpd.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Manages OTA (Over-The-Air) app updates using Firebase Remote Config.
 * 
 * Flow:
 * 1. Check Firebase Remote Config for latest version info
 * 2. Compare with current app version
 * 3. If update available, download APK from provided URL
 * 4. Trigger system installer
 */
class AppUpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AppUpdateManager"
        
        // Firebase Remote Config keys
        const val KEY_LATEST_VERSION_CODE = "latest_version_code"
        const val KEY_LATEST_VERSION_NAME = "latest_version_name"
        const val KEY_APK_DOWNLOAD_URL = "apk_download_url"
        const val KEY_UPDATE_MESSAGE = "update_message"
        const val KEY_FORCE_UPDATE = "force_update"
        const val KEY_MIN_VERSION_CODE = "min_version_code" // Below this = force update
        
        // Cache duration (1 hour for production, 0 for debug)
        private const val CACHE_DURATION_SECONDS = 3600L
    }
    
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
    
    // Update state
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    
    init {
        // Configure Remote Config
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(
                if (BuildConfig.DEBUG) 0 else CACHE_DURATION_SECONDS
            )
            .build()
        
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Set default values
        remoteConfig.setDefaultsAsync(mapOf(
            KEY_LATEST_VERSION_CODE to BuildConfig.VERSION_CODE.toLong(),
            KEY_LATEST_VERSION_NAME to BuildConfig.VERSION_NAME,
            KEY_APK_DOWNLOAD_URL to "",
            KEY_UPDATE_MESSAGE to "A new version is available with bug fixes and improvements.",
            KEY_FORCE_UPDATE to false,
            KEY_MIN_VERSION_CODE to 1L
        ))
    }
    
    /**
     * Check if an update is available
     * Returns UpdateInfo if update available, null otherwise
     */
    suspend fun checkForUpdate(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Checking
                
                // Fetch latest config from Firebase
                remoteConfig.fetchAndActivate().await()
                
                val latestVersionCode = remoteConfig.getLong(KEY_LATEST_VERSION_CODE).toInt()
                val latestVersionName = remoteConfig.getString(KEY_LATEST_VERSION_NAME)
                val downloadUrl = remoteConfig.getString(KEY_APK_DOWNLOAD_URL)
                val updateMessage = remoteConfig.getString(KEY_UPDATE_MESSAGE)
                val forceUpdate = remoteConfig.getBoolean(KEY_FORCE_UPDATE)
                val minVersionCode = remoteConfig.getLong(KEY_MIN_VERSION_CODE).toInt()
                
                val currentVersionCode = BuildConfig.VERSION_CODE
                val currentVersionName = BuildConfig.VERSION_NAME
                
                Log.d(TAG, "Current version: $currentVersionName ($currentVersionCode)")
                Log.d(TAG, "Latest version: $latestVersionName ($latestVersionCode)")
                Log.d(TAG, "Download URL: $downloadUrl")
                
                // Check if update is available
                if (latestVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                    val isForceUpdate = forceUpdate || currentVersionCode < minVersionCode
                    
                    val updateInfo = UpdateInfo(
                        currentVersionCode = currentVersionCode,
                        currentVersionName = currentVersionName,
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        downloadUrl = downloadUrl,
                        updateMessage = updateMessage,
                        isForceUpdate = isForceUpdate
                    )
                    
                    _updateState.value = UpdateState.UpdateAvailable(updateInfo)
                    updateInfo
                } else {
                    _updateState.value = UpdateState.UpToDate
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
                null
            }
        }
    }
    
    /**
     * Check if app can install unknown apps
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }
    
    /**
     * Open settings to allow install from unknown sources
     */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    
    /**
     * Download and install the update
     */
    suspend fun downloadAndInstall(updateInfo: UpdateInfo) {
        withContext(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Downloading(0)
                
                // Clean up old APK files
                cleanupOldApks()
                
                // Download using DownloadManager
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                
                val fileName = "Stumpd-${updateInfo.latestVersionName}.apk"
                
                Log.d(TAG, "Starting download from: ${updateInfo.downloadUrl}")
                
                // GitHub releases use redirects - resolve the final URL first
                val finalUrl = resolveRedirects(updateInfo.downloadUrl)
                Log.d(TAG, "Final URL after redirects: $finalUrl")
                
                // Use setDestinationInExternalFilesDir for better compatibility
                val request = DownloadManager.Request(Uri.parse(finalUrl))
                    .setTitle("Stumpd Update")
                    .setDescription("Downloading version ${updateInfo.latestVersionName}")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/$fileName")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .addRequestHeader("User-Agent", "Stumpd/${BuildConfig.VERSION_NAME} Android")
                    .setMimeType("application/vnd.android.package-archive")
                
                downloadId = downloadManager.enqueue(request)
                Log.d(TAG, "Download enqueued with ID: $downloadId")
                
                // Wait for download to complete
                val success = waitForDownload(downloadManager, downloadId)
                
                if (success) {
                    // Get the downloaded file path from DownloadManager
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    var downloadedFilePath: String? = null
                    if (cursor.moveToFirst()) {
                        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (localUriIndex >= 0) {
                            val localUri = cursor.getString(localUriIndex)
                            downloadedFilePath = Uri.parse(localUri).path
                        }
                    }
                    cursor.close()
                    
                    if (downloadedFilePath != null) {
                        val destinationFile = File(downloadedFilePath)
                        if (destinationFile.exists()) {
                            Log.d(TAG, "Download successful: ${destinationFile.absolutePath}")
                            _updateState.value = UpdateState.ReadyToInstall(destinationFile)
                            installApk(destinationFile)
                        } else {
                            Log.e(TAG, "Downloaded file not found at: $downloadedFilePath")
                            _updateState.value = UpdateState.Error("Downloaded file not found")
                        }
                    } else {
                        Log.e(TAG, "Could not get downloaded file path")
                        _updateState.value = UpdateState.Error("Could not locate downloaded file")
                    }
                } else {
                    Log.e(TAG, "Download failed")
                    Log.e(TAG, "URL was: ${updateInfo.downloadUrl}")
                    _updateState.value = UpdateState.Error("Download failed. Check if the release exists.")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }
    
    /**
     * Wait for download to complete and track progress
     */
    private suspend fun waitForDownload(
        downloadManager: DownloadManager,
        downloadId: Long
    ): Boolean = suspendCancellableCoroutine { continuation ->
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                unregisterReceiver()
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                                val reasonText = when (reason) {
                                    DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
                                    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
                                    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                                    DownloadManager.ERROR_FILE_ERROR -> "File error"
                                    DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
                                    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
                                    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                                    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
                                    DownloadManager.ERROR_UNKNOWN -> "Unknown error"
                                    404 -> "File not found (404) - Check if release exists"
                                    else -> "Error code: $reason"
                                }
                                Log.e(TAG, "Download failed: $reasonText (code: $reason)")
                                unregisterReceiver()
                                if (continuation.isActive) {
                                    continuation.resume(false)
                                }
                            }
                        }
                    }
                    cursor.close()
                }
            }
            
            private fun unregisterReceiver() {
                try {
                    context.unregisterReceiver(this)
                    downloadReceiver = null
                } catch (e: Exception) {
                    // Already unregistered
                }
            }
        }
        
        downloadReceiver = receiver
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
        
        // Start progress tracking
        Thread {
            while (continuation.isActive) {
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        
                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                        val bytesTotal = cursor.getLong(bytesTotalIndex)
                        
                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                            _updateState.value = UpdateState.Downloading(progress)
                        }
                    }
                    cursor.close()
                    
                    Thread.sleep(500)
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
        
        continuation.invokeOnCancellation {
            try {
                context.unregisterReceiver(receiver)
                downloadReceiver = null
                downloadManager.remove(downloadId)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Install the downloaded APK
     */
    fun installApk(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(installIntent)
            _updateState.value = UpdateState.Installing
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start installation", e)
            _updateState.value = UpdateState.Error("Failed to start installation: ${e.message}")
        }
    }
    
    /**
     * Resolve redirects and get the final URL.
     * GitHub releases redirect to objects.githubusercontent.com
     */
    private fun resolveRedirects(urlString: String): String {
        try {
            var currentUrl = urlString
            var redirectCount = 0
            val maxRedirects = 10
            
            while (redirectCount < maxRedirects) {
                val url = URL(currentUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.requestMethod = "HEAD"
                connection.setRequestProperty("User-Agent", "Stumpd/${BuildConfig.VERSION_NAME} Android")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "URL: $currentUrl -> Response: $responseCode")
                
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        currentUrl = if (location.startsWith("http")) {
                            location
                        } else {
                            // Handle relative redirects
                            URL(url, location).toString()
                        }
                        redirectCount++
                        connection.disconnect()
                        continue
                    }
                }
                
                connection.disconnect()
                return currentUrl
            }
            
            Log.w(TAG, "Too many redirects, using original URL")
            return urlString
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve redirects: ${e.message}")
            return urlString
        }
    }
    
    /**
     * Clean up old downloaded APK files
     */
    private fun cleanupOldApks() {
        try {
            val updatesDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "updates"
            )
            
            if (updatesDir.exists()) {
                updatesDir.listFiles()?.forEach { file ->
                    if (file.extension == "apk") {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old APKs", e)
        }
    }
    
    /**
     * Cancel ongoing download
     */
    fun cancelDownload() {
        try {
            if (downloadId != -1L) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.remove(downloadId)
                downloadId = -1
            }
            
            downloadReceiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (e: Exception) {
                    // Already unregistered
                }
                downloadReceiver = null
            }
            
            _updateState.value = UpdateState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel download", e)
        }
    }
    
    /**
     * Reset state to idle
     */
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}

/**
 * Information about an available update
 */
data class UpdateInfo(
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val updateMessage: String,
    val isForceUpdate: Boolean
)

/**
 * Possible states of the update process
 */
sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data object Installing : UpdateState()
    data class Error(val message: String) : UpdateState()
}
