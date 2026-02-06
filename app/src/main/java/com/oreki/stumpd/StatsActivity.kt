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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.SectionTitle
import com.oreki.stumpd.ui.theme.StatsTopBar
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.sectionContainer
import com.oreki.stumpd.utils.RankingUtils
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.ui.text.style.TextAlign
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.viewmodel.StatsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val context = LocalContext.current

    val isLoading = vm.isLoading
    val players = vm.players
    val matches = vm.matches
    val selectedFilter = vm.selectedFilter
    val selectedGroupId = vm.selectedGroupId
    val selectedGroupName = vm.selectedGroupName
    val selectedPitchType = vm.selectedPitchType
    val groups = vm.groups

    Scaffold(
        topBar = {
            Column {
                StatsTopBar(
                    title = "Statistics",
                    subtitle = "${players.size} players • ${matches.size} matches\n$selectedGroupName",
                    onBack = {
                        val intent = Intent(context, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    }
                )
                // Filter Chips Row
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
                        // Group Filter Chip
                        FilterChip(
                            selected = selectedGroupId != null,
                            onClick = { vm.showGroupPicker = true },
                            label = { 
                                Text(
                                    selectedGroupName,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        // Pitch Type Filter Chip
                        val pitchLabel = when (selectedPitchType) {
                            true -> "Short Pitch"
                            false -> "Long Pitch"
                            null -> "All Pitches"
                        }
                        FilterChip(
                            selected = selectedPitchType != null,
                            onClick = { vm.showPitchPicker = true },
                            label = { 
                                Text(
                                    pitchLabel,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Terrain,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )

                        // Date Filter Chip
                        FilterChip(
                            selected = selectedFilter != "All Time",
                            onClick = { vm.showFilterDialog = true },
                            label = { 
                                Text(
                                    selectedFilter,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (players.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Statistics Yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Play some matches to see player statistics and leaderboards!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(context, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                context.startActivity(intent)
                                (context as ComponentActivity).finish()
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start a Match")
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Top Batsmen Section Header
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Top Batsmen",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    val topBatsmen = players.sortedByDescending { it.totalRuns }.take(5)
                    items(topBatsmen.size) { index ->
                        val player = topBatsmen[index]
                        PlayerStatsCard(
                            player = player,
                            statType = "Batting",
                            primaryStat = "${player.totalRuns} runs",
                            secondaryStat = "Avg: ${"%.1f".format(player.battingAverage)} • SR: ${
                                "%.1f".format(
                                    player.strikeRate
                                )
                            }",
                            rank = index + 1,
                            onClick = {
                                val intent = Intent(context, PlayerDetailActivity::class.java)
                                intent.putExtra("player_name", player.name)
                                // Pass filter information
                                intent.putExtra("filter_group_id", selectedGroupId)
                                intent.putExtra("filter_group_name", selectedGroupName)
                                selectedPitchType?.let { intent.putExtra("filter_pitch_type", it) }
                                intent.putExtra("filter_date", selectedFilter)
                                context.startActivity(intent)
                            }
                        )
                    }
                    
                    // Top Bowlers Section Header
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Top Bowlers",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val topBowlers = players.filter { it.totalWickets > 0 }
                        .sortedByDescending { it.totalWickets }.take(5)
                    items(topBowlers.size) { index ->
                        val player = topBowlers[index]
                        PlayerStatsCard(
                            player = player,
                            statType = "Bowling",
                            primaryStat = "${player.totalWickets} wickets",
                            secondaryStat = "Avg: ${"%.1f".format(player.bowlingAverage)} • Eco: ${
                                "%.1f".format(
                                    player.economyRate
                                )
                            }",
                            rank = index + 1,
                            onClick = {
                                val intent = Intent(context, PlayerDetailActivity::class.java)
                                intent.putExtra("player_name", player.name)
                                // Pass filter information
                                intent.putExtra("filter_group_id", selectedGroupId)
                                intent.putExtra("filter_group_name", selectedGroupName)
                                selectedPitchType?.let { intent.putExtra("filter_pitch_type", it) }
                                intent.putExtra("filter_date", selectedFilter)
                                context.startActivity(intent)
                            }
                        )
                    }
                    
                    // View All Players button
                    item { 
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(context, AllPlayersStatsActivity::class.java)
                                // Pass filter information
                                intent.putExtra("filter_group_id", selectedGroupId)
                                intent.putExtra("filter_group_name", selectedGroupName)
                                selectedPitchType?.let { intent.putExtra("filter_pitch_type", it) }
                                intent.putExtra("filter_date", selectedFilter)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.List, contentDescription = "View All")
                            Spacer(Modifier.width(8.dp))
                            Text("View All Players Stats")
                        }
                    }
                }
            }
        }

        if (vm.showGroupPicker) {
            AlertDialog(
                onDismissRequest = { vm.showGroupPicker = false },
                title = { Text("Filter by Group") },
                text = {
                    LazyColumn(Modifier.height(360.dp)) {
                        item {
                            ListItem(
                                headlineContent = { Text("All Groups") },
                                modifier = Modifier.clickable {
                                    vm.onGroupSelected(null, "All Groups")
                                }
                            )
                        }
                        items(groups) { g ->
                            ListItem(
                                headlineContent = { Text(g.name) },
                                supportingContent = {
                                    val count = matches.count { it.groupId == g.id }
                                    Text("$count matches", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                modifier = Modifier.clickable {
                                    vm.onGroupSelected(g.id, g.name)
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { vm.showGroupPicker = false }) { Text("Close") } }
            )
        }

        if (vm.showPitchPicker) {
            AlertDialog(
                onDismissRequest = { vm.showPitchPicker = false },
                title = { Text("Filter by Pitch Type") },
                text = {
                    Column {
                        listOf("All", "Short", "Long").forEach { option ->
                            Text(
                                text = option,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.onPitchTypeSelected(
                                            when (option) {
                                                "Short" -> true
                                                "Long" -> false
                                                else -> null
                                            }
                                        )
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (vm.showFilterDialog) {
            // compute last 3 distinct match dates as LocalDate
            val last3Dates = matches
                .map { Instant.ofEpochMilli(it.matchDate).atZone(ZoneId.systemDefault()).toLocalDate() }
                .distinct()
                .sortedDescending()
                .take(3)

            AlertDialog(
                onDismissRequest = { vm.showFilterDialog = false },
                title = { Text("Filter Statistics") },
                text = {
                    Column {
                        // 1) All Time
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.onFilterSelected("All Time") }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFilter == "All Time",
                                onClick = { vm.onFilterSelected("All Time") }
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
                                    .clickable { vm.onFilterSelected("Date:$iso") }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFilter == "Date:$iso",
                                    onClick = { vm.onFilterSelected("Date:$iso") }
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
                                .clickable { vm.showDateRangePicker = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFilter.startsWith("CustomRange"),
                                onClick = { vm.showDateRangePicker = true }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Custom Date Range…")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.showFilterDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Modern Date Range Picker Dialog
        if (vm.showDateRangePicker) {
            val dateRangeState = rememberDateRangePickerState()

            DatePickerDialog(
                onDismissRequest = { vm.showDateRangePicker = false },
                confirmButton = {
                    Button(
                        onClick = {
                            val startMillis = dateRangeState.selectedStartDateMillis
                            val endMillis = dateRangeState.selectedEndDateMillis
                            if (startMillis != null && endMillis != null) {
                                vm.onDateRangeSelected(startMillis, endMillis)
                            }
                        },
                        enabled = dateRangeState.selectedStartDateMillis != null && 
                                  dateRangeState.selectedEndDateMillis != null
                    ) {
                        Text("Apply Range")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.showDateRangePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DateRangePicker(
                    state = dateRangeState,
                    title = {
                        Text(
                            "Select Date Range",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    headline = {
                        val start = dateRangeState.selectedStartDateMillis?.let {
                            Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                        } ?: "Start"
                        val end = dateRangeState.selectedEndDateMillis?.let {
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
fun PlayerStatsCard(
    player: PlayerDetailedStats,
    statType: String,
    primaryStat: String,
    secondaryStat: String,
    rank: Int? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (rank != null && rank <= 3) {
                when (rank) {
                    1 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    2 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    3 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            } else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (rank != null && rank <= 3) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            if (rank != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = when (rank) {
                        1 -> MaterialTheme.colorScheme.primary
                        2 -> MaterialTheme.colorScheme.secondary
                        3 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "#$rank",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rank <= 3) MaterialTheme.colorScheme.onPrimary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Player Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondaryStat,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            // Primary Stat with Badge
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = primaryStat,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Arrow indicator
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Pure aggregation from filtered matches; optional idToName map for normalization
private fun computeDetailedStatsFromMatches(
    source: List<MatchHistory>,
    idToName: Map<String, String> = emptyMap()
): List<PlayerDetailedStats> {
    val playersMap = linkedMapOf<String, PlayerDetailedStats>()

    fun normName(raw: String): String {
        // If an id was mistakenly stored as "uuid", translate via map when possible
        return idToName[raw] ?: raw
    }

    fun getOrPutPlayer(n: String): PlayerDetailedStats {
        val name = normName(n)
        return playersMap.getOrPut(name.lowercase()) {
            PlayerDetailedStats(
                playerId = name.replace(" ", "_").lowercase(),
                name = name
            )
        }
    }

    source.forEach { match ->
        // 1) First-innings batting (Team 1)
        match.firstInningsBatting.forEach { p ->
            val player = getOrPutPlayer(p.name)
            player.totalRuns += p.runs
            player.totalBallsFaced += p.ballsFaced
            player.totalFours += p.fours
            player.totalSixes += p.sixes
            if (p.isOut) player.timesOut++ else player.notOuts++
            val existing = player.matchPerformances.find { it.matchId == match.id }
            if (existing == null) {
                player.totalMatches++
                player.matchPerformances.add(
                    playerPerformanceFromBat(match, p, myTeam = match.team1Name, opp = match.team2Name)
                )
            } else {
                val idx = player.matchPerformances.indexOf(existing)
                player.matchPerformances[idx] = existing.copy(
                    runs = existing.runs + p.runs,
                    ballsFaced = existing.ballsFaced + p.ballsFaced,
                    fours = existing.fours + p.fours,
                    sixes = existing.sixes + p.sixes,
                    isOut = existing.isOut || p.isOut
                )
            }
        }
        // 2) First-innings bowling (Team 2)
        match.firstInningsBowling.forEach { p ->
            val player = getOrPutPlayer(p.name)
            player.totalWickets += p.wickets
            player.totalRunsConceded += p.runsConceded
            player.totalBallsBowled += (p.oversBowled * 6).toInt()
            upsertBowlPerf(player, match, p, myTeam = match.team2Name, opp = match.team1Name)
        }
        // 3) Second-innings batting (Team 2)
        match.secondInningsBatting.forEach { p ->
            val player = getOrPutPlayer(p.name)
            player.totalRuns += p.runs
            player.totalBallsFaced += p.ballsFaced
            player.totalFours += p.fours
            player.totalSixes += p.sixes
            if (p.isOut) player.timesOut++ else player.notOuts++
            val existing = player.matchPerformances.find { it.matchId == match.id }
            if (existing == null) {
                player.totalMatches++
                player.matchPerformances.add(
                    playerPerformanceFromBat(match, p, myTeam = match.team2Name, opp = match.team1Name)
                )
            } else {
                val idx = player.matchPerformances.indexOf(existing)
                player.matchPerformances[idx] = existing.copy(
                    runs = existing.runs + p.runs,
                    ballsFaced = existing.ballsFaced + p.ballsFaced,
                    fours = existing.fours + p.fours,
                    sixes = existing.sixes + p.sixes,
                    isOut = existing.isOut || p.isOut
                )
            }
        }
        // 4) Second-innings bowling (Team 1)
        match.secondInningsBowling.forEach { p ->
            val player = getOrPutPlayer(p.name)
            player.totalWickets += p.wickets
            player.totalRunsConceded += p.runsConceded
            player.totalBallsBowled += (p.oversBowled * 6).toInt()
            upsertBowlPerf(player, match, p, myTeam = match.team1Name, opp = match.team2Name)
        }
    }

    return playersMap.values.toList()
}

// Helpers inside EnhancedPlayerStorageManager
private fun playerPerformanceFromBat(
    match: MatchHistory,
    p: PlayerMatchStats,
    myTeam: String,
    opp: String
): MatchPerformance {
    return MatchPerformance(
        matchId = match.id,
        matchDate = match.matchDate,
        opposingTeam = opp,
        myTeam = myTeam,
        runs = p.runs,
        ballsFaced = p.ballsFaced,
        fours = p.fours,
        sixes = p.sixes,
        isOut = p.isOut,
        wickets = 0,
        runsConceded = 0,
        ballsBowled = 0,
        isWinner = match.winnerTeam == myTeam,
        isJoker = p.isJoker,
        isShortPitch = match.shortPitch,
        groupId = match.groupId
    )
}

private fun upsertBowlPerf(
    player: PlayerDetailedStats,
    match: MatchHistory,
    p: PlayerMatchStats,
    myTeam: String,
    opp: String
) {
    val existing = player.matchPerformances.find { it.matchId == match.id }
    if (existing != null) {
        val idx = player.matchPerformances.indexOf(existing)
        player.matchPerformances[idx] = existing.copy(
            wickets = existing.wickets + p.wickets,
            runsConceded = existing.runsConceded + p.runsConceded,
            ballsBowled = existing.ballsBowled + (p.oversBowled * 6).toInt(),
            isWinner = match.winnerTeam == myTeam || existing.isWinner,
            isJoker = existing.isJoker || p.isJoker
        )
    } else {
        player.totalMatches++
        player.matchPerformances.add(
            MatchPerformance(
                matchId = match.id,
                matchDate = match.matchDate,
                opposingTeam = opp,
                myTeam = myTeam,
                wickets = p.wickets,
                runsConceded = p.runsConceded,
                ballsBowled = (p.oversBowled * 6).toInt(),
                isWinner = match.winnerTeam == myTeam,
                isJoker = p.isJoker
            )
        )
    }
}

@Composable
fun RankingCard(
    rank: Int,
    player: PlayerDetailedStats,
    score: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when (rank) {
                1 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                2 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                3 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (rank <= 3) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = when (rank) {
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.secondary
                    3 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (rank <= 3) {
                            when (rank) {
                                1 -> "🥇"
                                2 -> "🥈"
                                else -> "🥉"
                            }
                        } else {
                            "#$rank"
                        },
                        fontSize = if (rank <= 3) 24.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rank > 3) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Player Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "${player.totalRuns}r",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${player.totalWickets}w",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    val fielding = player.totalCatches + player.totalRunOuts + player.totalStumpings
                    if (fielding > 0) {
                        Text(
                            text = "${fielding}f",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Score
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.1f".format(score),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "pts",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}