package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import com.oreki.stumpd.utils.FeatureFlags
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.oreki.stumpd.ui.theme.StumpdTopBar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.ui.history.rememberGroupRepository
import kotlinx.coroutines.launch

class AddPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlayerScreen() {
    val context = LocalContext.current
    val playerRepo = rememberPlayerRepository()
    val groupRepo = rememberGroupRepository()
    var playerName by remember { mutableStateOf("") }
    var allPlayers by remember { mutableStateOf<List<UiPlayer>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordDialogMode by remember { mutableStateOf<String>("edit") } // "edit" or "delete"
    var playerToEdit by remember { mutableStateOf<UiPlayer?>(null) }
    var playerToDelete by remember { mutableStateOf<UiPlayer?>(null) }
    var successMessage by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    
    // Group management
    var allGroups by remember { mutableStateOf<List<com.oreki.stumpd.data.local.entity.GroupEntity>>(emptyList()) }
    var playerGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Password management
    val prefs = remember { context.getSharedPreferences("stumpd_prefs", android.content.Context.MODE_PRIVATE) }
    var hasPassword by remember { mutableStateOf(!prefs.getString("edit_password", "").isNullOrBlank()) }
    // Note: Password is still in SharedPreferences for now - can be migrated to Room DB later if needed
    
    // Snackbar for success messages
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(successMessage) {
        if (successMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(
                message = successMessage,
                duration = SnackbarDuration.Short
            )
            successMessage = ""
        }
    }
    
    // Group filter for player list
    // null = All Players, "__ALL_GROUPS__" = players in any group, "__NONE__" = players not in any group
    var selectedFilterGroupId by remember { mutableStateOf<String?>(null) }
    var showGroupFilterPicker by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger, selectedFilterGroupId) {
        // Load players based on group filter
        val allDbPlayers = playerRepo.getAllPlayers()
        allPlayers = when (selectedFilterGroupId) {
            null -> {
                // "All Players" view - show everyone
                allDbPlayers.map { UiPlayer(id = it.id, name = it.name) }
            }
            "__ALL_GROUPS__" -> {
                // "All Groups" view - show players who are members of ALL groups (intersection)
                if (allGroups.isEmpty()) {
                    // No groups exist, show no players
                    emptyList()
                } else {
                    val playerGroupsMap = allDbPlayers.associateWith { player ->
                        groupRepo.getGroupsForPlayer(player.id).map { it.id }.toSet()
                    }
                    val allGroupIds = allGroups.map { it.id }.toSet()
                    allDbPlayers.filter { player ->
                        val playerGroups = playerGroupsMap[player] ?: emptySet()
                        // Player must be in ALL groups
                        playerGroups.containsAll(allGroupIds)
                    }.map { UiPlayer(id = it.id, name = it.name) }
                }
            }
            "__NONE__" -> {
                // "None" view - show players not in ANY group
                val playerGroupsMap = allDbPlayers.associateWith { player ->
                    groupRepo.getGroupsForPlayer(player.id)
                }
                allDbPlayers.filter { player ->
                    val playerGroups = playerGroupsMap[player]
                    playerGroups.isNullOrEmpty()
                }.map { UiPlayer(id = it.id, name = it.name) }
            }
            else -> {
                // Specific group view - show only players in this group
            val playerGroupsMap = allDbPlayers.associateWith { player ->
                groupRepo.getGroupsForPlayer(player.id).map { it.id }
            }
            allDbPlayers.filter { player ->
                val playerGroups = playerGroupsMap[player]
                !playerGroups.isNullOrEmpty() && playerGroups.contains(selectedFilterGroupId)
            }.map { UiPlayer(id = it.id, name = it.name) }
            }
        }
        // Load all groups for selection
        allGroups = groupRepo.listGroups()
    }

    val filteredPlayers = remember(allPlayers, searchQuery) {
        if (searchQuery.isBlank()) {
            allPlayers
        } else {
            allPlayers.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupName = when (selectedFilterGroupId) {
        null -> "All Players"
        "__ALL_GROUPS__" -> "All Groups"
        "__NONE__" -> "No Group"
        else -> allGroups.firstOrNull { it.id == selectedFilterGroupId }?.name ?: "Selected Group"
    }
    
    val filterDescription = when (selectedFilterGroupId) {
        null -> "Everyone in database"
        "__ALL_GROUPS__" -> "Players in every group"
        "__NONE__" -> "Players without groups"
        else -> "Group-specific filter"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StumpdTopBar(
                title = "Player Management",
                subtitle = "${allPlayers.size} players • $groupName",
                onBack = {
                    val intent = Intent(context, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                },
                actions = {
            // Refresh button
            IconButton(
                onClick = {
                    refreshTrigger += 1
                            successMessage = "✅ Refreshed"
                }
            ) {
                Icon(
                    Icons.Default.Refresh,
                            contentDescription = "Refresh"
                )
            }
            
            // Group filter button
                    IconButton(
                onClick = { showGroupFilterPicker = true }
                    ) {
                        Badge(
                            containerColor = if (selectedFilterGroupId != null)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Transparent
            ) {
                Icon(
                                Icons.Default.AccountCircle,
                    contentDescription = "Filter by Group",
                    tint = if (selectedFilterGroupId != null) 
                        MaterialTheme.colorScheme.primary 
                    else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Player"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
        Spacer(modifier = Modifier.height(16.dp))

            // Search bar
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
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = MaterialTheme.shapes.medium
        )

        Spacer(modifier = Modifier.height(16.dp))

            // Quick stats card
        if (allPlayers.isNotEmpty()) {
                Card(
                modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = allPlayers.size.toString(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Total Players",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        
                        VerticalDivider(
                            modifier = Modifier.height(48.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = filteredPlayers.size.toString(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Found" else "Showing",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredPlayers.isEmpty()) {
            if (allPlayers.isEmpty()) {
                    // Empty state - no players at all
                Card(
                    modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        Text(
                                text = "No Players Yet",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = "Add your first player to start tracking cricket stats!",
                            fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                        )
                            Spacer(modifier = Modifier.height(24.dp))
                        Button(
                                onClick = { showAddDialog = true }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add First Player")
                        }
                    }
                }
            } else {
                    // Empty state - search has no results
                Card(
                    modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        Text(
                                text = "No Players Found",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = "Try a different search term or filter",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (searchQuery.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Clear Search")
                                }
                            }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (searchQuery.isBlank()) Icons.Default.Person else Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (searchQuery.isBlank()) "All Players" else "Search Results",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                )
                    }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                Text(
                            text = "${filteredPlayers.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPlayers) { player ->
                    PlayerManagementCard(
                        player = player,
                        onEdit = {
                            playerToEdit = player
                            passwordDialogMode = "edit"
                            showPasswordDialog = true
                        },
                        onDelete = {
                            playerToDelete = player
                            // Check for password before showing delete dialog
                            if (hasPassword) {
                                passwordDialogMode = "delete"
                                showPasswordDialog = true
                            } else {
                                // If no password is set, show delete dialog directly
                                showDeleteDialog = true
                            }
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
    }

    if (showPasswordDialog && (playerToEdit != null || playerToDelete != null)) {
        PasswordDialog(
            storedPassword = prefs.getString("edit_password", "") ?: "",
            hasPassword = hasPassword,
            onPasswordCorrect = {
                scope.launch {
                    showPasswordDialog = false
                    if (passwordDialogMode == "edit" && playerToEdit != null) {
                        playerName = playerToEdit!!.name
                        // Load player's current groups
                        val groups = groupRepo.getGroupsForPlayer(playerToEdit!!.id)
                        playerGroupIds = groups.map { group -> group.id }.toSet()
                        showAddDialog = true
                    } else if (passwordDialogMode == "delete" && playerToDelete != null) {
                        // Password correct, show delete confirmation dialog
                        showDeleteDialog = true
                    }
                }
            },
            onPasswordSet = { newPassword ->
                scope.launch {
                    prefs.edit().putString("edit_password", newPassword).apply()
                    hasPassword = true
                    showPasswordDialog = false
                    if (passwordDialogMode == "edit" && playerToEdit != null) {
                        playerName = playerToEdit!!.name
                        // Load player's current groups
                        val groups = groupRepo.getGroupsForPlayer(playerToEdit!!.id)
                        playerGroupIds = groups.map { group -> group.id }.toSet()
                        showAddDialog = true
                    } else if (passwordDialogMode == "delete" && playerToDelete != null) {
                        // Password set, show delete confirmation dialog
                        showDeleteDialog = true
                    }
                }
            },
            onDismiss = {
                playerToEdit = null
                playerToDelete = null
                playerGroupIds = emptySet()
                showPasswordDialog = false
            }
        )
    }
    
    if (showAddDialog) {
        AddPlayerDialog(
            initialName = playerName,
            initialPlayerId = playerToEdit?.id,
            initialGroupIds = playerGroupIds,
            availableGroups = allGroups,
            onPlayerAdded = { name, groupIds ->
                scope.launch {
                    playerRepo.addOrUpdatePlayer(name, playerToEdit?.id)
                    // Update player's group memberships
                    if (playerToEdit != null) {
                        groupRepo.updatePlayerGroups(playerToEdit!!.id, groupIds.toList())
                    } else {
                        // For new players, we need to get the player ID first
                        val newPlayer = playerRepo.getAllPlayers().find { it.name == name }
                        newPlayer?.let {
                            groupRepo.updatePlayerGroups(it.id, groupIds.toList())
                        }
                    }
                    refreshTrigger += 1
                    successMessage = if (playerToEdit != null) {
                        "✅ Updated $name (stats & groups synced)"
                    } else {
                        "✅ Added $name"
                    }
                    showAddDialog = false
                    playerName = ""
                    playerToEdit = null
                    playerGroupIds = emptySet()
                }
            },
            onDismiss = { 
                playerName = ""
                playerToEdit = null
                playerGroupIds = emptySet()
                showAddDialog = false 
            }
        )
    }

    if (showDeleteDialog && playerToDelete != null) {
        DeletePlayerDialog(
            player = playerToDelete!!,
            onConfirm = {
                scope.launch {
                    try {
                        // Actually delete the player from the database
                        playerRepo.deletePlayer(playerToDelete!!.id)
                        
                refreshTrigger += 1 // Trigger refresh
                successMessage = "🗑️ Removed ${playerToDelete!!.name}"
                playerToDelete = null
                showDeleteDialog = false
                    } catch (e: Exception) {
                        // Handle error
                        Toast.makeText(context, "Failed to delete player: ${e.message}", Toast.LENGTH_SHORT).show()
                        playerToDelete = null
                        showDeleteDialog = false
                    }
                }
            },
            onDismiss = {
                playerToDelete = null
                showDeleteDialog = false
            }
        )
    }
    
    // Group filter picker dialog
    if (showGroupFilterPicker) {
        AlertDialog(
            onDismissRequest = { showGroupFilterPicker = false },
            title = { 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Filter by Group", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Text(
                            "Show players from:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // Section: Common Filters
                    item {
                        Text(
                            "COMMON FILTERS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                    
                    // All Players option
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedFilterGroupId = null
                                showGroupFilterPicker = false
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selectedFilterGroupId == null)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (selectedFilterGroupId == null) 2.dp else 1.dp
                            )
                        ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "All Players",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Everyone in the database",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                                if (selectedFilterGroupId == null) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // All Groups option (players in at least one group)
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedFilterGroupId = "__ALL_GROUPS__"
                                showGroupFilterPicker = false
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selectedFilterGroupId == "__ALL_GROUPS__")
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (selectedFilterGroupId == "__ALL_GROUPS__") 2.dp else 1.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "All Groups",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Players present in every group",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                                if (selectedFilterGroupId == "__ALL_GROUPS__") {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // None option (players not in any group)
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedFilterGroupId = "__NONE__"
                                showGroupFilterPicker = false
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selectedFilterGroupId == "__NONE__")
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (selectedFilterGroupId == "__NONE__") 2.dp else 1.dp
                            )
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "None",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Players not in any group",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                                if (selectedFilterGroupId == "__NONE__") {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Divider before specific groups
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "SPECIFIC GROUPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    // Group options
                    items(allGroups.size) { index ->
                        val group = allGroups[index]
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    selectedFilterGroupId = group.id
                                    showGroupFilterPicker = false
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selectedFilterGroupId == group.id)
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (selectedFilterGroupId == group.id) 2.dp else 1.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        group.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (selectedFilterGroupId == group.id) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
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
    player: UiPlayer,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player Avatar
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Player Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tap to view stats",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Player",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Only show delete button if feature flag is enabled
                if (FeatureFlags.isDeletionsEnabled(LocalContext.current) && onDelete != null) {
                IconButton(
                    onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Player",
                        tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Arrow indicator
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPlayerDialog(
    initialName: String = "",
    initialPlayerId: String? = null,
    initialGroupIds: Set<String> = emptySet(),
    availableGroups: List<com.oreki.stumpd.data.local.entity.GroupEntity> = emptyList(),
    onPlayerAdded: (name: String, groupIds: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var playerName by remember { mutableStateOf(initialName) }
    var selectedGroupIds by remember { mutableStateOf(initialGroupIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        if (initialName.isBlank()) Icons.Default.Add else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            Text(
                text = if (initialName.isBlank()) "Add New Player" else "Edit Player",
                    fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    Text(
                        text = "Note: This will update the player's name in all records",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                // Group restrictions section
                if (availableGroups.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text(
                        text = "Group Access",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = "Empty = Can play in all groups\nSelected = Restricted to selected groups only",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                    
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableGroups.forEach { group ->
                            FilterChip(
                                selected = selectedGroupIds.contains(group.id),
                                onClick = {
                                    selectedGroupIds = if (selectedGroupIds.contains(group.id)) {
                                        selectedGroupIds - group.id
                                    } else {
                                        selectedGroupIds + group.id
                                    }
                                },
                                label = { Text(group.name, fontSize = 13.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val nameValid = playerName.trim().isNotBlank()
            val nameChanged = playerName.trim() != initialName
            val groupsChanged = selectedGroupIds != initialGroupIds
            val hasChanges = if (initialName.isBlank()) {
                // Adding new player - just need valid name
                nameValid
            } else {
                // Editing player - need valid name AND (name changed OR groups changed)
                nameValid && (nameChanged || groupsChanged)
            }
            
            Button(
                onClick = {
                    if (playerName.trim().isNotBlank()) {
                        onPlayerAdded(playerName.trim(), selectedGroupIds)
                    }
                },
                enabled = hasChanges,
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
    player: UiPlayer,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    "Delete Player?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Are you sure you want to delete ${player.name}?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "This action cannot be undone. All match statistics for this player will be permanently removed.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete", fontWeight = FontWeight.SemiBold)
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
fun PasswordDialog(
    storedPassword: String?,
    hasPassword: Boolean,
    onPasswordCorrect: () -> Unit,
    onPasswordSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPassword by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var isSettingPassword by remember { mutableStateOf(!hasPassword) }
    var confirmPassword by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(if (isSettingPassword) "Set Edit Password" else "Enter Password") 
        },
        text = {
            Column {
                if (isSettingPassword) {
                    Text(
                        "Set a password to protect player name edits",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                
                OutlinedTextField(
                    value = enteredPassword,
                    onValueChange = { 
                        enteredPassword = it
                        showError = false
                    },
                    label = { Text(if (isSettingPassword) "New Password" else "Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showError
                )
                
                if (isSettingPassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it
                            showError = false
                        },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = showError
                    )
                }
                
                if (showError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isSettingPassword) "Passwords do not match" else "Incorrect password",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                if (!isSettingPassword && hasPassword) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { 
                        isSettingPassword = true
                        enteredPassword = ""
                        showError = false
                    }) {
                        Text("Forgot password? Reset it", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSettingPassword) {
                        if (enteredPassword.isNotBlank() && enteredPassword == confirmPassword) {
                            onPasswordSet(enteredPassword)
                        } else {
                            showError = true
                        }
                    } else {
                        if (enteredPassword == storedPassword) {
                            onPasswordCorrect()
                        } else {
                            showError = true
                        }
                    }
                },
                enabled = enteredPassword.isNotBlank() && (!isSettingPassword || confirmPassword.isNotBlank())
            ) {
                Text(if (isSettingPassword) "Set Password" else "Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
