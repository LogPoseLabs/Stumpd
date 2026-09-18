package com.oreki.stumpd.ui.correction

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.oreki.stumpd.data.preferences.PasscodeManager
import com.oreki.stumpd.data.repository.MatchCorrectionRepository
import com.oreki.stumpd.domain.match.CorrectionOutcome
import com.oreki.stumpd.domain.match.DeliveryOutcome
import com.oreki.stumpd.domain.match.MatchCorrection
import com.oreki.stumpd.domain.match.effectiveRuns
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.WicketType
import com.oreki.stumpd.ui.components.PasscodePrompt
import com.oreki.stumpd.ui.theme.EmptyState
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.hairline
import com.oreki.stumpd.ui.theme.rememberMessenger
import com.oreki.stumpd.viewmodel.CorrectMatchViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Correcting a finished match: the wrong batter given out, an over credited to the wrong bowler,
 * a player who wasn't actually there, a ball recorded as the wrong thing.
 *
 * A separate screen rather than a mode of the scorecard, for three reasons: the scorecard is a
 * dense read-only surface and making its rows tappable invites mis-taps; a correction that can
 * rewrite a result deserves its own deliberate place; and an Activity boundary gives the password
 * gate something to re-lock on when the phone is handed over.
 *
 * Every change is previewed before it's written. Nothing here writes on a single tap.
 */
@AndroidEntryPoint
class CorrectMatchActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MATCH_ID = "match_id"

        /** Opens straight into one section, for the entry points on the scorecard. */
        const val EXTRA_TARGET = "target"
        const val TARGET_DISMISSALS = "dismissals"
        const val TARGET_BOWLING = "bowling"
        const val TARGET_SQUAD = "squad"
        const val TARGET_BALLS = "balls"

        fun intent(
            context: android.content.Context,
            matchId: String,
            target: String? = null,
        ): Intent = Intent(context, CorrectMatchActivity::class.java).apply {
            putExtra(EXTRA_MATCH_ID, matchId)
            target?.let { putExtra(EXTRA_TARGET, it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: ""
        val target = intent.getStringExtra(EXTRA_TARGET)

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CorrectMatchScreen(
                        matchId = matchId,
                        initialTarget = target,
                        // Set the moment something is committed, not only on the way out: the
                        // system back button never runs `onDone`, so the scorecard behind was
                        // left showing the figures the correction had just replaced.
                        onCommitted = { setResult(RESULT_OK) },
                        onDone = { anythingChanged ->
                            setResult(if (anythingChanged) RESULT_OK else RESULT_CANCELED)
                            finish()
                        },
                    )
                }
            }
        }
    }
}

private enum class Section { Menu, Dismissals, Bowling, Squad, Balls }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectMatchScreen(
    matchId: String,
    initialTarget: String? = null,
    onCommitted: () -> Unit = {},
    onDone: (anythingChanged: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val editLock = remember { PasscodeManager.matchEditLock(context) }
    val viewModel: CorrectMatchViewModel = hiltViewModel(
        creationCallback = { factory: CorrectMatchViewModel.Factory -> factory.create(matchId) }
    )

    var unlocked by remember { mutableStateOf(false) }
    var lockSet by remember { mutableStateOf<Boolean?>(null) }
    var anythingCommitted by remember { mutableStateOf(false) }
    var section by remember {
        mutableStateOf(
            when (initialTarget) {
                CorrectMatchActivity.TARGET_DISMISSALS -> Section.Dismissals
                CorrectMatchActivity.TARGET_BOWLING -> Section.Bowling
                CorrectMatchActivity.TARGET_SQUAD -> Section.Squad
                CorrectMatchActivity.TARGET_BALLS -> Section.Balls
                else -> Section.Menu
            }
        )
    }

    LaunchedEffect(Unit) { lockSet = editLock.isSet() }

    // Handing the phone over shouldn't hand over the ability to rewrite matches.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false }

    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = rememberMessenger(snackbarHostState)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StumpdTopBar(
                title = "Correct match",
                subtitle = when (val state = viewModel.uiState) {
                    is CorrectMatchViewModel.UiState.Content ->
                        "${state.match.team1Name} vs ${state.match.team2Name}"
                    else -> null
                },
                onBack = { onDone(anythingCommitted) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // The match loads before the password is asked for, so somebody who can't correct it
            // at all is told that instead of typing a password to reach a dead end.
            when (val state = viewModel.uiState) {
                is CorrectMatchViewModel.UiState.Loading -> Loading()
                is CorrectMatchViewModel.UiState.Error -> EmptyState(
                    icon = Icons.Default.Lock,
                    title = "Couldn't open that match",
                    description = state.message,
                )

                is CorrectMatchViewModel.UiState.Content -> when {
                    state.editability is MatchCorrectionRepository.Editability.NotOurs ->
                        NotOurs(
                            groupName = (state.editability as MatchCorrectionRepository.Editability.NotOurs).groupName,
                            onBack = { onDone(anythingCommitted) },
                        )

                    lockSet == null -> Loading()

                    lockSet == false -> NoPasswordYet(onBack = { onDone(anythingCommitted) })

                    !unlocked -> LockedGate(
                        onVerify = { editLock.verify(it) },
                        onUnlocked = { unlocked = true },
                        onCancel = { onDone(anythingCommitted) },
                    )

                    else -> {
                        val staged = viewModel.staged
                        val preview = viewModel.preview

                        if (staged != null) {
                            PreviewStep(
                                preview = preview,
                                committing = viewModel.committing,
                                onDiscard = viewModel::discard,
                                onApply = viewModel::commit,
                            )
                        } else {
                            SectionContent(
                                section = section,
                                state = state,
                                onSection = { section = it },
                                onStage = viewModel::stage,
                            )
                        }

                        // Committed: tell the user, including when it can't leave the phone.
                        val committed = viewModel.committed
                        LaunchedEffect(committed) {
                            val result = committed ?: return@LaunchedEffect
                            anythingCommitted = true
                            onCommitted()
                            section = Section.Menu
                            messenger.show("Correction saved.")
                            scope.launch { kickSync(context) }
                        }
                    }
                }
            }
        }
    }
}

/** Nudges the sync the same way the adopt and merge flows do, so the fix reaches the group. */
private suspend fun kickSync(context: android.content.Context) {
    runCatching {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.oreki.stumpd.data.sync.SyncWorkEntryPoint::class.java,
        )
        entryPoint.completeSyncManager().apply {
            initialize()
            syncIncrementalChanges()
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoPasswordYet(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Default.Lock,
            title = "Set a match editing password first",
            description = "Corrections rewrite a finished match, the players' career figures and " +
                "the group's rankings, so they get their own password. Settings → Security.",
            actionButton = { OutlinedButton(onClick = onBack) { Text("Go back") } },
        )
    }
}

@Composable
private fun LockedGate(
    onVerify: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    var prompting by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Default.Lock,
            title = "Corrections are password-protected",
            description = "Changing a finished match affects results, career figures and " +
                "rankings for everyone in the group.",
            actionButton = { Button(onClick = { prompting = true }) { Text("Unlock") } },
        )
    }

    if (prompting) {
        PasscodePrompt(
            title = "Enter match editing password",
            onVerify = onVerify,
            onPasscodeCorrect = {
                prompting = false
                onUnlocked()
            },
            onDismiss = {
                prompting = false
                onCancel()
            },
        )
    }
}

@Composable
private fun SectionContent(
    section: Section,
    state: CorrectMatchViewModel.UiState.Content,
    onSection: (Section) -> Unit,
    onStage: (MatchCorrection) -> Unit,
) {
    when (section) {
        Section.Menu -> CorrectionMenu(state = state, onSection = onSection)
        Section.Dismissals -> DismissalsEditor(state.match, onStage)
        Section.Bowling -> BowlingEditor(state.match, onStage)
        Section.Squad -> SquadEditor(state.match, onStage)
        Section.Balls -> BallsEditor(state.match, onStage)
    }
}

@Composable
private fun CorrectionMenu(
    state: CorrectMatchViewModel.UiState.Content,
    onSection: (Section) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MenuRow(
                title = "Who got out",
                detail = "A wicket credited to the wrong batter, or the wrong dismissal.",
                onClick = { onSection(Section.Dismissals) },
            )
        }
        item {
            MenuRow(
                title = "Which bowler",
                detail = "An over or a single ball credited to the wrong bowler.",
                onClick = { onSection(Section.Bowling) },
            )
        }
        item {
            MenuRow(
                title = "Swap a player",
                detail = "Someone played the whole match under another player's name.",
                onClick = { onSection(Section.Squad) },
            )
        }
        item {
            MenuRow(
                title = "A single ball",
                detail = "Runs or extras recorded wrongly on one delivery.",
                onClick = { onSection(Section.Balls) },
            )
        }

        if (state.log.isNotEmpty()) {
            item { CorrectionLogCard(state.log) }
        }
    }
}

@Composable
/**
 * The match belongs to a group this device doesn't own, so there is nothing to offer.
 *
 * Correcting it here would work, look right, and then be overwritten by the owner's copy at the
 * next sync — the worst of the three outcomes, because the person who made the fix would believe
 * it had stuck. So the answer is who to ask instead.
 */
private fun NotOurs(groupName: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Default.CloudOff,
            title = "Only $groupName's owner can correct this",
            description = "This phone can't upload matches for $groupName, so a correction made " +
                "here would be replaced the next time the group syncs. Ask whoever owns the " +
                "group to make it.",
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack) { Text("Go back") }
    }
}

@Composable
private fun MenuRow(title: String, detail: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = hairline(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CorrectionLogCard(log: List<MatchCorrectionRepository.CorrectionLogEntry>) {
    val format = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = hairline(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "CORRECTIONS (${log.size})".uppercase(),
                style = MicroLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            log.forEach { entry ->
                Spacer(Modifier.height(10.dp))
                entry.summary.take(3).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
                if (entry.summary.size > 3) {
                    Text(
                        "and ${entry.summary.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${format.format(Date(entry.appliedAt))} · ${entry.deviceLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── innings switch, shared by the editors ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InningsSwitch(match: MatchHistory, innings: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1 to match.team1Name, 2 to match.team2Name).forEach { (number, team) ->
            FilterChip(
                selected = innings == number,
                onClick = { onChange(number) },
                label = { Text(team, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

// ── who got out ─────────────────────────────────────────────────────────────────────────

@Composable
private fun DismissalsEditor(match: MatchHistory, onStage: (MatchCorrection) -> Unit) {
    var innings by remember { mutableStateOf(1) }
    var editing by remember { mutableStateOf<FallOfWicket?>(null) }

    val wickets = if (innings == 1) match.firstInningsFallOfWickets else match.secondInningsFallOfWickets
    val batters = if (innings == 1) match.firstInningsBatting else match.secondInningsBatting
    val bowlers = if (innings == 1) match.firstInningsBowling else match.secondInningsBowling
    val fielders = remember(match, innings) {
        val side = if (innings == 1) match.secondInningsBatting else match.firstInningsBatting
        (bowlers + side).distinctBy { it.name.lowercase() }
    }

    Column {
        InningsSwitch(match, innings) { innings = it; editing = null }
        if (wickets.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Lock,
                title = "No wickets in this innings",
                description = "There's nothing to reassign here.",
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(wickets) { wicket ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = hairline(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { editing = wicket }.padding(14.dp),
                    ) {
                        Text(
                            "Wicket ${wicket.wicketNumber} — ${wicket.batsmanName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            buildString {
                                append("at ${wicket.runs} in ${wicket.overs} ov")
                                wicket.dismissalType?.let { append(" · ${it.lowercase().replace('_', ' ')}") }
                                wicket.bowlerName?.let { append(" b $it") }
                                wicket.fielderName?.let { append(" (${it})") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    editing?.let { wicket ->
        DismissalDialog(
            wicket = wicket,
            batters = batters,
            bowlers = bowlers,
            fielders = fielders,
            onDismiss = { editing = null },
            onConfirm = { batter, type, bowler, fielder ->
                editing = null
                onStage(
                    MatchCorrection.ReassignDismissal(
                        innings = innings,
                        wicketNumber = wicket.wicketNumber,
                        outBatterName = batter,
                        dismissalType = type,
                        bowlerName = bowler,
                        fielderName = fielder,
                    )
                )
            },
        )
    }
}

@Composable
private fun DismissalDialog(
    wicket: FallOfWicket,
    batters: List<PlayerMatchStats>,
    bowlers: List<PlayerMatchStats>,
    fielders: List<PlayerMatchStats>,
    onDismiss: () -> Unit,
    onConfirm: (batter: String, type: WicketType, bowler: String?, fielder: String?) -> Unit,
) {
    var batter by remember { mutableStateOf(wicket.batsmanName) }
    var type by remember {
        mutableStateOf(
            wicket.dismissalType?.let { runCatching { WicketType.valueOf(it) }.getOrNull() }
                ?: WicketType.BOWLED
        )
    }
    var bowler by remember { mutableStateOf(wicket.bowlerName) }
    var fielder by remember { mutableStateOf(wicket.fielderName) }

    val needsBowler = type in setOf(
        WicketType.BOWLED, WicketType.CAUGHT, WicketType.LBW, WicketType.STUMPED, WicketType.HIT_WICKET,
    )
    val needsFielder = type in setOf(WicketType.CAUGHT, WicketType.STUMPED, WicketType.RUN_OUT)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wicket ${wicket.wicketNumber}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { PickerLabel("Who was out") }
                item {
                    ChoiceRow(batters.map { it.name }, batter) { batter = it }
                }
                item { PickerLabel("How") }
                item {
                    ChoiceRow(
                        WicketType.entries.map { it.name.lowercase().replace('_', ' ') },
                        type.name.lowercase().replace('_', ' '),
                    ) { chosen ->
                        type = WicketType.entries.first {
                            it.name.lowercase().replace('_', ' ') == chosen
                        }
                    }
                }
                if (needsBowler) {
                    item { PickerLabel("Bowler") }
                    item { ChoiceRow(bowlers.map { it.name }, bowler) { bowler = it } }
                }
                if (needsFielder) {
                    item { PickerLabel("Fielder") }
                    item { ChoiceRow(fielders.map { it.name } + "nobody", fielder ?: "nobody") {
                        fielder = it.takeIf { name -> name != "nobody" }
                    } }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        batter,
                        type,
                        bowler.takeIf { needsBowler },
                        fielder.takeIf { needsFielder },
                    )
                },
                enabled = batter.isNotBlank() && (!needsBowler || !bowler.isNullOrBlank()),
            ) { Text("Preview") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── which bowler ────────────────────────────────────────────────────────────────────────

@Composable
private fun BowlingEditor(match: MatchHistory, onStage: (MatchCorrection) -> Unit) {
    var innings by remember { mutableStateOf(1) }
    var editingOver by remember { mutableStateOf<Int?>(null) }

    val overs = remember(match, innings) {
        match.allDeliveries.filter { it.inning == innings }
            .groupBy { it.over }
            .toSortedMap()
    }
    val bowlers = remember(match, innings) {
        val bowling = if (innings == 1) match.firstInningsBowling else match.secondInningsBowling
        val fieldingBatting = if (innings == 1) match.secondInningsBatting else match.firstInningsBatting
        (bowling + fieldingBatting).distinctBy { it.name.lowercase() }
    }

    Column {
        InningsSwitch(match, innings) { innings = it; editingOver = null }
        if (overs.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Lock,
                title = "No ball-by-ball record",
                description = "This match wasn't scored ball by ball, so its bowling figures " +
                    "can't be moved between bowlers.",
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(overs.keys.toList()) { over ->
                val balls = overs[over].orEmpty()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = hairline(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { editingOver = over }.padding(14.dp),
                    ) {
                        Text(
                            "Over $over — ${balls.firstOrNull()?.bowlerName.orEmpty()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${balls.sumOf { it.effectiveRuns() }} runs · " +
                                balls.joinToString(" ") { it.outcome },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    editingOver?.let { over ->
        var chosen by remember(over) {
            mutableStateOf(overs[over]?.firstOrNull()?.bowlerName.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { editingOver = null },
            title = { Text("Who bowled over $over?") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { ChoiceRow(bowlers.map { it.name }, chosen) { chosen = it } }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingOver = null
                        onStage(
                            MatchCorrection.ReassignBowler(
                                innings = innings,
                                over = over,
                                ballInOver = null,
                                toBowlerName = chosen,
                            )
                        )
                    },
                    enabled = chosen.isNotBlank(),
                ) { Text("Preview") }
            },
            dismissButton = { TextButton(onClick = { editingOver = null }) { Text("Cancel") } },
        )
    }
}

// ── swap a player ───────────────────────────────────────────────────────────────────────

@Composable
private fun SquadEditor(match: MatchHistory, onStage: (MatchCorrection) -> Unit) {
    var replacing by remember { mutableStateOf<String?>(null) }

    val squad = remember(match) {
        (match.firstInningsBatting + match.firstInningsBowling +
            match.secondInningsBatting + match.secondInningsBowling)
            .distinctBy { it.name.lowercase() }
            .sortedBy { it.name }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(squad) { player ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = hairline(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { replacing = player.name }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            player.team,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    replacing?.let { from ->
        var name by remember(from) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { replacing = null },
            title = { Text("Who actually played?") },
            text = {
                Column {
                    Text(
                        "$from's batting, bowling, fielding and awards in this match will all be " +
                            "recorded against the name you enter.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Player's name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        replacing = null
                        onStage(
                            MatchCorrection.SubstitutePlayer(
                                fromName = from,
                                toPlayerId = name.trim().lowercase().replace(" ", "_"),
                                toName = name.trim(),
                            )
                        )
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Preview") }
            },
            dismissButton = { TextButton(onClick = { replacing = null }) { Text("Cancel") } },
        )
    }
}

// ── a single ball ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BallsEditor(match: MatchHistory, onStage: (MatchCorrection) -> Unit) {
    var innings by remember { mutableStateOf(1) }
    var editing by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }

    val overs = remember(match, innings) {
        match.allDeliveries.filter { it.inning == innings }.groupBy { it.over }.toSortedMap()
    }

    Column {
        InningsSwitch(match, innings) { innings = it; editing = null }
        if (overs.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Lock,
                title = "No ball-by-ball record",
                description = "This match wasn't scored ball by ball, so individual deliveries " +
                    "can't be corrected.",
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(overs.keys.toList()) { over ->
                Column {
                    Text(
                        "OVER $over",
                        style = MicroLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(overs[over].orEmpty()) { ball ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { editing = Triple(over, ball.ballInOver, ball.outcome) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(ball.outcome, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { (over, ballInOver, currentOutcome) ->
        var kind by remember(over, ballInOver) {
            mutableStateOf(DeliveryOutcome.kindOf(currentOutcome))
        }
        var runs by remember(over, ballInOver) { mutableStateOf("0") }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Ball $over.$ballInOver") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Recorded as \"$currentOutcome\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PickerLabel("What happened")
                    ChoiceRow(
                        DeliveryOutcome.Kind.entries.map { it.label() },
                        kind.label(),
                    ) { chosen ->
                        kind = DeliveryOutcome.Kind.entries.first { it.label() == chosen }
                    }
                    PickerLabel("Runs in total")
                    OutlinedTextField(
                        value = runs,
                        onValueChange = { runs = it.filter(Char::isDigit).take(2) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editing = null
                        onStage(
                            MatchCorrection.AmendBall(
                                innings = innings,
                                over = over,
                                ballInOver = ballInOver,
                                newKind = kind,
                                newTotalRuns = runs.toIntOrNull() ?: 0,
                            )
                        )
                    },
                    enabled = runs.isNotBlank(),
                ) { Text("Preview") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

private fun DeliveryOutcome.Kind.label(): String = when (this) {
    DeliveryOutcome.Kind.OFF_THE_BAT -> "off the bat"
    DeliveryOutcome.Kind.WIDE -> "wide"
    DeliveryOutcome.Kind.NO_BALL -> "no-ball"
    DeliveryOutcome.Kind.BYE -> "bye"
    DeliveryOutcome.Kind.LEG_BYE -> "leg bye"
}

// ── preview and confirm ─────────────────────────────────────────────────────────────────

@Composable
private fun PreviewStep(
    preview: CorrectionOutcome?,
    committing: Boolean,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
) {
    when (preview) {
        null -> Loading()

        is CorrectionOutcome.Rejected -> Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "That correction can't be made",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            preview.errors.forEach { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                ) {
                    Text(
                        error.message,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            OutlinedButton(onClick = onDiscard) { Text("Back") }
        }

        is CorrectionOutcome.Applied -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Check what this changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (preview.diff.resultChanged) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                    ) {
                        Text(
                            "This changes the result of the match.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            items(preview.diff.lines) { line ->
                Column {
                    Text(
                        line.scope.name.uppercase(),
                        style = MicroLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(line.text, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            if (preview.warnings.isNotEmpty()) {
                items(preview.warnings) { warning ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    ) {
                        Text(
                            warning.message,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (preview.preExistingProblems.isNotEmpty()) {
                item {
                    Text(
                        "Already in this match, and not fixed by this correction:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(preview.preExistingProblems) { problem ->
                    Text(
                        "• ${problem.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApply, enabled = !committing) {
                        if (committing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Apply correction")
                        }
                    }
                    OutlinedButton(onClick = onDiscard, enabled = !committing) { Text("Discard") }
                }
            }
        }
    }
}

@Composable
private fun PickerLabel(text: String) {
    Text(
        text.uppercase(),
        style = MicroLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A wrapping row of choices, used wherever a correction needs one value out of a short list. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.equals(selected, ignoreCase = true),
                onClick = { onSelect(option) },
                label = { Text(option, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}
