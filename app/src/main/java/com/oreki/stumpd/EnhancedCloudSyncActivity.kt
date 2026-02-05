package com.oreki.stumpd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.data.sync.SyncState

/**
 * Enhanced Cloud Sync Activity
 *
 * Features:
 * - Anonymous mode (no login required)
 * - Group invite code sharing
 * - Real-time sync status
 * - Manual sync controls
 */
class EnhancedCloudSyncActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                EnhancedCloudSyncScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedCloudSyncScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as StumpdApplication
    val syncManager = app.syncManager

    val syncState by syncManager.syncState.collectAsState()
    val syncMetadata by syncManager.syncMetadata.collectAsState()


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            // Device Info Card
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
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "This Device",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Device ID: ${syncMetadata.deviceId.take(8)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Data syncs automatically with group members via invite codes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sync Status Card
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
                            contentDescription = null
                        )

                        Column {
                            val state = syncState
                            Text(
                                text = when (state) {
                                    is SyncState.Idle -> "Ready to sync"
                                    is SyncState.Syncing -> "Syncing... (${state.progress}/${state.total})"
                                    is SyncState.Success -> "Synced ${state.itemsSynced} items"
                                    is SyncState.Error -> "Sync error"
                                    is SyncState.Offline -> "Offline"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Show detailed message when syncing
                            if (state is SyncState.Syncing) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val currentSyncState = syncState
                    if (currentSyncState is SyncState.Syncing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { currentSyncState.progress.toFloat() / currentSyncState.total.coerceAtLeast(1).toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Group Sharing Info Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Group Data Sharing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Share your group's data with members:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("1. Go to your Group settings", style = MaterialTheme.typography.bodySmall)
                        Text("2. Share the invite code with friends", style = MaterialTheme.typography.bodySmall)
                        Text("3. They enter the code to join your group", style = MaterialTheme.typography.bodySmall)
                        Text("4. All members see the same matches and stats", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Sync Actions
            // Note: Using launchXxx() methods so sync survives navigation away from this screen
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        syncManager.launchSyncAll()
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
                        syncManager.launchDownloadAllFromCloud()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncState !is SyncState.Syncing
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download All from Cloud")
                }
            }
        }
    }
}
