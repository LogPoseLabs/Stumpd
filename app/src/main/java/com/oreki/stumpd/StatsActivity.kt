package com.oreki.stumpd

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.SectionTitle
import com.oreki.stumpd.ui.theme.StatsTopBar
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.sectionContainer
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StatsActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
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

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
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

    var selectedPitchType by remember { mutableStateOf<Boolean?>(null) }
    var showPitchPicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }


    LaunchedEffect(selectedGroupId, selectedPitchType, selectedFilter) {
        val all = matchStorage.getAllMatches()
        var filtered = all

        // Group filter
        selectedGroupId?.let { gId ->
            filtered = filtered.filter { it.groupId == gId }
        }

        // Pitch type filter
        selectedPitchType?.let { type ->
            filtered = filtered.filter { it.shortPitch == type }
        }

        when {
            selectedFilter == "All Time" -> { /* no-op */ }

            selectedFilter.startsWith("Date:") -> {
                val iso = selectedFilter.removePrefix("Date:")
                val selDate = LocalDate.parse(iso) // ISO yyyy-MM-dd
                filtered = filtered.filter {
                    val d = Instant.ofEpochMilli(it.matchDate).atZone(ZoneId.systemDefault()).toLocalDate()
                    d == selDate
                }
            }

            selectedFilter.startsWith("CustomRange:") -> {
                val parts = selectedFilter.removePrefix("CustomRange:").split("|")
                val start = LocalDate.parse(parts[0])
                val end = LocalDate.parse(parts[1])
                filtered = filtered.filter {
                    val d = Instant.ofEpochMilli(it.matchDate).atZone(ZoneId.systemDefault()).toLocalDate()
                    d in start..end
                }
            }
        }
        matches = filtered
        val detailed = EnhancedPlayerStorageManager(context).computeFromMatches(filtered)
        players = detailed
    }

    Scaffold(
        topBar = {
            Column {
                StatsTopBar(
                    title = "Statistics",
                    subtitle = "${players.size} players • ${matches.size} matches\n$selectedGroupName",
                    onBack = {
                        val intent = Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    }
                )
                // 👇 separate filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showGroupPicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Group", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(selectedGroupName, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    val pitchLabel = when (selectedPitchType) {
                        true -> "Short Pitch"
                        false -> "Long Pitch"
                        null -> "All Pitches"
                    }
                    FilledTonalButton(
                        onClick = { showPitchPicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Pitch", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(pitchLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    FilledTonalButton(
                        onClick = { showFilterDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "Filter", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(selectedFilter, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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

        if (showPitchPicker) {
            AlertDialog(
                onDismissRequest = { showPitchPicker = false },
                title = { Text("Filter by Pitch Type") },
                text = {
                    Column {
                        listOf("All", "Short", "Long").forEach { option ->
                            Text(
                                text = option,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPitchType = when (option) {
                                            "Short" -> true
                                            "Long" -> false
                                            else -> null
                                        }
                                        showPitchPicker = false
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showFilterDialog) {
            // compute last 3 distinct match dates as LocalDate
            val last3Dates = matches
                .map { Instant.ofEpochMilli(it.matchDate).atZone(ZoneId.systemDefault()).toLocalDate() }
                .distinct()
                .sortedDescending()
                .take(3)

            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("Filter Statistics") },
                text = {
                    Column {
                        // 1) All Time
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFilter = "All Time"
                                    showFilterDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFilter == "All Time",
                                onClick = {
                                    selectedFilter = "All Time"
                                    showFilterDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("All Time")
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Last 3 Match Dates", fontWeight = FontWeight.SemiBold)

                        // 2) Last 3 dates — store token as Date:yyyy-MM-dd for easy parsing
                        last3Dates.forEach { date ->
                            val iso = date.toString() // yyyy-MM-dd
                            val label = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFilter = "Date:$iso"
                                        showFilterDialog = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFilter == "Date:$iso",
                                    onClick = {
                                        selectedFilter = "Date:$iso"
                                        showFilterDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 3) Custom date range -> open date-range picker dialog
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDateRangePicker = true
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFilter.startsWith("CustomRange"),
                                onClick = { showDateRangePicker = true }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Custom Date Range…")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFilterDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // show DateRangePicker inline inside an AlertDialog (only when requested)
        if (showDateRangePicker) {
            val dateRangeState = rememberDateRangePickerState()

            AlertDialog(
                onDismissRequest = { showDateRangePicker = false },
                title = { Text("Select Date Range") },
                text = {
                    // the inline picker (shows calendar UI)
                    DateRangePicker(state = dateRangeState)
                },
                confirmButton = {
                    TextButton(onClick = {
                        val startMillis = dateRangeState.selectedStartDateMillis
                        val endMillis = dateRangeState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val startLocal = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            val endLocal = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            // store as ISO for parsing later
                            selectedFilter = "CustomRange:${startLocal}|${endLocal}"
                        }
                        showDateRangePicker = false
                        showFilterDialog = false
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDateRangePicker = false }) {
                        Text("Cancel")
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
