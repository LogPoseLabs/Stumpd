package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.oreki.stumpd.ui.theme.StumpdTheme

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

    // Calculate dynamic max players (actual team sizes + joker if applicable)
    val calculatedMaxPlayers = maxOf(team1.players.size, team2.players.size) +
            if (jokerPlayer != null && matchSettings.jokerCountsForBothTeams) 1 else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        val intent = Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                        (context as androidx.activity.ComponentActivity).finish()
                    },
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚡ Quick Match Setup",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Configure match settings and teams",
                        fontSize = 14.sp,
                        color = Color.Gray,
                    )
                }
            }
        }

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
                    value =  byeRunsText,
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
                    label = "Enable Joker Player",
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

                    SwitchSettingRow(
                        label = "Joker Counts for Both Teams",
                        description = "Joker player adds to both team sizes",
                        checked = matchSettings.jokerCountsForBothTeams,
                        onCheckedChange = {
                            matchSettings = matchSettings.copy(jokerCountsForBothTeams = it)
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
                    onAddPlayer = { showTeam1Dialog = true },
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
                    onAddPlayer = { showTeam2Dialog = true },
                    onRemovePlayer = { player ->
                        val newPlayers = team2.players.toMutableList()
                        newPlayers.remove(player)
                        team2 = team2.copy(players = newPlayers)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Joker Player Section
        if (matchSettings.jokerCanBatAndBowl) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "🃏 Joker Player (Optional)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = "A player who can bat and bowl for both teams (max ${matchSettings.jokerMaxOvers} overs)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        if (jokerPlayer == null) {
                            Button(
                                onClick = { showJokerDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Joker")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Joker Player")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "🃏 ${jokerPlayer!!.name}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )

                                Row {
                                    TextButton(onClick = { showJokerDialog = true }) {
                                        Text("Change")
                                    }
                                    TextButton(onClick = { jokerPlayer = null }) {
                                        Text("Remove")
                                    }
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
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "📊 Match Summary",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Teams:", fontSize = 12.sp, color = Color.Gray)
                        Text("${team1.players.size} v ${team2.players.size}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    if (jokerPlayer != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Joker:", fontSize = 12.sp, color = Color.Gray)
                            Text("${jokerPlayer!!.name}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Special:", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = buildString {
                                if (matchSettings.allowSingleSideBatting) append("Single Side")
                                if (matchSettings.jokerCanBatAndBowl && isNotEmpty()) append(", ")
                                if (matchSettings.jokerCanBatAndBowl) append("Joker Enabled")
                                if (isEmpty()) append("Standard Rules")
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val minPlayersPerTeam = 2
                            val team1Size = team1.players.size
                            val team2Size = team2.players.size
                            if (team1Size >= minPlayersPerTeam && team2Size >= minPlayersPerTeam) {
                                if (team1Size == team2Size) {
                                    val intent = Intent(context, ScoringActivity::class.java)

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        enabled = team1.players.isNotEmpty() && team2.players.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start Match")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start ${matchSettings.totalOvers} overs Match",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    // Player Selection Dialogs remain the same...
    if (showTeam1Dialog) {
        PlayerSuggestionDialog(
            title = "Add Player to ${team1.name}",
            selectedPlayers = (team1.players + team2.players).map { it.name } +
                    listOfNotNull(jokerPlayer?.name),
            currentTeamName = team1.name,
            onPlayerSelected = { playerName ->
                val newPlayers = team1.players.toMutableList()
                newPlayers.add(Player(playerName))
                team1 = team1.copy(players = newPlayers)
                showTeam1Dialog = false
            },
            onDismiss = { showTeam1Dialog = false },
        )
    }

    if (showTeam2Dialog) {
        PlayerSuggestionDialog(
            title = "Add Player to ${team2.name}",
            selectedPlayers = (team1.players + team2.players).map { it.name } +
                    listOfNotNull(jokerPlayer?.name),
            currentTeamName = team2.name,
            onPlayerSelected = { playerName ->
                val newPlayers = team2.players.toMutableList()
                newPlayers.add(Player(playerName))
                team2 = team2.copy(players = newPlayers)
                showTeam2Dialog = false
            },
            onDismiss = { showTeam2Dialog = false },
        )
    }

    if (showJokerDialog) {
        PlayerSuggestionDialog(
            title = "Select Joker Player",
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

// Helper Composables for Settings Sections
@Composable
fun SettingsSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.PlayArrow,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(80.dp),
            singleLine = true
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SwitchSettingRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            description?.let { desc ->
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

// Updated EnhancedTeamCard without maxPlayers parameter
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    color = Color.Gray,
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

                team.players.forEach { player ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Player",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = player.name,
                                fontSize = 14.sp,
                            )
                        }

                        IconButton(
                            onClick = { onRemovePlayer(player) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove Player",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

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

            // Show joker info if selected
            jokerPlayer?.let { joker ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🃏",
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Joker: ${joker.name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
