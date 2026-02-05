package com.oreki.stumpd

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class CaptainStatsActivity : ComponentActivity() {
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
                    CaptainStatsScreen(onBack = { finish() })
                }
            }
        }
    }
}

data class CaptainStats(
    val captainName: String,
    val matchesAsCaptain: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val noResults: Int = 0
) {
    val winPercentage: Double get() = if (wins + losses > 0) (wins.toDouble() / (wins + losses)) * 100 else 0.0
    val winLossRatio: String get() = if (losses > 0) String.format("%.2f", wins.toDouble() / losses) else if (wins > 0) "∞" else "0"
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CaptainStatsScreen(onBack: () -> Unit) {
    val matchRepo = rememberMatchRepository()
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var allMatches by remember { mutableStateOf<List<MatchHistory>>(emptyList()) }
    var captainStats by remember { mutableStateOf<List<CaptainStats>>(emptyList()) }

    // Date filter
    var selectedFilter by remember { mutableStateOf("All Time") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    // Sort options
    var sortBy by remember { mutableStateOf("Matches") }
    var showSortMenu by remember { mutableStateOf(false) }

    // Load data
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            allMatches = matchRepo.getAllMatches()
            isLoading = false
        }
    }

    // Calculate stats when filter changes
    LaunchedEffect(allMatches, selectedFilter, startDate, endDate, sortBy) {
        if (allMatches.isNotEmpty()) {
            scope.launch {
                val filteredMatches = filterMatchesByDate(allMatches, selectedFilter, startDate, endDate)
                captainStats = calculateCaptainStats(filteredMatches, sortBy)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Captain Stats", fontWeight = FontWeight.Bold)
                        Text(
                            "Win/Loss as Captain",
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
                    // Sort button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf("Matches", "Wins", "Win %", "Name").forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (sortBy == option) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text(option)
                                        }
                                    },
                                    onClick = {
                                        sortBy = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (captainStats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No captain data available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Make sure to set captains when starting matches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(captainStats.withIndex().toList()) { (index, stats) ->
                    CaptainStatsCard(
                        rank = index + 1,
                        stats = stats
                    )
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
fun CaptainStatsCard(
    rank: Int,
    stats: CaptainStats
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (rank) {
                1 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                2 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                3 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> MaterialTheme.colorScheme.primary
                            2 -> MaterialTheme.colorScheme.secondary
                            3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#$rank",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (rank) {
                        1 -> MaterialTheme.colorScheme.onPrimary
                        2 -> MaterialTheme.colorScheme.onSecondary
                        3 -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            }

            // Captain name and win %
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stats.captainName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${stats.matchesAsCaptain} matches • ${String.format("%.1f", stats.winPercentage)}% win rate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Win/Loss display
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.wins}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "W",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "-",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.losses}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (stats.ties > 0) {
                    Text(
                        "-",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${stats.ties}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "T",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun calculateCaptainStats(matches: List<MatchHistory>, sortBy: String): List<CaptainStats> {
    val captainMap = mutableMapOf<String, CaptainStats>()

    matches.forEach { match ->
        val winner = match.winnerTeam
        val isTie = winner.contains("Tie", ignoreCase = true) || winner.contains("Draw", ignoreCase = true)

        // Process team1 captain
        match.team1CaptainName?.takeIf { it.isNotBlank() }?.let { captain ->
            val existing = captainMap[captain] ?: CaptainStats(captainName = captain)
            val isWin = !isTie && winner.equals(match.team1Name, ignoreCase = true)
            val isLoss = !isTie && winner.equals(match.team2Name, ignoreCase = true)

            captainMap[captain] = existing.copy(
                matchesAsCaptain = existing.matchesAsCaptain + 1,
                wins = existing.wins + if (isWin) 1 else 0,
                losses = existing.losses + if (isLoss) 1 else 0,
                ties = existing.ties + if (isTie) 1 else 0
            )
        }

        // Process team2 captain
        match.team2CaptainName?.takeIf { it.isNotBlank() }?.let { captain ->
            val existing = captainMap[captain] ?: CaptainStats(captainName = captain)
            val isWin = !isTie && winner.equals(match.team2Name, ignoreCase = true)
            val isLoss = !isTie && winner.equals(match.team1Name, ignoreCase = true)

            captainMap[captain] = existing.copy(
                matchesAsCaptain = existing.matchesAsCaptain + 1,
                wins = existing.wins + if (isWin) 1 else 0,
                losses = existing.losses + if (isLoss) 1 else 0,
                ties = existing.ties + if (isTie) 1 else 0
            )
        }
    }

    val stats = captainMap.values.toList()

    return when (sortBy) {
        "Wins" -> stats.sortedByDescending { it.wins }
        "Win %" -> stats.sortedByDescending { it.winPercentage }
        "Name" -> stats.sortedBy { it.captainName }
        else -> stats.sortedByDescending { it.matchesAsCaptain } // "Matches"
    }
}
