package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.oreki.stumpd.ui.theme.PrimaryCta
import com.oreki.stumpd.ui.theme.SectionTitle
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.WarningContainer
import androidx.compose.foundation.layout.FlowRow
import com.oreki.stumpd.ui.theme.SectionCard
import com.oreki.stumpd.ui.theme.StumpdTopBar
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.items
import com.oreki.stumpd.ui.theme.Label

class TeamSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TeamSetupScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSetupScreen() {
    val context = LocalContext.current
    val gson = Gson()
    val settingsManager = remember { MatchSettingsManager(context) }

    // Initialize with default match settings
    var matchSettings by remember {
        mutableStateOf(settingsManager.getDefaultMatchSettings())
    }

    // Team states
    var team1 by remember { mutableStateOf(Team("Team A", mutableListOf())) }
    var team2 by remember { mutableStateOf(Team("Team B", mutableListOf())) }
    var jokerPlayer by remember { mutableStateOf<Player?>(null) }

    // Dialog states
    var showTeam1Dialog by remember { mutableStateOf(false) }
    var showTeam2Dialog by remember { mutableStateOf(false) }
    var showJokerDialog by remember { mutableStateOf(false) }

    // (Use matchSettings as the source of truth for initial value)
    var oversText by remember { mutableStateOf(matchSettings.totalOvers.toString()) }
    var maxOversPerBowlerText by remember { mutableStateOf(matchSettings.maxOversPerBowler.toString()) }
    var legSideWideRunsText by remember { mutableStateOf(matchSettings.legSideWideRuns.toString()) }
    var offSideWideRunsText by remember { mutableStateOf(matchSettings.offSideWideRuns.toString()) }
    var noballRunsText by remember { mutableStateOf(matchSettings.noballRuns.toString()) }
    var byeRunsText by remember { mutableStateOf(matchSettings.byeRuns.toString()) }
    var legByeRunsText by remember { mutableStateOf(matchSettings.legByeRuns.toString()) }
    var powerplayOversText by remember { mutableStateOf(matchSettings.powerplayOvers.toString()) }
    var jokerMaxOversText by remember { mutableStateOf(matchSettings.jokerMaxOvers.toString()) }

    val groupStorage = remember { PlayerGroupStorageManager(context) }
    var selectedGroup by remember { mutableStateOf<GroupInfo?>(null) }
    var showGroupPicker by remember { mutableStateOf(false) }

    // Expandable sections state
    var expandedSections by remember {
        mutableStateOf(setOf("basic")) // Start with basic expanded
    }

    fun toggleSection(section: String) {
        expandedSections = if (expandedSections.contains(section)) {
            expandedSections - section
        } else {
            expandedSections + section
        }
    }

    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Quick Match Setup",
                subtitle = "Configure match settings and teams",
                onBack = {
                    val intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Basic Settings Section
            item {
                SettingsSection(
                    title = "🏏 Basic Settings",
                    isExpanded = expandedSections.contains("basic"),
                    onToggle = { toggleSection("basic") }
                ) {
                    // Total Overs
                    SettingRow(
                        label = "Total Overs",
                        value = oversText,
                        onValueChange = { value ->
                            oversText = value
                            value.toIntOrNull()?.let { overs ->
                                if (overs > 0 && overs <= 50) {
                                    matchSettings = matchSettings.copy(totalOvers = overs)
                                }
                            }
                        }
                    )
                }
            }

            // Extras Settings Section
            item {
                SettingsSection(
                    title = "⚾ Extras Settings",
                    isExpanded = expandedSections.contains("extras"),
                    onToggle = { toggleSection("extras") }
                ) {
                    SettingRow(
                        label = "Leg Side Wide Runs",
                        value = legSideWideRunsText,
                        onValueChange = { value ->
                            legSideWideRunsText = value
                            value.toIntOrNull()?.let { runs ->
                                if (runs >= 1 && runs <= 1) {
                                    matchSettings = matchSettings.copy(legSideWideRuns = runs)
                                }
                            }
                        }
                    )

                    SettingRow(
                        label = "Off Side Wide Runs",
                        value = offSideWideRunsText,
                        onValueChange = { value ->
                            offSideWideRunsText = value
                            value.toIntOrNull()?.let { runs ->
                                if (runs >= 0 && runs <= 1) {
                                    matchSettings = matchSettings.copy(offSideWideRuns = runs)
                                }
                            }
                        }
                    )

                    SettingRow(
                        label = "No Ball Runs",
                        value = noballRunsText,
                        onValueChange = { value ->
                            noballRunsText = value
                            value.toIntOrNull()?.let { runs ->
                                if (runs >= 0 && runs <= 1) {
                                    matchSettings = matchSettings.copy(noballRuns = runs)
                                }
                            }
                        }
                    )

                    SettingRow(
                        label = "Bye Runs",
                        value = byeRunsText,
                        onValueChange = { value ->
                            byeRunsText = value
                            value.toIntOrNull()?.let { runs ->
                                if (runs >= 0 && runs <= 1) {
                                    matchSettings = matchSettings.copy(byeRuns = runs)
                                }
                            }
                        }
                    )

                    SettingRow(
                        label = "Leg Bye Runs",
                        value = legByeRunsText,
                        onValueChange = { value ->
                            legByeRunsText = value
                            value.toIntOrNull()?.let { runs ->
                                if (runs >= 0 && runs <= 1) {
                                    matchSettings = matchSettings.copy(legByeRuns = runs)
                                }
                            }
                        }
                    )
                }
            }

            // Special Rules Section
            item {
                SettingsSection(
                    title = "⚡ Special Rules",
                    isExpanded = expandedSections.contains("special"),
                    onToggle = { toggleSection("special") }
                ) {
                    SwitchSettingRow(
                        label = "Single Side Batting",
                        description = "Allow one batsman to continue if others get out",
                        checked = matchSettings.allowSingleSideBatting,
                        onCheckedChange = {
                            matchSettings = matchSettings.copy(allowSingleSideBatting = it)
                        }
                    )
                    SwitchSettingRow(
                        label = "Short Pitch",
                        description = "0-4 runs available. No 6",
                        checked = matchSettings.shortPitch,
                        onCheckedChange = {
                            matchSettings = matchSettings.copy(shortPitch = it)
                        }
                    )

                    SwitchSettingRow(
                        label = "Enable Joker",
                        description = "A player who can bat and bowl for both teams",
                        checked = matchSettings.jokerCanBatAndBowl,
                        onCheckedChange = {
                            matchSettings = matchSettings.copy(jokerCanBatAndBowl = it)
                            if (!it) jokerPlayer = null // Clear joker if disabled
                        }
                    )

                    if (matchSettings.jokerCanBatAndBowl) {
                        SettingRow(
                            label = "Joker Max Overs",
                            value = jokerMaxOversText,
                            onValueChange = { value ->
                                jokerMaxOversText = value
                                value.toIntOrNull()?.let { overs ->
                                    if (overs >= 1 && overs <= matchSettings.totalOvers) {
                                        matchSettings = matchSettings.copy(jokerMaxOvers = overs)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Advanced Rules Section
            item {
                SettingsSection(
                    title = "🎯 Advanced Rules",
                    isExpanded = expandedSections.contains("advanced"),
                    onToggle = { toggleSection("advanced") }
                ) {
                    SettingRow(
                        label = "Max Overs per Bowler",
                        value = maxOversPerBowlerText,
                        onValueChange = { value ->
                            maxOversPerBowlerText = value
                            value.toIntOrNull()?.let { overs ->
                                if (overs > 0 && overs <= matchSettings.totalOvers) {
                                    matchSettings = matchSettings.copy(maxOversPerBowler = overs)
                                }
                            }
                        }
                    )

                    SettingRow(
                        label = "Powerplay Overs",
                        value = powerplayOversText,
                        onValueChange = { value ->
                            powerplayOversText = value
                            value.toIntOrNull()?.let { overs ->
                                if (overs >= 0 && overs <= matchSettings.totalOvers) {
                                    matchSettings = matchSettings.copy(powerplayOvers = overs)
                                }
                            }
                        }
                    )

                    SwitchSettingRow(
                        label = "Enforce Follow-On",
                        description = "Traditional test match follow-on rules",
                        checked = matchSettings.enforceFollowOn,
                        onCheckedChange = {
                            matchSettings = matchSettings.copy(enforceFollowOn = it)
                        }
                    )

                    SwitchSettingRow(
                        label = "Duckworth-Lewis Method",
                        description = "Apply D/L method for interrupted matches",
                        checked = matchSettings.duckworthLewisMethod,
                        onCheckedChange = {
                            matchSettings = matchSettings.copy(duckworthLewisMethod = it)
                        }
                    )
                }
            }

            // Team Names Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "👥 Team Names",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = team1.name,
                                onValueChange = { team1 = team1.copy(name = it) },
                                label = { Text("Team 1 Name") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            )

                            OutlinedTextField(
                                value = team2.name,
                                onValueChange = { team2 = team2.copy(name = it) },
                                label = { Text("Team 2 Name") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "📂 Group",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedGroup?.name ?: "No group selected",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(onClick = { showGroupPicker = true }) {
                                Icon(Icons.Default.Home, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (selectedGroup == null) "Select Group" else "Change")
                            }
                        }
                    }
                }

                if (showGroupPicker) {
                    val groups = groupStorage.getAllGroups()
                    AlertDialog(
                        onDismissRequest = { showGroupPicker = false },
                        title = { Text("Choose Group") },
                        text = {
                            if (groups.isEmpty()) {
                                Column {
                                    Text("No groups yet.")
                                    Spacer(Modifier.height(8.dp))
                                    FilledTonalButton(onClick = {
                                        // quick-create Saturday and Sunday for convenience (optional)
                                        groupStorage.createGroup("Saturday")
                                        groupStorage.createGroup("Sunday")
                                        showGroupPicker = false
                                        Toast.makeText(
                                            context,
                                            "Created Saturday & Sunday",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }) { Text("Create Saturday & Sunday") }
                                }
                            } else {
                                LazyColumn(Modifier.height(300.dp)) {
                                    items(groups) { g ->
                                        ListItem(
                                            headlineContent = { Text(g.name) },
                                            supportingContent = {
                                                Text(
                                                    "${g.players.size} players",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            modifier = Modifier.clickable {
                                                selectedGroup = GroupInfo(id = g.id, name = g.name)
                                                showGroupPicker = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = {
                                showGroupPicker = false
                            }) { Text("Close") }
                        }
                    )
                }
            }

            // Team Cards Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Team 1 Card
                    EnhancedTeamCard(
                        team = team1,
                        jokerPlayer = jokerPlayer,
                        onAddPlayer = {
                            if (selectedGroup == null) {
                                Toast.makeText(context, "Select a group first", Toast.LENGTH_SHORT).show()
                            } else {
                                showTeam1Dialog = true
                            }
                        },
                        onRemovePlayer = { player ->
                            val newPlayers = team1.players.toMutableList()
                            newPlayers.remove(player)
                            team1 = team1.copy(players = newPlayers)
                        },
                        modifier = Modifier.weight(1f),
                    )

                    // Team 2 Card
                    EnhancedTeamCard(
                        team = team2,
                        jokerPlayer = jokerPlayer,
                        onAddPlayer = {
                            if (selectedGroup == null) {
                                Toast.makeText(context, "Select a group first", Toast.LENGTH_SHORT).show()
                            } else {
                                showTeam2Dialog = true
                            }
                        },
                        onRemovePlayer = { player ->
                            val newPlayers = team2.players.toMutableList()
                            newPlayers.remove(player)
                            team2 = team2.copy(players = newPlayers)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Joker Section
            if (matchSettings.jokerCanBatAndBowl) {
                // Joker Section (adaptive container + better contrast)
                item {
                    val container = com.oreki.stumpd.ui.theme.warningContainerAdaptive()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = container),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🃏", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                SectionTitle("Joker (Optional)")
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "A player who can bat and bowl for both teams (max ${matchSettings.jokerMaxOvers} overs)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(12.dp))
                            if (jokerPlayer == null) {
                                FilledTonalButton(
                                    onClick = { if (selectedGroup == null) {
                                        Toast.makeText(context, "Select a group first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showJokerDialog = true
                                    } },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Joker")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add Joker")
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🃏 ${jokerPlayer!!.name}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Row {
                                        TextButton(onClick = { showJokerDialog = true }) { Text("Change") }
                                        TextButton(onClick = { jokerPlayer = null }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Match Summary & Start Button
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = com.oreki.stumpd.ui.theme.sectionContainer()
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📊", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            SectionTitle("Match Summary")
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label("Teams")
                            Text("${team1.players.size} v ${team2.players.size}", fontWeight = FontWeight.Medium)
                        }

                        if (jokerPlayer != null) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Label("Joker")
                                Text(jokerPlayer!!.name, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label("Special")
                            Text(
                                buildString {
                                    if (matchSettings.allowSingleSideBatting) append("Single Side")
                                    if (matchSettings.jokerCanBatAndBowl && isNotEmpty()) append(", ")
                                    if (matchSettings.jokerCanBatAndBowl) append("Joker Enabled")
                                    if (isEmpty()) append("Standard Rules")
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))

                        PrimaryCta(
                            text = "Start ${matchSettings.totalOvers} overs Match",
                            onClick = {
                                val minPlayersPerTeam = 2
                                val team1Size = team1.players.size
                                val team2Size = team2.players.size
                                if (team1Size >= minPlayersPerTeam && team2Size >= minPlayersPerTeam) {
                                    if (team1Size == team2Size) {
                                        val intent = Intent(context, ScoringActivity::class.java)

                                        if (selectedGroup == null) {
                                            Toast.makeText(context, "Please select a group (e.g., Saturday or Sunday)", Toast.LENGTH_SHORT).show()
                                            return@PrimaryCta
                                        }
                                        intent.putExtra("group_id", selectedGroup?.id ?: "")
                                        intent.putExtra("group_name", selectedGroup?.name ?: "")

                                        // Pass team data via intent
                                        intent.putExtra("team1_name", team1.name)
                                        intent.putExtra("team2_name", team2.name)
                                        intent.putExtra("joker_name", jokerPlayer?.name ?: "")

                                        // Pass player names as string arrays
                                        val team1PlayerNames =
                                            team1.players.map { it.name }.toTypedArray()
                                        val team2PlayerNames =
                                            team2.players.map { it.name }.toTypedArray()

                                        intent.putExtra("team1_players", team1PlayerNames)
                                        intent.putExtra("team2_players", team2PlayerNames)

                                        // Pass match settings with calculated max players
                                        val finalMatchSettings = matchSettings.copy(
                                            maxPlayersPerTeam = maxOf(
                                                team1.players.size,
                                                team2.players.size,
                                                11
                                            )
                                        )
                                        intent.putExtra(
                                            "match_settings",
                                            gson.toJson(finalMatchSettings)
                                        )

                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Both team needs to have same number of players",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                else {
                                    Toast.makeText(context, "Each team needs at least $minPlayersPerTeam player!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = selectedGroup != null && team1.players.isNotEmpty() && team2.players.isNotEmpty()
                        )
                    }
                }
            }
        }

        // Team 1 multi-add
        if (showTeam1Dialog) {
            val occupied = (team1.players + team2.players).map { it.name }.toSet() +
                    listOfNotNull(jokerPlayer?.name)
            PlayerMultiSelectDialog(
                title = "Add Players to ${team1.name}",
                occupiedNames = occupied,
                onConfirm = { names ->
                    val newPlayers = team1.players.toMutableList()
                    names.forEach { newPlayers.add(Player(it)) }
                    // Optional: keep sorted
                    newPlayers.sortBy { it.name.lowercase() }
                    team1 = team1.copy(players = newPlayers)
                    showTeam1Dialog = false
                },
                onDismiss = { showTeam1Dialog = false }
            )
        }

// Team 2 multi-add
        if (showTeam2Dialog) {
            val occupied = (team1.players + team2.players).map { it.name }.toSet() +
                    listOfNotNull(jokerPlayer?.name)
            PlayerMultiSelectDialog(
                title = "Add Players to ${team2.name}",
                occupiedNames = occupied,
                onConfirm = { names ->
                    val newPlayers = team2.players.toMutableList()
                    names.forEach { newPlayers.add(Player(it)) }
                    newPlayers.sortBy { it.name.lowercase() }
                    team2 = team2.copy(players = newPlayers)
                    showTeam2Dialog = false
                },
                onDismiss = { showTeam2Dialog = false }
            )
        }

        if (showJokerDialog) {
            PlayerSuggestionDialog(
                title = "Select Joker",
                selectedPlayers = (team1.players + team2.players).map { it.name },
                currentTeamName = "Joker (Both Teams)",
                onPlayerSelected = { playerName ->
                    jokerPlayer = Player(playerName, isJoker = true)
                    showJokerDialog = false
                },
                onDismiss = { showJokerDialog = false },
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionCard {
        ListItem(
            headlineContent = { SectionTitle(title) },
            trailingContent = {
                Icon(
                    if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.PlayArrow,
                    null
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
        )
        AnimatedVisibility(visible = isExpanded) {
            Column(Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.width(110.dp),
                placeholder = { Label("0") }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}


@Composable
fun SwitchSettingRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (description != null) Label(description)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    Spacer(Modifier.height(8.dp))
}


// Updated EnhancedTeamCard without maxPlayers parameter
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnhancedTeamCard(
    team: Team,
    jokerPlayer: Player?,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Player) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Team Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = team.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = "${team.players.size} players",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Players List
            if (team.players.isNotEmpty()) {
                Text(
                    text = "Players:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                // after "Players:" text
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    team.players.forEach { player ->
                        AssistChip(
                            onClick = {},
                            label = { Text(player.name) },
                            trailingIcon = {
                                IconButton(onClick = { onRemovePlayer(player) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Add Player Button
            Button(
                onClick = onAddPlayer,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Player")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Player")
            }
        }
    }
}

@Composable
private fun PickFromGroupButton(
    label: String,
    onGroupPicked: (PlayerGroup) -> Unit
) {
    val context = LocalContext.current
    val storage = remember { PlayerGroupStorageManager(context) }
    var open by remember { mutableStateOf(false) }

    FilledTonalButton(onClick = { open = true }) {
        Icon(Icons.Default.Home, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }

    if (open) {
        val groups = storage.getAllGroups()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Choose Group") },
            text = {
                if (groups.isEmpty()) {
                    Text("No groups yet.")
                } else {
                    LazyColumn(Modifier.height(300.dp)) {
                        items(groups) { g ->
                            ListItem(
                                headlineContent = { Text(g.name) },
                                supportingContent = { Text("${g.players.size} players", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.clickable {
                                    onGroupPicked(g)
                                    open = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}
