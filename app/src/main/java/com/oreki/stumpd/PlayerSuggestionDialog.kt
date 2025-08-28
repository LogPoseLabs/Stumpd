package com.oreki.stumpd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerSuggestionDialog(
    title: String,
    selectedPlayers: List<String> = emptyList(), // selected playerIds
    currentTeamName: String = "",
    allowedPlayerIds: Set<String> = emptySet(), // NEW: group scope
    onPlayerSelected: (String) -> Unit,  // returns playerId
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerStorage = remember { PlayerStorageManager(context) }
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<StoredPlayer>()) }
    var recentPlayers by remember { mutableStateOf(emptyList<StoredPlayer>()) }
    val all = remember { playerStorage.getAllPlayers().associateBy { it.id } }

    // Only players in allowedPlayerIds and not already selected
    LaunchedEffect(selectedPlayers, allowedPlayerIds) {
        val allow = if (allowedPlayerIds.isEmpty()) {
            // if no group selected, show none
            emptySet()
        } else allowedPlayerIds
        val allRecent = playerStorage.getAllPlayers()
        recentPlayers = allRecent
            .filter { it.id in allow }
            .filter { it.id !in selectedPlayers.toSet() }
    }

    LaunchedEffect(searchQuery, selectedPlayers, allowedPlayerIds) {
        val allow = if (allowedPlayerIds.isEmpty()) emptySet() else allowedPlayerIds
        suggestions =
            if (searchQuery.isBlank()) emptyList()
            else playerStorage.searchPlayers(searchQuery)
                .filter { it.id in allow }
                .filter { it.id !in selectedPlayers.toSet() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (currentTeamName.isNotEmpty()) {
                    Text(text = "Adding to: $currentTeamName", fontSize = 12.sp, color = Color.Gray)
                }
                if (selectedPlayers.isNotEmpty()) {
                    Text(
                        text = "${selectedPlayers.size} player(s) already selected",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        text = {
            Column {
                // Search & add
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Player Name") },
                    placeholder = { Text("Type to search or add new player") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Player") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                )

                Spacer(Modifier.height(12.dp))

                // Already selected warning by id is not useful when searching a name; remove the name-based warning

                // Add new player: allow only if allowedPlayerIds is empty (no group) or will belong to group after creation
                val canQuickAdd = searchQuery.isNotBlank() &&
                        suggestions.none { it.name.equals(searchQuery, ignoreCase = true) } &&
                        searchQuery.trim().isNotEmpty()

                if (canQuickAdd) {
                    Button(
                        onClick = {
                            val added = playerStorage.addOrUpdatePlayer(searchQuery.trim())
                            // Only allow select if group allows it
                            if (allowedPlayerIds.isEmpty() || added.id in allowedPlayerIds) {
                                onPlayerSelected(added.id)
                            } else {
                                // ignore if not permitted by group
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(Modifier.width(8.dp))
                        Text("Add '${searchQuery.trim()}'")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Results list
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    if (searchQuery.isBlank()) {
                        if (recentPlayers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Available Players", // title change
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(recentPlayers) { player ->
                                PlayerSuggestionCard(
                                    player = player,
                                    onClick = { onPlayerSelected(player.id) }
                                )
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    Text(
                                        text = if (selectedPlayers.isEmpty())
                                            "No players available for selection.\nType a name to add new player."
                                        else
                                            "All the players are already part of a team.\nType a name to add new player.",
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                    )
                                }
                            }
                        }
                    } else {
                        if (suggestions.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Available Players",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(suggestions) { player ->
                                PlayerSuggestionCard(
                                    player = player,
                                    onClick = { onPlayerSelected(player.id) },
                                    highlightQuery = searchQuery
                                )
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    Text(
                                        text = "No available players found matching '$searchQuery'.\nAll matching players may already be selected.",
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                    )
                                }
                            }
                        }
                    }

                    // Selected summary
                    if (selectedPlayers.isNotEmpty()) {
                        val selectedNames = selectedPlayers.mapNotNull { all[it]?.name }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Already Selected (${selectedPlayers.size}):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1976D2),
                                    )
                                    Text(
                                        text = selectedNames.joinToString(", "),
                                        fontSize = 10.sp,
                                        color = Color(0xFF1976D2),
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlayerSuggestionCard(
    player: StoredPlayer,
    onClick: () -> Unit,
    highlightQuery: String = "",
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Player",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Highlight matching text if search query exists
                if (highlightQuery.isNotEmpty() && player.name.contains(highlightQuery, ignoreCase = true)) {
                    val startIndex = player.name.indexOf(highlightQuery, ignoreCase = true)
                    val endIndex = startIndex + highlightQuery.length

                    Row {
                        if (startIndex > 0) {
                            Text(
                                text = player.name.substring(0, startIndex),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Text(
                            text = player.name.substring(startIndex, endIndex),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (endIndex < player.name.length) {
                            Text(
                                text = player.name.substring(endIndex),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    Text(
                        text = player.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (player.matchesPlayed > 0) {
                    Text(
                        text = "${player.matchesPlayed} matches • ${player.totalRuns} runs • ${player.totalWickets} wickets",
                        fontSize = 10.sp,
                        color = Color.Gray,
                    )
                } else {
                    Text(
                        text = "New player",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }

            // Show match count badge or new player indicator
            if (player.matchesPlayed > 0) {
                Text(
                    text = "${player.matchesPlayed}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New Player",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}


@Composable
fun PlayerMultiSelectDialog(
    title: String,
    occupiedIds: Set<String>,
    preselectedIds: Set<String> = emptySet(),
    allowedPlayerIds: Set<String> = emptySet(), // NEW
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerStorage = remember { PlayerStorageManager(context) }
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<StoredPlayer>()) }
    var recentPlayers by remember { mutableStateOf(emptyList<StoredPlayer>()) }
    var selected by remember { mutableStateOf(preselectedIds) }

    val allowed = remember(allowedPlayerIds) { allowedPlayerIds.toSet() }

    LaunchedEffect(occupiedIds, allowed) {
        recentPlayers = playerStorage.getAllPlayers()
            .filter { it.id !in occupiedIds }
            .filter { allowed.isEmpty() || it.id in allowed }
    }
    LaunchedEffect(searchQuery, occupiedIds, allowed) {
        suggestions =
            if (searchQuery.isBlank()) emptyList()
            else playerStorage.searchPlayers(searchQuery)
                .filter { it.id !in occupiedIds }
                .filter { allowed.isEmpty() || it.id in allowed }
    }

    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Player Name") },
                    placeholder = { Text("Type to search or add new player") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                val canQuickAdd = searchQuery.isNotBlank() &&
                        suggestions.none { it.name.equals(searchQuery, true) }

                if (canQuickAdd) {
                    FilledTonalButton(
                        onClick = {
                            val cleaned = searchQuery.trim()
                            val sp = playerStorage.addOrUpdatePlayer(cleaned)
                            if (allowed.isEmpty() || sp.id in allowed) {
                                toggle(sp.id)
                            }
                            searchQuery = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add '$searchQuery'")
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.height(240.dp)) {
                    if (searchQuery.isBlank()) {
                        if (recentPlayers.isNotEmpty()) {
                            item {
                                Text(
                                    "Available Players",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(recentPlayers) { player ->
                                MultiSelectRow(
                                    id = player.id,                            // id
                                    name = player.name,
                                    checked = player.id in selected,          // check by id
                                    onToggle = { toggle(player.id) },         // toggle by id
                                )
                            }
                        } else {
                            item { InfoCard("No available players.\nType a name to add new player.") }
                        }
                    } else {
                        if (suggestions.isNotEmpty()) {
                            item {
                                Text(
                                    "Available Players",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(suggestions) { player ->
                                MultiSelectRow(
                                    id = player.id,
                                    name = player.name,
                                    checked = player.id in selected,
                                    onToggle = { toggle(player.id) },
                                    highlightQuery = searchQuery
                                )
                            }
                        } else {
                            item { InfoCard("No available players matching '$searchQuery'.") }
                        }
                    }

                    if (selected.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            AssistChip(onClick = {}, label = { Text("Selected: ${selected.size}") })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text("Add (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MultiSelectRow(
    id: String,
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
    highlightQuery: String = ""
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onToggle() }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            if (highlightQuery.isNotEmpty() && name.contains(highlightQuery, true)) {
                val start = name.indexOf(highlightQuery, ignoreCase = true)
                val end = start + highlightQuery.length
                Row {
                    if (start > 0) Text(name.substring(0, start))
                    Text(name.substring(start, end), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (end < name.length) Text(name.substring(end))
                }
            } else {
                Text(name)
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
