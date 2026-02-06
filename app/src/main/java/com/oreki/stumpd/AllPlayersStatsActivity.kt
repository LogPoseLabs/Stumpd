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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.ui.theme.StatsTopBar
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.sectionContainer
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.utils.RankingUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AllPlayersStatsActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        
        // Get filter information from intent, or use default group from preferences
        val prefs = getSharedPreferences("stumpd_prefs", MODE_PRIVATE)
        val defaultGroupId = prefs.getString("default_group_id", null)
        val filterGroupId = intent.getStringExtra("filter_group_id") ?: defaultGroupId
        val filterGroupName = intent.getStringExtra("filter_group_name") ?: if (defaultGroupId != null) "" else "All Groups"
        val filterPitchType = if (intent.hasExtra("filter_pitch_type")) {
            intent.getBooleanExtra("filter_pitch_type", false)
        } else false // Default to Long Pitch
        val filterDate = intent.getStringExtra("filter_date") ?: "All Time"
        
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AllPlayersStatsScreen(
                        filterGroupId = filterGroupId,
                        filterGroupName = filterGroupName,
                        filterPitchType = filterPitchType,
                        filterDate = filterDate
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPlayersStatsScreen(
    filterGroupId: String?,
    filterGroupName: String,
    filterPitchType: Boolean?,
    filterDate: String
) {
    val context = LocalContext.current
    val repo = rememberMatchRepository()
    val playerRepo = rememberPlayerRepository()
    val groupRepo = rememberGroupRepository()
    
    var isLoading by remember { mutableStateOf(true) }
    var players by remember { mutableStateOf<List<PlayerDetailedStats>>(emptyList()) }
    var sortBy by rememberSaveable { mutableStateOf("Runs") } // Runs, Wickets, Avg, SR, Economy
    var showSortDialog by remember { mutableStateOf(false) }
    
    // Make filters interactive
    var selectedGroupId by rememberSaveable { mutableStateOf(filterGroupId) }
    var selectedGroupName by rememberSaveable { mutableStateOf(filterGroupName) }
    var selectedPitchType by rememberSaveable { mutableStateOf(filterPitchType) }
    var selectedFilter by rememberSaveable { mutableStateOf(filterDate) }
    
    var showGroupPicker by remember { mutableStateOf(false) }
    var showPitchPicker by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    
    var groups by remember { mutableStateOf<List<PlayerGroup>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val summaries = groupRepo.listGroupSummaries()
        groups = summaries.map { (g, d, _) -> g.toDomain(d, emptyList()) }
        // Set group name if default group is set but name is empty
        if (selectedGroupId != null && selectedGroupName.isEmpty()) {
            selectedGroupName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "All Groups"
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
        
        players = playerRepo.getPlayerDetailedStats(filtered)
        isLoading = false
    }
    
    val sortedPlayers = remember(players, sortBy) {
        when (sortBy) {
            "Runs" -> players.sortedByDescending { it.totalRuns }
            "Balls Faced" -> players.sortedByDescending { it.totalBallsFaced }
            "Wickets" -> players.sortedByDescending { it.totalWickets }
            "Batting Avg" -> players.filter { it.timesOut > 0 }.sortedByDescending { it.battingAverage }
            "Strike Rate" -> players.filter { it.totalBallsFaced > 0 }.sortedByDescending { it.strikeRate }
            "Bowling SR" -> players.filter { it.totalWickets > 0 && it.totalBallsBowled > 0 }
                .sortedBy { it.wicketStrikeRate }
            "Economy" -> players.filter { it.totalBallsBowled > 0 }.sortedBy { it.economyRate }
            "Extras" -> players.filter { it.totalWides + it.totalNoBalls > 0 }.sortedByDescending { it.totalWides + it.totalNoBalls }
            "Catches" -> players.filter { it.totalCatches > 0 }.sortedByDescending { it.totalCatches }
            "Run Outs" -> players.filter { it.totalRunOuts > 0 }.sortedByDescending { it.totalRunOuts }
            "Stumpings" -> players.filter { it.totalStumpings > 0 }.sortedByDescending { it.totalStumpings }
            "Boundaries" -> players.sortedByDescending { it.totalFours + it.totalSixes }
            "Fours" -> players.sortedByDescending { it.totalFours }
            "Sixes" -> players.sortedByDescending { it.totalSixes }
            "Matches" -> players.sortedByDescending { it.totalMatches }
            "Bowling Avg" -> players.filter { it.totalWickets > 0 }.sortedBy { it.bowlingAverage }
            // New interesting stats
            "Duck %" -> players.filter { it.totalMatches > 0 }.sortedByDescending { it.duckPercentage }
            "Boundary %" -> players.filter { it.totalRuns > 0 }.sortedByDescending { it.boundaryPercentage }
            "Dot Ball %" -> players.filter { it.totalBallsFaced > 0 }.sortedBy { it.dotBallPercentage } // Lower is better
            "Maidens" -> players.filter { it.totalMaidenOvers > 0 }.sortedByDescending { it.maidenOverPercentage }
            "Pressure Index" -> players.filter { it.totalBallsBowled > 0 }.sortedByDescending { it.pressureIndex }
            "Consistency" -> players.filter { it.matchPerformances.size >= 2 }.sortedBy { it.consistency } // Lower is better
            "Overall Ranking" -> players
                .filter { it.totalMatches >= 3 } // Min 3 matches to qualify
                .map { player ->
                    Pair(player, RankingUtils.calculateOverallScore(player, players))
                }
                .sortedByDescending { it.second }
                .map { it.first }
            else -> players
        }
    }
    
    // Format date filter for display
    val formattedDateFilter = remember(selectedFilter) {
        when {
            selectedFilter == "All Time" -> "All Time"
            selectedFilter.startsWith("Date:") -> {
                val iso = selectedFilter.removePrefix("Date:")
                try {
                    val date = LocalDate.parse(iso)
                    date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
                } catch (e: Exception) {
                    selectedFilter
                }
            }
            selectedFilter.startsWith("CustomRange:") -> {
                try {
                    val parts = selectedFilter.removePrefix("CustomRange:").split("|")
                    val start = LocalDate.parse(parts[0])
                    val end = LocalDate.parse(parts[1])
                    "${start.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))} - ${end.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
                } catch (e: Exception) {
                    "Custom Range"
                }
            }
            else -> selectedFilter
        }
    }
    
    val pitchTypeLabel = when (selectedPitchType) {
        true -> "Short Pitch"
        false -> "Long Pitch"
        null -> "All Pitches"
    }
    
    val dateRangePickerState = rememberDateRangePickerState()

    Scaffold(
        topBar = {
            Column {
                StatsTopBar(
                    title = "All Players Statistics",
                    subtitle = if (sortBy == "Overall Ranking") {
                        "${sortedPlayers.size} players (3+ matches) • Sorted by: $sortBy"
                    } else {
                        "${players.size} players • Sorted by: $sortBy"
                    },
                    onBack = {
                        (context as ComponentActivity).finish()
                    }
                )
                // Filter row
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
                        
                        FilterChip(
                            selected = true,
                            onClick = { showSortDialog = true },
                            label = { Text(sortBy, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (players.isEmpty()) {
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
                            text = "No players found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sortedPlayers.size) { index ->
                        val player = sortedPlayers[index]
                        AllPlayerStatsCard(
                            player = player,
                            rank = if (sortBy == "Overall Ranking") index + 1 else null,
                            showRankingScore = sortBy == "Overall Ranking",
                            rankingScore = if (sortBy == "Overall Ranking") {
                                RankingUtils.calculateOverallScore(player, players)
                            } else 0.0,
                            sortBy = sortBy, // Pass the sort option
                            onClick = {
                                val intent = Intent(context, PlayerDetailActivity::class.java)
                                intent.putExtra("player_name", player.name)
                                // Pass current filter information
                                intent.putExtra("filter_group_id", selectedGroupId)
                                intent.putExtra("filter_group_name", selectedGroupName)
                                selectedPitchType?.let { intent.putExtra("filter_pitch_type", it) }
                                intent.putExtra("filter_date", selectedFilter)
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
        
        if (showSortDialog) {
            AlertDialog(
                onDismissRequest = { showSortDialog = false },
                title = { Text("Sort Players By", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Batting stats
                        item {
                            OutlinedCard(
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏏", fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Batting",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf("Runs", "Balls Faced", "Batting Avg", "Strike Rate", "Boundaries", "Fours", "Sixes", "Duck %", "Boundary %", "Dot Ball %", "Consistency").forEach { option ->
                                        SortOption(option, sortBy) {
                                            sortBy = option
                                            showSortDialog = false
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Bowling stats
                        item {
                            OutlinedCard(
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚾", fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Bowling",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf("Wickets", "Bowling Avg", "Bowling SR", "Economy", "Extras", "Maidens", "Pressure Index").forEach { option ->
                                        SortOption(option, sortBy) {
                                            sortBy = option
                                            showSortDialog = false
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Fielding stats
                        item {
                            OutlinedCard(
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🥊", fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Fielding",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf("Catches", "Run Outs", "Stumpings").forEach { option ->
                                        SortOption(option, sortBy) {
                                            sortBy = option
                                            showSortDialog = false
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Overall Ranking
                        item {
                            OutlinedCard(
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    width = 2.dp
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏆", fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                "Overall Ranking",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "40% Bat • 40% Bowl • 20% Field",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    SortOption("Overall Ranking", sortBy) {
                                        sortBy = "Overall Ranking"
                                        showSortDialog = false
                                    }
                                }
                            }
                        }
                        
                        // General
                        item {
                            OutlinedCard(
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("📊", fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "General",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf("Matches").forEach { option ->
                                        SortOption(option, sortBy) {
                                            sortBy = option
                                            showSortDialog = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
        
        // Group Picker Dialog
        if (showGroupPicker) {
            AlertDialog(
                onDismissRequest = { showGroupPicker = false },
                title = { Text("Select Group") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGroupId = null
                                    selectedGroupName = "All Groups"
                                    showGroupPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedGroupId == null,
                                onClick = {
                                    selectedGroupId = null
                                    selectedGroupName = "All Groups"
                                    showGroupPicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("All Groups")
                        }
                        groups.forEach { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedGroupId = group.id
                                        selectedGroupName = group.name
                                        showGroupPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedGroupId == group.id,
                                    onClick = {
                                        selectedGroupId = group.id
                                        selectedGroupName = group.name
                                        showGroupPicker = false
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(group.name)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
        
        // Pitch Type Picker Dialog
        if (showPitchPicker) {
            AlertDialog(
                onDismissRequest = { showPitchPicker = false },
                title = { Text("Select Pitch Type") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPitchType = null
                                    showPitchPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPitchType == null,
                                onClick = {
                                    selectedPitchType = null
                                    showPitchPicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("All Pitches")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPitchType = true
                                    showPitchPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPitchType == true,
                                onClick = {
                                    selectedPitchType = true
                                    showPitchPicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Short Pitch")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPitchType = false
                                    showPitchPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPitchType == false,
                                onClick = {
                                    selectedPitchType = false
                                    showPitchPicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Long Pitch")
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
                        listOf("All Time", "Today", "This Week", "This Month", "Custom Range").forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (option == "Custom Range") {
                                            showFilterDialog = false
                                            showDateRangePicker = true
                                        } else {
                                            selectedFilter = option
                                            showFilterDialog = false
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFilter == option,
                                    onClick = {
                                        if (option == "Custom Range") {
                                            showFilterDialog = false
                                            showDateRangePicker = true
                                        } else {
                                            selectedFilter = option
                                            showFilterDialog = false
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(option)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
        
        // Modern Date Range Picker Dialog
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
                    ) {
                        Text("Apply Range")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDateRangePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    title = {
                        Text(
                            "Select Date Range",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    headline = {
                        val start = dateRangePickerState.selectedStartDateMillis?.let {
                            Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                        } ?: "Start"
                        val end = dateRangePickerState.selectedEndDateMillis?.let {
                            Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                        } ?: "End"
                        
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                start,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("→", fontSize = 14.sp)
                            Text(
                                end,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AllPlayerStatsCard(
    player: PlayerDetailedStats,
    rank: Int? = null,
    showRankingScore: Boolean = false,
    rankingScore: Double = 0.0,
    sortBy: String = "Runs", // Add sortBy parameter
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (showRankingScore && rank != null && rank <= 3) {
                when (rank) {
                    1 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    2 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    3 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    else -> sectionContainer()
                }
            } else sectionContainer()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (showRankingScore && rank != null && rank <= 3) 4.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Player name with rank if applicable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showRankingScore && rank != null) {
                        // Rank badge
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = when (rank) {
                                1 -> MaterialTheme.colorScheme.primary
                                2 -> MaterialTheme.colorScheme.secondary
                                3 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (rank <= 3) {
                                        when (rank) {
                                            1 -> "🥇"
                                            2 -> "🥈"
                                            else -> "🥉"
                                        }
                                    } else "#$rank",
                                    fontSize = if (rank <= 3) 16.sp else 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rank > 3) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Transparent
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = player.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (showRankingScore) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%.1f".format(rankingScore),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "pts",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Dynamic content based on sort option
            when (sortBy) {
                "Balls Faced" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🏏 Balls Faced", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("${player.totalBallsFaced}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Runs: ${player.totalRuns} • Strike Rate: ${"%.1f".format(player.strikeRate)} • Dots: ${player.totalDots}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Sixes" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🏏 Sixes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("${player.totalSixes}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Total Runs: ${player.totalRuns} • Strike Rate: ${"%.1f".format(player.strikeRate)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Fours" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🏏 Fours", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("${player.totalFours}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Total Runs: ${player.totalRuns} • Strike Rate: ${"%.1f".format(player.strikeRate)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Boundaries" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🏏 Boundaries", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("${player.totalFours + player.totalSixes}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("4s: ${player.totalFours} • 6s: ${player.totalSixes} • Boundary %: ${"%.1f".format(player.boundaryPercentage)}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Duck %" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🦆 Duck %", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text("${"%.1f".format(player.duckPercentage)}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Text("${player.ducks}/${player.totalMatches} matches • Golden: ${player.goldenDucks} • Avg: ${"%.1f".format(player.battingAverage)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Boundary %" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🏏 Boundary %", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("${"%.1f".format(player.boundaryPercentage)}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("4s: ${player.totalFours} • 6s: ${player.totalSixes} • Total Runs: ${player.totalRuns}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Dot Ball %" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🔴 Dot Ball %", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        if (player.totalDots == 0 && player.totalBallsFaced > 0) {
                            Text("N/A", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("${"%.1f".format(player.dotBallPercentage)}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (player.totalDots == 0 && player.totalBallsFaced > 0) {
                        Text("Dot ball data only available for new matches • Balls Faced: ${player.totalBallsFaced}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        Text("Dots: ${player.totalDots} • Balls Faced: ${player.totalBallsFaced} • Strike Rate: ${"%.1f".format(player.strikeRate)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "Consistency" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📊 Consistency", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                        Text(player.consistencyRating, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Text("Std Dev: ${"%.1f".format(player.consistency)} • Matches: ${player.totalMatches} • Form: ${player.recentForm}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Maidens" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⚾ Maidens", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                        Text("${player.totalMaidenOvers}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Text("Maiden %: ${"%.1f".format(player.maidenOverPercentage)}% • Overs: ${"%.1f".format(player.oversBowled)} • Economy: ${"%.1f".format(player.economyRate)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Pressure Index" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⚾ Pressure Index", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                        Text(player.pressureRating, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Text("Score: ${"%.0f".format(player.pressureIndex)}/100 • Wickets: ${player.totalWickets} • Economy: ${"%.1f".format(player.economyRate)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Catches" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🧤 Catches", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Text("${player.totalCatches}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text("Matches: ${player.totalMatches} • Run-outs: ${player.totalRunOuts} • Stumpings: ${player.totalStumpings}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Run Outs" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🧤 Run Outs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Text("${player.totalRunOuts}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text("Matches: ${player.totalMatches} • Catches: ${player.totalCatches} • Stumpings: ${player.totalStumpings}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Stumpings" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🧤 Stumpings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Text("${player.totalStumpings}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text("Matches: ${player.totalMatches} • Catches: ${player.totalCatches} • Run-outs: ${player.totalRunOuts}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Extras" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⚠️ Extras", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text("${player.totalWides + player.totalNoBalls}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Text("Wides: ${player.totalWides} • No-balls: ${player.totalNoBalls} • Economy: ${"%.1f".format(player.economyRate)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    // Default display - show batting, bowling, fielding
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🏏 BATTING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${player.totalRuns} runs • ${player.totalMatches} matches",
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Avg: ${"%.1f".format(player.battingAverage)} • SR: ${"%.1f".format(player.strikeRate)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "⚾ BOWLING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${player.totalWickets} wickets",
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Avg: ${"%.1f".format(player.bowlingAverage)} • Eco: ${"%.1f".format(player.economyRate)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Fielding stats
                    if (player.totalCatches + player.totalRunOuts + player.totalStumpings > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "🧤 Fielding: ${player.totalCatches} catches • ${player.totalRunOuts} run-outs • ${player.totalStumpings} stumpings",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Extras bowled
                    if (player.totalWides + player.totalNoBalls > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Extras: ${player.totalWides} wides • ${player.totalNoBalls} no-balls",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SortOption(
    option: String,
    selectedOption: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedOption == option,
            onClick = onClick
        )
        Spacer(Modifier.width(8.dp))
        Text(option, fontSize = 14.sp)
    }
}

