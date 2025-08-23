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
    selectedPlayers: List<String> = emptyList(), // NEW: List of already selected player names
    currentTeamName: String = "", // NEW: Current team being set up
    onPlayerSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerStorage = remember { PlayerStorageManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<StoredPlayer>>(emptyList()) }
    var recentPlayers by remember { mutableStateOf<List<StoredPlayer>>(emptyList()) }

    // Load recent players on first show, filtered by selection status
    LaunchedEffect(selectedPlayers) {
        val allRecentPlayers = playerStorage.getRecentPlayers()
        recentPlayers =
            allRecentPlayers.filter { player ->
                !selectedPlayers.contains(player.name)
            }
    }

    // Update suggestions when search query changes, filtered by selection status
    LaunchedEffect(searchQuery, selectedPlayers) {
        suggestions =
            if (searchQuery.isBlank()) {
                emptyList()
            } else {
                playerStorage.searchPlayers(searchQuery).filter { player ->
                    !selectedPlayers.contains(player.name)
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (currentTeamName.isNotEmpty()) {
                    Text(
                        text = "Adding to: $currentTeamName",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                }
                if (selectedPlayers.isNotEmpty()) {
                    Text(
                        text = "${selectedPlayers.size} player(s) already selected",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
        text = {
            Column {
                // Search/Add Player Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Player Name") },
                    placeholder = { Text("Type to search or add new player") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "Player")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Show warning if player is already selected
                if (searchQuery.isNotBlank() && selectedPlayers.any { it.equals(searchQuery.trim(), ignoreCase = true) }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "'${searchQuery.trim()}' is already selected",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Add New Player Button (shown when typing and player doesn't exist and isn't selected)
                if (searchQuery.isNotBlank() &&
                    suggestions.none { it.name.equals(searchQuery, ignoreCase = true) } &&
                    !selectedPlayers.any { it.equals(searchQuery.trim(), ignoreCase = true) }
                ) {
                    Button(
                        onClick = {
                            playerStorage.addOrUpdatePlayer(searchQuery.trim())
                            onPlayerSelected(searchQuery.trim())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add '${searchQuery.trim()}'")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Suggestions or Recent Players
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                ) {
                    if (searchQuery.isBlank()) {
                        // Show recent players when no search
                        if (recentPlayers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Recent Players (Available)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }

                            items(recentPlayers) { player ->
                                PlayerSuggestionCard(
                                    player = player,
                                    onClick = { onPlayerSelected(player.name) },
                                )
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    Text(
                                        text =
                                            if (selectedPlayers.isEmpty()) {
                                                "No recent players found.\nType a name to add new player."
                                            } else {
                                                "All recent players are already selected.\nType a name to add new player."
                                            },
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                    )
                                }
                            }
                        }
                    } else {
                        // Show search suggestions
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
                                    onClick = { onPlayerSelected(player.name) },
                                    highlightQuery = searchQuery,
                                )
                            }
                        } else {
                            // Show message when no available suggestions
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

                    // Show selected players count at bottom
                    if (selectedPlayers.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Already Selected (${selectedPlayers.size}):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1976D2),
                                    )
                                    Text(
                                        text = selectedPlayers.joinToString(", "),
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
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
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
    occupiedNames: Set<String>,
    preselected: Set<String> = emptySet(),
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerStorage = remember { PlayerStorageManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<StoredPlayer>()) }
    var recentPlayers by remember { mutableStateOf(emptyList<StoredPlayer>()) }
    var selected by remember { mutableStateOf(preselected) }

    // Load recent players (excluding occupied)
    LaunchedEffect(occupiedNames) {
        recentPlayers = playerStorage
            .getRecentPlayers()
            .filter { it.name !in occupiedNames }
    }

    // Update suggestions as user types (excluding occupied)
    LaunchedEffect(searchQuery, occupiedNames) {
        suggestions =
            if (searchQuery.isBlank()) emptyList()
            else playerStorage.searchPlayers(searchQuery)
                .filter { it.name !in occupiedNames }
    }

    fun toggle(name: String) {
        selected = if (name in selected) selected - name else selected + name
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                // Search / add
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

                // If typing a new unique name: quick add chip
                val canQuickAdd = searchQuery.isNotBlank()
                        && suggestions.none { it.name.equals(searchQuery, true) }
                        && (searchQuery !in occupiedNames)
                if (canQuickAdd) {
                    FilledTonalButton(
                        onClick = {
                            val cleaned = searchQuery.trim()
                            playerStorage.addOrUpdatePlayer(cleaned)
                            toggle(cleaned)
                            searchQuery = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add '$searchQuery'")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // List
                LazyColumn(Modifier.height(240.dp)) {
                    if (searchQuery.isBlank()) {
                        if (recentPlayers.isNotEmpty()) {
                            item {
                                Text(
                                    "Recent Players (Available)",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(recentPlayers) { player ->
                                MultiSelectRow(
                                    name = player.name,
                                    checked = player.name in selected,
                                    onToggle = { toggle(player.name) }
                                )
                            }
                        } else {
                            item {
                                InfoCard("No recent players found.\nType a name to add new player.")
                            }
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
                                    name = player.name,
                                    checked = player.name in selected,
                                    onToggle = { toggle(player.name) },
                                    highlightQuery = searchQuery
                                )
                            }
                        } else {
                            item {
                                InfoCard("No available players matching '$searchQuery'.")
                            }
                        }
                    }
                }

                if (selected.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("Selected: ${selected.size}") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty()
            ) { Text("Add (${selected.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MultiSelectRow(
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
            // Simple highlighter like your single-select dialog
            if (highlightQuery.isNotEmpty() && name.contains(highlightQuery, true)) {
                val start = name.indexOf(highlightQuery, ignoreCase = true)
                val end = start + highlightQuery.length
                Row {
                    if (start > 0) Text(name.substring(0, start))
                    Text(
                        name.substring(start, end),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
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
