package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.StumpdTheme
import kotlinx.coroutines.launch

class PlayerDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerDetailScreen(playerName: String) {
    val context = LocalContext.current
    val playerStorage = remember { EnhancedPlayerStorageManager(context) }
    var player by remember { mutableStateOf<PlayerDetailedStats?>(null) }

    // Pager state for swipeable tabs
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(playerName) {
        playerStorage.syncPlayerStatsFromMatches()
        player = playerStorage.getPlayerDetailed(playerName)
    }

    if (player == null) {
        // Loading state...
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Enhanced Header (your existing code)
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
                        text = player!!.name,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = "${player!!.totalMatches} matches",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Player avatar or stats summary
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = player!!.totalRuns.toString(),
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
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = player!!.totalWickets.toString(),
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Last played: ${formatDate(player!!.lastPlayed)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    Spacer(modifier = Modifier.height(16.dp))

            // Swipeable TabRow
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
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
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

            // Swipeable Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> PlayerOverviewTab(player!!)
                    1 -> PlayerMatchesTab(player!!)
                    2 -> PlayerStatisticsTab(player!!)
                }
            }
        }
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
