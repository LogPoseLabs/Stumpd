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
import com.oreki.stumpd.ui.theme.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.PrimaryCta
import com.oreki.stumpd.ui.theme.ResultChip
import com.oreki.stumpd.ui.theme.SecondaryCta
import com.oreki.stumpd.ui.theme.SectionCard
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
    var matches by remember { mutableStateOf<List<MatchHistory>>(listOf()) }
    var debugInfo by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        matches = storageManager.getAllMatches()
        debugInfo = storageManager.debugStorage()
        android.util.Log.d("MatchHistory", "Loaded ${matches.size} matches")
        android.util.Log.d("MatchHistory", debugInfo)
    }

    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Match History",
                subtitle = "${matches.size} matches found",
                onBack = { (context as ComponentActivity).finish() },
                actions = {
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

                    OutlinedButton(
                        onClick = {
                            val downloadsPath = android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS
                            )
                            val backupFile = java.io.File(downloadsPath, "stumpd_backup.json")
                            if (storageManager.importMatches(backupFile.absolutePath)) {
                                matches = storageManager.getAllMatches()
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
            if (matches.isEmpty()) {
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
                            text = "No matches played yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Start scoring to build your match history!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(matches) { match ->
                        MatchHistoryCard(match = match) {
                            storageManager.deleteMatch(match.id)
                            matches = storageManager.getAllMatches()
                        }
                    }
                }
            }
        }
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
            // Header with date and delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateFormat.format(Date(match.matchDate)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

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

            // Replace the green Card winner section with:
            val winnerText = if (match.winnerTeam.equals("TIE", true)) {
                "Match Tied • ${match.winningMargin}"
            } else {
                "${match.winnerTeam} won by ${match.winningMargin}"
            }
            Row(Modifier.padding(top = 8.dp)) {
                ResultChip(text = winnerText, positive = !match.winnerTeam.equals("TIE", true))
            }

            // Show joker if present
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
