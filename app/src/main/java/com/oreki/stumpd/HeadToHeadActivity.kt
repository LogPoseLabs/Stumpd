package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.viewmodel.HeadToHeadViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HeadToHeadActivity : ComponentActivity() {
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
                    HeadToHeadScreen(onBack = { finish() })
                }
            }
        }
    }
}

data class HeadToHeadStats(
    val batsmanName: String,
    val bowlerName: String,
    val ballsFaced: Int = 0,
    val runsScored: Int = 0,
    val dotBalls: Int = 0,
    val singles: Int = 0,
    val twos: Int = 0,
    val threes: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val dismissals: Int = 0,
    val matchCount: Int = 0
) {
    val strikeRate: Double get() = if (ballsFaced > 0) (runsScored.toDouble() / ballsFaced) * 100 else 0.0
    val dotBallPercentage: Double get() = if (ballsFaced > 0) (dotBalls.toDouble() / ballsFaced) * 100 else 0.0
    val boundaryPercentage: Double get() = if (ballsFaced > 0) ((fours + sixes).toDouble() / ballsFaced) * 100 else 0.0
    val averagePerDismissal: Double get() = if (dismissals > 0) runsScored.toDouble() / dismissals else runsScored.toDouble()
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HeadToHeadScreen(onBack: () -> Unit, vm: HeadToHeadViewModel = viewModel()) {
    val isLoading = vm.isLoading
    val groups = vm.groups
    val selectedGroupId = vm.selectedGroupId
    val selectedFilter = vm.selectedFilter
    val selectedPitchType = vm.selectedPitchType
    val selectedBatsman = vm.selectedBatsman
    val selectedBowler = vm.selectedBowler
    val headToHeadStats = vm.headToHeadStats
    val matchDetails = vm.matchDetails
    val filteredPlayers = vm.filteredPlayers

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Head to Head", fontWeight = FontWeight.Bold)
                        Text(
                            "Batsman vs Bowler",
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
                GroupFilterDropdown(
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    onGroupSelected = { id, name -> vm.onGroupSelected(id, name) },
                    modifier = Modifier.weight(1f)
                )

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

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Player Selection
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Select Players",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Batsman dropdown
                                ExposedDropdownMenuBox(
                                    expanded = vm.showBatsmanDropdown,
                                    onExpandedChange = { vm.showBatsmanDropdown = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedBatsman ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Batsman") },
                                        leadingIcon = {
                                            Icon(Icons.Default.SportsCricket, contentDescription = null)
                                        },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vm.showBatsmanDropdown) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = vm.showBatsmanDropdown,
                                        onDismissRequest = { vm.showBatsmanDropdown = false }
                                    ) {
                                        filteredPlayers.forEach { player ->
                                            DropdownMenuItem(
                                                text = { Text(player) },
                                                onClick = { vm.onBatsmanSelected(player) }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // VS indicator
                                Text(
                                    "VS",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Bowler dropdown
                                ExposedDropdownMenuBox(
                                    expanded = vm.showBowlerDropdown,
                                    onExpandedChange = { vm.showBowlerDropdown = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedBowler ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Bowler") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Sports, contentDescription = null)
                                        },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vm.showBowlerDropdown) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = vm.showBowlerDropdown,
                                        onDismissRequest = { vm.showBowlerDropdown = false }
                                    ) {
                                        filteredPlayers.forEach { player ->
                                            DropdownMenuItem(
                                                text = { Text(player) },
                                                onClick = { vm.onBowlerSelected(player) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Stats Display
                    if (headToHeadStats != null && selectedBatsman != null && selectedBowler != null) {
                        item {
                            HeadToHeadStatsCard(stats = headToHeadStats!!)
                        }

                        // Match-by-match breakdown
                        if (matchDetails.isNotEmpty()) {
                            item {
                                Text(
                                    "Match-by-Match Breakdown",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            items(matchDetails) { detail ->
                                MatchHeadToHeadCard(detail = detail)
                            }
                        }
                    } else if (selectedBatsman != null && selectedBowler != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    "No head-to-head data found between these players",
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
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
fun HeadToHeadStatsCard(stats: HeadToHeadStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.runsScored}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Runs", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.ballsFaced}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Balls", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.dismissals}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text("Outs", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip("SR", String.format("%.1f", stats.strikeRate))
                StatChip("Avg", String.format("%.1f", stats.averagePerDismissal))
                StatChip("Dots", "${stats.dotBalls}")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip("4s", "${stats.fours}")
                StatChip("6s", "${stats.sixes}")
                StatChip("Matches", "${stats.matchCount}")
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class MatchHeadToHeadDetail(
    val matchDate: Long,
    val team1Name: String,
    val team2Name: String,
    val runsScored: Int,
    val ballsFaced: Int,
    val fours: Int,
    val sixes: Int,
    val wasOut: Boolean
)

@Composable
fun MatchHeadToHeadCard(detail: MatchHeadToHeadDetail) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${detail.team1Name} vs ${detail.team2Name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(detail.matchDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${detail.runsScored}(${detail.ballsFaced})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${detail.fours}x4 ${detail.sixes}x6",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (detail.wasOut) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Out",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Not Out",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun filterMatchesByDate(
    matches: List<MatchHistory>,
    filter: String,
    customStart: LocalDate?,
    customEnd: LocalDate?
): List<MatchHistory> {
    val now = LocalDate.now()

    return when (filter) {
        "Last 7 Days" -> {
            val cutoff = now.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            matches.filter { it.matchDate >= cutoff }
        }
        "Last 30 Days" -> {
            val cutoff = now.minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            matches.filter { it.matchDate >= cutoff }
        }
        "Last 3 Months" -> {
            val cutoff = now.minusMonths(3).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            matches.filter { it.matchDate >= cutoff }
        }
        "This Year" -> {
            val startOfYear = LocalDate.of(now.year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            matches.filter { it.matchDate >= startOfYear }
        }
        "Custom" -> {
            if (customStart != null && customEnd != null) {
                val startMillis = customStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMillis = customEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                matches.filter { it.matchDate >= startMillis && it.matchDate < endMillis }
            } else {
                matches
            }
        }
        else -> matches // "All Time"
    }
}

fun calculateHeadToHead(
    matches: List<MatchHistory>,
    batsmanName: String,
    bowlerName: String
): Pair<HeadToHeadStats?, List<MatchHeadToHeadDetail>> {
    var totalRuns = 0
    var totalBalls = 0
    var dots = 0
    var singles = 0
    var twos = 0
    var threes = 0
    var fours = 0
    var sixes = 0
    var dismissals = 0
    val matchesWithData = mutableSetOf<String>()
    val matchDetails = mutableListOf<MatchHeadToHeadDetail>()

    matches.forEach { match ->
        var matchRuns = 0
        var matchBalls = 0
        var matchFours = 0
        var matchSixes = 0
        var wasOutInMatch = false
        var hasDataInMatch = false

        match.allDeliveries.forEach { delivery ->
            if (delivery.strikerName.equals(batsmanName, ignoreCase = true) &&
                delivery.bowlerName.equals(bowlerName, ignoreCase = true)) {

                hasDataInMatch = true
                matchesWithData.add(match.id)

                // Count based on outcome
                val outcome = delivery.outcome.uppercase()
                when {
                    outcome == "0" || outcome == "DOT" -> {
                        dots++
                        matchBalls++
                    }
                    outcome == "1" -> {
                        singles++
                        totalRuns += 1
                        matchRuns += 1
                        matchBalls++
                    }
                    outcome == "2" -> {
                        twos++
                        totalRuns += 2
                        matchRuns += 2
                        matchBalls++
                    }
                    outcome == "3" -> {
                        threes++
                        totalRuns += 3
                        matchRuns += 3
                        matchBalls++
                    }
                    outcome == "4" -> {
                        fours++
                        matchFours++
                        totalRuns += 4
                        matchRuns += 4
                        matchBalls++
                    }
                    outcome == "6" -> {
                        sixes++
                        matchSixes++
                        totalRuns += 6
                        matchRuns += 6
                        matchBalls++
                    }
                    outcome.startsWith("W") || outcome == "OUT" -> {
                        dismissals++
                        wasOutInMatch = true
                        matchBalls++
                    }
                    outcome.startsWith("WD") || outcome.startsWith("WIDE") -> {
                        // Wides don't count as balls faced
                        val wideRuns = delivery.runs
                        totalRuns += wideRuns
                        matchRuns += wideRuns
                    }
                    outcome.startsWith("NB") || outcome.startsWith("NOBALL") -> {
                        // No ball - runs scored but doesn't count as ball faced
                        val nbRuns = delivery.runs
                        totalRuns += nbRuns
                        matchRuns += nbRuns
                    }
                    else -> {
                        // Any other runs
                        totalRuns += delivery.runs
                        matchRuns += delivery.runs
                        matchBalls++
                    }
                }
            }
        }

        totalBalls += matchBalls

        if (hasDataInMatch) {
            matchDetails.add(
                MatchHeadToHeadDetail(
                    matchDate = match.matchDate,
                    team1Name = match.team1Name,
                    team2Name = match.team2Name,
                    runsScored = matchRuns,
                    ballsFaced = matchBalls,
                    fours = matchFours,
                    sixes = matchSixes,
                    wasOut = wasOutInMatch
                )
            )
        }
    }

    return if (matchesWithData.isEmpty()) {
        null to emptyList()
    } else {
        HeadToHeadStats(
            batsmanName = batsmanName,
            bowlerName = bowlerName,
            ballsFaced = totalBalls,
            runsScored = totalRuns,
            dotBalls = dots,
            singles = singles,
            twos = twos,
            threes = threes,
            fours = fours,
            sixes = sixes,
            dismissals = dismissals,
            matchCount = matchesWithData.size
        ) to matchDetails.sortedByDescending { it.matchDate }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateFilterDialog(
    currentFilter: String,
    onFilterSelected: (String, LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val filters = listOf("All Time", "Last 7 Days", "Last 30 Days", "Last 3 Months", "This Year", "Custom")
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Date") },
        text = {
            Column {
                filters.forEach { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (filter == "Custom") {
                                    showDatePicker = true
                                } else {
                                    onFilterSelected(filter, null, null)
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFilter == filter,
                            onClick = null // Handled by row click
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(filter)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val start = Instant.ofEpochMilli(startMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            val end = Instant.ofEpochMilli(endMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onFilterSelected("Custom", start, end)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.height(500.dp)
            )
        }
    }
}
