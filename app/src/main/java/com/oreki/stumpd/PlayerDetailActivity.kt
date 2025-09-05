package com.oreki.stumpd

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.StumpdTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PlayerDetailActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val playerName = intent.getStringExtra("player_name") ?: ""

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PlayerDetailScreen(playerName)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(playerName: String) {
    val context = LocalContext.current
    val playerStorage = remember { EnhancedPlayerStorageManager(context) }
    val matchStorage = remember { MatchStorageManager(context) }
    val groupStorage = remember { PlayerGroupStorageManager(context) }
    // Data state
    var allMatches by remember { mutableStateOf(emptyList<MatchHistory>()) }
    var filteredMatches by remember { mutableStateOf(emptyList<MatchHistory>()) }
    var player by remember { mutableStateOf<PlayerDetailedStats?>(null) }

    // Filter state (no date filter)
    var selectedGroupId by remember { mutableStateOf<String?>(null) }      // null => All Groups
    var selectedGroupName by remember { mutableStateOf("All Groups") }
    var showGroupPicker by remember { mutableStateOf(false) }

    var selectedPitchType by remember { mutableStateOf<Boolean?>(null) }   // null => All, true => Short, false => Long
    var showPitchPicker by remember { mutableStateOf(false) }
    //  Date Filters
    var selectedFilter by remember { mutableStateOf("All Time") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

// Load all matches once
    LaunchedEffect(Unit) {
        allMatches = matchStorage.getAllMatches()
    }

// Recompute filtered data whenever filters or name change
    LaunchedEffect(allMatches, selectedGroupId, selectedPitchType, playerName, selectedFilter) {
        var fm = allMatches
        selectedGroupId?.let { gid -> fm = fm.filter { it.groupId == gid } }
        selectedPitchType?.let { s -> fm = fm.filter { it.shortPitch == s } }

        when {
            selectedFilter == "All Time" -> { /* no-op */ }

            selectedFilter.startsWith("Date:") -> {
                val iso = selectedFilter.removePrefix("Date:")
                val selDate = LocalDate.parse(iso)
                fm = fm.filter {
                    val d = Instant.ofEpochMilli(it.matchDate)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    d == selDate
                }
            }

            selectedFilter.startsWith("CustomRange:") -> {
                val parts = selectedFilter.removePrefix("CustomRange:").split("|")
                val start = LocalDate.parse(parts[0])
                val end = LocalDate.parse(parts[1])
                fm = fm.filter {
                    val d = Instant.ofEpochMilli(it.matchDate)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    d in start..end
                }
            }
        }

        filteredMatches = fm
        val computed = EnhancedPlayerStorageManager(context).computeFromMatches(fm)
        player = computed.firstOrNull { it.name.equals(playerName, ignoreCase = true) }
    }


// UI
    Column(modifier = Modifier.fillMaxSize()) {

        // Header card (unchanged, except safe calls on player)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playerName,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${player?.totalMatches ?: 0} matches",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Summary chips (unchanged but safe calls)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = (player?.totalRuns ?: 0).toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "RUNS",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = (player?.totalWickets ?: 0).toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "WICKETS",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last played: ${player?.let { formatDate(it.lastPlayed) } ?: "-"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Filter row (new) – mirrors StatsActivity’s chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Group filter chip
            FilledTonalButton(
                onClick = { showGroupPicker = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Group",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(selectedGroupName, fontSize = 12.sp)
            }

            // Pitch filter chip
            val pitchLabel = when (selectedPitchType) {
                true -> "Short Pitch"
                false -> "Long Pitch"
                null -> "All Pitches"
            }
            FilledTonalButton(
                onClick = { showPitchPicker = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Pitch",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(pitchLabel, fontSize = 12.sp)
            }

            // Date Filter Chip
            FilledTonalButton(
                onClick = { showFilterDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Date", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        selectedFilter == "All Time" -> "All Time"
                        selectedFilter.startsWith("Date:") -> LocalDate.parse(
                            selectedFilter.removePrefix("Date:")
                        ).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                        selectedFilter.startsWith("CustomRange:") -> "Custom Range"
                        else -> "Filter"
                    },
                    fontSize = 12.sp
                )
            }
        }

        // TabRow + Pager (keep as-is; just make sure to null-check player)
        val pagerState = rememberPagerState(pageCount = { 3 })
        val coroutineScope = rememberCoroutineScope()

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            val tabs = listOf("Overview", "Matches", "Statistics")
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> if (player != null) PlayerOverviewTab(player!!) else EmptyOverview()
                1 -> if (player != null) PlayerMatchesTab(player!!) else EmptyMatches()
                2 -> if (player != null) PlayerStatisticsTab(player!!) else EmptyStats()
            }
        }
    }

// Group picker dialog
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

// Pitch picker dialog
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
        val last3Dates = allMatches
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

@Composable
fun PlayerOverviewTab(player: PlayerDetailedStats) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🏏 Batting Summary",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatBox("Runs", player.totalRuns.toString(), Modifier.weight(1f))
                        StatBox("Balls Faced", player.totalBallsFaced.toString(), Modifier.weight(1f))
                        StatBox("Average", "%.1f".format(player.battingAverage), Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatBox("Strike Rate", "%.1f".format(player.strikeRate), Modifier.weight(1f))
                        StatBox("4s", player.totalFours.toString(), Modifier.weight(1f))
                        StatBox("6s", player.totalSixes.toString(), Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚾ Bowling Summary",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (player.totalWickets > 0 || player.totalBallsBowled > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            StatBox("Wickets", player.totalWickets.toString(), Modifier.weight(1f))
                            StatBox(
                                "Average",
                                if (player.totalWickets >
                                    0
                                ) {
                                    "%.1f".format(player.bowlingAverage)
                                } else {
                                    "-"
                                },
                                Modifier.weight(1f),
                            )
                            StatBox("Economy", "%.1f".format(player.economyRate), Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            StatBox("Overs", "%.1f".format(player.oversBowled), Modifier.weight(1f))
                            StatBox("Runs Given", player.totalRunsConceded.toString(), Modifier.weight(1f))
                        }
                    } else {
                        Text(
                            text = "No bowling statistics available",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📈 Recent Form (Last 5 Matches)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val recentMatches =
                        player.matchPerformances
                            .sortedByDescending { it.matchDate }
                            .take(5)

                    if (recentMatches.isEmpty()) {
                        Text("No recent matches", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    } else {
                        recentMatches.forEach { match ->
                            RecentMatchCard(match)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerMatchesTab(player: PlayerDetailedStats) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (player.matchPerformances.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "🏏", fontSize = 48.sp)
                        Text(
                            text = "No match data available",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        } else {
            items(player.matchPerformances.sortedByDescending { it.matchDate }) { match ->
                MatchPerformanceCard(match)
            }
        }
    }
}

@Composable
fun PlayerStatisticsTab(player: PlayerDetailedStats) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Add consistent padding
        verticalArrangement = Arrangement.spacedBy(16.dp) // Better spacing
    ) {
        item {
            Text(
                text = "Career Statistics",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Overall Performance",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    StatRow("Matches Played", player.totalMatches.toString())
                    StatRow("Total Runs", player.totalRuns.toString())
                    StatRow("Total Balls Faced", player.totalBallsFaced.toString())
                    StatRow("Total Wickets", player.totalWickets.toString())
                    StatRow("Times Out", player.timesOut.toString())
                    StatRow("Not Outs", player.notOuts.toString())

                    if (player.totalWickets > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bowling Records",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        StatRow("Overs Bowled", "%.1f".format(player.oversBowled))
                        StatRow("Runs Conceded", player.totalRunsConceded.toString())
                        StatRow("Economy", "%.2f".format(player.economyRate))
                        StatRow("Average", player.bowlingAverage.toString())
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Best Performances",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    val bestBattingScore = player.matchPerformances.maxByOrNull { it.runs }
                    val bestBowlingFigures = player.matchPerformances.maxByOrNull { it.wickets }

                    bestBattingScore?.let { performance ->
                        StatRow("Best Batting", "${performance.runs} vs ${performance.opposingTeam}")
                    }

                    bestBowlingFigures?.let { performance ->
                        if (performance.wickets > 0) {
                            StatRow("Best Bowling", "${performance.wickets}/${performance.runsConceded} vs ${performance.opposingTeam}")
                        }
                    }

                    val wins = player.matchPerformances.count { it.isWinner }
                    val winPercentage = if (player.totalMatches > 0) (wins.toDouble() / player.totalMatches) * 100 else 0.0

                    StatRow("Matches Won", "$wins (${winPercentage.toInt()}%)")
                }
            }
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isHighlight: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlight)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                fontSize = 24.sp, // Bigger numbers
                fontWeight = FontWeight.Bold,
                color = if (isHighlight)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
fun StatRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 14.sp)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RecentMatchCard(match: MatchPerformance) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "vs ${match.opposingTeam}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatDate(match.matchDate),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            if (match.runs > 0 || match.ballsFaced > 0) {
                Text(
                    text = "${match.runs} (${match.ballsFaced})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (match.wickets > 0) {
                Text(
                    text = "${match.wickets}/${match.runsConceded}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        Icon(
            imageVector = if (match.isWinner) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = if (match.isWinner) "Won" else "Lost",
            tint = if (match.isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun MatchPerformanceCard(match: MatchPerformance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (match.isWinner)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (match.isJoker) {
                            Text("🃏 ", fontSize = 16.sp)
                        }
                        Text(
                            text = "${match.myTeam} vs ${match.opposingTeam}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = formatDate(match.matchDate),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Win/Loss indicator
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (match.isWinner)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = if (match.isWinner) "WON" else "LOST",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (match.isWinner)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Performance stats
            if (match.runs > 0 || match.ballsFaced > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🏏 Batting",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${match.runs}${if (!match.isOut) "*" else ""} (${match.ballsFaced})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (match.fours > 0 || match.sixes > 0) {
                    Text(
                        text = "4s: ${match.fours} • 6s: ${match.sixes}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Bowling stats (if available)
            if (match.wickets > 0 || match.ballsBowled > 0) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚾ Bowling",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${match.wickets}/${match.runsConceded} (${match.ballsBowled / 6}.${match.ballsBowled % 6})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyOverview() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 0.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    Text(
                        "🏏 Batting Summary",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No stats in this filter",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMatches() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 0.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🏏", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No match data available",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}


@Composable
private fun EmptyStats() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 0.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "📊", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No statistics available",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}