package com.oreki.stumpd.ui.tournament

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.domain.tournament.TournamentFormat
import com.oreki.stumpd.ui.components.GroupFilterDropdown
import com.oreki.stumpd.ui.theme.EmptyState
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.hairline
import com.oreki.stumpd.ui.theme.pressScale
import com.oreki.stumpd.utils.FeatureFlags
import com.oreki.stumpd.viewmodel.TournamentListViewModel

/**
 * The tournaments in a group.
 *
 * Scoped by the same group picker the rest of the app uses, because a tournament belongs to a
 * group — it draws its squads from that roster and inherits its match rules.
 */
@Composable
fun TournamentListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    vm: TournamentListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeletion by remember { mutableStateOf<TournamentEntity?>(null) }

    // Coming back from a tournament, its state may have moved on.
    LifecycleResumeEffect(Unit) {
        vm.reload()
        onPauseOrDispose { }
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
                title = "Tournaments",
                subtitle = vm.selectedGroup?.name?.let { "in $it" },
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (vm.canEdit) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New tournament") },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            GroupFilterDropdown(
                groups = vm.groups,
                selectedGroupId = vm.selectedGroupId,
                onGroupSelected = { id, _ -> vm.selectGroup(id) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (!vm.canEdit && vm.selectedGroup != null) {
                ReadOnlyNotice(vm.selectedGroup!!.name)
            }

            when {
                vm.loading -> Box { CircularProgressIndicator(Modifier.padding(24.dp)) }

                vm.tournaments.isEmpty() -> EmptyState(
                    icon = Icons.Default.EmojiEvents,
                    title = "No tournaments yet",
                    description = if (vm.canEdit) {
                        "Create one to set up teams, generate fixtures and keep a table."
                    } else {
                        "Whoever owns this group runs its tournaments."
                    },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(vm.tournaments, key = { it.tournamentId }) { tournament ->
                        TournamentCard(
                            tournament = tournament,
                            // Same gate as deleting a player or a group: owner, and the app's
                            // single passcode-guarded "Enable deletions" toggle in Data
                            // Management — one consistent bar for every destructive action,
                            // rather than a tournament being the one thing that skips it.
                            canDelete = vm.canEdit && FeatureFlags.isDeletionsEnabled(context),
                            onClick = { onOpen(tournament.tournamentId) },
                            onDelete = { pendingDeletion = tournament },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    pendingDeletion?.let { tournament ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Delete ${tournament.name}?") },
            text = {
                Text(
                    "The tournament, its teams and its fixtures go. The matches that were played " +
                        "stay in History — they just stop belonging to a fixture.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(tournament.tournamentId)
                    pendingDeletion = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("Keep") }
            },
        )
    }
}

/**
 * Why there is nothing to press.
 *
 * Stated rather than left as absence: a non-owner's tournament edits could never be uploaded, so
 * they would be right on this phone and invisible to everyone else.
 */
@Composable
private fun ReadOnlyNotice(groupName: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            "Read-only — $groupName's owner runs its tournaments.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun TournamentCard(
    tournament: TournamentEntity,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = hairline(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tournament.status.statusLabel(),
                    style = MicroLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tournament.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(
                        tournament.format.formatLabel(),
                        "${tournament.teamCount} teams",
                        "${tournament.squadSize} a side",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete tournament",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** A tiny box so the loading spinner can be centred without importing a layout for it. */
@Composable
private fun Box(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) { content() }
}

internal fun String.formatLabel(): String = when (this) {
    TournamentFormat.SINGLE_ROUND_ROBIN.name -> "Round robin"
    TournamentFormat.DOUBLE_ROUND_ROBIN.name -> "Double round robin"
    TournamentFormat.GROUPS_KNOCKOUT.name -> "Groups + knockout"
    TournamentFormat.KNOCKOUT.name -> "Knockout"
    else -> "Round robin"
}

internal fun String.statusLabel(): String = when (this) {
    "DRAFT" -> "SETTING UP"
    "COMPLETE" -> "FINISHED"
    else -> "IN PROGRESS"
}
