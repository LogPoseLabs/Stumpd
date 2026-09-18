package com.oreki.stumpd.ui.tournament

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.oreki.stumpd.PlayerMultiSelectDialog
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.PrimaryCta
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.hairline

/**
 * One team: its name, its squad and its captain.
 *
 * Edited in isolation but saved with the rest, because the repository validates the whole field at
 * once — duplicate names and a player in two squads are only visible across teams. Saving one team
 * therefore sends every team, with this one's changes applied.
 */
@Composable
fun TeamSquadScreen(
    tournamentId: String,
    teamId: String,
    onBack: () -> Unit,
) {
    val vm = tournamentViewModel(tournamentId)
    val snackbarHostState = remember { SnackbarHostState() }
    var showPicker by remember { mutableStateOf(false) }

    val bundle = vm.bundle
    val team = bundle?.teams?.firstOrNull { it.teamId == teamId }

    // Seeded from storage once the bundle arrives, then owned by the screen until saved.
    var name by remember(team?.teamId) { mutableStateOf(team?.name.orEmpty()) }
    var squad by remember(team?.teamId) { mutableStateOf(vm.squadOf(teamId)) }
    var captain by remember(team?.teamId) { mutableStateOf(team?.captainPlayerId) }

    // A captain must come from the squad; dropping them from it drops the armband too.
    if (captain != null && captain !in squad) captain = null

    vm.problem?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            vm.problem = null
        }
    }

    val squadSize = bundle?.tournament?.squadSize ?: 0
    val complete = name.isNotBlank() && squad.size == squadSize && captain != null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StumpdTopBar(
                title = team?.name ?: "Team",
                subtitle = bundle?.tournament?.name,
                onBack = onBack,
            )
        },
    ) { padding ->
        if (bundle == null || team == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (vm.loading) CircularProgressIndicator() else Text("Team not found")
            }
            return@Scaffold
        }

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
                label = { Text("Team name") },
                singleLine = true,
                enabled = vm.canEdit && !vm.hasPlayedFixtures,
                supportingText = if (vm.hasPlayedFixtures) {
                    { Text("Locked — results are recorded against this name.") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = hairline(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SQUAD ${squad.size}/$squadSize",
                            style = MicroLabel,
                            color = if (squad.size == squadSize) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (vm.canEdit) {
                            OutlinedButton(onClick = { showPicker = true }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(if (squad.isEmpty()) "Pick players" else "Change")
                            }
                        }
                    }

                    if (squad.isEmpty()) {
                        Text(
                            "Nobody picked yet. Squads come from the group's roster.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Tap a player to make them captain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        squad.forEach { playerId ->
                            SquadMemberRow(
                                name = vm.nameOf(playerId).ifBlank { "Unknown player" },
                                isCaptain = playerId == captain,
                                enabled = vm.canEdit,
                                onPick = { captain = playerId },
                            )
                        }
                    }
                }
            }

            if (vm.canEdit) {
                PrimaryCta(
                    text = "Save team",
                    enabled = complete,
                    onClick = {
                        // The whole field is sent, with this team's edits applied: the rules that
                        // matter are cross-team, so they can only be checked together.
                        vm.saveTeams(
                            vm.draftsWith(teamId, name, squad, captain),
                            onSaved = onBack,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!complete) {
                    Text(
                        when {
                            name.isBlank() -> "Give the team a name."
                            squad.size != squadSize -> "Pick exactly $squadSize players."
                            else -> "Choose a captain."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        PlayerMultiSelectDialog(
            title = "Squad for ${name.ifBlank { team?.name.orEmpty() }}",
            // Availability is a match-day thing, so the whole roster is offered; players already
            // in another squad are shown as taken rather than hidden.
            occupiedIds = vm.playersTakenExcept(teamId),
            preselectedIds = squad.toSet(),
            allowedPlayerIds = vm.rosterIds,
            onConfirm = { picked ->
                squad = picked.toList()
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun SquadMemberRow(
    name: String,
    isCaptain: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onPick() } else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isCaptain, onClick = onPick, enabled = enabled)
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCaptain) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (isCaptain) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "Captain",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
