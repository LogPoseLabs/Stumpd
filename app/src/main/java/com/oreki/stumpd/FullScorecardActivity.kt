package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Edit
import com.oreki.stumpd.data.repository.MatchCorrectionRepository
import com.oreki.stumpd.ui.correction.CorrectMatchActivity
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.hairline
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.domain.match.effectiveRuns
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.domain.match.inningsLabel
import com.oreki.stumpd.ui.scoring.InningsScorecardCard
import com.oreki.stumpd.ui.components.InningsPillOption
import com.oreki.stumpd.ui.components.InningsPillRow
import com.oreki.stumpd.ui.scoring.formatBallsAsOvers
import com.oreki.stumpd.ui.scoring.toScorecardPlayer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.oreki.stumpd.viewmodel.FullScorecardViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FullScorecardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val matchId = intent.getStringExtra("match_id") ?: ""
        // Callers that used to open the separate Match Summary screen land on that tab instead.
        val initialTab = intent.getStringExtra("initial_tab")

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FullScorecardScreen(matchId, initialTab)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullScorecardScreen(matchId: String, initialTab: String? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: FullScorecardViewModel = hiltViewModel(
        creationCallback = { factory: FullScorecardViewModel.Factory ->
            factory.create(matchId)
        }
    )
    val uiState = viewModel.uiState

    when (uiState) {
        is FullScorecardViewModel.UiState.Loading -> {
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
        }
        is FullScorecardViewModel.UiState.Error -> {
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
                            if (uiState.message == "Match not found") "Match Not Found" else "Error",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            uiState.message,
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
        }
        is FullScorecardViewModel.UiState.Content -> {
            val match = uiState.match
            val matchSettings = match.matchSettings ?: MatchSettings()
            val totalOvers = matchSettings.totalOvers

            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

            // A correction changes the very figures on this screen, so a successful edit
            // re-reads the match rather than leaving stale numbers behind.
            val correctionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.reload()
            }
            val openEditor: (String?) -> Unit = { target ->
                correctionLauncher.launch(CorrectMatchActivity.intent(context, matchId, target))
            }

            // Fix mode makes the wrong figure itself the way in: you tap the dismissal that reads
            // wrong rather than hunting for it in a menu. It opens the editor at the matching
            // section, so there is one implementation of each correction, not two. Off by default,
            // and the editor still asks for the password.
            var fixMode by rememberSaveable { mutableStateOf(false) }
            // Somebody else's group match can't be corrected from this phone, so fix mode has
            // nothing to offer even if a stale saved flag says it was on.
            if (!uiState.correctable) fixMode = false

            val tabs = listOf("Scorecard", "Overs", "Summary", "Squads")
            val pagerState = rememberPagerState(
                initialPage = tabs.indexOf(initialTab).coerceAtLeast(0),
                pageCount = { tabs.size },
            )

            Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Match Scorecard",
                subtitle = "${match.team1Name} vs ${match.team2Name} • ${dateFormat.format(Date(match.matchDate))}",
                onBack = { (context as ComponentActivity).finish() },
                actions = {
                if (uiState.correctable) {
                    IconButton(onClick = { fixMode = !fixMode }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = if (fixMode) "Leave fix mode" else "Correct match",
                            tint = if (fixMode) MaterialTheme.colorScheme.primary
                                   else LocalContentColor.current,
                        )
                    }
                }
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
            // Result strip. It sits above the tabs and so never scrolls away — one line, because
            // every tab pays for its height.
            val isTie = match.winnerTeam.equals("TIE", true)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (isTie)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val onColor = if (isTie)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                    Icon(
                        if (isTie) Icons.Default.Info else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = onColor
                    )
                    Text(
                        // Recomputed from the squads: the saved margin assumed eleven-a-side.
                        text = matchResultLine(match),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = onColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = listOfNotNull(
                            "$totalOvers ov",
                            match.groupName?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = onColor.copy(alpha = 0.8f)
                    )
                }
            }
        
        if (fixMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Tap a dismissal, bowler or player to correct it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { openEditor(null) }) { Text("All edits") }
                    TextButton(onClick = { fixMode = false }) { Text("Done") }
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
                    0 -> ScorecardTab(
                        match = match,
                        onFix = if (fixMode) openEditor else null,
                    )
                    1 -> OversTabContent(
                        match = match,
                        onFixBalls = if (fixMode) { -> openEditor(CorrectMatchActivity.TARGET_BALLS) } else null,
                    )
                    2 -> SummaryTab(match = match, corrections = uiState.corrections)
                    3 -> SquadsTab(
                        match = match,
                        onFixPlayer = if (fixMode) { -> openEditor(CorrectMatchActivity.TARGET_SQUAD) } else null,
                    )
                }
            }
        }
    }
        }
    }
}

@Composable
fun ScorecardTab(
    match: MatchHistory,
    /** Given the section to open — see `CorrectMatchActivity.TARGET_*`. Null outside fix mode. */
    onFix: ((String) -> Unit)? = null,
) {
    // One innings at a time, picked with the team pills: the two stacked collapsible cards meant
    // the tab opened on a pair of headers with almost no data behind them.
    val secondInningsHasData =
        match.secondInningsBatting.isNotEmpty() || match.secondInningsBowling.isNotEmpty()
    var selectedInnings by rememberSaveable { mutableStateOf(if (secondInningsHasData) 2 else 1) }

    Column(modifier = Modifier.fillMaxSize()) {
        InningsPillRow(
            options = inningsPillOptions(match),
            selectedIndex = selectedInnings - 1,
            onSelect = { selectedInnings = it + 1 },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SavedInningsCard(match = match, innings = selectedInnings, onFix = onFix) }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

/**
 * One saved innings, rendered by the same card the live scoring and spectator screens use.
 *
 * The saved match stores batting and bowling as [PlayerMatchStats] with overs in cricket
 * notation, so the rows are mapped across; everything the card can't derive from the rows
 * themselves — the total, extras, who never batted — is worked out here.
 */
@Composable
private fun SavedInningsCard(
    match: MatchHistory,
    innings: Int,
    onFix: ((String) -> Unit)? = null,
) {
    // This card can only render the two innings that have per-player rows. Anything else would
    // silently fall through to the second innings and print it twice.
    if (innings !in 1..2) return
    val firstInnings = innings == 1
    val battingStats = if (firstInnings) match.firstInningsBatting else match.secondInningsBatting
    val bowlingStats = if (firstInnings) match.firstInningsBowling else match.secondInningsBowling
    val battingTeam = if (firstInnings) match.team1Name else match.team2Name
    val title = if (firstInnings) "First Innings" else "Second Innings"

    // Never fabricate players. This previously fell back to sample data seeded with real
    // international names, so a match missing its batting stats displayed "Virat Kohli" and
    // "MS Dhoni" as though they had played.
    if (battingStats.isEmpty() && bowlingStats.isEmpty()) {
        MissingInningsCard(title, battingTeam)
        return
    }

    val batters = remember(battingStats) { battingStats.map { it.toScorecardPlayer() } }
    val bowlers = remember(bowlingStats) { bowlingStats.map { it.toScorecardPlayer() } }

    // A team's squad for an innings is whoever turns up on their side of either innings: the
    // batting side also bowled in the other innings, and vice versa.
    val didNotBat = remember(match, innings) {
        val squad = if (firstInnings) match.secondInningsBowling else match.firstInningsBowling
        squad.map { it.name }.distinct().filter { name -> battingStats.none { it.name == name } }
    }
    val didNotBowl = remember(match, innings) {
        val squad = if (firstInnings) match.secondInningsBatting else match.firstInningsBatting
        squad.map { it.name }.distinct().filter { name -> bowlingStats.none { it.name == name } }
    }

    // Prefer the delivery log for the innings length; fall back to the bowlers' own figures for
    // older matches saved without ball-by-ball data.
    val ballsBowled = remember(match, innings, bowlers) {
        legalBallsInInnings(match, innings).takeIf { it > 0 } ?: bowlers.sumOf { it.ballsBowled }
    }
    val bowlerExtras = remember(match.allDeliveries, innings) {
        widesAndNoBallsByBowler(match.allDeliveries, innings)
    }

    val totalRuns = if (firstInnings) match.firstInningsRuns else match.secondInningsRuns
    // Extras are whatever the team scored that no batter is credited with, which is the only
    // definition that always reconciles with the total on the line below it.
    val extras = (totalRuns - batters.sumOf { it.runs }).coerceAtLeast(0)

    InningsScorecardCard(
        title = title,
        isExpanded = true,
        onToggleExpand = {},
        collapsible = false,
        battingTeam = battingTeam,
        bowlingTeam = if (firstInnings) match.team2Name else match.team1Name,
        batters = batters,
        bowlers = bowlers,
        partnerships = if (firstInnings) match.firstInningsPartnerships else match.secondInningsPartnerships,
        fallOfWickets = if (firstInnings) match.firstInningsFallOfWickets else match.secondInningsFallOfWickets,
        shortPitch = match.shortPitch,
        totalRuns = totalRuns,
        totalWickets = if (firstInnings) match.firstInningsWickets else match.secondInningsWickets,
        ballsBowled = ballsBowled,
        extras = extras,
        didNotBat = didNotBat,
        didNotBowl = didNotBowl,
        bowlerWidesAndNoBalls = bowlerExtras,
        onFixBatter = onFix?.let { open -> { _ -> open(CorrectMatchActivity.TARGET_DISMISSALS) } },
        onFixBowler = onFix?.let { open -> { _ -> open(CorrectMatchActivity.TARGET_BOWLING) } },
    )
}

/** Wides and no-balls each bowler sent down, keyed by name, for the innings' bowling figures. */
private fun widesAndNoBallsByBowler(
    deliveries: List<DeliveryUI>,
    innings: Int,
): Map<String, Pair<Int, Int>> =
    deliveries
        .filter { it.inning == innings }
        .groupBy { it.bowlerName.orEmpty() }
        .mapValues { (_, bowled) ->
            bowled.count { it.outcome.startsWith("Wd", ignoreCase = true) } to
                bowled.count { it.outcome.startsWith("Nb", ignoreCase = true) }
        }

@Composable
fun OversTabContent(match: MatchHistory, onFixBalls: (() -> Unit)? = null) {
    val secondInningsHasOvers = match.allDeliveries.any { it.inning == 2 }
    var selectedInnings by rememberSaveable { mutableStateOf(if (secondInningsHasOvers) 2 else 1) }

    // Pill order is innings order — 1, 2, then each super over — so the index maps straight to the
    // innings number, rather than assuming there are only ever two.
    val innings = remember(match.superOvers) {
        listOf(1, 2) + match.superOvers.map { it.inning }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        InningsPillRow(
            options = oversPillOptions(match),
            selectedIndex = innings.indexOf(selectedInnings).coerceAtLeast(0),
            onSelect = { selectedInnings = innings.getOrElse(it) { 1 } },
        )
        if (onFixBalls != null) {
            TextButton(
                onClick = onFixBalls,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit a ball")
            }
        }
        OversTabList(match = match, innings = selectedInnings)
    }
}

/**
 * The pills for a saved match's scorecard: the two innings, each with the score it made.
 *
 * Deliberately *not* extended with super-over innings. There are no per-player rows for an
 * eliminator — its runs and wickets are kept out of everyone's figures — so a super-over pill here
 * would have nothing to show, and [SavedInningsCard] would fall through to the second innings and
 * render it a second time. The eliminator lives on the Overs tab and the Summary card instead.
 */
private fun inningsPillOptions(match: MatchHistory) = listOf(
    InningsPillOption(match.team1Name, "${match.firstInningsRuns}/${match.firstInningsWickets}"),
    InningsPillOption(match.team2Name, "${match.secondInningsRuns}/${match.secondInningsWickets}"),
)

/**
 * The pills for the Overs tab, which *can* show a super over: the two innings, then one per
 * eliminator innings, in the order they were bowled.
 *
 * Ball-by-ball is filtered by innings number, so these work without anything further.
 */
private fun oversPillOptions(match: MatchHistory) = inningsPillOptions(match) +
    match.superOvers.map { so ->
        InningsPillOption(so.battingTeam, "${so.runs}/${so.wickets} · SO")
    }

@Composable
private fun OversTabList(match: MatchHistory, innings: Int) {
    val deliveries = remember(match.allDeliveries, innings) {
        match.allDeliveries.filter { it.inning == innings }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (deliveries.isEmpty()) {
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
                OversDetailCard(deliveries = deliveries)
            }
        }
    }
}

@Composable
fun SummaryTab(
    match: MatchHistory,
    corrections: List<MatchCorrectionRepository.CorrectionLogEntry> = emptyList(),
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchInfoCard(match) }
        item { InningsComparisonCard(match) }
        item { EnhancedMatchSummaryCard(match = match) }
        if (match.superOvers.isNotEmpty()) {
            item { SuperOverCard(match) }
        }
        item { MatchOverviewCard(match) }
        if (corrections.isNotEmpty()) {
            item { CorrectionsCard(corrections) }
        }
    }
}

/** When, how long, and under what conditions — the things not visible anywhere else. */
/**
 * What's been corrected on this match, and by whom.
 *
 * A corrected match syncs to the rest of the group without comment, so someone can open a
 * scorecard and find a figure different from the one they watched being scored. This is the
 * answer to "why" — last, because it's provenance rather than performance, and absent entirely
 * for the vast majority of matches that were never corrected.
 */
@Composable
private fun CorrectionsCard(corrections: List<MatchCorrectionRepository.CorrectionLogEntry>) {
    val format = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val thisDevice = remember { "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim() }
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = hairline(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "CORRECTIONS (${corrections.size})",
                style = MicroLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val shown = if (expanded) corrections else corrections.take(2)
            shown.forEach { entry ->
                Spacer(Modifier.height(10.dp))
                entry.summary.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    format.format(Date(entry.appliedAt)) + " · " +
                        if (entry.deviceLabel == thisDevice) "this device" else entry.deviceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (corrections.size > 2 && !expanded) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { expanded = true }) {
                    Text("Show all (${corrections.size})")
                }
            }
        }
    }
}

/**
 * How the eliminator went.
 *
 * There are no per-player rows for a super over — its runs and wickets are deliberately kept out of
 * everyone's figures, as in the real game — so this card and the Overs tab's ball-by-ball are the
 * complete record of it.
 */
@Composable
private fun SuperOverCard(match: MatchHistory) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = hairline(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (match.superOvers.size > 2) "SUPER OVERS" else "SUPER OVER",
                style = MicroLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            match.superOvers.forEach { so ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(so.battingTeam, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${so.runs}/${so.wickets} (${so.balls} ${if (so.balls == 1) "ball" else "balls"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            match.superOverWinner?.let { winner ->
                Spacer(Modifier.height(8.dp))
                Text(
                    if (winner.equals("TIE", ignoreCase = true)) {
                        "Still tied — the match is a tie"
                    } else {
                        "$winner won the Super Over"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun MatchInfoCard(match: MatchHistory) {
    val settings = match.matchSettings ?: MatchSettings()
    val facts = buildList {
        add("Overs" to settings.totalOvers.toString())
        add("Pitch" to if (match.shortPitch) "Short" else "Long")
        match.groupName?.takeIf { it.isNotBlank() }?.let { add("Group" to it) }
        match.jokerPlayerName?.takeIf { it.isNotBlank() }?.let { add("Joker" to it) }
        match.team1CaptainName?.takeIf { it.isNotBlank() }?.let { add("${match.team1Name} captain" to it) }
        match.team2CaptainName?.takeIf { it.isNotBlank() }?.let { add("${match.team2Name} captain" to it) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                    .format(Date(match.matchDate)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            facts.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Both innings side by side with their run rates, and how the chase went. */
@Composable
private fun InningsComparisonCard(match: MatchHistory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            InningsScoreRow(
                team = match.team1Name,
                runs = match.firstInningsRuns,
                wickets = match.firstInningsWickets,
                balls = legalBallsInInnings(match, 1),
                runRate = inningsRunRate(match, innings = 1, runs = match.firstInningsRuns),
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            InningsScoreRow(
                team = match.team2Name,
                runs = match.secondInningsRuns,
                wickets = match.secondInningsWickets,
                balls = legalBallsInInnings(match, 2),
                runRate = inningsRunRate(match, innings = 2, runs = match.secondInningsRuns),
            )

            // The chase: what the side batting second had to get, and whether they got there.
            // Level scores are their own case — "fell 1 short" is arithmetically true of a tie and
            // reads like a defeat, which is exactly wrong when a super over then decided it.
            val target = match.firstInningsRuns + 1
            val stillNeeded = target - match.secondInningsRuns
            val scoresLevel = match.secondInningsRuns == match.firstInningsRuns
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    scoresLevel && match.superOverWinner != null ->
                        "Scores level — decided by the Super Over"

                    scoresLevel -> "Scores level — match tied"
                    stillNeeded > 0 -> "Chasing $target — fell $stillNeeded short"
                    else -> "Chasing $target — target reached"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (stillNeeded > 0 && !scoresLevel)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.primary,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
private fun InningsScoreRow(
    team: String,
    runs: Int,
    wickets: Int,
    balls: Int,
    runRate: Double,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            team,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$runs/$wickets",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildString {
                if (balls > 0) append("${formatBallsAsOvers(balls)} ov · ")
                append("RR ${"%.2f".format(runRate)}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Whole-match totals and the two standout performances. */
@Composable
private fun MatchOverviewCard(match: MatchHistory) {
    val topScorer = (match.firstInningsBatting + match.secondInningsBatting).maxByOrNull { it.runs }
    val topWicketTaker = (match.firstInningsBowling + match.secondInningsBowling).maxByOrNull { it.wickets }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Match Overview",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            OverviewRow("Total runs", (match.firstInningsRuns + match.secondInningsRuns).toString())
            OverviewRow("Total wickets", (match.firstInningsWickets + match.secondInningsWickets).toString())
            topScorer?.takeIf { it.runs > 0 }?.let {
                OverviewRow("Top scorer", "${it.name} — ${it.runs}${if (it.isOut) "" else "*"} (${it.ballsFaced})")
            }
            topWicketTaker?.takeIf { it.wickets > 0 }?.let {
                OverviewRow("Best bowling", "${it.name} — ${it.wickets}/${it.runsConceded}")
            }
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
fun SquadsTab(match: MatchHistory, onFixPlayer: (() -> Unit)? = null) {
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TeamSquadCard(
                teamName = match.team1Name,
                players = team1,
                teamColor = MaterialTheme.colorScheme.primaryContainer,
                shortPitch = match.shortPitch,
                onFixPlayer = onFixPlayer,
            )
        }
        item {
            TeamSquadCard(
                teamName = match.team2Name,
                players = team2,
                teamColor = MaterialTheme.colorScheme.secondaryContainer,
                shortPitch = match.shortPitch,
                onFixPlayer = onFixPlayer,
            )
        }
    }
}

@Composable
private fun TeamSquadCard(
    teamName: String,
    players: List<SquadPlayerSummary>,
    teamColor: Color,
    shortPitch: Boolean = false,
    onFixPlayer: (() -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = teamColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = teamName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${players.size} players",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
            }

            HorizontalDivider()

            // Players List
            players.forEachIndexed { index, player ->
                SquadPlayerRow(
                    player = player,
                    shortPitch = shortPitch,
                    onFix = onFixPlayer,
                )
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
private fun SquadPlayerRow(
    player: SquadPlayerSummary,
    shortPitch: Boolean = false,
    onFix: (() -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onFix != null) Modifier.clickable { onFix() } else Modifier)
            .padding(vertical = 8.dp),
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
                style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    "DNB",
                    style = MaterialTheme.typography.labelSmall,
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
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (hasWickets) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (hasWickets) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    "DNB",
                    style = MaterialTheme.typography.labelSmall,
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
            modifier = Modifier.padding(12.dp)
        ) {
            // The result itself is in the strip above every tab, so it isn't repeated here.
            val hasPotm = match.playerOfTheMatchName != null
            if (hasPotm) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * Shown when a saved match has no batting or bowling rows for an innings. Older matches and
 * ones interrupted mid-save can be missing them; the screen used to substitute fabricated
 * sample players here, which read as real data.
 */
@Composable
private fun MissingInningsCard(title: String, battingTeam: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "No scorecard recorded for $battingTeam in this innings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Over-by-over breakdown. One card per innings with divider-separated over rows — a card per
 * over inside a card per innings spent most of the screen on nesting.
 */
@Composable
fun OversDetailCard(deliveries: List<DeliveryUI>) {
    val deliveriesByInnings = deliveries.groupBy { it.inning }

    // Older matches were saved without per-delivery runs, so fall back to reading the outcome.

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        deliveriesByInnings.keys.sorted().forEach { inningsNumber ->
            val inningsDeliveries = deliveriesByInnings[inningsNumber] ?: emptyList()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Text(
                        text = when (inningsNumber) {
                            1 -> "First Innings"
                            2 -> "Second Innings"
                            // "Super Over", or "Super Over 2" for a repeat.
                            else -> inningsLabel(inningsNumber)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    HorizontalDivider()

                    val deliveriesByOver = inningsDeliveries.groupBy { it.over }
                    val overNumbers = deliveriesByOver.keys.sorted()

                    overNumbers.forEach { overNumber ->
                        val overDeliveries = deliveriesByOver[overNumber] ?: emptyList()
                        val overTotalRuns = overDeliveries.sumOf { it.effectiveRuns() }
                        val bowler = overDeliveries.firstOrNull()?.bowlerName

                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buildString {
                                        append("Over $overNumber")
                                        if (!bowler.isNullOrEmpty()) append(" · $bowler")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$overTotalRuns run${if (overTotalRuns == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            val battersInOver = overDeliveries
                                .flatMap { listOf(it.strikerName, it.nonStrikerName) }
                                .filterNot { it.isNullOrEmpty() }
                                .distinct()
                            if (battersInOver.isNotEmpty()) {
                                Text(
                                    "Batters: ${battersInOver.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(overDeliveries.size) { index ->
                                    val delivery = overDeliveries[index]
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (delivery.highlight)
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.defaultMinSize(minWidth = 30.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                delivery.outcome,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (overNumber != overNumbers.last()) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
