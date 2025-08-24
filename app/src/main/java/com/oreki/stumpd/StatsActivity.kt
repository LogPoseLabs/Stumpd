package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.SectionTitle
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.sectionContainer

class StatsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StatsScreen()
                }
            }
        }
    }
}

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val playerStorage = remember { EnhancedPlayerStorageManager(context) }
    val matchStorage = remember { MatchStorageManager(context) }

    var players by remember { mutableStateOf<List<PlayerDetailedStats>>(emptyList()) }
    var matches by remember { mutableStateOf<List<MatchHistory>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("All Time") }
    var showFilterDialog by remember { mutableStateOf(false) }

    val groupStorage = remember { PlayerGroupStorageManager(context) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) } // null => All Groups
    var selectedGroupName by remember { mutableStateOf("All Groups") }
    var showGroupPicker by remember { mutableStateOf(false) }

    LaunchedEffect(selectedGroupId) {
        // Filter matches by group selection first
        val all = matchStorage.getAllMatches()
        val groupFiltered = selectedGroupId?.let { gId -> all.filter { it.groupId == gId } } ?: all
        matches = groupFiltered

        // Recompute detailed players from groupFiltered only
        val detailed = EnhancedPlayerStorageManager(context).computeFromMatches(groupFiltered)
        players = detailed
    }


    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Statistics",
                subtitle = "${players.size} players - ${matches.size} matches • $selectedGroupName",
                onBack = {
                    val intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showGroupPicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Group", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(selectedGroupName, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))

                    // Compact tonal Filter action (similar size to Import/Export you added)
                    FilledTonalButton(
                        onClick = { showFilterDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Filter",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(selectedFilter, fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {

            if (players.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "📊", fontSize = 48.sp)
                        Text(
                            text = "No statistics available",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Play some matches to see your stats!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { SectionTitle("🏏 Top Batsmen"); Spacer(Modifier.height(8.dp)) }
                    val topBatsmen = players.sortedByDescending { it.totalRuns }.take(5)
                    items(topBatsmen) { player ->
                        PlayerStatsCard(
                            player = player,
                            statType = "Batting",
                            primaryStat = "${player.totalRuns} runs",
                            secondaryStat = "Avg: ${"%.1f".format(player.battingAverage)} • SR: ${
                                "%.1f".format(
                                    player.strikeRate
                                )
                            }",
                            onClick = {
                                val intent = Intent(context, PlayerDetailActivity::class.java)
                                intent.putExtra("player_name", player.name)
                                context.startActivity(intent)
                            }
                        )
                    }
                    item { SectionTitle("⚾ Top Bowlers"); Spacer(Modifier.height(8.dp)) }

                    val topBowlers = players.filter { it.totalWickets > 0 }
                        .sortedByDescending { it.totalWickets }.take(5)
                    items(topBowlers) { player ->
                        PlayerStatsCard(
                            player = player,
                            statType = "Bowling",
                            primaryStat = "${player.totalWickets} wickets",
                            secondaryStat = "Avg: ${"%.1f".format(player.bowlingAverage)} • Eco: ${
                                "%.1f".format(
                                    player.economyRate
                                )
                            }",
                            onClick = {
                                val intent = Intent(context, PlayerDetailActivity::class.java)
                                intent.putExtra("player_name", player.name)
                                context.startActivity(intent)
                            }
                        )
                    }

                    item { SectionTitle("🏆 Match Winners"); Spacer(Modifier.height(8.dp)) }
                    val topWinners = players
                        .filter { it.totalMatches > 0 }
                        .map { player ->
                            val wins = player.matchPerformances.count { it.isWinner }
                            val winPercentage = (wins.toDouble() / player.totalMatches) * 100
                            player to winPercentage
                        }
                        .sortedByDescending { it.second }
                        .take(5)

                    items(topWinners) { (player, winPercentage) ->
                        val wins = player.matchPerformances.count { it.isWinner }
                        PlayerStatsCard(
                            player = player,
                            statType = "Wins",
                            primaryStat = "${winPercentage.toInt()}%",
                            secondaryStat = "$wins wins in ${player.totalMatches} matches",
                            onClick = {
                                val intent = Intent(context, PlayerDetailActivity::class.java)
                                intent.putExtra("player_name", player.name)
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }

        if (showGroupPicker) {
            val groups = groupStorage.getAllGroups()
            AlertDialog(
                onDismissRequest = { showGroupPicker = false },
                title = { Text("Filter by Group") },
                text = {
                    LazyColumn(Modifier.height(360.dp)) {
                        item {
                            ListItem(
                                headlineContent = { Text("All Groups") },
                                modifier = Modifier.clickable {
                                    selectedGroupId = null
                                    selectedGroupName = "All Groups"
                                    showGroupPicker = false
                                }
                            )
                        }
                        items(groups) { g ->
                            ListItem(
                                headlineContent = { Text(g.name) },
                                supportingContent = {
                                    val count = matches.count { it.groupId == g.id } // current list; optional
                                    Text("$count matches", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                modifier = Modifier.clickable {
                                    selectedGroupId = g.id
                                    selectedGroupName = g.name
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

        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("Filter Statistics") },
                text = {
                    Column {
                        val filters = listOf(
                            "All Time",
                            "Last 30 Days",
                            "Last 3 Months",
                            "This Year",
                            "Per Match Average"
                        )
                        filters.forEach { filter ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFilter = filter
                                        showFilterDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFilter == filter,
                                    onClick = {
                                        selectedFilter = filter
                                        showFilterDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(filter)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFilterDialog = false }) {
                        Text("Apply")
                    }
                }
            )
        }
    }
}
@Composable
fun PlayerStatsCard(
    player: PlayerDetailedStats,
    statType: String,
    primaryStat: String,
    secondaryStat: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = sectionContainer()),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Player",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = secondaryStat,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = primaryStat,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
