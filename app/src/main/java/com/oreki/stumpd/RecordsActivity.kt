package com.oreki.stumpd

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
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
    val matchDate: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RecordsScreen(onBack: () -> Unit) {
    val matchRepo = rememberMatchRepository()
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var allMatches by remember { mutableStateOf<List<MatchHistory>>(emptyList()) }

    // Selected category
    var selectedCategory by remember { mutableStateOf<RecordCategory>(RecordCategory.BattingRecords) }
    var records by remember { mutableStateOf<List<RecordEntry>>(emptyList()) }

    // Date filter
    var selectedFilter by remember { mutableStateOf("All Time") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    val categories = listOf(
        RecordCategory.BattingRecords,
        RecordCategory.BowlingRecords,
        RecordCategory.FieldingRecords,
        RecordCategory.PartnershipRecords,
        RecordCategory.MatchRecords
    )

    // Load data
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            allMatches = matchRepo.getAllMatches()
            isLoading = false
        }
    }

    // Calculate records when filter/category changes
    LaunchedEffect(allMatches, selectedFilter, startDate, endDate, selectedCategory) {
        if (allMatches.isNotEmpty()) {
            scope.launch {
                val filteredMatches = filterMatchesByDate(allMatches, selectedFilter, startDate, endDate)
                records = calculateRecords(filteredMatches, selectedCategory)
            }
        }
    }

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
                    TextButton(onClick = { showFilterDialog = true }) {
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
            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
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
                            "Play more matches to see records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    if (showFilterDialog) {
        DateFilterDialog(
            currentFilter = selectedFilter,
            onFilterSelected = { filter, start, end ->
                selectedFilter = filter
                startDate = start
                endDate = end
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
fun RecordCard(record: RecordEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    "${record.matchInfo} • ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(record.matchDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                record.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun calculateRecords(matches: List<MatchHistory>, category: RecordCategory): List<RecordEntry> {
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
                val allBatters = match.firstInningsBatting + match.secondInningsBatting +
                        match.team1Players + match.team2Players

                allBatters.distinctBy { "${it.name}_${it.runs}_${it.ballsFaced}_${match.id}" }.forEach { player ->
                    if (player.runs > 0) {
                        // Highest score
                        if (highestScore == null || player.runs > (highestScore!!.value.substringBefore("*").substringBefore("(").toIntOrNull() ?: 0)) {
                            val outIndicator = if (player.isOut) "" else "*"
                            highestScore = RecordEntry(
                                title = "Highest Individual Score",
                                value = "${player.runs}$outIndicator",
                                holder = player.name,
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate
                            )
                        }

                        // Highest strike rate (min 10 balls)
                        if (player.ballsFaced >= 10) {
                            val sr = (player.runs.toDouble() / player.ballsFaced) * 100
                            val currentHighestSR = highestStrikeRate?.value?.toDoubleOrNull() ?: 0.0
                            if (sr > currentHighestSR) {
                                highestStrikeRate = RecordEntry(
                                    title = "Highest Strike Rate (min 10 balls)",
                                    value = String.format("%.1f", sr),
                                    holder = "${player.name} (${player.runs} off ${player.ballsFaced})",
                                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                    matchDate = match.matchDate
                                )
                            }
                        }

                        // Most sixes
                        if (player.sixes > 0) {
                            val currentMostSixes = mostSixes?.value?.toIntOrNull() ?: 0
                            if (player.sixes > currentMostSixes) {
                                mostSixes = RecordEntry(
                                    title = "Most Sixes in an Innings",
                                    value = "${player.sixes}",
                                    holder = "${player.name} (${player.runs} runs)",
                                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                    matchDate = match.matchDate
                                )
                            }
                        }

                        // Most fours
                        if (player.fours > 0) {
                            val currentMostFours = mostFours?.value?.toIntOrNull() ?: 0
                            if (player.fours > currentMostFours) {
                                mostFours = RecordEntry(
                                    title = "Most Fours in an Innings",
                                    value = "${player.fours}",
                                    holder = "${player.name} (${player.runs} runs)",
                                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                    matchDate = match.matchDate
                                )
                            }
                        }

                        // Fastest fifty
                        if (player.runs >= 50) {
                            val currentFastestFifty = fastestFifty?.value?.toIntOrNull() ?: Int.MAX_VALUE
                            if (player.ballsFaced < currentFastestFifty) {
                                fastestFifty = RecordEntry(
                                    title = "Fastest Fifty",
                                    value = "${player.ballsFaced}",
                                    holder = "${player.name} (${player.runs} runs)",
                                    matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                    matchDate = match.matchDate
                                )
                            }
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
                val allBowlers = match.firstInningsBowling + match.secondInningsBowling +
                        match.team1Players + match.team2Players

                allBowlers.distinctBy { "${it.name}_${it.wickets}_${it.runsConceded}_${match.id}" }.forEach { player ->
                    // Best bowling (most wickets, then fewer runs)
                    if (player.wickets > 0) {
                        val currentBest = bestBowlingFigures
                        if (currentBest == null) {
                            bestBowlingFigures = createBowlingRecord(player, match, "Best Bowling Figures")
                        } else {
                            val (currentW, currentR) = parseBowlingFigures(currentBest.value)
                            if (player.wickets > currentW ||
                                (player.wickets == currentW && player.runsConceded < currentR)) {
                                bestBowlingFigures = createBowlingRecord(player, match, "Best Bowling Figures")
                            }
                        }
                    }

                    // Best economy (min 2 overs)
                    val ballsBowled = (player.oversBowled * 10).toInt().let {
                        (it / 10) * 6 + (it % 10)
                    }
                    if (ballsBowled >= 12) { // At least 2 overs
                        val economy = (player.runsConceded.toDouble() / ballsBowled) * 6
                        val currentBestEconomy = bestEconomy?.value?.toDoubleOrNull() ?: Double.MAX_VALUE
                        if (economy < currentBestEconomy) {
                            bestEconomy = RecordEntry(
                                title = "Best Economy Rate (min 2 overs)",
                                value = String.format("%.2f", economy),
                                holder = "${player.name} (${player.wickets}/${player.runsConceded})",
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate
                            )
                        }
                    }

                    // Most maidens
                    if (player.maidenOvers > 0) {
                        val currentMostMaidens = mostMaidens?.value?.toIntOrNull() ?: 0
                        if (player.maidenOvers > currentMostMaidens) {
                            mostMaidens = RecordEntry(
                                title = "Most Maiden Overs",
                                value = "${player.maidenOvers}",
                                holder = "${player.name} (${player.wickets}/${player.runsConceded})",
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate
                            )
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
                val allPlayers = match.team1Players + match.team2Players

                allPlayers.forEach { player ->
                    // Most catches
                    if (player.catches > 0) {
                        val currentMost = mostCatches?.value?.toIntOrNull() ?: 0
                        if (player.catches > currentMost) {
                            mostCatches = RecordEntry(
                                title = "Most Catches in a Match",
                                value = "${player.catches}",
                                holder = player.name,
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate
                            )
                        }
                    }

                    // Most run outs
                    if (player.runOuts > 0) {
                        val currentMost = mostRunOuts?.value?.toIntOrNull() ?: 0
                        if (player.runOuts > currentMost) {
                            mostRunOuts = RecordEntry(
                                title = "Most Run Outs in a Match",
                                value = "${player.runOuts}",
                                holder = player.name,
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate
                            )
                        }
                    }

                    // Most stumpings
                    if (player.stumpings > 0) {
                        val currentMost = mostStumpings?.value?.toIntOrNull() ?: 0
                        if (player.stumpings > currentMost) {
                            mostStumpings = RecordEntry(
                                title = "Most Stumpings in a Match",
                                value = "${player.stumpings}",
                                holder = player.name,
                                matchInfo = "${match.team1Name} vs ${match.team2Name}",
                                matchDate = match.matchDate
                            )
                        }
                    }
                }
            }

            listOfNotNull(mostCatches, mostRunOuts, mostStumpings)
                .let { records.addAll(it) }
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
                            matchDate = match.matchDate
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
                        matchDate = match.matchDate
                    )
                }
                if (team2Score > (highestTeamScore?.value?.substringBefore("/")?.toIntOrNull() ?: 0)) {
                    highestTeamScore = RecordEntry(
                        title = "Highest Team Score",
                        value = "${team2Score}/${match.secondInningsWickets}",
                        holder = match.team2Name,
                        matchInfo = "vs ${match.team1Name}",
                        matchDate = match.matchDate
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
                            matchDate = match.matchDate
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
                            matchDate = match.matchDate
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
                            matchDate = match.matchDate
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
                                matchDate = match.matchDate
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

private fun createBowlingRecord(player: PlayerMatchStats, match: MatchHistory, title: String): RecordEntry {
    return RecordEntry(
        title = title,
        value = "${player.wickets}/${player.runsConceded}",
        holder = player.name,
        matchInfo = "${match.team1Name} vs ${match.team2Name}",
        matchDate = match.matchDate
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
