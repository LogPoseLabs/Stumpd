package com.oreki.stumpd

import android.os.Bundle
import androidx.compose.material3.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar

class GroupManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            StumpdTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GroupManagementScreen()
                }
            }
        }
    }
}

@Composable
fun GroupManagementScreen() {
    val context = LocalContext.current
    val groupStorage = remember { PlayerGroupStorageManager(context) }
    val playerStorage = remember { PlayerStorageManager(context) }
    var groups by remember { mutableStateOf(groupStorage.getAllGroups()) }
    var showCreate by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PlayerGroup?>(null) }

    Scaffold(
        topBar = { StumpdTopBar(title = "Groups", subtitle = "Create and manage groups", onBack = { (context as ComponentActivity).finish() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, contentDescription = "Add Group") }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups.size) { idx ->
                val g = groups[idx]
                GroupCard(
                    group = g,
                    playerCount = g.playerIds.size,
                    onEdit = { editTarget = g },
                    onDelete = {
                        groupStorage.deleteGroup(g.id)
                        groups = groupStorage.getAllGroups()
                    }
                )
            }
        }
    }

    if (showCreate) {
        CreateOrEditGroupDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name, defaults ->
                groupStorage.createGroup(name, defaults)
                groups = groupStorage.getAllGroups()
                showCreate = false
            }
        )
    }
    if (editTarget != null) {
        EditGroupDialog(
            group = editTarget!!,
            allPlayers = playerStorage.getAllPlayers(),
            onUpdate = { updated ->
                // members
                groupStorage.replaceMembers(updated.id, updated.playerIds)
                // defaults
                groupStorage.updateDefaults(updated.id, updated.defaults)
                // name handled separately
                if (updated.name != editTarget!!.name) groupStorage.renameGroup(updated.id, updated.name)
                groups = groupStorage.getAllGroups()
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }
}

@Composable
private fun GroupCard(group: PlayerGroup, playerCount: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(group.name, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("$playerCount players", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun CreateOrEditGroupDialog(
    initial: PlayerGroup? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, GroupDefaultSettings) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var ground by remember { mutableStateOf(initial?.defaults?.groundName ?: "") }
    var format by remember { mutableStateOf(initial?.defaults?.format ?: BallFormat.WHITE_BALL) }
    var shortPitch by remember { mutableStateOf(initial?.defaults?.shortPitch ?: false) }
    val context = LocalContext.current
    val defaultMatchSettings = remember { MatchSettingsManager(context).getDefaultMatchSettings() }
    var matchSettings by remember { mutableStateOf(initial?.defaults?.matchSettings ?: defaultMatchSettings) }

    var totalOversText by remember(matchSettings.totalOvers) { mutableStateOf(matchSettings.totalOvers.toString()) }
    var maxPerBowlerText by remember(matchSettings.maxOversPerBowler, matchSettings.totalOvers) {
        mutableStateOf(matchSettings.maxOversPerBowler.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Group" else "Edit Group") },
        text = {
            // Bounded height so actions remain visible and content scrolls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp) // tune for device density
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = ground, onValueChange = { ground = it }, label = { Text("Ground name") })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Format")
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = format == BallFormat.WHITE_BALL,
                        onClick = { format = BallFormat.WHITE_BALL },
                        label = { Text("Limited Overs") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = format == BallFormat.RED_BALL,
                        onClick = { format = BallFormat.RED_BALL },
                        label = { Text("Test") },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Short pitch")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = shortPitch, onCheckedChange = { shortPitch = it })
                }

                Spacer(Modifier.height(12.dp))
                Text("Default match settings", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)

                SettingRowMini(
                    label = "Total overs",
                    value = totalOversText,
                    keyboard = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    onValueChange = { v ->
                        // keep UI responsive
                        totalOversText = v.filter { it.isDigit() }.take(2) // e.g., cap to 2 digits if desired
                        val ov = totalOversText.toIntOrNull()
                        if (ov != null && ov in 1..50) {
                            if (matchSettings.totalOvers != ov) {
                                matchSettings = matchSettings.copy(totalOvers = ov)
                                // clamp max/bowler if needed and sync its text mirror
                                if (matchSettings.maxOversPerBowler > ov) {
                                    matchSettings = matchSettings.copy(maxOversPerBowler = ov)
                                    maxPerBowlerText = ov.toString()
                                }
                            }
                        }
                    }
                )

                SettingRowMini(
                    label = "Max/ bowler",
                    value = maxPerBowlerText,
                    keyboard = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    onValueChange = { v ->
                        maxPerBowlerText = v.filter { it.isDigit() }.take(2)
                        val ov = maxPerBowlerText.toIntOrNull()
                        val cap = matchSettings.totalOvers
                        if (ov != null && ov in 1..cap) {
                            if (matchSettings.maxOversPerBowler != ov) {
                                matchSettings = matchSettings.copy(maxOversPerBowler = ov)
                            }
                        }
                    }
                )

                ZeroOneSegment(
                    label = "No ball (+)",
                    selected = matchSettings.noballRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(noballRuns = r) }
                )

                ZeroOneSegment(
                    label = "Wide off (+)",
                    selected = matchSettings.offSideWideRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(offSideWideRuns = r) }
                )

                ZeroOneSegment(
                    label = "Wide leg (+)",
                    selected = matchSettings.legSideWideRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(legSideWideRuns = r) }
                )

                ZeroOneSegment(
                    label = "Bye (+)",
                    selected = matchSettings.byeRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(byeRuns = r) }
                )

                ZeroOneSegment(
                    label = "Leg bye (+)",
                    selected = matchSettings.legByeRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(legByeRuns = r) }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    name.trim(),
                    GroupDefaultSettings(
                        matchSettings = matchSettings.copy(shortPitch = shortPitch),
                        groundName = ground.trim(),
                        format = format,
                        shortPitch = shortPitch
                    )
                )
            }, enabled = name.isNotBlank()) { Text(if (initial == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZeroOneSegment(
    label: String,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow {
            listOf(0, 1).forEachIndexed { index, value ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    label = { Text(value.toString()) }
                )
            }
        }
    }
}


@Composable
private fun SettingRowMini(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboard: KeyboardOptions = KeyboardOptions.Default
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboard,
            modifier = Modifier.width(96.dp)
        )
    }
    Spacer(Modifier.height(6.dp))
}



@Composable
fun EditGroupDialog(
    group: PlayerGroup,
    allPlayers: List<StoredPlayer>,
    onUpdate: (PlayerGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    // Local editable state
    var name by remember(group) { mutableStateOf(group.name) }
    var ground by remember(group) { mutableStateOf(group.defaults.groundName) }
    var format by remember(group) { mutableStateOf(group.defaults.format) }
    var shortPitch by remember(group) { mutableStateOf(group.defaults.shortPitch) }
    var memberIds by remember(group) { mutableStateOf(group.playerIds.toSet()) }

    // Simple search for players to add/remove
    var search by remember { mutableStateOf("") }
    val filtered = remember(allPlayers, search) {
        val q = search.trim()
        if (q.isEmpty()) allPlayers
        else allPlayers.filter { it.name.contains(q, ignoreCase = true) }
    }
    var matchSettings by remember(group) { mutableStateOf(group.defaults.matchSettings) }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Group") },
        text = {
            Column {
                // Basics
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = ground, onValueChange = { ground = it }, label = { Text("Ground name") })
                Spacer(Modifier.height(8.dp))

                // Format and pitch
                Text("Format", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == BallFormat.WHITE_BALL,
                        onClick = { format = BallFormat.WHITE_BALL },
                        label = { Text("White ball") },
                    )
                    FilterChip(
                        selected = format == BallFormat.RED_BALL,
                        onClick = { format = BallFormat.RED_BALL },
                        label = { Text("Red ball") },
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Short pitch")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = shortPitch, onCheckedChange = { shortPitch = it })
                }

                Spacer(Modifier.height(12.dp))
                Text("Members", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search players") }
                )

                // Member checklist
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered.size) { idx ->
                        val p = filtered[idx]
                        val selected = p.id in memberIds
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(p.name)
                                Switch(
                                    checked = selected,
                                    onCheckedChange = {
                                        memberIds = if (selected) memberIds - p.id else memberIds + p.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = group.copy(
                        name = name.trim(),
                        playerIds = memberIds.toList(),
                        defaults = group.defaults.copy(
                            groundName = ground.trim(),
                            format = format,
                            shortPitch = shortPitch,
                            matchSettings = matchSettings.copy(shortPitch = shortPitch)
                        )
                    )
                    onUpdate(updated)
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


