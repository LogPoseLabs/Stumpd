package com.oreki.stumpd

import com.oreki.stumpd.data.manager.*
import com.oreki.stumpd.domain.model.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.StumpdTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
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
        // Get filter information from intent
        val filterGroupId = intent.getStringExtra("filter_group_id")
        val filterGroupName = intent.getStringExtra("filter_group_name") ?: "Select Group"
        val filterPitchType = if (intent.hasExtra("filter_pitch_type")) {
            intent.getBooleanExtra("filter_pitch_type", false)
        } else false // Default to Long Pitch
        val filterDate = intent.getStringExtra("filter_date") ?: "All Time"

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PlayerDetailScreen(
                        playerName = playerName,
                        initialGroupId = filterGroupId,
                        initialGroupName = filterGroupName,
                        initialPitchType = filterPitchType,
                        initialDateFilter = filterDate
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(
    playerName: String,
    initialGroupId: String? = null,
    initialGroupName: String = "Select Group",
    initialPitchType: Boolean? = false,
    initialDateFilter: String = "All Time"
) {
    val context = LocalContext.current
    val matchRepo = rememberMatchRepository()

    val groupRepo = rememberGroupRepository()
    val playerRepo = rememberPlayerRepository()
    // Data state
    var allMatches by remember { mutableStateOf<List<MatchHistory>>(emptyList()) }

    var groups by remember { mutableStateOf<List<PlayerGroup>>(emptyList()) }
    var filteredMatches by remember { mutableStateOf(emptyList<MatchHistory>()) }
    var player by remember { mutableStateOf<PlayerDetailedStats?>(null) }

    // Filter state - use initial values from intent
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }
    var selectedGroupName by remember { mutableStateOf(initialGroupName) }
    var showGroupPicker by remember { mutableStateOf(false) }

    var selectedPitchType by remember { mutableStateOf(initialPitchType) }
    var showPitchPicker by remember { mutableStateOf(false) }
    //  Date Filters
    var selectedFilter by remember { mutableStateOf(initialDateFilter) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

// Load all matches once
    LaunchedEffect(Unit) {
        val summaries = groupRepo.listGroupSummaries()
        groups = summaries.map { (g, d, _) -> g.toDomain(d, emptyList()) }
        allMatches = matchRepo.getAllMatchesWithStats(null)
        // Auto-select first group if none selected
        if (groups.isNotEmpty() && selectedGroupId == null) {
            selectedGroupId = groups[0].id
            selectedGroupName = groups[0].name
        }
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
        val computed = playerRepo.getPlayerDetailedStats(fm)
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
                    Icons.Default.Group,
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
                    Icons.Default.Terrain,
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
        val pagerState = rememberPagerState(pageCount = { 5 })
        val coroutineScope = rememberCoroutineScope()

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            val tabs = listOf("Overview", "Performance", "Batting", "Bowling", "Stats")
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            
            // Calculate dynamic font size based on screen width
            // Small screens (<360dp): 11sp, Medium (360-400dp): 12sp, Large (>400dp): 13sp
            val tabFontSize = when {
                screenWidth < 360.dp -> 11.sp
                screenWidth < 400.dp -> 12.sp
                else -> 13.sp
            }
            
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = tabFontSize,
                            maxLines = 1,
                            softWrap = false
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
                1 -> if (player != null) PerformanceGraphTab(player!!) else EmptyPerformance()
                2 -> if (player != null) BattingTab(player!!) else EmptyBatting()
                3 -> if (player != null) BowlingTab(player!!) else EmptyBowling()
                4 -> if (player != null) InterestingStatsTab(player!!) else EmptyStats()
            }
        }
    }

// Group picker dialog
    if (showGroupPicker) {
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = { Text("Filter by Group") },
            text = {
                LazyColumn(Modifier.height(360.dp)) {
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
                TextButton(
                    onClick = {
                        val startMillis = dateRangeState.selectedStartDateMillis
                        val endMillis = dateRangeState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val startLocal = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            val endLocal = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            selectedFilter = "CustomRange:${startLocal}|${endLocal}"
                        }
                        showDateRangePicker = false
                        showFilterDialog = false
                    },
                    enabled = dateRangeState.selectedStartDateMillis != null && dateRangeState.selectedEndDateMillis != null
                ) {
                    Text("OK")
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
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Batting",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Batting Summary",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

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
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Bowling",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bowling Summary",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

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

        // Fielding Stats
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Fielding",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Fielding Stats",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatBox("Catches", player.totalCatches.toString(), Modifier.weight(1f))
                        StatBox("Run Outs", player.totalRunOuts.toString(), Modifier.weight(1f))
                        StatBox("Stumpings", player.totalStumpings.toString(), Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Recent Form",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Recent Form (Last 5 Matches)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PerformanceGraphTab(player: PlayerDetailedStats) {
    if (player.matchPerformances.isEmpty()) {
        EmptyPerformance()
        return
    }
    
    // Group performances by week
    val weeklyPerformance = remember(player.matchPerformances) {
        player.matchPerformances
            .groupBy { match ->
                val instant = Instant.ofEpochMilli(match.matchDate)
                val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                val week = localDate.minusDays((localDate.dayOfWeek.value % 7).toLong())
                week
            }
            .mapValues { (_, matches) ->
                Triple(
                    matches.sumOf { it.runs },
                    matches.sumOf { it.wickets },
                    matches.size
                )
            }
            .toList()
            .sortedBy { it.first }
            .takeLast(12) // Last 12 weeks
    }
    
    val maxRuns = weeklyPerformance.maxOfOrNull { it.second.first } ?: 1
    val maxWickets = weeklyPerformance.maxOfOrNull { it.second.second } ?: 1
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "📊 Performance Trends",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Last ${weeklyPerformance.size} weeks",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Batting Performance
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏏", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Batting Performance",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Matches",
                            fontSize = 9.sp,
                            modifier = Modifier.width(30.dp),
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    weeklyPerformance.forEach { (week, performance) ->
                        val (runs, _, matches) = performance
                        val weekStr = week.format(DateTimeFormatter.ofPattern("MMM dd"))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = weekStr,
                                fontSize = 11.sp,
                                modifier = Modifier.width(60.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Bar representing runs
                            val barWidth = if (maxRuns > 0) (runs.toFloat() / maxRuns) else 0f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(barWidth.coerceIn(0.05f, 1f))
                                        .height(24.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "$runs runs",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            
                            Text(
                                text = "$matches",
                                fontSize = 10.sp,
                                modifier = Modifier.width(30.dp),
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        // Bowling Performance
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚾", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Bowling Performance",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Matches",
                            fontSize = 9.sp,
                            modifier = Modifier.width(30.dp),
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    weeklyPerformance.forEach { (week, performance) ->
                        val (_, wickets, matches) = performance
                        val weekStr = week.format(DateTimeFormatter.ofPattern("MMM dd"))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = weekStr,
                                fontSize = 11.sp,
                                modifier = Modifier.width(60.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Bar representing wickets
                            val barWidth = if (maxWickets > 0) (wickets.toFloat() / maxWickets) else 0f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(barWidth.coerceIn(0.05f, 1f))
                                        .height(24.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "$wickets wkts",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            
                            Text(
                                text = "$matches",
                                fontSize = 10.sp,
                                modifier = Modifier.width(30.dp),
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Overall",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Overall Performance",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

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
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Best",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Best Performances",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

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
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHighlight)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    )
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = MaterialTheme.shapes.medium,
        border = if (match.isWinner) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        else null
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                        text = "${match.wickets}/${match.runsConceded} (${match.ballsBowled / 6}.${match.ballsBowled % 6} ov)",
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
private fun EmptyPerformance() {
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
                    Text(text = "📊", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No performance data available",
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
@Composable
fun BattingTab(player: PlayerDetailedStats) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Batting",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Batting Statistics",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Career Stats
                    Text(
                        text = "Career Overview",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    StatRow("Matches Played", player.totalMatches.toString())
                    StatRow("Total Runs", player.totalRuns.toString())
                    StatRow("Balls Faced", player.totalBallsFaced.toString())
                    StatRow("Batting Average", "%.2f".format(player.battingAverage))
                    StatRow("Strike Rate", "%.2f".format(player.strikeRate))
                    StatRow("Highest Score", player.highestScore.toString())
                    StatRow("50s", player.fifties.toString())
                    StatRow("100s", player.hundreds.toString())
                    StatRow("Times Out", player.timesOut.toString())
                    StatRow("Not Outs", player.notOuts.toString())
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Scoring Breakdown
                    Text(
                        text = "Scoring Breakdown",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBox("Dots", player.totalDots.toString(), Modifier.weight(1f))
                        StatBox("1s", player.totalSingles.toString(), Modifier.weight(1f))
                        StatBox("2s", player.totalTwos.toString(), Modifier.weight(1f))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBox("3s", player.totalThrees.toString(), Modifier.weight(1f))
                        StatBox("4s", player.totalFours.toString(), Modifier.weight(1f))
                        StatBox("6s", player.totalSixes.toString(), Modifier.weight(1f))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Interesting Stats
                    Text(
                        text = "Interesting Stats",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    StatRow("Ducks", if (player.ducks > 0) "${player.ducks}/${player.totalMatches} (${"%.1f".format(player.duckPercentage)}% • ${player.goldenDucks} golden 🦆)" else "0")
                    StatRow("Boundary %", "%.1f%%".format(player.boundaryPercentage))
                    StatRow("Dot Ball %", "%.1f%%".format(player.dotBallPercentage))
                    StatRow("Run Rate", "%.1f runs/over".format(player.currentRunRate))
                    StatRow("Consistency", "${player.consistencyRating} (σ=%.1f)".format(player.consistency))
                    StatRow("Form (Last 5)", player.recentForm)
                }
            }
        }
    }
}

@Composable
fun BowlingTab(player: PlayerDetailedStats) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Bowling",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bowling Statistics",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (player.totalWickets > 0 || player.totalBallsBowled > 0) {
                        Text(
                            text = "Career Overview",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        StatRow("Total Wickets", player.totalWickets.toString())
                        StatRow("Best Bowling", player.bestBowling)
                        StatRow("5-Wicket Hauls", player.fiveWicketHauls.toString())
                        StatRow("Overs Bowled", "%.1f".format(player.oversBowled))
                        StatRow("Maiden Overs", player.totalMaidenOvers.toString())
                        StatRow("Runs Conceded", player.totalRunsConceded.toString())
                        StatRow("Bowling Average", if (player.totalWickets > 0) "%.2f".format(player.bowlingAverage) else "-")
                        StatRow("Economy Rate", "%.2f".format(player.economyRate))
                        StatRow("Balls Bowled", player.totalBallsBowled.toString())
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Extras Conceded",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatBox("Wides", player.totalWides.toString(), Modifier.weight(1f))
                            StatBox("No Balls", player.totalNoBalls.toString(), Modifier.weight(1f))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Interesting Stats
                        Text(
                            text = "Bowling Effectiveness",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        StatRow("Maiden %", "%.1f%% (%d maidens)".format(player.maidenOverPercentage, player.totalMaidenOvers))
                        StatRow("Wicket Strike Rate", if (player.totalWickets > 0) "%.1f balls/wicket".format(player.wicketStrikeRate) else "-")
                        StatRow("Pressure Index", "${player.pressureRating} (%.0f/100)".format(player.pressureIndex))
                        StatRow("Form (Last 5)", player.recentForm)
                    } else {
                        Text(
                            text = "No bowling statistics available",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyBatting() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No batting statistics available", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyBowling() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No bowling statistics available", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InterestingStatsTab(player: PlayerDetailedStats) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Batting Insights
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🏏",
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Batting Insights",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    StatRow("Ducks 🦆", if (player.ducks > 0) {
                        "${player.ducks}/${player.totalMatches} (${"%.1f".format(player.duckPercentage)}%)" + 
                        if (player.goldenDucks > 0) " • ${player.goldenDucks} golden" else ""
                    } else "0")
                    StatRow("Boundary %", "%.1f%% of runs".format(player.boundaryPercentage))
                    StatRow("Dot Ball %", "%.1f%% of balls".format(player.dotBallPercentage))
                    StatRow("Run Rate", "%.1f runs/over".format(player.currentRunRate))
                    StatRow("Consistency", "${player.consistencyRating} (σ=%.1f)".format(player.consistency))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Form Guide
                    Text(
                        text = "Recent Form",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = player.recentForm,
                        fontSize = 24.sp,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🟢 50+ • 🟡 25-49 • 🟠 10-24 • 🔴 <10",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Bowling Insights
        if (player.totalWickets > 0 || player.totalBallsBowled > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎳",
                                fontSize = 20.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Bowling Insights",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        StatRow("Maiden Overs", "${player.totalMaidenOvers} (%.1f%%)".format(player.maidenOverPercentage))
                        StatRow("Wicket Strike Rate", if (player.totalWickets > 0) "%.1f balls/wicket".format(player.wicketStrikeRate) else "-")
                        StatRow("Pressure Index", "${player.pressureRating} (%.0f/100)".format(player.pressureIndex))
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Pressure rating visual
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when (player.pressureRating) {
                                    "Excellent" -> MaterialTheme.colorScheme.primaryContainer
                                    "Very Good" -> MaterialTheme.colorScheme.secondaryContainer
                                    "Good" -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Pressure Rating",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    player.pressureRating,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Scoring Pattern Breakdown
        if (player.totalBallsFaced > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📊",
                                fontSize = 20.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Scoring Pattern",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        player.scoringBreakdown.forEach { (label, percentage) ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "%.1f%%".format(percentage),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (percentage / 100).toFloat() },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = when (label) {
                                        "Dots" -> MaterialTheme.colorScheme.error
                                        "Singles" -> MaterialTheme.colorScheme.tertiary
                                        "Twos" -> MaterialTheme.colorScheme.secondary
                                        "Boundaries" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
        
        // Milestones Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🏆",
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Milestones",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    player.fifties.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Fifties",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    player.hundreds.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "Hundreds",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    player.fiveWicketHauls.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "5-Wicket",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

