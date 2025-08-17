package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.oreki.stumpd.ui.theme.StumpdTheme
import android.widget.Toast

class AddPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AddPlayerScreen()
                }
            }
        }
    }
}

@Composable
fun AddPlayerScreen() {
    val context = LocalContext.current
    val playerStorage = remember { PlayerStorageManager(context) }

    var playerName by remember { mutableStateOf("") }
    var allPlayers by remember { mutableStateOf<List<StoredPlayer>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var playerToDelete by remember { mutableStateOf<StoredPlayer?>(null) }
    var successMessage by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load players with force sync
    LaunchedEffect(refreshTrigger) {
        playerStorage.forceSyncWithMatches() // Force sync first
        allPlayers = playerStorage.getAllPlayers()
    }

    val filteredPlayers = remember(allPlayers, searchQuery) {
        if (searchQuery.isBlank()) {
            allPlayers.sortedByDescending { it.lastPlayed }
        } else {
            allPlayers.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }.sortedByDescending { it.matchesPlayed }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = Color(0xFF2E7D32)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Player Management",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "${allPlayers.size} players in database",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            // Refresh button
            IconButton(
                onClick = {
                    refreshTrigger += 1
                    successMessage = "🔄 Player data refreshed"
                }
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF2196F3)
                )
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF4CAF50),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Player",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (successMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
            ) {
                Text(
                    text = successMessage,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }

            LaunchedEffect(successMessage) {
                kotlinx.coroutines.delay(3000)
                successMessage = ""
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search players") },
            placeholder = { Text("Type player name...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (allPlayers.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Total Players",
                    value = allPlayers.size.toString(),
                    icon = Icons.Default.Person,
                    modifier = Modifier.weight(1f)
                )

                StatCard(
                    title = "Active Players",
                    value = allPlayers.count { it.matchesPlayed > 0 }.toString(),
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f)
                )

                StatCard(
                    title = "New Players",
                    value = allPlayers.count { it.matchesPlayed == 0 }.toString(),
                    icon = Icons.Default.Add,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredPlayers.isEmpty()) {
            if (allPlayers.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "👥", fontSize = 48.sp)
                        Text(
                            text = "No players added yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Add your first player to get started!",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add First Player")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🔍", fontSize = 32.sp)
                        Text(
                            text = "No players found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Try a different search term",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "All Players" else "Search Results",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${filteredPlayers.size} player${if (filteredPlayers.size != 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPlayers) { player ->
                    PlayerManagementCard(
                        player = player,
                        onEdit = {
                            playerName = player.name
                            showAddDialog = true
                        },
                        onDelete = {
                            playerToDelete = player
                            showDeleteDialog = true
                        },
                        onViewDetails = {
                            val intent = Intent(context, PlayerDetailActivity::class.java)
                            intent.putExtra("player_name", player.name)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlayerDialog(
            initialName = playerName,
            onPlayerAdded = { name ->
                val addedPlayer = playerStorage.addOrUpdatePlayer(name)
                refreshTrigger += 1 // Trigger refresh
                playerName = ""
                successMessage = "✅ Added ${name} to player database"
                showAddDialog = false
            },
            onDismiss = {
                playerName = ""
                showAddDialog = false
            }
        )
    }

    if (showDeleteDialog && playerToDelete != null) {
        DeletePlayerDialog(
            player = playerToDelete!!,
            onConfirm = {
                playerStorage.deletePlayer(playerToDelete!!.id)
                refreshTrigger += 1 // Trigger refresh
                successMessage = "🗑️ Removed ${playerToDelete!!.name}"
                playerToDelete = null
                showDeleteDialog = false
            },
            onDismiss = {
                playerToDelete = null
                showDeleteDialog = false
            }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )

            Text(
                text = title,
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PlayerManagementCard(
    player: StoredPlayer,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewDetails: () -> Unit
) {
    // Add debug logging
    LaunchedEffect(player) {
        android.util.Log.d("PlayerCard", "Player: ${player.name}, Matches: ${player.matchesPlayed}, Runs: ${player.totalRuns}, Wickets: ${player.totalWickets}")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        colors = CardDefaults.cardColors(
            containerColor = if (player.matchesPlayed > 0) Color.White else Color(0xFFF8F9FA)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Player",
                tint = if (player.matchesPlayed > 0) Color(0xFF2E7D32) else Color.Gray,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                if (player.matchesPlayed > 0) {
                    Text(
                        text = "${player.matchesPlayed} matches • ${player.totalRuns} runs • ${player.totalWickets} wickets",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Text(
                        text = "Last played: ${formatDate(player.lastPlayed)}",
                        fontSize = 10.sp,
                        color = Color(0xFF4CAF50)
                    )
                } else {
                    Text(
                        text = "New player - hasn't played yet",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Player",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (player.matchesPlayed == 0) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Player",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun AddPlayerDialog(
    initialName: String = "",
    onPlayerAdded: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var playerName by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialName.isBlank()) "Add New Player" else "Edit Player",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Player Name") },
                    placeholder = { Text("Enter player name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "Player")
                    },
                    singleLine = true
                )

                if (initialName.isNotBlank() && playerName != initialName) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: This will update the player's name in all records",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playerName.trim().isNotBlank()) {
                        onPlayerAdded(playerName.trim())
                    }
                },
                enabled = playerName.trim().isNotBlank() && playerName.trim() != initialName,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text(if (initialName.isBlank()) "Add Player" else "Update Player")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeletePlayerDialog(
    player: StoredPlayer,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Player?") },
        text = {
            Text("Are you sure you want to delete ${player.name}? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
