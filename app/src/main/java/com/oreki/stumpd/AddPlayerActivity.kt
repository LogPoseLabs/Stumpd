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
import com.oreki.stumpd.ui.theme.GroupActionsRow

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
    val groupStorage = remember { PlayerGroupStorageManager(context) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showManageGroups by remember { mutableStateOf(false) }
    var showAddToGroup by remember { mutableStateOf(false) }
    var showChooseGroup by remember { mutableStateOf(false) } // if you still need a separate picker
    var chooseGroupForMembers by remember { mutableStateOf<PlayerGroup?>(null) }
    var pendingSinglePlayer by remember { mutableStateOf<StoredPlayer?>(null) }

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
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Player Management",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }

            FloatingActionButton(
                onClick = {
                    val groups = groupStorage.getAllGroups()
                    if (groups.isEmpty()) {
                        Toast.makeText(context, "Create a group first", Toast.LENGTH_SHORT).show()
                        showCreateGroup = true
                    } else {
                        showAddDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Player",
                    tint = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GroupActionsRow(
            onNewGroup = { showCreateGroup = true },
            onManageGroups = { showManageGroups = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (successMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
            ) {
                Text(
                    text = successMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.primary,
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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

    if (showCreateGroup) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroup = false },
            title = { Text("Create Group") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name (e.g., Gully Cricket)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        groupStorage.createGroup(name.trim())
                        Toast.makeText(context, "Created '${name.trim()}'", Toast.LENGTH_SHORT).show()
                        showCreateGroup = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateGroup = false }) { Text("Cancel") } }
        )
    }

    if (showManageGroups) {
        val groups =
            remember { mutableStateListOf<PlayerGroup>().apply { addAll(groupStorage.getAllGroups()) } }
        var editing by remember { mutableStateOf<PlayerGroup?>(null) }
        var renameText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManageGroups = false },
            title = { Text("Manage Groups") },
            text = {
                if (groups.isEmpty()) {
                    Text("No groups yet. Create one first.")
                } else {
                    LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups, key = { it.id }) { g ->
                            Card {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(g.name, fontWeight = FontWeight.SemiBold)
                                        Text("${g.players.size} players", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            editing = g
                                            renameText = g.name
                                        }) { Text("Rename") }
                                        TextButton(onClick = {
                                            groupStorage.deleteGroup(g.id)
                                            groups.removeAll { it.id == g.id }
                                        }) { Text("Delete") }
                                        FilledTonalButton(onClick = {
                                            // open member editor directly
                                            chooseGroupForMembers = g
                                            showManageGroups = false
                                        }) { Text("Members") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showManageGroups = false }) { Text("Close") } }
        )

// Inline rename dialog
        if (editing != null) {
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("Rename '${editing!!.name}'") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("New name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val g = editing!!
                        if (renameText.isNotBlank()) {
                            groupStorage.renameGroup(g.id, renameText.trim())
                            val updated = groups.map { if (it.id == g.id) it.copy(name = renameText.trim()) else it }
                            groups.clear(); groups.addAll(updated)
                            editing = null
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
            )
        }
    }
    if (chooseGroupForMembers != null) {
// Load the latest group (by id) and players
        val allGroups = remember { groupStorage.getAllGroups() }
        val target = remember(chooseGroupForMembers, allGroups) {
            val picked = chooseGroupForMembers!!
            allGroups.find { it.id == picked.id } ?: run {
// If opened via "Add players to group" without picking a group yet, force choose
                null
            }
        }
        if (target == null) {
// Ask to choose a group first
            val groups = groupStorage.getAllGroups()
            AlertDialog(
                onDismissRequest = { chooseGroupForMembers = null },
                title = { Text("Choose Group") },
                text = {
                    if (groups.isEmpty()) {
                        Text("No groups found.")
                    } else {
                        LazyColumn(Modifier.height(360.dp)) {
                            items(groups) { g ->
                                ListItem(
                                    headlineContent = { Text(g.name) },
                                    supportingContent = { Text("${g.players.size} players", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    modifier = Modifier.clickable {
                                        chooseGroupForMembers = g
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { chooseGroupForMembers = null }) { Text("Close") } }
            )
        } else {
// Members editor
            val currentMembers =
                remember(target) { target.players.associateBy { it.name }.toMutableMap() }
            val all = allPlayers // from your screen state (already loaded)
            var search by remember { mutableStateOf("") }
            val list = remember(all, search) {
                if (search.isBlank()) all else all.filter { it.name.contains(search, true) }
            }
            AlertDialog(
                onDismissRequest = { chooseGroupForMembers = null },
                title = { Text("Edit '${target.name}' Members") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            singleLine = true,
                            label = { Text("Search players") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.height(360.dp)) {
                            items(list) { p ->
                                val isMember = currentMembers.containsKey(p.name)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isMember) currentMembers.remove(p.name)
                                            else currentMembers[p.name] = PlayerRef(name = p.name)
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isMember,
                                        onCheckedChange = {
                                            if (isMember) currentMembers.remove(p.name)
                                            else currentMembers[p.name] = PlayerRef(name = p.name)
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(p.name)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${currentMembers.size} members selected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val updated = target.copy(players = currentMembers.values.toList())
                        groupStorage.addPlayers(target.id, updated.players) // addPlayers merges by id; names-only is OK since we create refs
                        // To support removals, we overwrite by clearing then adding:
                        // Implement a replace function or:
                        groupStorage.deleteGroup(target.id)
                        // Recreate with same id to preserve references (workaround): better, add a replaceGroup API.
                        // Simpler: add a replace API below and use it.
                        groupStorage.replaceMembers(target.id, currentMembers.values.toList())
                        chooseGroupForMembers = null
                        Toast.makeText(context, "Saved members of '${target.name}'", Toast.LENGTH_SHORT).show()
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { chooseGroupForMembers = null }) { Text("Cancel") } }
            )
        }
    }
    if (showAddDialog) {
        AddPlayerDialog(
            initialName = playerName,
            onPlayerAdded = { name ->
                val addedPlayer = playerStorage.addOrUpdatePlayer(name)
                refreshTrigger += 1
                playerName = ""
                successMessage = "✅ Added $name to player database"
// NEW: prompt to assign to group
                val groups = groupStorage.getAllGroups()
                if (groups.isEmpty()) {
                    Toast.makeText(context, "Create a group to assign this player", Toast.LENGTH_LONG).show()
                    showCreateGroup = true
                } else {
// open a lightweight dialog to pick a group and add the single player
// Set some local state to carry the new player reference and open showAddToGroup with a filtered list = listOf(addedPlayer)
// Easiest: directly add to a chosen group via a quick single-select picker:
// Implement a small inline dialog here (see snippet below)
                    pendingSinglePlayer = addedPlayer // add state: var pendingSinglePlayer by remember { mutableStateOf<StoredPlayer?>(null) }
                    showChooseGroup = true
                }
                showAddDialog = false
            },
            onDismiss = {
                playerName = ""
                showAddDialog = false
            }
        )
    }

    if (showChooseGroup && pendingSinglePlayer != null) {
        val groups = groupStorage.getAllGroups()
        AlertDialog(
            onDismissRequest = { showChooseGroup = false; pendingSinglePlayer = null },
            title = { Text("Assign to Group") },
            text = {
                if (groups.isEmpty()) {
                    Text("No groups found. Create one first.")
                } else {
                    LazyColumn(Modifier.height(300.dp)) {
                        items(groups) { g ->
                            ListItem(
                                headlineContent = { Text(g.name) },
                                supportingContent = { Text("${g.players.size} players", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.clickable {
                                    val ref = PlayerRef(name = pendingSinglePlayer!!.name)
                                    groupStorage.addPlayers(g.id, listOf(ref))
                                    Toast.makeText(context, "Added ${pendingSinglePlayer!!.name} to '${g.name}'", Toast.LENGTH_SHORT).show()
                                    showChooseGroup = false
                                    pendingSinglePlayer = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showChooseGroup = false; pendingSinglePlayer = null }) { Text("Close") } }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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
            containerColor = if (player.matchesPlayed > 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
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
                tint = if (player.matchesPlayed > 0) MaterialTheme.colorScheme.primary else Color.Gray,
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
                        color = MaterialTheme.colorScheme.primary
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
                        tint = MaterialTheme.colorScheme.tertiary,
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
                            tint = MaterialTheme.colorScheme.error,
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
                        color = MaterialTheme.colorScheme.secondary
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
