package com.oreki.stumpd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.data.mappers.toEntityWithId
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import kotlinx.coroutines.launch

class EditGroupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        
        val groupId = intent.getStringExtra("group_id")
        val isNew = groupId == null
        
        setContent {
            StumpdTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EditGroupScreen(groupId = groupId, isNew = isNew)
                }
            }
        }
    }
}

@Composable
fun EditGroupScreen(groupId: String?, isNew: Boolean) {
    val context = LocalContext.current
    val groupRepo = rememberGroupRepository()
    val playerRepo = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    
    var groupData by remember { mutableStateOf<PlayerGroup?>(null) }
    var allPlayers by remember { mutableStateOf<List<StoredPlayer>>(emptyList()) }
    var isLoading by remember { mutableStateOf(!isNew) }
    
    // Form fields
    var name by rememberSaveable { mutableStateOf("") }
    var ground by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(BallFormat.WHITE_BALL) }
    var shortPitch by rememberSaveable { mutableStateOf(false) }
    var memberIds by remember { mutableStateOf(setOf<String>()) } // Complex type, keep remember
    var unavailablePlayerIds by remember { mutableStateOf(setOf<String>()) } // Availability tracking
    
    // Match settings
    val settingsManager = remember { MatchSettingsManager(context) }
    val defaultMatchSettings = remember { settingsManager.getDefaultMatchSettings() }
    var matchSettings by remember { mutableStateOf(defaultMatchSettings) }
    
    var totalOversText by rememberSaveable { mutableStateOf(defaultMatchSettings.totalOvers.toString()) }
    var maxPerBowlerText by rememberSaveable { mutableStateOf(defaultMatchSettings.maxOversPerBowler.toString()) }
    var powerplayOversText by rememberSaveable { mutableStateOf(defaultMatchSettings.powerplayOvers.toString()) }
    var maxPlayersText by rememberSaveable { mutableStateOf(defaultMatchSettings.maxPlayersPerTeam.toString()) }
    var jokerMaxOversText by rememberSaveable { mutableStateOf(defaultMatchSettings.jokerMaxOvers.toString()) }
    
    // Search for adding players
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    // Load data
    LaunchedEffect(Unit) {
        if (!isNew && groupId != null) {
            try {
                val editInfo = groupRepo.getGroupForEdit(groupId)
                val group = editInfo.entity.toDomain(editInfo.defaults, editInfo.memberIds, editInfo.unavailablePlayerIds)
                groupData = group
                
                name = group.name
                ground = group.defaults.groundName
                format = BallFormat.valueOf(group.defaults.format)
                shortPitch = group.defaults.shortPitch
                memberIds = group.playerIds.toSet()
                unavailablePlayerIds = group.unavailablePlayerIds.toSet()
                matchSettings = group.defaults.matchSettings
                
                totalOversText = matchSettings.totalOvers.toString()
                maxPerBowlerText = matchSettings.maxOversPerBowler.toString()
                powerplayOversText = matchSettings.powerplayOvers.toString()
                maxPlayersText = matchSettings.maxPlayersPerTeam.toString()
                jokerMaxOversText = matchSettings.jokerMaxOvers.toString()
                
                // Load all players for group (members + potential members)
                // For editing, we need to show all players who could be members
                allPlayers = playerRepo.getAllPlayers().map { StoredPlayer(id = it.id, name = it.name) }
                
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            // For new groups, show all players
            allPlayers = playerRepo.getAllPlayers().map { StoredPlayer(id = it.id, name = it.name) }
        }
    }
    
    val filteredAvailablePlayers = remember(allPlayers, memberIds, searchQuery) {
        allPlayers
            .filter { it.id !in memberIds }
            .filter { if (searchQuery.isBlank()) true else it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    val selectedPlayers = remember(allPlayers, memberIds) {
        allPlayers.filter { it.id in memberIds }
    }
    
    Scaffold(
        topBar = {
            StumpdTopBar(
                title = if (isNew) "Create Group" else "Edit Group",
                subtitle = if (isNew) "Configure a new player group" else "Update group settings",
                onBack = { (context as ComponentActivity).finish() }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { (context as ComponentActivity).finish() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val settings = GroupDefaultSettings(
                                        matchSettings = matchSettings.copy(shortPitch = shortPitch),
                                        groundName = ground.trim(),
                                        format = format.toString(),
                                        shortPitch = shortPitch
                                    )
                                    
                                    if (isNew) {
                                        val newGroupId = groupRepo.createGroup(name.trim(), settings)
                                        groupRepo.replaceMembers(newGroupId, memberIds.toList())
                                        groupRepo.replaceUnavailablePlayers(newGroupId, unavailablePlayerIds.toList())
                                    } else if (groupId != null) {
                                        groupRepo.renameGroup(groupId, name.trim())
                                        groupRepo.updateDefaults(groupId, settings.toEntityWithId(groupId))
                                        groupRepo.replaceMembers(groupId, memberIds.toList())
                                        groupRepo.replaceUnavailablePlayers(groupId, unavailablePlayerIds.toList())
                                    }
                                    
                                    (context as ComponentActivity).finish()
                                } catch (e: Exception) {
                                    android.util.Log.e("EditGroupActivity", "Failed to save group", e)
                                }
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isNew) "Create" else "Save")
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Basic Info Section
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "📋 Basic Information",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Group Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = ground,
                                onValueChange = { ground = it },
                                label = { Text("Ground Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Text("Match Format", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = format == BallFormat.WHITE_BALL,
                                    onClick = { format = BallFormat.WHITE_BALL },
                                    label = { Text("Limited Overs") }
                                )
                                FilterChip(
                                    selected = format == BallFormat.RED_BALL,
                                    onClick = { format = BallFormat.RED_BALL },
                                    label = { Text("Test") }
                                )
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Short Pitch", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Switch(checked = shortPitch, onCheckedChange = { shortPitch = it })
                            }
                        }
                    }
                }
                
                // Match Settings Section
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "⚙️ Match Settings",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            
                            // Total Overs
                            OutlinedTextField(
                                value = totalOversText,
                                onValueChange = { v ->
                                    totalOversText = v
                                    v.toIntOrNull()?.let { overs ->
                                        if (overs in 1..50) {
                                            matchSettings = matchSettings.copy(totalOvers = overs)
                                        }
                                    }
                                },
                                label = { Text("Total Overs") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            // Max Overs per Bowler
                            OutlinedTextField(
                                value = maxPerBowlerText,
                                onValueChange = { v ->
                                    maxPerBowlerText = v
                                    v.toIntOrNull()?.let { overs ->
                                        if (overs > 0 && overs <= matchSettings.totalOvers) {
                                            matchSettings = matchSettings.copy(maxOversPerBowler = overs)
                                        }
                                    }
                                },
                                label = { Text("Max Overs per Bowler") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            // Powerplay Overs
                            OutlinedTextField(
                                value = powerplayOversText,
                                onValueChange = { v ->
                                    powerplayOversText = v
                                    v.toIntOrNull()?.let { overs ->
                                        if (overs >= 0 && overs <= matchSettings.totalOvers) {
                                            matchSettings = matchSettings.copy(powerplayOvers = overs)
                                        }
                                    }
                                },
                                label = { Text("Powerplay Overs") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            if (matchSettings.powerplayOvers > 0) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Double Runs in Powerplay", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            "Runs scored during powerplay will be doubled",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = matchSettings.doubleRunsInPowerplay,
                                        onCheckedChange = { matchSettings = matchSettings.copy(doubleRunsInPowerplay = it) }
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            // Max Players per Team
                            OutlinedTextField(
                                value = maxPlayersText,
                                onValueChange = { v ->
                                    maxPlayersText = v
                                    v.toIntOrNull()?.let { players ->
                                        if (players in 1..15) {
                                            matchSettings = matchSettings.copy(maxPlayersPerTeam = players)
                                        }
                                    }
                                },
                                label = { Text("Max Players per Team") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(16.dp))
                            
                            // Extras Settings
                            Text("Extras Configuration", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExtrasChip("No-ball +1", matchSettings.noballRuns) { 
                                    matchSettings = matchSettings.copy(noballRuns = if (it) 1 else 0) 
                                }
                                ExtrasChip("Wide +1", matchSettings.wideRuns) { 
                                    matchSettings = matchSettings.copy(wideRuns = if (it) 1 else 0) 
                                }
                                ExtrasChip("Bye +1", matchSettings.byeRuns) { 
                                    matchSettings = matchSettings.copy(byeRuns = if (it) 1 else 0) 
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExtrasChip("Leg Bye +1", matchSettings.legByeRuns) { 
                                    matchSettings = matchSettings.copy(legByeRuns = if (it) 1 else 0) 
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(16.dp))
                            
                            // Batting Rules
                            Text("Batting Rules", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Single Side Batting", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "Allow single batsman to continue after all out",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = matchSettings.allowSingleSideBatting,
                                    onCheckedChange = { matchSettings = matchSettings.copy(allowSingleSideBatting = it) }
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(16.dp))
                            
                            // Joker Rules
                            Text("Joker Rules", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Joker Can Bat & Bowl", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "Allow joker player to participate fully",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = matchSettings.jokerCanBatAndBowl,
                                    onCheckedChange = { matchSettings = matchSettings.copy(jokerCanBatAndBowl = it) }
                                )
                            }
                            
                            if (matchSettings.jokerCanBatAndBowl) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = jokerMaxOversText,
                                    onValueChange = { v ->
                                        jokerMaxOversText = v
                                        v.toIntOrNull()?.let { overs ->
                                            if (overs >= 1 && overs <= matchSettings.totalOvers) {
                                                matchSettings = matchSettings.copy(jokerMaxOvers = overs)
                                            }
                                        }
                                    },
                                    label = { Text("Joker Max Overs") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(16.dp))
                            
                            // Advanced Rules
                            Text("Advanced Rules", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            
                            
                            // Toss Choice
                            Text("Default Toss Decision", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = matchSettings.tossWinnerChoice == TossChoice.BAT_FIRST,
                                    onClick = { matchSettings = matchSettings.copy(tossWinnerChoice = TossChoice.BAT_FIRST) },
                                    label = { Text("Bat First") }
                                )
                                FilterChip(
                                    selected = matchSettings.tossWinnerChoice == TossChoice.BOWL_FIRST,
                                    onClick = { matchSettings = matchSettings.copy(tossWinnerChoice = TossChoice.BOWL_FIRST) },
                                    label = { Text("Bowl First") }
                                )
                            }
                        }
                    }
                }
                
                // Members & Availability Section
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "👥 Members & Availability (${selectedPlayers.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Info box explaining the feature
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Column {
                                            Text(
                                                "Understanding Availability",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "• Switch ON = Available for team selection\n" +
                                                "• Switch OFF = Temporarily unavailable (still a member)\n" +
                                                "• ❌ Button = Permanently remove from group",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                            
                            if (selectedPlayers.isNotEmpty()) {
                                selectedPlayers.forEach { player ->
                                    val isAvailable = player.id !in unavailablePlayerIds
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isAvailable) 
                                                MaterialTheme.colorScheme.surfaceVariant
                                            else 
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(player.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        if (isAvailable) Icons.Default.CheckCircle else Icons.Default.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = if (isAvailable) 
                                                            MaterialTheme.colorScheme.primary 
                                                        else 
                                                            MaterialTheme.colorScheme.error
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        if (isAvailable) "Available for team selection" else "Temporarily unavailable",
                                                        fontSize = 11.sp,
                                                        color = if (isAvailable) 
                                                            MaterialTheme.colorScheme.primary 
                                                        else 
                                                            MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Switch(
                                                        checked = isAvailable,
                                                        onCheckedChange = {
                                                            unavailablePlayerIds = if (it) {
                                                                unavailablePlayerIds - player.id
                                                            } else {
                                                                unavailablePlayerIds + player.id
                                                            }
                                                        }
                                                    )
                                                    Text(
                                                        "Available",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    IconButton(onClick = { memberIds = memberIds - player.id }) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Remove from group permanently",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                    Text(
                                                        "Remove",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "No members yet. Add players below.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                
                // Add Players Section
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "➕ Add Players to Group",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                "Add players as permanent members. New members are automatically available for selection.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Search players...") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            if (filteredAvailablePlayers.isEmpty()) {
                                Text(
                                    "No available players to add. All players are already in this group.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Text(
                                    "Showing ${filteredAvailablePlayers.size} available ${if (filteredAvailablePlayers.size == 1) "player" else "players"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                filteredAvailablePlayers.forEach { player ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { memberIds = memberIds + player.id }
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(player.name, fontSize = 14.sp)
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    HorizontalDivider()
                                }
                                if (false) { // Removed the "X more players" message
                                    Text(
                                        "+ ${filteredAvailablePlayers.size - 10} more...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Bottom padding for safe area
                item {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun ExtrasChip(label: String, value: Int, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = value > 0,
        onClick = { onToggle(value == 0) },
        label = { Text(label, fontSize = 12.sp) }
    )
}

