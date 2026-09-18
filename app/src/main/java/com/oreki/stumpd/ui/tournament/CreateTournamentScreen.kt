package com.oreki.stumpd.ui.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oreki.stumpd.domain.tournament.MAX_SQUAD
import com.oreki.stumpd.domain.tournament.MAX_TEAMS
import com.oreki.stumpd.domain.tournament.TournamentFormat
import com.oreki.stumpd.domain.tournament.planFixtures
import com.oreki.stumpd.domain.tournament.TeamRef
import com.oreki.stumpd.domain.tournament.validateSetup
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.PrimaryCta
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.hairline
import com.oreki.stumpd.viewmodel.TournamentListViewModel

/**
 * Setting up a tournament: a name, a format, how many teams and how many a side.
 *
 * The team count and squad size are asked rather than assumed because real games vary — the
 * matches in this app run anywhere from two a side to ten. The fixture count is shown live, since
 * "six teams, round robin" meaning fifteen matches is the kind of thing worth knowing *before*
 * committing to it rather than after.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTournamentScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    vm: TournamentListViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(TournamentFormat.SINGLE_ROUND_ROBIN) }
    var teamCount by remember { mutableStateOf(4) }
    var squadSize by remember { mutableStateOf(6) }
    var poolCount by remember { mutableStateOf(2) }
    var advancePerPool by remember { mutableStateOf(2) }

    val needsPools = format == TournamentFormat.GROUPS_KNOCKOUT
    val problem = validateSetup(
        format = format,
        teamCount = teamCount,
        squadSize = squadSize,
        poolCount = if (needsPools) poolCount else 0,
        advancePerPool = if (needsPools) advancePerPool else 0,
    )

    /** What the schedule will look like, worked out from the same code that will generate it. */
    val plannedCount = remember(format, teamCount, poolCount, advancePerPool, problem) {
        if (problem != null) 0
        else planFixtures(
            format = format,
            teams = (1..teamCount).map { TeamRef("t$it", it, "Team $it") },
            poolCount = if (needsPools) poolCount else 0,
            advancePerPool = if (needsPools) advancePerPool else 0,
        ).count { !it.isBye }
    }

    vm.problem?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            vm.problem = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StumpdTopBar(
                title = "New tournament",
                subtitle = vm.selectedGroup?.name?.let { "in $it" },
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Sunday Cup") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SetupCard("Format") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TournamentFormat.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == format,
                            onClick = { format = candidate },
                            label = { Text(candidate.name.formatLabel()) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    format.explain(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SetupCard("Teams and squads") {
                Stepper(
                    label = "Teams",
                    value = teamCount,
                    range = 2..MAX_TEAMS,
                    onChange = { teamCount = it },
                )
                Stepper(
                    label = "Players a side",
                    value = squadSize,
                    range = 2..MAX_SQUAD,
                    onChange = { squadSize = it },
                )
                if (needsPools) {
                    Stepper(
                        label = "Groups",
                        value = poolCount,
                        range = 2..(teamCount / 2).coerceAtLeast(2),
                        onChange = { poolCount = it },
                    )
                    Stepper(
                        label = "Through from each group",
                        value = advancePerPool,
                        range = 1..(teamCount / poolCount).coerceAtLeast(1),
                        onChange = { advancePerPool = it },
                    )
                }
            }

            // Said before committing, not after: the difference between six teams and eight is
            // fifteen matches and twenty-eight.
            Text(
                text = problem?.message
                    ?: "$plannedCount ${if (plannedCount == 1) "match" else "matches"} to play",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (problem != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )

            PrimaryCta(
                text = "Create",
                enabled = problem == null && name.isNotBlank(),
                onClick = {
                    vm.create(
                        name = name,
                        format = format,
                        teamCount = teamCount,
                        squadSize = squadSize,
                        poolCount = if (needsPools) poolCount else 0,
                        advancePerPool = if (needsPools) advancePerPool else 0,
                        onCreated = onCreated,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Match rules come from the group and are fixed for the whole tournament, so the " +
                    "table compares like with like.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SetupCard(title: String, content: ColumnContent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = hairline(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title.uppercase(),
                style = MicroLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

private typealias ColumnContent = @Composable () -> Unit

/** A number the user picks rather than types: fewer ways to get it wrong. */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
        ) {
            Icon(Icons.Default.Remove, contentDescription = "One fewer $label")
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = { onChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
        ) {
            Icon(Icons.Default.Add, contentDescription = "One more $label")
        }
    }
}

private fun TournamentFormat.explain(): String = when (this) {
    TournamentFormat.SINGLE_ROUND_ROBIN -> "Everyone plays everyone once."
    TournamentFormat.DOUBLE_ROUND_ROBIN -> "Everyone plays everyone twice, once each way round."
    TournamentFormat.GROUPS_KNOCKOUT ->
        "Groups play among themselves, then the top teams meet in a knockout."
    TournamentFormat.KNOCKOUT -> "Straight elimination. An odd field gives the top seeds a bye."
}
