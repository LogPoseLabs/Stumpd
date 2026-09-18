package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.ui.scoring.ScoringActivity
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import com.oreki.stumpd.ui.theme.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.oreki.stumpd.ui.theme.rememberMessenger
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.manager.InProgressMatchManager
import com.oreki.stumpd.data.models.MatchInProgress
import com.oreki.stumpd.ui.components.DateFilterDialog
import com.oreki.stumpd.ui.components.GroupFilterDropdown
import com.oreki.stumpd.ui.components.filterMatchesByGroup
import com.oreki.stumpd.ui.components.filterMatchesByPitchType
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.data.preferences.PasscodeManager
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.hairline
import com.oreki.stumpd.ui.theme.ScoreMedium
import com.oreki.stumpd.ui.theme.StatValue
import com.oreki.stumpd.ui.theme.animatedInt
import com.oreki.stumpd.ui.theme.rememberRevealState
import com.oreki.stumpd.ui.theme.revealOnFirstPaint
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import com.oreki.stumpd.ui.theme.pressScale
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MatchHistoryActivity : ComponentActivity() {
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
                    MatchHistoryScreen()
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchHistoryScreen() {
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val repo = rememberMatchRepository()
    val groupRepo = rememberGroupRepository()
    val inProgressManager = remember { InProgressMatchManager(context) }

    var allMatches by remember { mutableStateOf<List<MatchHistory>>(emptyList()) }
    var inProgressMatch by remember { mutableStateOf<MatchInProgress?>(null) }

    // Group filter state
    var groups by remember { mutableStateOf<List<GroupEntity>>(emptyList()) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var selectedGroupName by remember { mutableStateOf("Select Group") }

    // Pitch type filter
    var selectedPitchType by remember { mutableStateOf<Boolean?>(false) } // Default: Long Pitch
    var showPitchPicker by remember { mutableStateOf(false) }

    /** Plays the list's entrance once per visit, not once per row per scroll. */
    val revealState = rememberRevealState()

    /**
     * False until the first load returns. Without it the empty state renders while the query is
     * still running, so opening History flashed "No Matches Yet" every time.
     */
    var hasLoaded by remember { mutableStateOf(false) }

    /**
     * Squad size per (matchId, team). The list loads matches without their per-player stats, so
     * the wicket margin needs the squads from a separate grouped query.
     */
    var squadSizes by remember { mutableStateOf<Map<Pair<String, String>, Int>>(emptyMap()) }

    /** The match whose bin icon was tapped, awaiting confirmation (and passcode, if set). */
    var matchPendingDeletion by remember { mutableStateOf<MatchHistory?>(null) }

    // Date filter
    var selectedDateFilter by remember { mutableStateOf("All Time") }
    var showDateFilterDialog by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    /**
     * Re-read on every resume, not once per visit.
     *
     * History is the screen you come back to after correcting a match, and the row carries the
     * result and margin the correction may have just changed — so a load-once list showed the old
     * figures until the activity was destroyed. Two queries; the group defaults below stay in the
     * first-run effect so returning here doesn't undo a filter choice.
     */
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            allMatches = repo.getAllMatches(groupId = null)
            squadSizes = repo.squadSizesByMatchAndTeam()
            inProgressMatch = inProgressManager.loadMatch()
            hasLoaded = true
        }
        onPauseOrDispose { job.cancel() }
    }

    LaunchedEffect(Unit) {
        groups = groupRepo.listGroups()

        // Load default group from Room DB (auto-select handled by GroupFilterDropdown for single group)
        val defaultGroupId = groupRepo.getDefaultGroupId()
        if (defaultGroupId != null) {
            val groupName = groups.firstOrNull { it.id == defaultGroupId }?.name ?: "Select Group"
            selectedGroupId = defaultGroupId
            selectedGroupName = groupName
        }
        // Auto-select first group if none selected (GroupFilterDropdown also does this)
        if (groups.isNotEmpty() && selectedGroupId == null) {
            selectedGroupId = groups[0].id
            selectedGroupName = groups[0].name
        }

    }

    // Apply all filters
    val filteredMatches = remember(allMatches, selectedGroupId, selectedPitchType, selectedDateFilter, startDate, endDate) {
        val groupFiltered = filterMatchesByGroup(allMatches, selectedGroupId)
        val pitchFiltered = filterMatchesByPitchType(groupFiltered, selectedPitchType)
        filterMatchesByDate(pitchFiltered, selectedDateFilter, startDate, endDate)
    }

    // Calculate quick stats
    val totalMatches = filteredMatches.size
    val completedMatches = filteredMatches.count { it.winnerTeam.isNotBlank() }
    val inProgressCount = if (inProgressMatch != null) 1 else 0

    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = rememberMessenger(snackbarHostState)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StumpdTopBar(
                title = "Match History",
                subtitle = "$totalMatches matches${if (selectedGroupId != null) " in $selectedGroupName" else ""}",
                onBack = { (context as ComponentActivity).finish() },
                actions = {
                    TextButton(
                        onClick = { showDateFilterDialog = true },
                        modifier = Modifier.semantics(mergeDescendants = true) {}
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = "Date Filter")
                        Spacer(Modifier.width(4.dp))
                        Text(selectedDateFilter)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Quick Stats Card (show even if no matches for better UX)
            // Only worth the space when there is a live match to surface: the total is
            // already in the top bar subtitle ("N matches in <group>").
            if (inProgressCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = animatedInt(totalMatches).toString(),
                                style = ScoreMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "MATCHES",
                                style = MicroLabel,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        if (inProgressCount > 0) {
                            VerticalDivider(
                                modifier = Modifier.height(56.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Pending,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = animatedInt(inProgressCount).toString(),
                                    style = ScoreMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "IN PROGRESS",
                                    style = MicroLabel,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Filter row: Group + Pitch Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupFilterDropdown(
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    onGroupSelected = { id, name ->
                        selectedGroupId = id
                        selectedGroupName = name
                        scope.launch { groupRepo.setSelectedGroupId(id) }
                    },
                    modifier = Modifier.weight(1f)
                )

                val pitchLabel = when (selectedPitchType) {
                    true -> "Short"
                    false -> "Long"
                    null -> "All Pitches"
                }
                FilledTonalButton(
                    onClick = { showPitchPicker = true },
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Terrain, contentDescription = "Pitch", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(pitchLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasLoaded) {
                // Deliberately blank: the query is fast enough that a spinner would be a flash of
                // its own, and the list is about to arrive.
                Spacer(Modifier.height(0.dp))
            } else if (filteredMatches.isEmpty() && inProgressMatch == null) {
                // Modern empty state
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
                            Icons.Default.SportsScore,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Matches Yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (selectedGroupId) {
                                null -> "Start scoring to build your match history!"
                                else -> "No matches found for the selected filters."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(context, MainActivity::class.java)
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                context.startActivity(intent)
                                (context as ComponentActivity).finish()
                            },
                            modifier = Modifier.semantics(mergeDescendants = true) {}
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start match")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start a Match")
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Show in-progress match at the top
                    inProgressMatch?.let { match ->
                        item {
                            InProgressMatchCard(
                                match = match,
                                onResume = {
                                    val intent = android.content.Intent(context, ScoringActivity::class.java)
                                    intent.putExtra("resume_match_id", match.matchId)
                                    intent.putExtra("match_id", match.matchId)
                                    intent.putExtra("team1_name", match.team1Name)
                                    intent.putExtra("team2_name", match.team2Name)
                                    intent.putExtra("joker_name", match.jokerName)
                                    intent.putExtra("team1_players", match.team1PlayerNames.toTypedArray())
                                    intent.putExtra("team2_players", match.team2PlayerNames.toTypedArray())
                                    intent.putExtra("team1_player_ids", match.team1PlayerIds.toTypedArray())
                                    intent.putExtra("team2_player_ids", match.team2PlayerIds.toTypedArray())
                                    intent.putExtra("match_settings", match.matchSettingsJson)
                                    intent.putExtra("group_id", match.groupId ?: "")
                                    intent.putExtra("group_name", match.groupName ?: "")
                                    intent.putExtra("toss_winner", match.tossWinner ?: "")
                                    intent.putExtra("toss_choice", match.tossChoice ?: "")
                                    // The fixture this match is settling, so resuming it still
                                    // closes the fixture out when it finishes.
                                    match.tournamentId?.let { intent.putExtra("tournament_id", it) }
                                    match.tournamentFixtureId?.let {
                                        intent.putExtra("tournament_fixture_id", it)
                                    }
                                    match.team1Id?.let { intent.putExtra("team1_id", it) }
                                    match.team2Id?.let { intent.putExtra("team2_id", it) }
                                    context.startActivity(intent)
                                },
                                onDiscard = {
                                    scope.launch {
                                        inProgressManager.clearMatch()
                                        inProgressMatch = null
                                        messenger.show("Unfinished match discarded")
                                    }
                                }
                            )
                        }
                    }

                    // Show completed matches
                    itemsIndexed(filteredMatches, key = { _, match -> match.id }) { index, match ->
                        MatchHistoryCard(
                            match = match,
                            chasingSquadSize = squadSizes[match.id to match.team2Name],
                            onDelete = { matchPendingDeletion = match },
                            modifier = Modifier.revealOnFirstPaint(
                                index = index,
                                key = match.id,
                                state = revealState,
                            ),
                        )
                    }
                }
            }
        }
    }

    matchPendingDeletion?.let { match ->
        DeleteMatchDialog(
            match = match,
            onConfirm = {
                matchPendingDeletion = null
                scope.launch {
                    repo.deleteMatch(match.id)
                    // Reload all matches to keep filtering consistent
                    allMatches = repo.getAllMatches(groupId = null)
                }
            },
            onDismiss = { matchPendingDeletion = null },
        )
    }

    // Pitch Type Picker
    if (showPitchPicker) {
        AlertDialog(
            onDismissRequest = { showPitchPicker = false },
            title = { Text("Select Pitch Type") },
            text = {
                Column {
                    listOf(null to "All Pitches", true to "Short Pitch", false to "Long Pitch").forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPitchType = value
                                    showPitchPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPitchType == value,
                                onClick = {
                                    selectedPitchType = value
                                    showPitchPicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Date Filter Dialog
    if (showDateFilterDialog) {
        DateFilterDialog(
            currentFilter = selectedDateFilter,
            onFilterSelected = { filter, start, end ->
                selectedDateFilter = filter
                startDate = start
                endDate = end
                showDateFilterDialog = false
            },
            onDismiss = { showDateFilterDialog = false }
        )
    }
}

/**
 * One saved match in the history list.
 *
 * The card itself opens the full scorecard, which is what the two full-width buttons at the
 * bottom used to do — they cost a divider and a button row per match, and with 250+ matches only
 * two cards fitted a screen. Summary keeps its own header button so it stays discoverable.
 */
@Composable
fun MatchHistoryCard(
    match: MatchHistory,
    onDelete: () -> Unit,
    /** Players the chasing side fielded, for the wicket margin; see [matchResultLine]. */
    chasingSquadSize: Int? = null,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val context = LocalContext.current
    val isTie = match.winnerTeam.equals("TIE", true)

    fun openScorecard(tab: String? = null) {
        val intent = android.content.Intent(context, FullScorecardActivity::class.java)
        intent.putExtra("match_id", match.id)
        tab?.let { intent.putExtra("initial_tab", it) }
        context.startActivity(intent)
    }

    val interactionSource = remember { MutableInteractionSource() }

    Card(
        onClick = { openScorecard() },
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            // The card is the tap target for the whole match, so it should feel like one.
            .pressScale(interactionSource),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header: when it was played, which group, and the two per-match actions.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        dateFormat.format(Date(match.matchDate)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        timeFormat.format(Date(match.matchDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    match.groupName?.takeIf { it.isNotBlank() }?.let { gName ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                gName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { openScorecard("Summary") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Match summary",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Match",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Result, as a line rather than a full-width banner.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isTie) Icons.Default.Info else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isTie)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // Recomputed from the squads: the saved margin assumed eleven-a-side.
                    text = matchResultLine(match, chasingSquadSize),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Teams and scores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MatchCardTeamScore(
                    teamName = match.team1Name,
                    runs = match.firstInningsRuns,
                    wickets = match.firstInningsWickets,
                    alignEnd = false,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "vs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                MatchCardTeamScore(
                    teamName = match.team2Name,
                    runs = match.secondInningsRuns,
                    wickets = match.secondInningsWickets,
                    alignEnd = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Player of the match and joker share one line.
            val potm = match.playerOfTheMatchName
            val joker = match.jokerPlayerName?.takeIf { it.isNotBlank() }
            if (potm != null || joker != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (potm != null) {
                        Text(
                            text = buildString {
                                append("⭐ ")
                                append(potm)
                                match.playerOfTheMatchImpact?.let { append(" · %.1f".format(it)) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    joker?.let {
                        Text(
                            text = "🃏 $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchCardTeamScore(
    teamName: String,
    runs: Int,
    wickets: Int,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = teamName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$runs",
                // Tabular figures: the two sides' scores line up in the middle of the card
                // instead of drifting with the digit widths.
                style = ScoreMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "/$wickets",
                style = StatValue,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
fun InProgressMatchCard(
    match: MatchInProgress,
    onResume: () -> Unit,
    onDiscard: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = hairline(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with "IN PROGRESS" badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "IN PROGRESS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    match.groupName?.takeIf { it.isNotBlank() }?.let { gName ->
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                gName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDiscard,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Discard Match",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Teams and score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.team1Name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "vs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = match.team2Name,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${match.calculatedTotalRuns}/${match.totalWickets}",
                        style = ScoreMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Overs: ${match.currentOver}.${match.ballsInOver}",
                        style = StatValue,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Innings ${match.currentInnings}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Resume button
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Resume Match", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last saved: ${dateFormat.format(Date(match.lastSavedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/**
 * Confirms deleting a match, and asks for the passcode when delete protection is on.
 *
 * Deleting used to happen on a single tap of the bin icon, with nothing in between.
 */
@Composable
fun DeleteMatchDialog(
    match: MatchHistory,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val passcodeManager = remember { PasscodeManager(context) }

    var lockState by remember { mutableStateOf<Boolean?>(null) }
    var passcode by remember { mutableStateOf("") }
    var wrongPasscode by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { lockState = passcodeManager.isSet() }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val isLocked = lockState == true

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Delete this match?") },
        text = {
            Column {
                Text(
                    "${match.team1Name} vs ${match.team2Name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${dateFormat.format(Date(match.matchDate))} • ${match.firstInningsRuns}/" +
                        "${match.firstInningsWickets} vs ${match.secondInningsRuns}/" +
                        "${match.secondInningsWickets}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Its scorecard, partnerships and player stats go with it. This can't be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isLocked) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = {
                            passcode = it
                            wrongPasscode = false
                        },
                        label = { Text("Passcode") },
                        singleLine = true,
                        isError = wrongPasscode,
                        supportingText = if (wrongPasscode) {
                            { Text("That passcode doesn't match") }
                        } else null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (lockState == false) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tip: Settings → Security can ask for a passcode before any delete.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isLocked) {
                        onConfirm()
                    } else {
                        checking = true
                        scope.launch {
                            val ok = passcodeManager.verify(passcode)
                            checking = false
                            if (ok) onConfirm() else wrongPasscode = true
                        }
                    }
                },
                enabled = lockState != null && !checking && (!isLocked || passcode.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
