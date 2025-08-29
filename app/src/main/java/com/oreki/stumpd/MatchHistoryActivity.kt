package com.oreki.stumpd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import com.oreki.stumpd.ui.theme.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.ResultChip
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.sectionContainer
import java.text.SimpleDateFormat
import java.util.*

class MatchHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MatchHistoryScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchHistoryScreen() {
    val context = LocalContext.current
    val storageManager = remember { MatchStorageManager(context) }
    val groupStorage = remember { PlayerGroupStorageManager(context) }

    var allMatches by remember { mutableStateOf<List<MatchHistory>>(emptyList()) }
    var debugInfo by remember { mutableStateOf("") }

    // Group filter state
    data class GroupFilter(val id: String?, val name: String) // id == null => All Groups; id == "__UNASSIGNED__" => legacy
    var selectedFilter by remember { mutableStateOf(GroupFilter(id = null, name = "All Groups")) }
    var showGroupPicker by remember { mutableStateOf(false) }

    // Load once
    LaunchedEffect(Unit) {
        allMatches = storageManager.getAllMatches()
        debugInfo = storageManager.debugStorage()
        android.util.Log.d("MatchHistory", "Loaded ${allMatches.size} matches")
        android.util.Log.d("MatchHistory", debugInfo)
    }

    // Apply filter
    val filteredMatches = remember(allMatches, selectedFilter) {
        when {
            selectedFilter.id == null -> allMatches
            else -> allMatches.filter { it.groupId == selectedFilter.id }
        }
    }

    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Archive",
                subtitle = "${filteredMatches.size} matches",
                onBack = { (context as ComponentActivity).finish() },
                actions = {
                    // Group filter button
                    FilledTonalButton(
                        onClick = { showGroupPicker = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Group", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(selectedFilter.name, fontSize = 8.sp)
                    }
                    Spacer(Modifier.width(8.dp))

                    // Export
                    FilledTonalButton(
                        onClick = {
                            val path = storageManager.exportMatches()
                            if (path != null) {
                                android.widget.Toast.makeText(context, "Backup saved to: $path", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(context, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Export", fontSize = 12.sp) }

                    Spacer(Modifier.width(8.dp))

                    // Import
                    OutlinedButton(
                        onClick = {
                            val downloadsPath = android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS
                            )
                            val backupFile = java.io.File(downloadsPath, "stumpd_backup.json")
                            if (storageManager.importMatches(backupFile.absolutePath)) {
                                allMatches = storageManager.getAllMatches()
                                android.widget.Toast.makeText(context, "Backup imported", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(context, "Import failed. Put stumpd_backup.json in Downloads", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Import", fontSize = 12.sp) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (filteredMatches.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🏏", fontSize = 48.sp)
                        Text(
                            text = "No matches found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when (selectedFilter.id) {
                                null -> "Start scoring to build your match history!"
                                else -> "No matches in ${selectedFilter.name}."
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredMatches) { match ->
                        MatchHistoryCard(
                            match = match,
                            onDelete = {
                                storageManager.deleteMatch(match.id)
                                allMatches = storageManager.getAllMatches()
                            }
                        )
                    }
                }
            }
        }
    }

    // Group picker dialog
    if (showGroupPicker) {
        val groups = remember { groupStorage.getAllGroups() }
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = { Text("Filter by Group") },
            text = {
                LazyColumn(Modifier.height(360.dp)) {
                    // All Groups
                    item {
                        ListItem(
                            headlineContent = { Text("All Groups") },
                            modifier = Modifier.clickable {
                                selectedFilter = GroupFilter(id = null, name = "All Groups")
                                showGroupPicker = false
                            }
                        )
                    }
                    // Real groups
                    items(groups) { g ->
                        val count = allMatches.count { it.groupId == g.id }
                        ListItem(
                            headlineContent = { Text(g.name) },
                            supportingContent = {
                                Text("$count matches", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.clickable {
                                selectedFilter = GroupFilter(id = g.id, name = g.name)
                                showGroupPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGroupPicker = false }) { Text("Close") } }
        )
    }
}

@Composable
fun MatchHistoryCard(
    match: MatchHistory,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = android.content.Intent(context, MatchDetailActivity::class.java)
                intent.putExtra("match_id", match.id)
                context.startActivity(intent)
            },
        colors = CardDefaults.cardColors(containerColor = sectionContainer()),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with date, group badge and delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dateFormat.format(Date(match.matchDate)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Group badge if present
                    match.groupName?.takeIf { it.isNotBlank() }?.let { gName ->
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = { Text(gName, fontSize = 10.sp) })
                    } ?: run {
                        // If no group, show "Unassigned" subtly
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("Unassigned", fontSize = 10.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Match",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Teams and scores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = match.team1Name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Label("${match.firstInningsRuns}/${match.firstInningsWickets}")
                }
                Text(
                    text = "vs",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = match.team2Name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Label("${match.secondInningsRuns}/${match.secondInningsWickets}")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Winner/Tie chip
            val winnerText = if (match.winnerTeam.equals("TIE", true)) {
                "Match Tied • ${match.winningMargin}"
            } else {
                "${match.winnerTeam} won by ${match.winningMargin}"
            }
            Row(Modifier.padding(top = 8.dp)) {
                ResultChip(text = winnerText, positive = !match.winnerTeam.equals("TIE", true))
            }

            // Joker if present
            match.jokerPlayerName?.let { jokerName ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🃏 Joker: $jokerName",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
