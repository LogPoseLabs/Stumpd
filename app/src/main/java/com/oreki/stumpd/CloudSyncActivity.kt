package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.data.sync.SyncResult
import com.oreki.stumpd.data.sync.SyncState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity to manage cloud sync settings and status
 * 
 * Features:
 * - View sync status
 * - Manual sync trigger
 * - Auto-sync toggle
 * - Last sync time
 * - User ID display
 */
class CloudSyncActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CloudSyncScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as StumpdApplication
    val syncManager = app.syncManager
    
    val scope = rememberCoroutineScope()
    val syncState by syncManager.syncState.collectAsState()
    val syncMetadata by syncManager.syncMetadata.collectAsState()
    
    var autoSyncEnabled by remember { mutableStateOf(syncManager.isAutoSyncEnabled()) }
    var showSyncResult by remember { mutableStateOf<SyncResult?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (syncState) {
                        is SyncState.Success -> MaterialTheme.colorScheme.primaryContainer
                        is SyncState.Error -> MaterialTheme.colorScheme.errorContainer
                        is SyncState.Offline -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (syncState) {
                                is SyncState.Success -> Icons.Default.CheckCircle
                                is SyncState.Error -> Icons.Default.Error
                                is SyncState.Syncing -> Icons.Default.Sync
                                is SyncState.Offline -> Icons.Default.CloudOff
                                else -> Icons.Default.Cloud
                            },
                            contentDescription = null,
                            tint = when (syncState) {
                                is SyncState.Success -> MaterialTheme.colorScheme.primary
                                is SyncState.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
                        Text(
                            text = when (val state = syncState) {
                                is SyncState.Idle -> "Ready to sync"
                                is SyncState.Syncing -> "Syncing... (${state.progress}/${state.total})"
                                is SyncState.Success -> "Synced ${state.itemsSynced} items"
                                is SyncState.Error -> "Sync error: ${state.message}"
                                is SyncState.Offline -> "Offline - sync when connected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    val currentSyncState = syncState
                    if (currentSyncState is SyncState.Syncing) {
                        // Step progress bar (e.g., step 2 of 6)
                        Text(
                            text = "Step ${currentSyncState.progress}/${currentSyncState.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { currentSyncState.progress.toFloat() / currentSyncState.total.coerceAtLeast(1).toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Sub-step progress bar (e.g., match 3 of 15)
                        if (currentSyncState.subTotal > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${currentSyncState.message} (${currentSyncState.subProgress}/${currentSyncState.subTotal})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { currentSyncState.subProgress.toFloat() / currentSyncState.subTotal.coerceAtLeast(1).toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            // Sync Info Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    HorizontalDivider()
                    
                    InfoRow("User ID", syncMetadata.userId ?: "Not signed in")
                    InfoRow("Device ID", syncMetadata.deviceId.take(8) + "...")
                    
                    if (syncMetadata.lastSyncTimestamp > 0) {
                        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        val lastSyncDate = dateFormat.format(Date(syncMetadata.lastSyncTimestamp))
                        InfoRow("Last Sync", lastSyncDate)
                        InfoRow(
                            "Status",
                            if (syncMetadata.lastSyncSuccess) "Success" else "Failed"
                        )
                    } else {
                        InfoRow("Last Sync", "Never")
                    }
                    
                    if (syncMetadata.pendingUploads > 0) {
                        InfoRow("Pending Uploads", "${syncMetadata.pendingUploads} items")
                    }
                }
            }
            
            // Settings Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto-Sync",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Sync automatically when online",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = {
                                autoSyncEnabled = it
                                syncManager.setAutoSyncEnabled(it)
                            }
                        )
                    }
                }
            }
            
            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val result = syncManager.syncAll()
                            showSyncResult = result
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncState !is SyncState.Syncing
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload All to Cloud")
                }
                
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val result = syncManager.downloadAllFromCloud()
                            showSyncResult = result
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncState !is SyncState.Syncing
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download All from Cloud")
                }
            }
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Text(
                            text = "How it works",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        text = """
                            • Your data is stored locally in Room Database (works offline)
                            • When online, data syncs to Firebase Firestore (free tier)
                            • Automatic sync happens when network reconnects
                            • Manual sync available anytime
                            • All match data, players, and groups are synced
                            • No data limits on free tier for typical usage
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    
    // Show sync result dialog
    showSyncResult?.let { result ->
        AlertDialog(
            onDismissRequest = { showSyncResult = null },
            title = {
                Text(
                    when (result) {
                        is SyncResult.Success -> "Sync Successful"
                        is SyncResult.PartialSuccess -> "Partial Sync"
                        is SyncResult.Failure -> "Sync Failed"
                        is SyncResult.NoDataToSync -> "No Data"
                        is SyncResult.Offline -> "Offline"
                    }
                )
            },
            text = {
                Text(
                    when (result) {
                        is SyncResult.Success -> "Synced ${result.itemsSynced} items successfully"
                        is SyncResult.PartialSuccess -> "Synced ${result.synced} items, ${result.failed} failed"
                        is SyncResult.Failure -> result.message
                        is SyncResult.NoDataToSync -> "No data to sync"
                        is SyncResult.Offline -> "Device is offline"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showSyncResult = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
