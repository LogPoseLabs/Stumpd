package com.oreki.stumpd

import com.oreki.stumpd.data.preferences.PasscodeManager
import com.oreki.stumpd.domain.model.*
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import com.oreki.stumpd.utils.FeatureFlags
import androidx.compose.foundation.layout.*
import com.oreki.stumpd.ui.components.PasscodePrompt
import com.oreki.stumpd.ui.theme.EmptyState
import com.oreki.stumpd.ui.theme.rememberMessenger
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.hairline
import com.oreki.stumpd.ui.theme.StatValue
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import dagger.hilt.android.AndroidEntryPoint
import com.oreki.stumpd.data.local.dao.PlayerCareerSummary
import com.oreki.stumpd.data.local.db.StumpdDb

@AndroidEntryPoint
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
    
    // The app's single passcode (Settings → Security). This used to be its own password, kept in
    // plain text in SharedPreferences, so the app had two to remember.
    val passcodeManager = remember { PasscodeManager(context) }
    var hasPasscode by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasPasscode = passcodeManager.isSet() }

    /**
     * Opens whichever player action was waiting on the gate. Called straight away when no
     * passcode is set, or once the entered one checks out.
     */
    val openPendingPlayerAction: () -> Unit = {
        scope.launch {
            showPasswordDialog = false
            if (passwordDialogMode == "edit" && playerToEdit != null) {
                playerName = playerToEdit!!.name
                val groups = groupRepo.getGroupsForPlayer(playerToEdit!!.id)
                playerGroupIds = groups.map { group -> group.id }.toSet()
                showAddDialog = true
            } else if (passwordDialogMode == "delete" && playerToDelete != null) {
                showDeleteDialog = true
            }
        }
        Unit
    }
    
    // Snackbar for success messages
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = rememberMessenger(snackbarHostState)
    
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

    // Career totals keyed by player id, so each row can show something useful instead of
    // 54 identical "Tap to view stats" lines. One aggregate query, not a per-player load.
    var careerSummaries by remember { mutableStateOf<Map<String, PlayerCareerSummary>>(emptyMap()) }

    LaunchedEffect(refreshTrigger) {
        careerSummaries = runCatching {
            StumpdDb.get(context).matchDao().playerCareerSummaries().associateBy { it.playerId }
        }.getOrElse { emptyMap() }
    }

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


        if (filteredPlayers.isEmpty()) {
            // Both of these were hand-built cards with their own paddings, icon sizes and type;
            // the shared primitive is what the stats screens already use.
            if (allPlayers.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Person,
                    title = "No Players Yet",
                    description = "Add your first player to start tracking cricket stats!",
                    actionButton = {
                        Button(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add First Player")
                        }
                    },
                )
            } else {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "No Players Found",
                    description = "Try a different search term or filter",
                    actionButton = if (searchQuery.isEmpty()) null else {
                        {
                            FilledTonalButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear Search")
                            }
                        }
                    },
                )
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
                    text = (if (searchQuery.isBlank()) "All Players" else "Search Results").uppercase(),
                            style = MicroLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                    }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                Text(
                            text = "${filteredPlayers.size}",
                            style = StatValue,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Grouped by initial so a 50+ player roster can be scanned rather than
                // scrolled blindly. Only shown for the unsearched list, where it helps.
                val grouped = if (searchQuery.isBlank()) {
                    filteredPlayers.groupBy { it.name.trim().firstOrNull()?.uppercaseChar() ?: '#' }
                } else {
                    mapOf(' ' to filteredPlayers)
                }

                grouped.toSortedMap().forEach { (initial, playersInSection) ->
                    if (initial != ' ') {
                        item(key = "header-$initial") {
                            Text(
                                text = initial.toString(),
                                style = MicroLabel,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                            )
                        }
                    }
                    items(playersInSection, key = { it.id }) { player ->
                    PlayerManagementCard(
                        player = player,
                        summary = careerSummaries[player.id],
                        onEdit = {
                            playerToEdit = player
                            passwordDialogMode = "edit"
                            if (hasPasscode) showPasswordDialog = true else openPendingPlayerAction()
                        },
                        onDelete = {
                            playerToDelete = player
                            passwordDialogMode = "delete"
                            if (hasPasscode) showPasswordDialog = true else openPendingPlayerAction()
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
    }

    if (showPasswordDialog && hasPasscode && (playerToEdit != null || playerToDelete != null)) {
        PasscodePrompt(
            onVerify = { entered -> passcodeManager.verify(entered) },
            onPasscodeCorrect = { openPendingPlayerAction() },
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
                        messenger.show("Failed to delete player: ${e.message}")
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
                    Text("Filter by Group", style = MaterialTheme.typography.titleLarge)
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
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // Section: Common Filters
                    item {
                        Text(
                            "COMMON FILTERS",
                            style = MicroLabel,
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
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Everyone in the database",
                                        style = MaterialTheme.typography.labelSmall,
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
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Players present in every group",
                                        style = MaterialTheme.typography.labelSmall,
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
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Players not in any group",
                                        style = MaterialTheme.typography.labelSmall,
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
                            style = MicroLabel,
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
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
                                        style = MaterialTheme.typography.bodyMedium,
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = hairline(),
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
                style = StatValue,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = title.uppercase(),
                style = MicroLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PlayerManagementCard(
    player: UiPlayer,
    summary: PlayerCareerSummary? = null,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = hairline(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Player Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary?.takeIf { it.matches > 0 }?.let {
                        "${it.matches} ${if (it.matches == 1) "match" else "matches"} • " +
                            "${it.runs} runs • ${it.wickets} wkts"
                    } ?: "Yet to play a match",
                    style = MaterialTheme.typography.bodySmall,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                    style = MaterialTheme.typography.titleLarge,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                // Group restrictions section
                if (availableGroups.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text(
                        text = "Group Access",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = "Empty = Can play in all groups\nSelected = Restricted to selected groups only",
                        style = MaterialTheme.typography.labelSmall,
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
                                    if (group.isOwner) {
                                        selectedGroupIds = if (selectedGroupIds.contains(group.id)) {
                                            selectedGroupIds - group.id
                                        } else {
                                            selectedGroupIds + group.id
                                        }
                                    }
                                },
                                enabled = group.isOwner,
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(group.name, style = MaterialTheme.typography.bodySmall)
                                        if (!group.isOwner) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = "Not owner",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
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
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Are you sure you want to delete ${player.name}?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "This action cannot be undone. All match statistics for this player will be permanently removed.",
                    style = MaterialTheme.typography.bodySmall,
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

