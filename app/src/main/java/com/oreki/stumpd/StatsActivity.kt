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
import com.oreki.stumpd.ui.theme.StumpdTheme

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

    LaunchedEffect(Unit) {
        playerStorage.syncPlayerStatsFromMatches()
        players = playerStorage.getAllPlayersDetailed()
        matches = matchStorage.getAllMatches()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = Color(0xFF2E7D32)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Statistics",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "${players.size} players • ${matches.size} matches",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = { showFilterDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Icon(Icons.Default.List, contentDescription = "Filter")
                Spacer(modifier = Modifier.width(4.dp))
                Text(selectedFilter, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (players.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "📊", fontSize = 48.sp)
                    Text(text = "No statistics available", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(text = "Play some matches to see your stats!", fontSize = 14.sp, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        text = "🏏 Top Batsmen",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }

                val topBatsmen = players.sortedByDescending { it.totalRuns }.take(5)
                items(topBatsmen) { player ->
                    PlayerStatsCard(
                        player = player,
                        statType = "Batting",
                        primaryStat = "${player.totalRuns} runs",
                        secondaryStat = "Avg: ${"%.1f".format(player.battingAverage)} • SR: ${"%.1f".format(player.strikeRate)}",
                        onClick = {
                            val intent = Intent(context, PlayerDetailActivity::class.java)
                            intent.putExtra("player_name", player.name)
                            context.startActivity(intent)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚾ Top Bowlers",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }

                val topBowlers = players.filter { it.totalWickets > 0 }.sortedByDescending { it.totalWickets }.take(5)
                items(topBowlers) { player ->
                    PlayerStatsCard(
                        player = player,
                        statType = "Bowling",
                        primaryStat = "${player.totalWickets} wickets",
                        secondaryStat = "Avg: ${"%.1f".format(player.bowlingAverage)} • Eco: ${"%.1f".format(player.economyRate)}",
                        onClick = {
                            val intent = Intent(context, PlayerDetailActivity::class.java)
                            intent.putExtra("player_name", player.name)
                            context.startActivity(intent)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🏆 Match Winners",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }

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

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter Statistics") },
            text = {
                Column {
                    val filters = listOf("All Time", "Last 30 Days", "Last 3 Months", "This Year", "Per Match Average")
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                tint = Color(0xFF2E7D32),
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
                    color = Color.Gray
                )
            }

            Text(
                text = primaryStat,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
        }
    }
}
