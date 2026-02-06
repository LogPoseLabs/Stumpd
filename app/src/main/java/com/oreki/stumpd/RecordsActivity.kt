package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.ui.components.DateFilterDialog
import com.oreki.stumpd.ui.components.GroupFilterDropdown
import com.oreki.stumpd.ui.components.filterMatchesByGroup
import com.oreki.stumpd.ui.components.filterMatchesByPitchType
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.viewmodel.RecordsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecordsActivity : ComponentActivity() {
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
                    RecordsScreen(onBack = { finish() })
                }
            }
        }
    }
}

sealed class RecordCategory(val title: String, val icon: ImageVector) {
    object BattingRecords : RecordCategory("Batting", Icons.Default.SportsCricket)
    object BowlingRecords : RecordCategory("Bowling", Icons.Default.Sports)
    object FieldingRecords : RecordCategory("Fielding", Icons.Default.Person)
    object PartnershipRecords : RecordCategory("Partnerships", Icons.Default.Handshake)
    object MatchRecords : RecordCategory("Match", Icons.Default.Stadium)
}

data class RecordEntry(
    val title: String,
    val value: String,
    val holder: String,
    val matchInfo: String,
    val matchDate: Long,
    val matchId: String? = null  // Added to enable navigation to match details
)

// Fielding filter options
enum class FieldingFilter(val label: String) {
    ALL("All"),
    CATCHES("Catches"),
    STUMPINGS("Stumpings"),
    RUN_OUTS("Run Outs")
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RecordsScreen(onBack: () -> Unit, vm: RecordsViewModel = viewModel()) {
    val isLoading = vm.isLoading
    val groups = vm.groups
    val selectedCategory = vm.selectedCategory
    val records = vm.records
    val selectedGroupId = vm.selectedGroupId
    val selectedFilter = vm.selectedFilter
    val selectedPitchType = vm.selectedPitchType
    val fieldingFilter = vm.fieldingFilter

    val categories = listOf(
        RecordCategory.BattingRecords,
        RecordCategory.BowlingRecords,
        RecordCategory.FieldingRecords,
        RecordCategory.PartnershipRecords,
        RecordCategory.MatchRecords
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Records", fontWeight = FontWeight.Bold)
                        Text(
                            "All-time bests",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.showFilterDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Filter")
                        Spacer(Modifier.width(4.dp))
                        Text(selectedFilter)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter row: Group + Pitch Type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Group filter
                GroupFilterDropdown(
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    onGroupSelected = { id, name -> vm.onGroupSelected(id, name) },
                    modifier = Modifier.weight(1f)
                )

                // Pitch type filter button
                val pitchLabel = when (selectedPitchType) {
                    true -> "Short"
                    false -> "Long"
                    null -> "All Pitches"
                }
                FilledTonalButton(
                    onClick = { vm.showPitchPicker = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Terrain,
                        contentDescription = "Pitch",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(pitchLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { vm.onCategorySelected(category) },
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    category.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(category.title)
                            }
                        }
                    )
                }
            }

            // Fielding filter chips (only show when Fielding tab is selected)
            if (selectedCategory is RecordCategory.FieldingRecords) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FieldingFilter.values().forEach { filter ->
                        FilterChip(
                            selected = fieldingFilter == filter,
                            onClick = { vm.onFieldingFilterSelected(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No records found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (selectedCategory is RecordCategory.FieldingRecords)
                                "Fielding stats require detailed match data"
                            else
                                "Play more matches to see records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(records) { record ->
                        RecordCard(record = record)
                    }
                }
            }
        }
    }

    // Date Filter Dialog
    if (vm.showFilterDialog) {
        DateFilterDialog(
            currentFilter = selectedFilter,
            onFilterSelected = { filter, start, end ->
                vm.onFilterSelected(filter, start, end)
            },
            onDismiss = { vm.showFilterDialog = false }
        )
    }

    // Pitch Type Picker Dialog
    if (vm.showPitchPicker) {
        AlertDialog(
            onDismissRequest = { vm.showPitchPicker = false },
            title = { Text("Filter by Pitch Type") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.onPitchTypeSelected(null) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPitchType == null,
                            onClick = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("All Pitches")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.onPitchTypeSelected(true) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPitchType == true,
                            onClick = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Short Pitch")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.onPitchTypeSelected(false) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPitchType == false,
                            onClick = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Long Pitch")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.showPitchPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RecordCard(record: RecordEntry) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (record.matchId != null) {
                    Modifier.clickable {
                        val intent = Intent(context, MatchDetailActivity::class.java).apply {
                            putExtra("match_id", record.matchId)
                        }
                        context.startActivity(intent)
                    }
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    record.holder,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${record.matchInfo} • ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(record.matchDate))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (record.matchId != null) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "View match",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                record.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// Helper data class for calculated batting stats from deliveries
private data class CalculatedBattingStats(
    val name: String,
    val runs: Int,
    val ballsFaced: Int,
    val fours: Int,
    val sixes: Int,
    val isOut: Boolean
)

// Helper data class for calculated bowling stats from deliveries
private data class CalculatedBowlingStats(
    val name: String,
    val wickets: Int,
    val runsConceded: Int,
    val ballsBowled: Int
) {
    val oversBowled: Double get() = ballsBowled / 6 + (ballsBowled % 6) / 10.0
}

// Calculate batting stats from ball-by-ball deliveries for a specific innings
private fun calculateBattingFromDeliveries(deliveries: List<DeliveryUI>, inning: Int): List<CalculatedBattingStats> {
    val inningsDeliveries = deliveries.filter { it.inning == inning }
    val batterStats = mutableMapOf<String, MutableList<DeliveryUI>>()
    val dismissedBatters = mutableSetOf<String>()

    // Group deliveries by striker
    inningsDeliveries.forEach { delivery ->
        val striker = delivery.strikerName
        if (striker.isNotBlank()) {
            batterStats.getOrPut(striker) { mutableListOf() }.add(delivery)

            // Check if this delivery was a wicket for this batter
            val outcome = delivery.outcome.uppercase()
            if (outcome == "W" || outcome.contains("W ") || outcome.startsWith("W(")) {
                dismissedBatters.add(striker)
            }
        }
    }

    return batterStats.map { (name, balls) ->
        var runs = 0
        var fours = 0
        var sixes = 0
        var legalBalls = 0

        balls.forEach { delivery ->
            val outcome = delivery.outcome

            // Count legal deliveries (exclude wides)
            if (!outcome.contains("Wd", ignoreCase = true)) {
                legalBalls++
            }

            // Count runs scored by the batter (not extras)
            when {
                outcome == "4" -> { runs += 4; fours++ }
                outcome == "6" -> { runs += 6; sixes++ }
                outcome == "0" || outcome == "W" -> { /* no runs */ }
                outcome.contains("Wd", ignoreCase = true) -> { /* wide - not batter's runs */ }
                outcome.contains("Nb", ignoreCase = true) -> {
                    // No-ball: count runs if there are any
                    val nbRuns = Regex("Nb\\+?(\\d+)").find(outcome)?.groupValues?.get(1)?.toIntOrNull()
                        ?: outcome.filter { it.isDigit() }.toIntOrNull() ?: 0
                    runs += nbRuns
                    if (nbRuns == 4) fours++
                    if (nbRuns == 6) sixes++
                }
                else -> {
                    // Regular runs: 1, 2, 3, or runs from complex outcomes
                    val runValue = outcome.filter { it.isDigit() }.take(1).toIntOrNull() ?: 0
                    runs += runValue
                }
            }
        }

        CalculatedBattingStats(
            name = name,
            runs = runs,
            ballsFaced = legalBalls,
            fours = fours,
            sixes = sixes,
            isOut = name in dismissedBatters
        )
    }.filter { it.ballsFaced > 0 || it.runs > 0 }
}

// Calculate bowling stats from ball-by-ball deliveries for a specific innings
private fun calculateBowlingFromDeliveries(deliveries: List<DeliveryUI>, inning: Int): List<CalculatedBowlingStats> {
    val inningsDeliveries = deliveries.filter { it.inning == inning }
    val bowlerStats = mutableMapOf<String, MutableList<DeliveryUI>>()

    inningsDeliveries.forEach { delivery ->
        val bowler = delivery.bowlerName
        if (bowler.isNotBlank()) {
            bowlerStats.getOrPut(bowler) { mutableListOf() }.add(delivery)
        }
    }

    return bowlerStats.map { (name, balls) ->
        var wickets = 0
        var runsConceded = 0
        var legalBalls = 0

        balls.forEach { delivery ->
            val outcome = delivery.outcome

            // Count wickets (but not run outs - those aren't bowler's wickets)
            if (outcome == "W" || (outcome.contains("W") && !outcome.contains("RO", ignoreCase = true))) {
                wickets++
            }

            // Count legal deliveries
            if (!outcome.contains("Wd", ignoreCase = true) && !outcome.contains("Nb", ignoreCase = true)) {
                legalBalls++
            }

            // Count runs conceded
            runsConceded += delivery.runs
        }

        CalculatedBowlingStats(
            name = name,
            wickets = wickets,
            runsConceded = runsConceded,
            ballsBowled = legalBalls
        )
    }.filter { it.ballsBowled > 0 }
}

fun calculateRecords(
    matches: List<MatchHistory>,
    category: RecordCategory,
    fieldingFilter: FieldingFilter = FieldingFilter.ALL
): List<RecordEntry> {
    val records = mutableListOf<RecordEntry>()

    when (category) {
        is RecordCategory.BattingRecords -> {
            // Highest individual score
            var highestScore: RecordEntry? = null
            var highestStrikeRate: RecordEntry? = null
            var mostSixes: RecordEntry? = null
            var mostFours: RecordEntry? = null
            var fastestFifty: RecordEntry? = null

            matches.forEach { match ->
                // Try to use pre-computed stats first, fall back to calculating from deliveries
                val hasBattingStats = match.firstInningsBatting.isNotEmpty() ||
                        match.secondInningsBatting.isNotEmpty()

                if (hasBattingStats) {
                    // Use existing stats (role-based: BAT rows only)
                    val allBatters = match.firstInningsBatting + match.secondInningsBatting

                    allBatters.distinctBy { "${it.name}_${it.runs}_${it.ballsFaced}_${match.id}" }.forEach { player ->
                        processBattingRecord(
                            name = player.name,
                            runs = player.runs,
                            ballsFaced = player.ballsFaced,
                            fours = player.fours,
                            sixes = player.sixes,
                            isOut = player.isOut,
                            match = match,
                            highestScore = highestScore,
                            highestStrikeRate = highestStrikeRate,
                            mostSixes = mostSixes,
                            mostFours = mostFours,
                            fastestFifty = fastestFifty
                        ).let { (hs, hsr, ms, mf, ff) ->
                            highestScore = hs ?: highestScore
                            highestStrikeRate = hsr ?: highestStrikeRate
                            mostSixes = ms ?: mostSixes
                            mostFours = mf ?: mostFours
                            fastestFifty = ff ?: fastestFifty
                        }
                    }
                } else if (match.allDeliveries.isNotEmpty()) {
                    // Calculate from ball-by-ball data
                    val innings1Batting = calculateBattingFromDeliveries(match.allDeliveries, 1)
                    val innings2Batting = calculateBattingFromDeliveries(match.allDeliveries, 2)

                    (innings1Batting + innings2Batting).forEach { player ->
                        processBattingRecord(
                            name = player.name,
                            runs = player.runs,
                            ballsFaced = player.ballsFaced,
                            fours = player.fours,
                            sixes = player.sixes,
                            isOut = player.isOut,
                            match = match,
                            highestScore = highestScore,
                            highestStrikeRate = highestStrikeRate,
                            mostSixes = mostSixes,
                            mostFours = mostFours,
                            fastestFifty = fastestFifty
                        ).let { (hs, hsr, ms, mf, ff) ->
                            highestScore = hs ?: highestScore
                            highestStrikeRate = hsr ?: highestStrikeRate
                            mostSixes = ms ?: mostSixes
                            mostFours = mf ?: mostFours
                            fastestFifty = ff ?: fastestFifty
                        }
                    }
                }
            }

            listOfNotNull(highestScore, highestStrikeRate, fastestFifty, mostSixes, mostFours)
                .let { records.addAll(it) }
        }

        is RecordCategory.BowlingRecords -> {
            var bestBowlingFigures: RecordEntry? = null
            var bestEconomy: RecordEntry? = null
            var mostMaidens: RecordEntry? = null

            matches.forEach { match ->
                val hasBowlingStats = match.firstInningsBowling.isNotEmpty() ||
                        match.secondInningsBowling.isNotEmpty()

                if (hasBowlingStats) {
                    // Use existing stats (role-based: BOWL rows only)
                    val allBowlers = match.firstInningsBowling + match.secondInningsBowling

                    allBowlers.distinctBy { "${it.name}_${it.wickets}_${it.runsConceded}_${match.id}" }.forEach { player ->
                        processBowlingRecord(
                            name = player.name,
                            wickets = player.wickets,
                            runsConceded = player.runsConceded,
                            oversBowled = player.oversBowled,
                            maidenOvers = player.maidenOvers,
                            match = match,
                            bestBowlingFigures = bestBowlingFigures,
                            bestEconomy = bestEconomy,
                            mostMaidens = mostMaidens
                        ).let { (bbf, be, mm) ->
                            bestBowlingFigures = bbf ?: bestBowlingFigures
                            bestEconomy = be ?: bestEconomy
                            mostMaidens = mm ?: mostMaidens
                        }
                    }
                } else if (match.allDeliveries.isNotEmpty()) {
                    // Calculate from ball-by-ball data
                    val innings1Bowling = calculateBowlingFromDeliveries(match.allDeliveries, 1)
                    val innings2Bowling = calculateBowlingFromDeliveries(match.allDeliveries, 2)

                    (innings1Bowling + innings2Bowling).forEach { player ->
                        processBowlingRecord(
                            name = player.name,
                            wickets = player.wickets,
                            runsConceded = player.runsConceded,
                            oversBowled = player.oversBowled,
                            maidenOvers = 0, // Can't easily calculate maidens from delivery data
                            match = match,
                            bestBowlingFigures = bestBowlingFigures,
                            bestEconomy = bestEconomy,
                            mostMaidens = mostMaidens
                        ).let { (bbf, be, mm) ->
                            bestBowlingFigures = bbf ?: bestBowlingFigures
                            bestEconomy = be ?: bestEconomy
                            mostMaidens = mm ?: mostMaidens
                        }
                    }
                }
            }

            listOfNotNull(bestBowlingFigures, bestEconomy, mostMaidens)
                .let { records.addAll(it) }
        }

        is RecordCategory.FieldingRecords -> {
            var mostCatches: RecordEntry? = null
            var mostRunOuts: RecordEntry? = null
            var mostStumpings: RecordEntry? = null

            matches.forEach { match ->
                // Fielding stats live on BOWL rows; include BAT rows too for completeness
                val allPlayers = (match.firstInningsBatting + match.firstInningsBowling +
                        match.secondInningsBatting + match.secondInningsBowling)
                    .distinctBy { "${it.name}_${match.id}" }

                allPlayers.forEach { player ->
                    // Most catches (show when filter is ALL or CATCHES)
                    if ((fieldingFilter == FieldingFilter.ALL || fieldingFilter == FieldingFilter.CATCHES) && player.catches > 0) {
                        val currentMost = mostCatches?.value?.toIntOrNull() ?: 0
                        if (player.catches > currentMost) {
                            mostCatches = RecordEntry(
                                title = "Most Catches in a Match",
                                value = "${player.catches}",
                                holder = player.name,
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate,
                                matchId = match.id
                            )
                        }
                    }

                    // Most run outs (show when filter is ALL or RUN_OUTS)
                    if ((fieldingFilter == FieldingFilter.ALL || fieldingFilter == FieldingFilter.RUN_OUTS) && player.runOuts > 0) {
                        val currentMost = mostRunOuts?.value?.toIntOrNull() ?: 0
                        if (player.runOuts > currentMost) {
                            mostRunOuts = RecordEntry(
                                title = "Most Run Outs in a Match",
                                value = "${player.runOuts}",
                                holder = player.name,
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate,
                                matchId = match.id
                            )
                        }
                    }

                    // Most stumpings (show when filter is ALL or STUMPINGS)
                    if ((fieldingFilter == FieldingFilter.ALL || fieldingFilter == FieldingFilter.STUMPINGS) && player.stumpings > 0) {
                        val currentMost = mostStumpings?.value?.toIntOrNull() ?: 0
                        if (player.stumpings > currentMost) {
                            mostStumpings = RecordEntry(
                                title = "Most Stumpings in a Match",
                                value = "${player.stumpings}",
                                holder = player.name,
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate,
                                matchId = match.id
                            )
                        }
                    }
                }
            }

            // Add records based on filter
            when (fieldingFilter) {
                FieldingFilter.ALL -> listOfNotNull(mostCatches, mostRunOuts, mostStumpings)
                FieldingFilter.CATCHES -> listOfNotNull(mostCatches)
                FieldingFilter.STUMPINGS -> listOfNotNull(mostStumpings)
                FieldingFilter.RUN_OUTS -> listOfNotNull(mostRunOuts)
            }.let { records.addAll(it) }
        }

        is RecordCategory.PartnershipRecords -> {
            var highestPartnership: RecordEntry? = null

            matches.forEach { match ->
                val allPartnerships = match.firstInningsPartnerships + match.secondInningsPartnerships

                allPartnerships.forEach { partnership ->
                    val currentHighest = highestPartnership?.value?.toIntOrNull() ?: 0
                    if (partnership.runs > currentHighest) {
                        highestPartnership = RecordEntry(
                            title = "Highest Partnership",
                            value = "${partnership.runs}",
                            holder = "${partnership.batsman1Name} & ${partnership.batsman2Name}",
                            matchInfo = "${match.team1Name} vs ${match.team2Name}",
                            matchDate = match.matchDate,
                            matchId = match.id
                        )
                    }
                }
            }

            listOfNotNull(highestPartnership).let { records.addAll(it) }
        }

        is RecordCategory.MatchRecords -> {
            var highestTeamScore: RecordEntry? = null
            var lowestTeamScore: RecordEntry? = null
            var biggestWinMargin: RecordEntry? = null
            var closestMatch: RecordEntry? = null

            matches.forEach { match ->
                // Highest team score
                val team1Score = match.firstInningsRuns
                val team2Score = match.secondInningsRuns

                val currentHighest = highestTeamScore?.value?.substringBefore("/")?.toIntOrNull() ?: 0
                if (team1Score > currentHighest) {
                    highestTeamScore = RecordEntry(
                        title = "Highest Team Score",
                        value = "${team1Score}/${match.firstInningsWickets}",
                        holder = match.team1Name,
                        matchInfo = "vs ${match.team2Name}",
                        matchDate = match.matchDate,
                        matchId = match.id
                    )
                }
                if (team2Score > (highestTeamScore?.value?.substringBefore("/")?.toIntOrNull() ?: 0)) {
                    highestTeamScore = RecordEntry(
                        title = "Highest Team Score",
                        value = "${team2Score}/${match.secondInningsWickets}",
                        holder = match.team2Name,
                        matchInfo = "vs ${match.team1Name}",
                        matchDate = match.matchDate,
                        matchId = match.id
                    )
                }

                // Lowest team score (must be all out)
                if (match.firstInningsWickets >= (match.matchSettings?.maxPlayersPerTeam ?: 11) - 1) {
                    val currentLowest = lowestTeamScore?.value?.substringBefore("/")?.toIntOrNull() ?: Int.MAX_VALUE
                    if (team1Score < currentLowest) {
                        lowestTeamScore = RecordEntry(
                            title = "Lowest Team Score (All Out)",
                            value = "${team1Score}/${match.firstInningsWickets}",
                            holder = match.team1Name,
                            matchInfo = "vs ${match.team2Name}",
                            matchDate = match.matchDate,
                            matchId = match.id
                        )
                    }
                }
                if (match.secondInningsWickets >= (match.matchSettings?.maxPlayersPerTeam ?: 11) - 1) {
                    val currentLowest = lowestTeamScore?.value?.substringBefore("/")?.toIntOrNull() ?: Int.MAX_VALUE
                    if (team2Score < currentLowest) {
                        lowestTeamScore = RecordEntry(
                            title = "Lowest Team Score (All Out)",
                            value = "${team2Score}/${match.secondInningsWickets}",
                            holder = match.team2Name,
                            matchInfo = "vs ${match.team1Name}",
                            matchDate = match.matchDate,
                            matchId = match.id
                        )
                    }
                }

                // Parse win margin
                val margin = match.winningMargin
                val runMarginMatch = Regex("(\\d+)\\s*runs?").find(margin)
                val wicketMarginMatch = Regex("(\\d+)\\s*wickets?").find(margin)

                if (runMarginMatch != null) {
                    val runs = runMarginMatch.groupValues[1].toIntOrNull() ?: 0
                    val currentBiggestMargin = extractRunMargin(biggestWinMargin?.value)
                    if (runs > currentBiggestMargin) {
                        biggestWinMargin = RecordEntry(
                            title = "Biggest Win (by Runs)",
                            value = "$runs runs",
                            holder = match.winnerTeam,
                            matchInfo = "${match.team1Name} vs ${match.team2Name}",
                            matchDate = match.matchDate,
                            matchId = match.id
                        )
                    }

                    // Closest match (1-5 runs)
                    if (runs in 1..5) {
                        val currentClosest = extractRunMargin(closestMatch?.value)
                        if (currentClosest == 0 || runs < currentClosest) {
                            closestMatch = RecordEntry(
                                title = "Closest Match",
                                value = "$runs run${if (runs > 1) "s" else ""}",
                                holder = "${match.winnerTeam} won",
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate,
                                matchId = match.id
                            )
                        }
                    }
                }
            }

            listOfNotNull(highestTeamScore, lowestTeamScore, biggestWinMargin, closestMatch)
                .let { records.addAll(it) }
        }
    }

    return records
}

// Helper function to process batting records
private fun processBattingRecord(
    name: String,
    runs: Int,
    ballsFaced: Int,
    fours: Int,
    sixes: Int,
    isOut: Boolean,
    match: MatchHistory,
    highestScore: RecordEntry?,
    highestStrikeRate: RecordEntry?,
    mostSixes: RecordEntry?,
    mostFours: RecordEntry?,
    fastestFifty: RecordEntry?
): Tuple5<RecordEntry?, RecordEntry?, RecordEntry?, RecordEntry?, RecordEntry?> {
    var newHighestScore: RecordEntry? = null
    var newHighestStrikeRate: RecordEntry? = null
    var newMostSixes: RecordEntry? = null
    var newMostFours: RecordEntry? = null
    var newFastestFifty: RecordEntry? = null

    if (runs > 0) {
        // Highest score
        if (highestScore == null || runs > (highestScore.value.substringBefore("*").substringBefore("(").toIntOrNull() ?: 0)) {
            val outIndicator = if (isOut) "" else "*"
            newHighestScore = RecordEntry(
                title = "Highest Individual Score",
                value = "$runs$outIndicator",
                holder = name,
                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                matchDate = match.matchDate,
                matchId = match.id
            )
        }

        // Highest strike rate (min 10 balls)
        if (ballsFaced >= 10) {
            val sr = (runs.toDouble() / ballsFaced) * 100
            val currentHighestSR = highestStrikeRate?.value?.toDoubleOrNull() ?: 0.0
            if (sr > currentHighestSR) {
                newHighestStrikeRate = RecordEntry(
                    title = "Highest Strike Rate (min 10 balls)",
                    value = String.format("%.1f", sr),
                    holder = "$name ($runs off $ballsFaced)",
                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                    matchDate = match.matchDate,
                    matchId = match.id
                )
            }
        }

        // Most sixes
        if (sixes > 0) {
            val currentMostSixes = mostSixes?.value?.toIntOrNull() ?: 0
            if (sixes > currentMostSixes) {
                newMostSixes = RecordEntry(
                    title = "Most Sixes in an Innings",
                    value = "$sixes",
                    holder = "$name ($runs runs)",
                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                    matchDate = match.matchDate,
                    matchId = match.id
                )
            }
        }

        // Most fours
        if (fours > 0) {
            val currentMostFours = mostFours?.value?.toIntOrNull() ?: 0
            if (fours > currentMostFours) {
                newMostFours = RecordEntry(
                    title = "Most Fours in an Innings",
                    value = "$fours",
                    holder = "$name ($runs runs)",
                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                    matchDate = match.matchDate,
                    matchId = match.id
                )
            }
        }

        // Fastest fifty
        if (runs >= 50) {
            val currentFastestFifty = fastestFifty?.value?.toIntOrNull() ?: Int.MAX_VALUE
            if (ballsFaced < currentFastestFifty) {
                newFastestFifty = RecordEntry(
                    title = "Fastest Fifty",
                    value = "$ballsFaced",
                    holder = "$name ($runs runs)",
                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                    matchDate = match.matchDate,
                    matchId = match.id
                )
            }
        }
    }

    return Tuple5(newHighestScore, newHighestStrikeRate, newMostSixes, newMostFours, newFastestFifty)
}

// Helper function to process bowling records
private fun processBowlingRecord(
    name: String,
    wickets: Int,
    runsConceded: Int,
    oversBowled: Double,
    maidenOvers: Int,
    match: MatchHistory,
    bestBowlingFigures: RecordEntry?,
    bestEconomy: RecordEntry?,
    mostMaidens: RecordEntry?
): Triple<RecordEntry?, RecordEntry?, RecordEntry?> {
    var newBestBowling: RecordEntry? = null
    var newBestEconomy: RecordEntry? = null
    var newMostMaidens: RecordEntry? = null

    // Best bowling (most wickets, then fewer runs)
    if (wickets > 0) {
        val currentBest = bestBowlingFigures
        if (currentBest == null) {
            newBestBowling = RecordEntry(
                title = "Best Bowling Figures",
                value = "$wickets/$runsConceded",
                holder = name,
                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                matchDate = match.matchDate,
                matchId = match.id
            )
        } else {
            val (currentW, currentR) = parseBowlingFigures(currentBest.value)
            if (wickets > currentW || (wickets == currentW && runsConceded < currentR)) {
                newBestBowling = RecordEntry(
                    title = "Best Bowling Figures",
                    value = "$wickets/$runsConceded",
                    holder = name,
                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                    matchDate = match.matchDate,
                    matchId = match.id
                )
            }
        }
    }

    // Best economy (min 2 overs)
    val ballsBowled = (oversBowled * 10).toInt().let {
        (it / 10) * 6 + (it % 10)
    }
    if (ballsBowled >= 12) { // At least 2 overs
        val economy = (runsConceded.toDouble() / ballsBowled) * 6
        val currentBestEconomy = bestEconomy?.value?.toDoubleOrNull() ?: Double.MAX_VALUE
        if (economy < currentBestEconomy) {
            newBestEconomy = RecordEntry(
                title = "Best Economy Rate (min 2 overs)",
                value = String.format("%.2f", economy),
                holder = "$name ($wickets/$runsConceded)",
                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                matchDate = match.matchDate,
                matchId = match.id
            )
        }
    }

    // Most maidens
    if (maidenOvers > 0) {
        val currentMostMaidens = mostMaidens?.value?.toIntOrNull() ?: 0
        if (maidenOvers > currentMostMaidens) {
            newMostMaidens = RecordEntry(
                title = "Most Maiden Overs",
                value = "$maidenOvers",
                holder = "$name ($wickets/$runsConceded)",
                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                matchDate = match.matchDate,
                matchId = match.id
            )
        }
    }

    return Triple(newBestBowling, newBestEconomy, newMostMaidens)
}

// Simple tuple class for returning multiple values
private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private fun createBowlingRecord(player: PlayerMatchStats, match: MatchHistory, title: String): RecordEntry {
    return RecordEntry(
        title = title,
        value = "${player.wickets}/${player.runsConceded}",
        holder = player.name,
        matchInfo = "${match.team1Name} vs ${match.team2Name}",
        matchDate = match.matchDate,
        matchId = match.id
    )
}

private fun parseBowlingFigures(value: String): Pair<Int, Int> {
    val parts = value.split("/")
    return if (parts.size == 2) {
        (parts[0].toIntOrNull() ?: 0) to (parts[1].toIntOrNull() ?: 999)
    } else {
        0 to 999
    }
}

private fun extractRunMargin(value: String?): Int {
    if (value == null) return 0
    return Regex("(\\d+)").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
