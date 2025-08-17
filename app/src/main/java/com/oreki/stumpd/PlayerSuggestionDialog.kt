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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playerStorage = remember { PlayerStorageManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<StoredPlayer>>(emptyList()) }
    var recentPlayers by remember { mutableStateOf<List<StoredPlayer>>(emptyList()) }

    // Load recent players on first show, filtered by selection status
    LaunchedEffect(selectedPlayers) {
        val allRecentPlayers = playerStorage.getRecentPlayers()
        recentPlayers = allRecentPlayers.filter { player ->
            !selectedPlayers.contains(player.name)
        }
    }

    // Update suggestions when search query changes, filtered by selection status
    LaunchedEffect(searchQuery, selectedPlayers) {
        suggestions = if (searchQuery.isBlank()) {
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
                    fontWeight = FontWeight.Bold
                )
                if (currentTeamName.isNotEmpty()) {
                    Text(
                        text = "Adding to: $currentTeamName",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                if (selectedPlayers.isNotEmpty()) {
                    Text(
                        text = "${selectedPlayers.size} player(s) already selected",
                        fontSize = 10.sp,
                        color = Color(0xFFFF9800)
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
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Show warning if player is already selected
                if (searchQuery.isNotBlank() && selectedPlayers.any { it.equals(searchQuery.trim(), ignoreCase = true) }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "'${searchQuery.trim()}' is already selected",
                                fontSize = 12.sp,
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Add New Player Button (shown when typing and player doesn't exist and isn't selected)
                if (searchQuery.isNotBlank() &&
                    suggestions.none { it.name.equals(searchQuery, ignoreCase = true) } &&
                    !selectedPlayers.any { it.equals(searchQuery.trim(), ignoreCase = true) }) {

                    Button(
                        onClick = {
                            playerStorage.addOrUpdatePlayer(searchQuery.trim())
                            onPlayerSelected(searchQuery.trim())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add '${searchQuery.trim()}'")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Suggestions or Recent Players
                LazyColumn(
                    modifier = Modifier.height(200.dp)
                ) {
                    if (searchQuery.isBlank()) {
                        // Show recent players when no search
                        if (recentPlayers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Recent Players (Available)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            items(recentPlayers) { player ->
                                PlayerSuggestionCard(
                                    player = player,
                                    onClick = { onPlayerSelected(player.name) }
                                )
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                                ) {
                                    Text(
                                        text = if (selectedPlayers.isEmpty()) {
                                            "No recent players found.\nType a name to add new player."
                                        } else {
                                            "All recent players are already selected.\nType a name to add new player."
                                        },
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = Color.Gray
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
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            items(suggestions) { player ->
                                PlayerSuggestionCard(
                                    player = player,
                                    onClick = { onPlayerSelected(player.name) },
                                    highlightQuery = searchQuery
                                )
                            }
                        } else {
                            // Show message when no available suggestions
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                                ) {
                                    Text(
                                        text = "No available players found matching '$searchQuery'.\nAll matching players may already be selected.",
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = Color.Gray
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
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Already Selected (${selectedPlayers.size}):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1976D2)
                                    )
                                    Text(
                                        text = selectedPlayers.joinToString(", "),
                                        fontSize = 10.sp,
                                        color = Color(0xFF1976D2),
                                        maxLines = 2
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
        }
    )
}

@Composable
fun PlayerSuggestionCard(
    player: StoredPlayer,
    onClick: () -> Unit,
    highlightQuery: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Player",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp)
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
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = player.name.substring(startIndex, endIndex),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        if (endIndex < player.name.length) {
                            Text(
                                text = player.name.substring(endIndex),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Text(
                        text = player.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (player.matchesPlayed > 0) {
                    Text(
                        text = "${player.matchesPlayed} matches • ${player.totalRuns} runs • ${player.totalWickets} wickets",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = "New player",
                        fontSize = 10.sp,
                        color = Color(0xFF4CAF50),
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            // Show match count badge or new player indicator
            if (player.matchesPlayed > 0) {
                Text(
                    text = "${player.matchesPlayed}",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New Player",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
