package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.sectionContainer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FullScorecardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val matchId = intent.getStringExtra("match_id") ?: ""

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FullScorecardScreen(matchId)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullScorecardScreen(matchId: String) {
    val context = LocalContext.current
    val repo = rememberMatchRepository()
    val coroutineScope = rememberCoroutineScope()

    var matchData by remember { mutableStateOf<MatchHistory?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(matchId) { 
        isLoading = true
        matchData = repo.getMatchWithStats(matchId)
        isLoading = false
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Loading scorecard...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    if (matchData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                    Text(
                        "Match Not Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "This match could not be loaded",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { (context as ComponentActivity).finish() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Go Back")
                    }
                }
            }
        }
        return
    }

    // Extract match settings and team information
    val match = matchData!! // safe because of early return above
    val matchSettings = match.matchSettings ?: MatchSettings()
    val totalOvers = matchSettings.totalOvers
    
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    val tabs = listOf("Scorecard", "Overs", "Summary", "Squads")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Match Scorecard",
                subtitle = "${match.team1Name} vs ${match.team2Name} • ${dateFormat.format(Date(match.matchDate))}",
                onBack = { (context as ComponentActivity).finish() },
                actions = {
                IconButton(
                    onClick = {
                        val intent = Intent(context, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        context.startActivity(intent)
                        }
                ) {
                    Icon(
                        Icons.Default.Home,
                            contentDescription = "Home"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Match result summary card - Modernized
            val isTie = match.winnerTeam.equals("TIE", true)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = if (isTie)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Result icon and text
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (isTie) Icons.Default.Info else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isTie)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isTie) "Match Tied" else "${match.winnerTeam} Won",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isTie)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = match.winningMargin,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isTie)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    // Match format info
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "$totalOvers Overs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        
                        match.groupName?.takeIf { it.isNotBlank() }?.let { gName ->
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    gName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        
        // Tab Row - Modernized
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            }
        ) {
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
                            title, 
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        ) 
                    }
                )
            }
        }
            
            // Swipeable Tab Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ScorecardTab(match = match)
                    1 -> OversTabContent(match = match)
                    2 -> SummaryTab(match = match)
                    3 -> SquadsTab(match = match)
                }
            }
        }
    }
}

@Composable
fun ScorecardTab(match: MatchHistory) {
    // Get all team players for first innings
    val firstInningsBattingTeamAllPlayers = (match.firstInningsBatting + match.secondInningsBowling)
        .distinctBy { it.name }
    val firstInningsBowlingTeamAllPlayers = (match.firstInningsBowling + match.secondInningsBatting)
        .distinctBy { it.name }
    
    // Get all team players for second innings
    val secondInningsBattingTeamAllPlayers = (match.secondInningsBatting + match.firstInningsBowling)
        .distinctBy { it.name }
    val secondInningsBowlingTeamAllPlayers = (match.secondInningsBowling + match.firstInningsBatting)
        .distinctBy { it.name }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // FIRST INNINGS
        item {
            val battingPlayers = if (match.firstInningsBatting.isNotEmpty()) {
                match.firstInningsBatting
            } else {
                generateSampleBattingData(match.team1Name, match.firstInningsRuns, match.firstInningsWickets, 1)
            }
            val bowlingPlayers = if (match.firstInningsBowling.isNotEmpty()) {
                match.firstInningsBowling
            } else {
                generateSampleBowlingData(match.team2Name, match.firstInningsWickets, 1)
            }
            
            // Find players who didn't bat or bowl
            val didNotBat = firstInningsBattingTeamAllPlayers.filter { player ->
                !battingPlayers.any { it.name == player.name }
            }
            val didNotBowl = firstInningsBowlingTeamAllPlayers.filter { player ->
                !bowlingPlayers.any { it.name == player.name }
            }
            
            CollapsibleInningsScorecardCard(
                title = "First Innings",
                battingTeam = match.team1Name,
                bowlingTeam = match.team2Name,
                totalRuns = match.firstInningsRuns,
                totalWickets = match.firstInningsWickets,
                batters = battingPlayers,
                bowlers = bowlingPlayers,
                didNotBat = didNotBat,
                didNotBowl = didNotBowl,
                isExpandedInitially = false,
                deliveries = match.allDeliveries,
                inningsNumber = 1,
                shortPitch = match.shortPitch
            )
        }
        
        // FIRST INNINGS PARTNERSHIPS
        if (match.firstInningsPartnerships.isNotEmpty()) {
            item {
                PartnershipsCard(
                    partnerships = match.firstInningsPartnerships,
                    inningsTitle = "First Innings"
                )
            }
        }
        
        // FIRST INNINGS FALL OF WICKETS
        if (match.firstInningsFallOfWickets.isNotEmpty()) {
            item {
                FallOfWicketsCard(
                    fallOfWickets = match.firstInningsFallOfWickets,
                    inningsTitle = "First Innings"
                )
            }
        }

        // SECOND INNINGS
        item {
            val battingPlayers = if (match.secondInningsBatting.isNotEmpty()) {
                match.secondInningsBatting
            } else {
                generateSampleBattingData(match.team2Name, match.secondInningsRuns, match.secondInningsWickets, 2)
            }
            val bowlingPlayers = if (match.secondInningsBowling.isNotEmpty()) {
                match.secondInningsBowling
            } else {
                generateSampleBowlingData(match.team1Name, match.secondInningsWickets, 2)
            }
            
            // Find players who didn't bat or bowl
            val didNotBat = secondInningsBattingTeamAllPlayers.filter { player ->
                !battingPlayers.any { it.name == player.name }
            }
            val didNotBowl = secondInningsBowlingTeamAllPlayers.filter { player ->
                !bowlingPlayers.any { it.name == player.name }
            }
            
            CollapsibleInningsScorecardCard(
                title = "Second Innings",
                battingTeam = match.team2Name,
                bowlingTeam = match.team1Name,
                totalRuns = match.secondInningsRuns,
                totalWickets = match.secondInningsWickets,
                batters = battingPlayers,
                bowlers = bowlingPlayers,
                didNotBat = didNotBat,
                didNotBowl = didNotBowl,
                isExpandedInitially = true,
                deliveries = match.allDeliveries,
                inningsNumber = 2,
                shortPitch = match.shortPitch
            )
        }
        
        // SECOND INNINGS PARTNERSHIPS
        if (match.secondInningsPartnerships.isNotEmpty()) {
            item {
                PartnershipsCard(
                    partnerships = match.secondInningsPartnerships,
                    inningsTitle = "Second Innings"
                )
            }
        }
        
        // SECOND INNINGS FALL OF WICKETS
        if (match.secondInningsFallOfWickets.isNotEmpty()) {
            item {
                FallOfWicketsCard(
                    fallOfWickets = match.secondInningsFallOfWickets,
                    inningsTitle = "Second Innings"
                )
            }
        }
    }
}

@Composable
fun OversTabContent(match: MatchHistory) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (match.allDeliveries.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            "No Ball-by-Ball Data",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Over-by-over breakdown is not available for this match.",
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                OversDetailCard(deliveries = match.allDeliveries)
            }
        }
    }
}

@Composable
fun SummaryTab(match: MatchHistory) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            EnhancedMatchSummaryCard(match = match)
        }
    }
}

/**
 * Merged view of a player's batting + bowling + fielding for the Squads tab.
 */
private data class SquadPlayerSummary(
    val id: String,
    val name: String,
    val isCaptain: Boolean,
    val isJoker: Boolean,
    // Batting (null = DNB)
    val runs: Int?,
    val ballsFaced: Int?,
    val fours: Int,
    val sixes: Int,
    val isOut: Boolean,
    val isRetired: Boolean,
    // Bowling (null = DNB)
    val wickets: Int?,
    val runsConceded: Int?,
    val oversBowled: Double?,
    // Fielding
    val catches: Int,
    val runOuts: Int,
    val stumpings: Int,
)

private fun buildSquadSummaries(
    battingStats: List<PlayerMatchStats>,
    bowlingStats: List<PlayerMatchStats>,
    captainName: String?,
    jokerName: String?,
): List<SquadPlayerSummary> {
    val map = linkedMapOf<String, SquadPlayerSummary>()

    // Batting entries first (preserves batting order)
    battingStats.forEach { p ->
        val hasBatted = p.runs > 0 || p.ballsFaced > 0 || p.isOut || p.isRetired
        map[p.id] = SquadPlayerSummary(
            id = p.id, name = p.name,
            isCaptain = p.name == captainName,
            isJoker = p.isJoker || p.name == jokerName,
            runs = if (hasBatted) p.runs else null,
            ballsFaced = if (hasBatted) p.ballsFaced else null,
            fours = p.fours, sixes = p.sixes,
            isOut = p.isOut, isRetired = p.isRetired,
            wickets = null, runsConceded = null, oversBowled = null,
            catches = p.catches, runOuts = p.runOuts, stumpings = p.stumpings,
        )
    }

    // Merge bowling entries
    bowlingStats.forEach { p ->
        val hasBowled = p.wickets > 0 || p.oversBowled > 0.0 || p.runsConceded > 0
        val existing = map[p.id]
        if (existing != null) {
            map[p.id] = existing.copy(
                wickets = if (hasBowled) p.wickets else null,
                runsConceded = if (hasBowled) p.runsConceded else null,
                oversBowled = if (hasBowled) p.oversBowled else null,
                catches = maxOf(existing.catches, p.catches),
                runOuts = maxOf(existing.runOuts, p.runOuts),
                stumpings = maxOf(existing.stumpings, p.stumpings),
            )
        } else {
            map[p.id] = SquadPlayerSummary(
                id = p.id, name = p.name,
                isCaptain = p.name == captainName,
                isJoker = p.isJoker || p.name == jokerName,
                runs = null, ballsFaced = null, fours = 0, sixes = 0,
                isOut = false, isRetired = false,
                wickets = if (hasBowled) p.wickets else null,
                runsConceded = if (hasBowled) p.runsConceded else null,
                oversBowled = if (hasBowled) p.oversBowled else null,
                catches = p.catches, runOuts = p.runOuts, stumpings = p.stumpings,
            )
        }
    }

    return map.values.toList()
}

@Composable
fun SquadsTab(match: MatchHistory) {
    val team1 = remember(match) {
        buildSquadSummaries(
            battingStats = match.firstInningsBatting,
            bowlingStats = match.secondInningsBowling,
            captainName = match.team1CaptainName,
            jokerName = match.jokerPlayerName,
        )
    }
    val team2 = remember(match) {
        buildSquadSummaries(
            battingStats = match.secondInningsBatting,
            bowlingStats = match.firstInningsBowling,
            captainName = match.team2CaptainName,
            jokerName = match.jokerPlayerName,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TeamSquadCard(
                teamName = match.team1Name,
                players = team1,
                teamColor = MaterialTheme.colorScheme.primaryContainer,
                shortPitch = match.shortPitch
            )
        }
        item {
            TeamSquadCard(
                teamName = match.team2Name,
                players = team2,
                teamColor = MaterialTheme.colorScheme.secondaryContainer,
                shortPitch = match.shortPitch
            )
        }
    }
}

@Composable
private fun TeamSquadCard(
    teamName: String,
    players: List<SquadPlayerSummary>,
    teamColor: Color,
    shortPitch: Boolean = false
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = teamColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Team Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = teamName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${players.size} players",
                    fontSize = 12.sp,
                    color = muted
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp)

            // Players List
            players.forEachIndexed { index, player ->
                SquadPlayerRow(player = player, shortPitch = shortPitch)
                if (index < players.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SquadPlayerRow(player: SquadPlayerSummary, shortPitch: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Name + badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = player.name,
                fontSize = 14.sp,
                fontWeight = if (player.isCaptain) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (player.isCaptain) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "C",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            if (player.isJoker) {
                Text("🃏", fontSize = 11.sp)
            }
        }

        // Right: stat chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Batting chip
            if (player.runs != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "${player.runs}(${player.ballsFaced})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    "DNB",
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    color = muted.copy(alpha = 0.45f)
                )
            }

            // Bowling chip
            if (player.wickets != null) {
                val hasWickets = player.wickets > 0
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (hasWickets)
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "${player.wickets}/${player.runsConceded}",
                        fontSize = 12.sp,
                        fontWeight = if (hasWickets) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (hasWickets) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    "DNB",
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    color = muted.copy(alpha = 0.45f)
                )
            }

            // Fielding chip (only if non-zero)
            val fieldingTotal = player.catches + player.runOuts + player.stumpings
            if (fieldingTotal > 0) {
                val parts = mutableListOf<String>()
                if (player.catches > 0) parts += "${player.catches}ct"
                if (player.stumpings > 0) parts += "${player.stumpings}st"
                if (player.runOuts > 0) parts += "${player.runOuts}ro"
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = parts.joinToString(" "),
                        fontSize = 11.sp,
                        color = muted,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun formatOvers(overs: Double): String {
    val fullOvers = overs.toInt()
    val balls = ((overs - fullOvers) * 10).toInt()
    return "$fullOvers.$balls"
}

// Helper function to calculate overs from bowling stats
fun calculateOversFromStats(bowlingPlayers: List<PlayerMatchStats>): Double {
    return bowlingPlayers.sumOf { it.oversBowled }
}

// Enhanced Match Summary Card with settings info
@Composable
fun EnhancedMatchSummaryCard(
    match: MatchHistory
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
                    Text(
                        text = "🏆 Match Result",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${match.winnerTeam} won by ${match.winningMargin}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

            Spacer(modifier = Modifier.height(11.dp))

            val hasPotm = match.playerOfTheMatchName != null
            if (hasPotm) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⭐ Player of the Match",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                val potmLine = buildString {
                    append(match.playerOfTheMatchName)
                    match.playerOfTheMatchTeam?.let { append(" (${it})") }
                    match.playerOfTheMatchSummary?.let { append(" — $it") }
                }
                Text(
                    text = potmLine,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                match.playerOfTheMatchImpact?.let { imp ->
                    Text(
                        text = "Impact: ${"%.1f".format(imp)}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            } else {
                // Fallback for older matches without POTM
                match.topBatsman?.let { topBat ->
                    Text(
                        text = "🏏 Top Batsman: ${topBat.name} - ${topBat.runs} runs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                match.topBowler?.let { topBowl ->
                    Text(
                        text = "⚾ Top Bowler: ${topBowl.name} - ${topBowl.wickets} wickets",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            match.playerImpacts.takeIf { it.isNotEmpty() }?.let { impacts ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Top Impact",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                impacts.take(3).forEach { pi ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${pi.name} (${pi.team})",
                            fontSize = 13.sp,
                            fontWeight = if (pi.name == match.playerOfTheMatchName) FontWeight.SemiBold else FontWeight.Medium
                        )
                        Text(
                            text = "${"%.1f".format(pi.impact)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    pi.summary.takeIf { it.isNotBlank() }?.let { sum ->
                        Text(
                            text = sum,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                val context = LocalContext.current
                TextButton(onClick = {
                    // Navigate to a dedicated Impact screen
                    val intent = Intent(context, ImpactListActivity::class.java)
                    intent.putExtra("match_id", match.id)
                    context.startActivity(intent)
                }) {
                    Text("View all impacts")
                }
            }
        }
    }
}

@Composable
fun PartnershipsCard(
    partnerships: List<Partnership>,
    inningsTitle: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header - Always visible, clickable
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "PARTNERSHIPS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                inningsTitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) 
                            Icons.Default.KeyboardArrowUp 
                        else 
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Expandable content
            if (isExpanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    partnerships.forEach { partnership ->
                        val activeMarker = if (partnership.isActive) " *" else ""
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            // Partnership header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${partnership.batsman1Name} & ${partnership.batsman2Name}$activeMarker",
                                    fontSize = 13.sp,
                                    fontWeight = if (partnership.isActive) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${partnership.runs} (${partnership.balls})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Individual contributions
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "${partnership.batsman1Name}: ${partnership.batsman1Runs}${if (partnership.isActive) "*" else ""}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${partnership.batsman2Name}: ${partnership.batsman2Runs}${if (partnership.isActive) "*" else ""}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        if (partnership != partnerships.last()) {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FallOfWicketsCard(
    fallOfWickets: List<FallOfWicket>,
    inningsTitle: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header - Always visible, clickable
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "FALL OF WICKETS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                inningsTitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) 
                            Icons.Default.KeyboardArrowUp 
                        else 
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Expandable content
            if (isExpanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    fallOfWickets.forEach { fow ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Wicket number and batsman
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        "${fow.wicketNumber}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    fow.batsmanName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            // Score and overs
                            Text(
                                "${fow.runs}-${fow.wicketNumber} (${String.format("%.1f", fow.overs)} ov)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (fow != fallOfWickets.last()) {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun EnhancedBattingScorecardCard(
    players: List<PlayerMatchStats>,
    didNotBat: List<PlayerMatchStats>,
    isComplete: Boolean,
    shortPitch: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Batsman", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("R", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("B", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("4s", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!shortPitch) {
                    Text("6s", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Text("SR", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // FILTER: Only show players who actually batted or retired
            val actualBatters = players.filter { player ->
                player.ballsFaced > 0 || player.runs > 0 || player.isOut || player.isRetired
            }

            // Player rows - only those who actually batted
            actualBatters.forEach { player ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (player.isJoker) "🃏 ${player.name}" else player.name,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(2f),
                            color = if (player.isJoker) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        )
                        Text("${player.runs}${if (player.isOut) "" else "*"}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${player.ballsFaced}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${player.fours}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (!shortPitch) {
                            Text("${player.sixes}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        }
                        Text("${"%.1f".format(player.strikeRate)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                    if (player.isOut || player.isRetired) {
                        Text(
                            text = player.getDismissalText(),
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }

            // Only show "did not bat" if there are actual players who didn't bat
            val relevantDidNotBat = players.filter { player ->
                !actualBatters.any { it.name == player.name }
            }

            if (relevantDidNotBat.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                val statusText = if (isComplete) {
                    "Did not bat: ${relevantDidNotBat.joinToString(", ") { it.name }}"
                } else {
                    "Did to bat: ${relevantDidNotBat.joinToString(", ") { it.name }}"
                }

                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun EnhancedBowlingScorecardCard(
    players: List<PlayerMatchStats>,
    didNotBowl: List<PlayerMatchStats>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Bowler", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("O", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("M", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                Text("R", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("W", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Eco", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Player rows
            players.forEach { player ->
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (player.isJoker) "🃏 ${player.name}" else player.name,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(2f),
                        color = if (player.isJoker) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    )
                    Text("${"%.1f".format(player.oversBowled)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${player.maidenOvers}", fontSize = 14.sp, modifier = Modifier.weight(0.7f))
                    Text("${player.runsConceded}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${player.wickets}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${"%.1f".format(player.economy)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }

            // Show players who didn't bowl
            if (didNotBowl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Did not bowl: ${didNotBowl.joinToString(", ") { it.name }}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// Keep all your existing helper functions unchanged
fun generateSampleBattingData(
    teamName: String,
    totalRuns: Int,
    totalWickets: Int,
    innings: Int,
): List<PlayerMatchStats> {
    val team1Players = listOf("Virat Kohli", "Rohit Sharma", "MS Dhoni", "KL Rahul", "Hardik Pandya")
    val team2Players = listOf("Kane Williamson", "David Warner", "Steve Smith", "Jos Buttler", "Ben Stokes")

    val playerNames =
        when (teamName) {
            "Team A" -> if (innings == 1) team1Players else team2Players
            "Team B" -> if (innings == 1) team2Players else team1Players
            else -> listOf("Player 1", "Player 2", "Player 3", "Player 4", "Player 5")
        }

    val players = mutableListOf<PlayerMatchStats>()
    var remainingRuns = totalRuns
    var playersOut = totalWickets

    val distribution =
        if (innings == 1) {
            listOf(0.35, 0.25, 0.20, 0.15, 0.05)
        } else {
            listOf(0.40, 0.30, 0.15, 0.10, 0.05)
        }

    playerNames.take(5).forEachIndexed { index, name ->
        if (index >= distribution.size) return@forEachIndexed

        val isOut = index < playersOut
        val runs = (totalRuns * distribution[index]).toInt()
        remainingRuns -= runs

        val ballsFaced =
            if (runs > 0) {
                val baseRate = if (innings == 1) 0.8 else 0.9
                (runs * baseRate + (5..15).random()).toInt()
            } else {
                0
            }

        val fours = runs / (if (innings == 1) 8 else 6)
        val sixes = runs / (if (innings == 1) 12 else 10)

        if (runs > 0 || ballsFaced > 0) {
            players.add(
                PlayerMatchStats(
                    id = name,
                    name = name,
                    runs = runs,
                    ballsFaced = ballsFaced,
                    fours = fours,
                    sixes = sixes,
                    isOut = isOut,
                    team = teamName,
                ),
            )
        }
    }

    return players
}

fun generateSampleBowlingData(
    teamName: String,
    totalWickets: Int,
    innings: Int,
): List<PlayerMatchStats> {
    val team1Bowlers = listOf("Jasprit Bumrah", "Mohammed Shami", "Ravindra Jadeja", "Yuzvendra Chahal")
    val team2Bowlers = listOf("Pat Cummins", "Mitchell Starc", "Adam Zampa", "Josh Hazlewood")

    val bowlerNames =
        when (teamName) {
            "Team A" -> if (innings == 2) team1Bowlers else team2Bowlers
            "Team B" -> if (innings == 2) team2Bowlers else team1Bowlers
            else -> listOf("Bowler 1", "Bowler 2", "Bowler 3", "Bowler 4")
        }

    val bowlers = mutableListOf<PlayerMatchStats>()
    var remainingWickets = totalWickets

    bowlerNames.take(4).forEachIndexed { index, name ->
        val wickets =
            when {
                index == 0 -> (totalWickets / 2).coerceAtMost(3)
                index == 1 -> (remainingWickets / 2).coerceAtMost(2)
                else -> if (remainingWickets > 0) 1 else 0
            }

        remainingWickets -= wickets
        val overs = (2..5).random().toDouble()

        val baseEconomy = if (innings == 1) 7.0 else 8.5
        val runsConceded = (overs * baseEconomy + (-10..10).random()).toInt().coerceAtLeast(0)

        if (overs > 0 || wickets > 0 || runsConceded > 0) {
            bowlers.add(
                PlayerMatchStats(
                    id = name,
                    name = name,
                    wickets = wickets,
                    runsConceded = runsConceded,
                    oversBowled = overs,
                    team = teamName,
                ),
            )
        }
    }

    return bowlers.filter { it.oversBowled > 0 || it.wickets > 0 }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OversDetailCard(deliveries: List<DeliveryUI>) {
    // Group by innings
    val deliveriesByInnings = deliveries.groupBy { it.inning }
    
    // Helper function to extract runs from outcome string (fallback for old data)
    fun extractRunsFromOutcome(outcome: String, storedRuns: Int): Int {
        if (storedRuns > 0) return storedRuns
        
        // Parse outcome string for runs
        return when {
            outcome == "W" -> 0
            outcome.startsWith("Wd+") -> outcome.substringAfter("Wd+").takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            outcome.startsWith("Nb+") -> outcome.substringAfter("Nb+").takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            outcome.startsWith("B+") -> outcome.substringAfter("B+").takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            outcome.startsWith("Lb+") -> outcome.substringAfter("Lb+").takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            outcome.contains("+") && outcome.contains("RO") -> {
                outcome.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
            else -> outcome.toIntOrNull() ?: 0
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        deliveriesByInnings.keys.sorted().forEach { inningsNumber ->
            val inningsDeliveries = deliveriesByInnings[inningsNumber] ?: emptyList()
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (inningsNumber == 1) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        when (inningsNumber) {
                            1 -> "First Innings"
                            2 -> "Second Innings"
                            else -> "Innings $inningsNumber"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (inningsNumber == 1) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Group by over
                    val deliveriesByOver = inningsDeliveries.groupBy { it.over }
                    
                    deliveriesByOver.keys.sorted().forEach { overNumber ->
                        val overDeliveries = deliveriesByOver[overNumber] ?: emptyList()
                        val overTotalRuns = overDeliveries.sumOf { extractRunsFromOutcome(it.outcome, it.runs) }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                // Over header with bowler info on same line
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Over $overNumber",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        // Bowler info inline
                                        val firstDelivery = overDeliveries.firstOrNull()
                                        if (firstDelivery != null && !firstDelivery.bowlerName.isNullOrEmpty()) {
                                            Text(
                                                "• ${firstDelivery.bowlerName}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        "$overTotalRuns runs",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                
                                // Batsmen info (compact, single line)
                                val firstDelivery = overDeliveries.firstOrNull()
                                if (firstDelivery != null) {
                                    val allBatsmen = overDeliveries.flatMap { 
                                        listOf(it.strikerName, it.nonStrikerName) 
                                    }.filter { !it.isNullOrEmpty() }.distinct()
                                    
                                    if (allBatsmen.isNotEmpty()) {
                                        Text(
                                            "Batters: ${allBatsmen.joinToString(", ")}",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(6.dp))
                                
                                // Deliveries - Smooth horizontal scrollable row with LazyRow
                                androidx.compose.foundation.lazy.LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(overDeliveries.size) { index ->
                                        val delivery = overDeliveries[index]
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = if (delivery.highlight) 
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            else 
                                                MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.defaultMinSize(minWidth = 32.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    delivery.outcome,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleInningsScorecardCard(
    title: String,
    battingTeam: String,
    bowlingTeam: String,
    totalRuns: Int,
    totalWickets: Int,
    batters: List<PlayerMatchStats>,
    bowlers: List<PlayerMatchStats>,
    didNotBat: List<PlayerMatchStats> = emptyList(),
    didNotBowl: List<PlayerMatchStats> = emptyList(),
    isExpandedInitially: Boolean = true,
    deliveries: List<DeliveryUI> = emptyList(),
    inningsNumber: Int = 1,
    shortPitch: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(isExpandedInitially) }
    
    // Calculate overs bowled from bowlers stats
    val totalBallsBowled = bowlers.sumOf { (it.oversBowled * 6).toInt() + ((it.oversBowled % 1) * 10).toInt() }
    val completeOvers = totalBallsBowled / 6
    val remainingBalls = totalBallsBowled % 6
    val oversString = if (remainingBalls > 0) "$completeOvers.$remainingBalls" else "$completeOvers"
    
    // Calculate extras per bowler from deliveries
    val extrasPerBowler = remember(deliveries, inningsNumber) {
        deliveries
            .filter { it.inning == inningsNumber }
            .groupBy { it.bowlerName }
            .mapValues { (_, bowlerDeliveries) ->
                val wides = bowlerDeliveries.count { it.outcome.startsWith("Wd") }
                val noBalls = bowlerDeliveries.count { it.outcome.startsWith("Nb") }
                Pair(wides, noBalls)
            }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            // Header - Always visible
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                color = if (isExpanded)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = battingTeam,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "$totalRuns/$totalWickets",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (totalBallsBowled > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "($oversString)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) 
                            Icons.Default.KeyboardArrowUp 
                        else 
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Expandable content
            if (isExpanded) {
                HorizontalDivider()
                
                // Batting Section
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "BATTING",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // Batting Header Row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Batter", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("R", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("B", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("4s", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!shortPitch) {
                            Text("6s", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("SR", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    
                    // Batters
                    batters.forEach { player ->
                        val sr = if (player.ballsFaced > 0) 
                            String.format("%.1f", (player.runs.toFloat() / player.ballsFaced) * 100)
                        else "0.0"
                        
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    player.name,
                                    modifier = Modifier.weight(2f),
                                    fontSize = 13.sp,
                                    fontWeight = if (!player.isOut) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(player.runs.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(player.ballsFaced.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                Text(player.fours.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                if (!shortPitch) {
                                    Text(player.sixes.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                }
                                Text(sr, modifier = Modifier.weight(0.9f), fontSize = 13.sp)
                            }
                            if (player.isOut || player.isRetired) {
                                Text(
                                    text = player.getDismissalText(),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    // Yet to bat / Did not bat
                    if (didNotBat.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        
                        val statusText = if (totalWickets == 10) "Did not bat" else "Did to bat"
                        
                        Text(
                            text = "$statusText: ${didNotBat.joinToString(", ") { it.name }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                
                // Bowling Section
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "BOWLING",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // Bowling Header Row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Bowler", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("O", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("M", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("R", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("W", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Econ", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    
                    // Bowlers
                    bowlers.forEach { player ->
                        val overs = (player.oversBowled.toInt())
                        val balls = ((player.oversBowled - overs) * 10).toInt()
                        val oversStr = "$overs.$balls"
                        val econ = if (player.oversBowled > 0) {
                            String.format("%.1f", player.runsConceded / player.oversBowled)
                        } else "0.0"
                        
                        val (wides, noBalls) = extrasPerBowler[player.name] ?: Pair(0, 0)
                        
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(player.name, modifier = Modifier.weight(2f), fontSize = 13.sp)
                                Text(oversStr, modifier = Modifier.weight(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(player.maidenOvers.toString(), modifier = Modifier.weight(0.5f), fontSize = 13.sp)
                                Text(player.runsConceded.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                Text(player.wickets.toString(), modifier = Modifier.weight(0.7f), fontSize = 13.sp)
                                Text(econ, modifier = Modifier.weight(0.9f), fontSize = 13.sp)
                            }
                            
                            // Show extras if any
                            if (wides > 0 || noBalls > 0) {
                                val extrasList = mutableListOf<String>()
                                if (wides > 0) extrasList.add("$wides Wd")
                                if (noBalls > 0) extrasList.add("$noBalls Nb")
                                
                                Text(
                                    "Extras: ${extrasList.joinToString(", ")}",
                                    modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                    
                    // Did not bowl
                    if (didNotBowl.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            text = "Did not bowl: ${didNotBowl.joinToString(", ") { it.name }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

