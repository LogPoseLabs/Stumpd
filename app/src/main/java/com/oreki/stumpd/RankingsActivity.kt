package com.oreki.stumpd

import com.oreki.stumpd.data.manager.*
import com.oreki.stumpd.domain.model.*
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.ui.theme.StatsTopBar
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.utils.RankingUtils
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RankingsActivity : ComponentActivity() {
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
                    RankingsScreen()
                }
            }
        }
    }
}

private data class RankingTab(
    val title: String,
    val icon: String,
    val sortByKey: String
)

private val tabs = listOf(
    RankingTab("Batting", "🏏", "Batting Ranking"),
    RankingTab("Bowling", "⚾", "Bowling Ranking"),
    RankingTab("All-Rounder", "🏆", "All-Rounder Ranking")
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RankingsScreen() {
    val context = LocalContext.current
    val repo = rememberMatchRepository()
    val playerRepo = rememberPlayerRepository()
    val groupRepo = rememberGroupRepository()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var players by remember { mutableStateOf<List<PlayerDetailedStats>>(emptyList()) }
    var groupMatchContext by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }

    // Filters
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGroupName by rememberSaveable { mutableStateOf("Select Group") }
    var selectedPitchType by rememberSaveable { mutableStateOf<Boolean?>(false) } // Default to Long Pitch
    var selectedFilter by rememberSaveable { mutableStateOf("All Time") }

    var showGroupPicker by remember { mutableStateOf(false) }
    var showPitchPicker by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    var groups by remember { mutableStateOf<List<PlayerGroup>>(emptyList()) }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    LaunchedEffect(Unit) {
        val summaries = groupRepo.listGroupSummaries()
        groups = summaries.map { (g, d, _) -> g.toDomain(d, emptyList()) }
        if (groups.isNotEmpty() && selectedGroupId == null) {
            selectedGroupId = groups[0].id
            selectedGroupName = groups[0].name
        }
    }

    LaunchedEffect(selectedGroupId, selectedPitchType, selectedFilter) {
        val all = repo.getAllMatches()
        var filtered = all

        selectedGroupId?.let { gId ->
            filtered = filtered.filter { it.groupId == gId }
        }
        selectedPitchType?.let { type ->
            filtered = filtered.filter { it.shortPitch == type }
        }
        when {
            selectedFilter == "All Time" -> { /* no-op */ }
            selectedFilter.startsWith("Date:") -> {
                val iso = selectedFilter.removePrefix("Date:")
                val selDate = LocalDate.parse(iso)
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

        // Build group match context for missed-match penalty (only when a specific group is selected)
        groupMatchContext = if (selectedGroupId != null) {
            filtered.map { Pair(it.id, it.matchDate) }
        } else {
            null
        }

        players = playerRepo.getPlayerDetailedStats(filtered)
        isLoading = false
    }

    val pitchTypeLabel = when (selectedPitchType) {
        true -> "Short Pitch"
        false -> "Long Pitch"
        null -> "All Pitches"
    }

    val formattedDateFilter = remember(selectedFilter) {
        when {
            selectedFilter == "All Time" -> "All Time"
            selectedFilter.startsWith("Date:") -> {
                try {
                    val date = LocalDate.parse(selectedFilter.removePrefix("Date:"))
                    date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                } catch (_: Exception) { selectedFilter }
            }
            selectedFilter.startsWith("CustomRange:") -> {
                try {
                    val parts = selectedFilter.removePrefix("CustomRange:").split("|")
                    val start = LocalDate.parse(parts[0])
                    val end = LocalDate.parse(parts[1])
                    "${start.format(DateTimeFormatter.ofPattern("dd MMM"))} - ${end.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
                } catch (_: Exception) { "Custom Range" }
            }
            else -> selectedFilter
        }
    }

    val dateRangePickerState = rememberDateRangePickerState()

    Scaffold(
        topBar = {
            Column {
                StatsTopBar(
                    title = "Rankings",
                    subtitle = "ICC-inspired player ratings",
                    onBack = { (context as ComponentActivity).finish() }
                )
                // Filter chips
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedGroupId != null,
                            onClick = { showGroupPicker = true },
                            label = { Text(selectedGroupName, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = selectedPitchType != null,
                            onClick = { showPitchPicker = true },
                            label = { Text(pitchTypeLabel, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                        FilterChip(
                            selected = selectedFilter != "All Time",
                            onClick = { showFilterDialog = true },
                            label = { Text(formattedDateFilter, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                }
                // Tabs
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(tab.icon, fontSize = 14.sp)
                                    Text(tab.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (players.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text("No Rankings Yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Play some matches to see player rankings!", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) { page ->
                val tab = tabs[page]
                val ranked = remember(players, page, groupMatchContext) {
                    when (page) {
                        0 -> players
                            .filter { (it.totalRuns > 0 || it.totalBallsFaced > 0) && it.matchPerformances.count { p -> p.ballsFaced > 0 || p.runs > 0 } >= 3 }
                            .map { Pair(it, RankingUtils.calculateBattingRating(it.matchPerformances, groupMatchContext)) }
                            .sortedByDescending { it.second }
                        1 -> players
                            .filter { it.totalBallsBowled > 0 && it.matchPerformances.count { p -> p.ballsBowled > 0 } >= 3 }
                            .map { Pair(it, RankingUtils.calculateBowlingRating(it.matchPerformances, groupMatchContext)) }
                            .sortedByDescending { it.second }
                        2 -> players
                            .filter { it.totalMatches >= 3 && (it.totalRuns > 0 || it.totalBallsFaced > 0) && it.totalBallsBowled > 0 }
                            .map { Pair(it, RankingUtils.calculateOverallRating(it, groupMatchContext)) }
                            .sortedByDescending { it.second }
                        else -> emptyList()
                    }
                }

                if (ranked.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Need 3+ qualifying innings",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(ranked.size) { index ->
                            val (player, rating) = ranked[index]
                            val rank = index + 1

                            RankingRow(
                                rank = rank,
                                player = player,
                                rating = rating,
                                statLine = when (page) {
                                    0 -> {
                                        val innings = player.matchPerformances.count { it.ballsFaced > 0 || it.runs > 0 }
                                        "${player.totalRuns} runs • $innings inn • Avg: ${"%.1f".format(player.battingAverage)} • SR: ${"%.1f".format(player.strikeRate)}"
                                    }
                                    1 -> {
                                        val innings = player.matchPerformances.count { it.ballsBowled > 0 }
                                        "${player.totalWickets}w • $innings inn • Avg: ${"%.1f".format(player.bowlingAverage)} • Eco: ${"%.1f".format(player.economyRate)}"
                                    }
                                    else -> "${player.totalRuns}r • ${player.totalWickets}w • ${player.totalMatches} matches"
                                },
                                onClick = {
                                    val intent = Intent(context, PlayerDetailActivity::class.java)
                                    intent.putExtra("player_name", player.name)
                                    intent.putExtra("filter_group_id", selectedGroupId)
                                    intent.putExtra("filter_group_name", selectedGroupName)
                                    selectedPitchType?.let { intent.putExtra("filter_pitch_type", it) }
                                    intent.putExtra("filter_date", selectedFilter)
                                    context.startActivity(intent)
                                }
                            )

                            if (index < ranked.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // View All button at bottom
                        item {
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(context, AllPlayersStatsActivity::class.java)
                                    intent.putExtra("filter_group_id", selectedGroupId)
                                    intent.putExtra("filter_group_name", selectedGroupName)
                                    selectedPitchType?.let { intent.putExtra("filter_pitch_type", it) }
                                    intent.putExtra("filter_date", selectedFilter)
                                    intent.putExtra("sort_by", tab.sortByKey)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View All ${tab.title} Rankings")
                            }
                        }
                    }
                }
            }
        }

        // Group Picker
        if (showGroupPicker) {
            AlertDialog(
                onDismissRequest = { showGroupPicker = false },
                title = { Text("Select Group") },
                text = {
                    LazyColumn {
                        items(groups.size) { index ->
                            val group = groups[index]
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedGroupId = group.id
                                    selectedGroupName = group.name
                                    showGroupPicker = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedGroupId == group.id, onClick = {
                                    selectedGroupId = group.id
                                    selectedGroupName = group.name
                                    showGroupPicker = false
                                })
                                Spacer(Modifier.width(8.dp))
                                Text(group.name)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Pitch Picker
        if (showPitchPicker) {
            AlertDialog(
                onDismissRequest = { showPitchPicker = false },
                title = { Text("Select Pitch Type") },
                text = {
                    Column {
                        listOf(null to "All Pitches", true to "Short Pitch", false to "Long Pitch").forEach { (value, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedPitchType = value
                                    showPitchPicker = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedPitchType == value, onClick = {
                                    selectedPitchType = value
                                    showPitchPicker = false
                                })
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Date Filter Dialog
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("Filter by Date") },
                text = {
                    Column {
                        listOf("All Time", "Custom Range").forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (option == "Custom Range") {
                                        showFilterDialog = false
                                        showDateRangePicker = true
                                    } else {
                                        selectedFilter = option
                                        showFilterDialog = false
                                    }
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedFilter == option, onClick = {
                                    if (option == "Custom Range") {
                                        showFilterDialog = false
                                        showDateRangePicker = true
                                    } else {
                                        selectedFilter = option
                                        showFilterDialog = false
                                    }
                                })
                                Spacer(Modifier.width(8.dp))
                                Text(option)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Date Range Picker
        if (showDateRangePicker) {
            DatePickerDialog(
                onDismissRequest = { showDateRangePicker = false },
                confirmButton = {
                    Button(
                        onClick = {
                            val start = dateRangePickerState.selectedStartDateMillis
                            val end = dateRangePickerState.selectedEndDateMillis
                            if (start != null && end != null) {
                                val startDate = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
                                val endDate = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate()
                                selectedFilter = "CustomRange:${startDate}|${endDate}"
                            }
                            showDateRangePicker = false
                        },
                        enabled = dateRangePickerState.selectedStartDateMillis != null &&
                                dateRangePickerState.selectedEndDateMillis != null
                    ) { Text("Apply Range") }
                },
                dismissButton = {
                    TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
                }
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    title = { Text("Select Date Range", modifier = Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    }
}

@Composable
private fun RankingRow(
    rank: Int,
    player: PlayerDetailedStats,
    rating: Double,
    statLine: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank badge
        Surface(
            shape = MaterialTheme.shapes.small,
            color = when (rank) {
                1 -> MaterialTheme.colorScheme.primary
                2 -> MaterialTheme.colorScheme.secondary
                3 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (rank <= 3) {
                        when (rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
                    } else "#$rank",
                    fontSize = if (rank <= 3) 18.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rank > 3) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Transparent
                )
            }
        }
        Spacer(Modifier.width(14.dp))

        // Player info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = statLine,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Rating
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "%.0f".format(rating),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "rating",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
