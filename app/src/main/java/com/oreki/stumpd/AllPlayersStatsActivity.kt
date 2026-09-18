package com.oreki.stumpd

import com.oreki.stumpd.domain.model.MatchSettings
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
import androidx.compose.material.icons.automirrored.filled.Sort
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
import com.oreki.stumpd.ui.components.MatchDateFilterDialog
import com.oreki.stumpd.ui.components.formatDateFilterLabel
import com.oreki.stumpd.ui.components.pitchTypeLabel
import com.oreki.stumpd.ui.theme.EmptyState
import com.oreki.stumpd.ui.theme.StatsTopBar
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.utils.RankingUtils
import com.oreki.stumpd.viewmodel.AllPlayersStatsViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AllPlayersStatsActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        
        // Filters come from the intent when another screen passed them; otherwise the view model
        // picks up the group the app is filtered to. (This used to read a "default_group_id"
        // SharedPreference that nothing writes any more — the selection lives in the database —
        // so the screen always opened on "All Groups".)
        val filterGroupId = intent.getStringExtra("filter_group_id")
        val filterGroupName = intent.getStringExtra("filter_group_name") ?: ""
        val filterPitchType = if (intent.hasExtra("filter_pitch_type")) {
            intent.getBooleanExtra("filter_pitch_type", false)
        } else false // Default to Long Pitch
        val filterDate = intent.getStringExtra("filter_date") ?: "All Time"
        val initialSortBy = intent.getStringExtra("sort_by") ?: "Runs"
        
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
                        filterDate = filterDate,
                        initialSortBy = initialSortBy
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
    filterDate: String,
    initialSortBy: String = "Runs"
) {
    val context = LocalContext.current
    val viewModel: AllPlayersStatsViewModel = hiltViewModel(
        creationCallback = { factory: AllPlayersStatsViewModel.Factory ->
            factory.create(
                filterGroupId,
                filterGroupName,
                filterPitchType,
                filterDate,
                initialSortBy,
            )
        }
    )
    val uiState = viewModel.uiState
    val sortBy = viewModel.sortBy
    val battingMilestone = viewModel.battingMilestone
    val selectedGroupId = viewModel.selectedGroupId
    val selectedGroupName = viewModel.selectedGroupName
    val selectedPitchType = viewModel.selectedPitchType
    val selectedFilter = viewModel.selectedFilter
    val groups = viewModel.groups

    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }
    var showPitchPicker by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val players: List<PlayerDetailedStats> =
        (uiState as? AllPlayersStatsViewModel.UiState.Content)?.players ?: emptyList()
    val groupMatchContext: List<Pair<String, Long>>? =
        (uiState as? AllPlayersStatsViewModel.UiState.Content)?.groupMatchContext
    val baseMatchesForFilter: List<MatchHistory> =
        (uiState as? AllPlayersStatsViewModel.UiState.Content)?.baseMatchesForFilter ?: emptyList()
    val potmCounts: Map<String, Int> =
        (uiState as? AllPlayersStatsViewModel.UiState.Content)?.potmCounts ?: emptyMap()

    val sortedPlayers = remember(players, sortBy, groupMatchContext, potmCounts) {
        when (sortBy) {
            // Equal runs are broken by average, then strike rate - both higher-is-better.
            "Runs" -> players.sortedWith(
                compareByDescending<PlayerDetailedStats> { it.totalRuns }
                    .thenByDescending { it.battingAverage }
                    .thenByDescending { it.strikeRate }
            )
            "Balls Faced" -> players.sortedByDescending { it.totalBallsFaced }
            "Highest Score" -> players.filter { it.highestScore > 0 }
                .sortedByDescending { it.highestScore }
            "Not Outs" -> players.filter { it.notOuts > 0 }.sortedByDescending { it.notOuts }
            // Milestone scores: how often the player passed the group's threshold, then how
            // far past it their best went.
            SORT_MILESTONES, "50s" -> players
                .filter { it.milestoneScores(battingMilestone) > 0 }
                .sortedWith(
                    compareByDescending<PlayerDetailedStats> { it.milestoneScores(battingMilestone) }
                        .thenByDescending { it.highestScore }
                )
            // Most wickets in an innings first, then the fewest runs conceded for that haul.
            "Best Bowling" -> players.filter { it.bestBowlingWickets > 0 }.sortedWith(
                compareByDescending<PlayerDetailedStats> { it.bestBowlingWickets }
                    .thenBy { it.bestBowlingRuns }
            )
            "Player of the Match" -> players.filter { (potmCounts[it.name] ?: 0) > 0 }
                .sortedWith(
                    compareByDescending<PlayerDetailedStats> { potmCounts[it.name] ?: 0 }
                        .thenByDescending { it.totalMatches }
                )
            // Equal wickets are broken by economy, then bowling average - both lower-is-better.
            // Players who have not bowled report 0.0 for these, which would otherwise rank them
            // as the most economical, so they are pushed to the bottom instead.
            "Wickets" -> players.sortedWith(
                compareByDescending<PlayerDetailedStats> { it.totalWickets }
                    .thenBy { if (it.totalBallsBowled > 0) it.economyRate else Double.MAX_VALUE }
                    .thenBy { if (it.totalWickets > 0) it.bowlingAverage else Double.MAX_VALUE }
            )
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
            // Ranked by how many ducks, not the rate - a 1-in-1 duck should not outrank 9-in-40.
            "Ducks" -> players.filter { it.ducks > 0 }.sortedWith(
                compareByDescending<PlayerDetailedStats> { it.ducks }
                    .thenByDescending { it.goldenDucks }
            )
            "Golden Ducks" -> players.filter { it.goldenDucks > 0 }.sortedWith(
                compareByDescending<PlayerDetailedStats> { it.goldenDucks }
                    .thenByDescending { it.ducks }
            )
            "Diamond Ducks" -> players.filter { it.diamondDucks > 0 }.sortedWith(
                compareByDescending<PlayerDetailedStats> { it.diamondDucks }
                    .thenByDescending { it.ducks }
            )
            "Boundary %" -> players.filter { it.totalRuns > 0 }.sortedByDescending { it.boundaryPercentage }
            "Dot Ball %" -> players.filter { it.totalBallsFaced > 0 }.sortedBy { it.dotBallPercentage }
            "Maidens" -> players.filter { it.totalMaidenOvers > 0 }.sortedByDescending { it.totalMaidenOvers }
            "Pressure Index" -> players.filter { it.totalBallsBowled > 0 }.sortedByDescending { it.pressureIndex }
            "Consistency" -> players.filter { it.matchPerformances.size >= 2 }.sortedBy { it.consistency }
            "Batting Ranking" -> players
                .filter { (it.totalRuns > 0 || it.totalBallsFaced > 0) && it.matchPerformances.count { p -> p.ballsFaced > 0 || p.runs > 0 } >= 3 }
                .map { player -> Pair(player, RankingUtils.calculateBattingRating(player.matchPerformances, groupMatchContext)) }
                .sortedByDescending { it.second }
                .map { it.first }
            "Bowling Ranking" -> players
                .filter { it.totalBallsBowled > 0 && it.matchPerformances.count { p -> p.ballsBowled > 0 } >= 3 }
                .map { player -> Pair(player, RankingUtils.calculateBowlingRating(player.matchPerformances, groupMatchContext)) }
                .sortedByDescending { it.second }
                .map { it.first }
            "All-Rounder Ranking" -> players
                .filter { it.totalMatches >= 3 && (it.totalRuns > 0 || it.totalBallsFaced > 0) && it.totalBallsBowled > 0 }
                .map { player -> Pair(player, RankingUtils.calculateOverallRating(player, groupMatchContext)) }
                .sortedByDescending { it.second }
                .map { it.first }
            else -> players
        }
    }
    
    val formattedDateFilter = remember(selectedFilter) { formatDateFilterLabel(selectedFilter) }
    val pitchTypeLabel = pitchTypeLabel(selectedPitchType)
    
    val dateRangePickerState = rememberDateRangePickerState()

    Scaffold(
        topBar = {
            Column {
                StatsTopBar(
                    title = "All Players Statistics",
                    subtitle = if (sortBy.endsWith("Ranking")) {
                        "${sortedPlayers.size} qualified • Sorted by: ${sortLabel(sortBy, battingMilestone)}"
                    } else {
                        // Says "career" because Records shows the same words for single-innings
                        // bests, and the two used to read as contradictions.
                        "${players.size} players • Career, sorted by: ${sortLabel(sortBy, battingMilestone)}"
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
                            label = { Text(selectedGroupName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )

                        FilterChip(
                            selected = selectedPitchType != null,
                            onClick = { showPitchPicker = true },
                            label = { Text(pitchTypeLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )

                        FilterChip(
                            selected = selectedFilter != "All Time",
                            onClick = { showFilterDialog = true },
                            label = { Text(formattedDateFilter, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        
                        FilterChip(
                            selected = true,
                            onClick = { showSortDialog = true },
                            label = { Text(sortLabel(sortBy, battingMilestone), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            
            if (uiState is AllPlayersStatsViewModel.UiState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState is AllPlayersStatsViewModel.UiState.Error) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = uiState.message,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else if (players.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    title = "No players found",
                    description = "No player has a record under the current filters.",
                )
            } else {
                LazyColumn {
                    items(sortedPlayers.size) { index ->
                        val player = sortedPlayers[index]
                        AllPlayerStatsRow(
                            player = player,
                            rank = index + 1,
                            statLine = statLineFor(
                                sortBy = sortBy,
                                player = player,
                                potmAwards = potmCounts[player.name] ?: 0,
                                milestone = battingMilestone,
                            ),
                            rating = when (sortBy) {
                                "Batting Ranking" -> RankingUtils.calculateBattingRating(player.matchPerformances, groupMatchContext)
                                "Bowling Ranking" -> RankingUtils.calculateBowlingRating(player.matchPerformances, groupMatchContext)
                                "All-Rounder Ranking" -> RankingUtils.calculateOverallRating(player, groupMatchContext)
                                else -> null
                            },
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
                        if (index < sortedPlayers.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
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
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏏", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Batting",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf("Runs", "Highest Score", "Batting Avg", "Strike Rate", SORT_MILESTONES, "Not Outs", "Balls Faced", "Boundaries", "Fours", "Sixes", "Ducks", "Golden Ducks", "Diamond Ducks", "Boundary %", "Dot Ball %", "Consistency").forEach { option ->
                                        SortOption(
                                            option = option,
                                            selectedOption = sortBy,
                                            label = sortLabel(option, battingMilestone),
                                        ) {
                                            viewModel.updateSortBy(option)
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
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚾", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Bowling",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf("Wickets", "Best Bowling", "Bowling Avg", "Bowling SR", "Economy", "Maidens", "Extras", "Pressure Index").forEach { option ->
                                        SortOption(option, sortBy, sortLabel(option, battingMilestone)) {
                                            viewModel.updateSortBy(option)
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
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🥊", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Fielding",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf("Catches", "Run Outs", "Stumpings").forEach { option ->
                                        SortOption(option, sortBy, sortLabel(option, battingMilestone)) {
                                            viewModel.updateSortBy(option)
                                            showSortDialog = false
                                        }
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
                                        Text("📊", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "General",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf("Matches", "Player of the Match").forEach { option ->
                                        SortOption(option, sortBy, sortLabel(option, battingMilestone)) {
                                            viewModel.updateSortBy(option)
                                            showSortDialog = false
                                        }
                                    }
                                }
                            }
                        }

                        // Ratings. These already worked as sorts but were only reachable by
                        // arriving from the Rankings screen, so they were never listed here.
                        item {
                            OutlinedCard(
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏆", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Ratings",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    listOf(
                                        "Batting Ranking",
                                        "Bowling Ranking",
                                        "All-Rounder Ranking",
                                    ).forEach { option ->
                                        SortOption(option, sortBy, sortLabel(option, battingMilestone)) {
                                            viewModel.updateSortBy(option)
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
                        // Only show "All Groups" when user belongs to more than one group
                        if (groups.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateSelectedGroup(null, "All Groups")
                                        showGroupPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedGroupId == null,
                                    onClick = {
                                        viewModel.updateSelectedGroup(null, "All Groups")
                                        showGroupPicker = false
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("All Groups")
                            }
                        }
                        groups.forEach { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateSelectedGroup(group.id, group.name)
                                        showGroupPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedGroupId == group.id,
                                    onClick = {
                                        viewModel.updateSelectedGroup(group.id, group.name)
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
                                    viewModel.updatePitchType(null)
                                    showPitchPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPitchType == null,
                                onClick = {
                                    viewModel.updatePitchType(null)
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
                                    viewModel.updatePitchType(true)
                                    showPitchPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPitchType == true,
                                onClick = {
                                    viewModel.updatePitchType(true)
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
                                    viewModel.updatePitchType(false)
                                    showPitchPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPitchType == false,
                                onClick = {
                                    viewModel.updatePitchType(false)
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
        
        if (showFilterDialog) {
            MatchDateFilterDialog(
                currentFilter = selectedFilter,
                matchesInScope = baseMatchesForFilter,
                onFilterSelected = {
                    viewModel.updateDateFilter(it)
                    showFilterDialog = false
                },
                onCustomRangeRequested = {
                    showFilterDialog = false
                    showDateRangePicker = true
                },
                onDismiss = { showFilterDialog = false },
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
                                viewModel.updateDateFilter("CustomRange:${startDate}|${endDate}")
                            }
                            showDateRangePicker = false
                        },
                        enabled = dateRangePickerState.selectedStartDateMillis != null &&
                                  dateRangePickerState.selectedEndDateMillis != null
                    ) {
                        Text("OK")
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
                            style = MaterialTheme.typography.titleLarge,
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("→", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                end,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * Sort key for milestone scores. It's stored as a key rather than as "20s" because the number
 * shown depends on the group's threshold, and a stored sort must not change meaning with it.
 */
const val SORT_MILESTONES = "Milestones"

/** How a sort key reads on screen. */
fun sortLabel(sortKey: String, battingMilestone: Int): String = when (sortKey) {
    SORT_MILESTONES, "50s" -> "${battingMilestone}s"
    else -> sortKey
}

/** Which theme colour a stat leads with — set here so [statLineFor] stays free of Compose. */
enum class StatTint { Primary, Secondary, Tertiary, Error }

/**
 * What a player row leads with: the metric the list is currently sorted by, plus a line of
 * supporting figures. Keeping this a plain function makes the 29 sort options testable and stops
 * the row from being a 370-line `when` of near-identical layouts.
 */
data class PlayerStatLine(
    val label: String,
    val value: String,
    val detail: String,
    val tint: StatTint = StatTint.Primary,
)

internal fun statLineFor(
    sortBy: String,
    player: PlayerDetailedStats,
    potmAwards: Int = 0,
    /** The group's milestone score; see [MatchSettings.battingMilestone]. */
    milestone: Int = MatchSettings().battingMilestone,
): PlayerStatLine {
    fun d(value: Double) = "%.1f".format(value)

    return when (sortBy) {
        "Highest Score" -> PlayerStatLine(
            "Highest score", "${player.highestScore}",
            "${player.totalRuns} runs • Avg ${d(player.battingAverage)} • SR ${d(player.strikeRate)}",
        )
        "Batting Avg" -> PlayerStatLine(
            "Batting average", d(player.battingAverage),
            "${player.totalRuns} runs • Out ${player.timesOut} • ${player.notOuts} not out",
        )
        "Strike Rate" -> PlayerStatLine(
            "Strike rate", d(player.strikeRate),
            "${player.totalRuns} runs off ${player.totalBallsFaced} • ${player.totalFours}x4 ${player.totalSixes}x6",
        )
        SORT_MILESTONES, "50s" -> PlayerStatLine(
            "${milestone}+ scores",
            "${player.milestoneScores(milestone)}",
            "Best ${player.highestScore} • ${player.totalRuns} runs • SR ${d(player.strikeRate)}",
        )
        "Not Outs" -> PlayerStatLine(
            "Not outs", "${player.notOuts}",
            "Out ${player.timesOut} of ${player.timesOut + player.notOuts} innings • Avg ${d(player.battingAverage)}",
        )
        "Balls Faced" -> PlayerStatLine(
            "Balls faced", "${player.totalBallsFaced}",
            "${player.totalRuns} runs • SR ${d(player.strikeRate)} • ${player.totalDots} dots",
        )
        "Boundaries" -> PlayerStatLine(
            "Boundaries", "${player.totalFours + player.totalSixes}",
            "${player.totalFours}x4 ${player.totalSixes}x6 • ${d(player.boundaryPercentage)}% of runs",
        )
        "Fours" -> PlayerStatLine(
            "Fours", "${player.totalFours}",
            "${player.totalRuns} runs • SR ${d(player.strikeRate)}",
        )
        "Sixes" -> PlayerStatLine(
            "Sixes", "${player.totalSixes}",
            "${player.totalRuns} runs • SR ${d(player.strikeRate)}",
        )
        "Ducks" -> PlayerStatLine(
            "Ducks", "${player.ducks}",
            "in ${player.totalMatches} matches • 🥇 ${player.goldenDucks} golden • 💎 ${player.diamondDucks} diamond",
            StatTint.Error,
        )
        "Golden Ducks" -> PlayerStatLine(
            "Golden ducks", "${player.goldenDucks}",
            "Out first ball • ${player.ducks} ducks in ${player.totalMatches} matches",
            StatTint.Error,
        )
        "Diamond Ducks" -> PlayerStatLine(
            "Diamond ducks", "${player.diamondDucks}",
            "Out without facing • ${player.ducks} ducks in ${player.totalMatches} matches",
            StatTint.Error,
        )
        "Boundary %" -> PlayerStatLine(
            "Boundary %", "${d(player.boundaryPercentage)}%",
            "${player.totalFours}x4 ${player.totalSixes}x6 • ${player.totalRuns} runs",
        )
        "Dot Ball %" -> {
            // Dots were only recorded from a later version, so a zero here means "unknown"
            // rather than "never played out a dot".
            val unknown = player.totalDots == 0 && player.totalBallsFaced > 0
            PlayerStatLine(
                "Dot ball %",
                if (unknown) "—" else "${d(player.dotBallPercentage)}%",
                if (unknown)
                    "Not recorded for these matches • ${player.totalBallsFaced} balls faced"
                else
                    "${player.totalDots} dots of ${player.totalBallsFaced} • SR ${d(player.strikeRate)}",
                StatTint.Secondary,
            )
        }
        "Consistency" -> PlayerStatLine(
            "Consistency", player.consistencyRating,
            "σ ${d(player.consistency)} • ${player.totalMatches} matches",
            StatTint.Tertiary,
        )
        "Wickets" -> PlayerStatLine(
            "Wickets", "${player.totalWickets}",
            "Eco ${d(player.economyRate)} • Avg ${d(player.bowlingAverage)} • ${d(player.oversBowled)} ov",
            StatTint.Tertiary,
        )
        "Best Bowling" -> PlayerStatLine(
            "Best bowling", player.bestBowling,
            "${player.totalWickets} wickets • Eco ${d(player.economyRate)} • Avg ${d(player.bowlingAverage)}",
            StatTint.Tertiary,
        )
        "Bowling Avg" -> PlayerStatLine(
            "Bowling average", if (player.totalWickets > 0) d(player.bowlingAverage) else "—",
            "${player.totalWickets} wickets • Eco ${d(player.economyRate)} • ${d(player.oversBowled)} ov",
            StatTint.Tertiary,
        )
        "Bowling SR" -> PlayerStatLine(
            "Balls per wicket", if (player.totalWickets > 0) d(player.wicketStrikeRate) else "—",
            "${player.totalWickets} wickets • Eco ${d(player.economyRate)}",
            StatTint.Tertiary,
        )
        "Economy" -> PlayerStatLine(
            "Economy", d(player.economyRate),
            "${player.totalWickets} wickets • ${d(player.oversBowled)} ov • ${player.totalMaidenOvers} maidens",
            StatTint.Tertiary,
        )
        "Maidens" -> PlayerStatLine(
            "Maiden overs", "${player.totalMaidenOvers}",
            "${d(player.maidenOverPercentage)}% of ${d(player.oversBowled)} ov • Eco ${d(player.economyRate)}",
            StatTint.Tertiary,
        )
        "Extras" -> PlayerStatLine(
            "Extras conceded", "${player.totalWides + player.totalNoBalls}",
            "${player.totalWides} wides • ${player.totalNoBalls} no-balls • Eco ${d(player.economyRate)}",
            StatTint.Error,
        )
        "Pressure Index" -> PlayerStatLine(
            "Pressure index", player.pressureRating,
            "${"%.0f".format(player.pressureIndex)}/100 • ${player.totalWickets} wickets • Eco ${d(player.economyRate)}",
            StatTint.Tertiary,
        )
        "Catches" -> PlayerStatLine(
            "Catches", "${player.totalCatches}",
            "${player.totalMatches} matches • ${player.totalRunOuts} run-outs • ${player.totalStumpings} stumpings",
            StatTint.Secondary,
        )
        "Run Outs" -> PlayerStatLine(
            "Run outs", "${player.totalRunOuts}",
            "${player.totalMatches} matches • ${player.totalCatches} catches • ${player.totalStumpings} stumpings",
            StatTint.Secondary,
        )
        "Stumpings" -> PlayerStatLine(
            "Stumpings", "${player.totalStumpings}",
            "${player.totalMatches} matches • ${player.totalCatches} catches • ${player.totalRunOuts} run-outs",
            StatTint.Secondary,
        )
        "Matches" -> PlayerStatLine(
            "Matches", "${player.totalMatches}",
            "${player.totalRuns} runs • ${player.totalWickets} wickets",
        )
        "Player of the Match" -> PlayerStatLine(
            "Player of the Match", "$potmAwards",
            "in ${player.totalMatches} matches • ${player.totalRuns} runs • ${player.totalWickets} wickets",
            StatTint.Secondary,
        )
        "Batting Ranking" -> PlayerStatLine(
            "rating", "",
            "${player.totalRuns} runs • Avg ${d(player.battingAverage)} • SR ${d(player.strikeRate)}",
        )
        "Bowling Ranking" -> PlayerStatLine(
            "rating", "",
            "${player.totalWickets} wickets • Avg ${d(player.bowlingAverage)} • Eco ${d(player.economyRate)}",
            StatTint.Tertiary,
        )
        "All-Rounder Ranking" -> PlayerStatLine(
            "rating", "",
            "${player.totalRuns} runs • ${player.totalWickets} wickets • ${player.totalMatches} matches",
        )
        // "Runs" and anything unrecognised: lead with the runs, since that's the default sort.
        else -> PlayerStatLine(
            "Runs", "${player.totalRuns}",
            "Avg ${d(player.battingAverage)} • SR ${d(player.strikeRate)} • " +
                "HS ${player.highestScore} • ${player.totalMatches} matches",
        )
    }
}

/**
 * One player in the list: rank, name, the metric being sorted by, and a supporting line.
 *
 * This replaced a card per player with 16dp padding, which fitted about five of 54 players on a
 * screen.
 */
@Composable
fun AllPlayerStatsRow(
    player: PlayerDetailedStats,
    rank: Int,
    statLine: PlayerStatLine,
    rating: Double? = null,
    onClick: () -> Unit,
) {
    val tint = when (statLine.tint) {
        StatTint.Primary -> MaterialTheme.colorScheme.primary
        StatTint.Secondary -> MaterialTheme.colorScheme.secondary
        StatTint.Tertiary -> MaterialTheme.colorScheme.tertiary
        StatTint.Error -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statLine.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = rating?.let { "%.0f".format(it) } ?: statLine.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            Text(
                text = statLine.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SortOption(
    option: String,
    selectedOption: String,
    /** Shown instead of [option] where the key isn't what the user should read ("20s"). */
    label: String = option,
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
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

