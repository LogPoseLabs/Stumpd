package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.google.gson.Gson
import com.oreki.stumpd.ui.theme.StumpdTheme

class TeamSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get match settings from intent (if passed from MatchSettingsActivity)
        val matchSettingsJson = intent.getStringExtra("match_settings") ?: ""

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TeamSetupScreen(matchSettingsJson = matchSettingsJson)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSetupScreen(matchSettingsJson: String = "") {
    val context = LocalContext.current
    val gson = Gson()
    val settingsManager = remember { MatchSettingsManager(context) }

    // Parse match settings or use defaults
    val matchSettings = remember {
        try {
            if (matchSettingsJson.isNotEmpty()) {
                gson.fromJson(matchSettingsJson, MatchSettings::class.java)
            } else {
                settingsManager.getDefaultMatchSettings()
            }
        } catch (e: Exception) {
            MatchSettings()
        }
    }

    // Team states - Initialize with MutableList
    var team1 by remember { mutableStateOf(Team("Team A", mutableListOf())) }
    var team2 by remember { mutableStateOf(Team("Team B", mutableListOf())) }
    var jokerPlayer by remember { mutableStateOf<Player?>(null) }

    // Dialog states
    var showTeam1Dialog by remember { mutableStateOf(false) }
    var showTeam2Dialog by remember { mutableStateOf(false) }
    var showJokerDialog by remember { mutableStateOf(false) }
    var showMatchSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Enhanced Header with settings access
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                    (context as androidx.activity.ComponentActivity).finish()
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
                    text = "⚡ Quick Match Setup",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "${matchSettings.matchFormat.displayName} • ${matchSettings.totalOvers} overs",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            // Match Settings Button
            IconButton(
                onClick = { showMatchSettingsDialog = true }
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Match Settings",
                    tint = Color(0xFF2E7D32)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Match Settings Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Match Configuration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "${matchSettings.totalOvers} overs • Max ${matchSettings.maxPlayersPerTeam} players",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Extras: W+${matchSettings.legSideWideRuns + matchSettings.offSideWideRuns}, NB+${matchSettings.noballRuns}, ",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    if (matchSettings.allowSingleSideBatting) {
                        Text(
                            text = "Single side batting: ON",
                            fontSize = 10.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Team Name Setup
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = team1.name,
                onValueChange = { team1 = team1.copy(name = it) },
                label = { Text("Team 1 Name") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            OutlinedTextField(
                value = team2.name,
                onValueChange = { team2 = team2.copy(name = it) },
                label = { Text("Team 2 Name") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Enhanced Team Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Team 1 Card
            EnhancedTeamCard(
                team = team1,
                jokerPlayer = jokerPlayer,
                maxPlayers = matchSettings.maxPlayersPerTeam,
                onAddPlayer = {
                    if (team1.players.size < matchSettings.maxPlayersPerTeam) {
                        showTeam1Dialog = true
                    } else {
                        Toast.makeText(context, "Maximum ${matchSettings.maxPlayersPerTeam} players allowed per team", Toast.LENGTH_SHORT).show()
                    }
                },
                onRemovePlayer = { player ->
                    // Create new MutableList without the player
                    val newPlayers = team1.players.toMutableList()
                    newPlayers.remove(player)
                    team1 = team1.copy(players = newPlayers)
                },
                modifier = Modifier.weight(1f)
            )

            // Team 2 Card
            EnhancedTeamCard(
                team = team2,
                jokerPlayer = jokerPlayer,
                maxPlayers = matchSettings.maxPlayersPerTeam,
                onAddPlayer = {
                    if (team2.players.size < matchSettings.maxPlayersPerTeam) {
                        showTeam2Dialog = true
                    } else {
                        Toast.makeText(context, "Maximum ${matchSettings.maxPlayersPerTeam} players allowed per team", Toast.LENGTH_SHORT).show()
                    }
                },
                onRemovePlayer = { player ->
                    // Create new MutableList without the player
                    val newPlayers = team2.players.toMutableList()
                    newPlayers.remove(player)
                    team2 = team2.copy(players = newPlayers)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Enhanced Joker Player Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🃏 Joker Player (Optional)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
                )
                Text(
                    text = if (matchSettings.jokerCanBatAndBowl) {
                        "A player who can bat and bowl for both teams (max ${matchSettings.jokerMaxOvers} overs)"
                    } else {
                        "Joker player is disabled in match settings"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (matchSettings.jokerCanBatAndBowl) {
                    if (jokerPlayer == null) {
                        Button(
                            onClick = { showJokerDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Joker")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Joker Player")
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
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9800)
                            )

                            Row {
                                TextButton(
                                    onClick = { showJokerDialog = true }
                                ) {
                                    Text("Change")
                                }
                                TextButton(
                                    onClick = { jokerPlayer = null }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Enhanced Start Match Button
        Button(
            onClick = {
                val minPlayersPerTeam = 1
                if (team1.players.size >= minPlayersPerTeam && team2.players.size >= minPlayersPerTeam) {
                    val intent = Intent(context, ScoringActivity::class.java)

                    // Pass team data via intent
                    intent.putExtra("team1_name", team1.name)
                    intent.putExtra("team2_name", team2.name)
                    intent.putExtra("joker_name", jokerPlayer?.name ?: "")

                    // Pass player names as string arrays
                    val team1PlayerNames = team1.players.map { it.name }.toTypedArray()
                    val team2PlayerNames = team2.players.map { it.name }.toTypedArray()

                    intent.putExtra("team1_players", team1PlayerNames)
                    intent.putExtra("team2_players", team2PlayerNames)

                    // Pass match settings
                    intent.putExtra("match_settings", gson.toJson(matchSettings))

                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Each team needs at least $minPlayersPerTeam player!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            ),
            enabled = team1.players.isNotEmpty() && team2.players.isNotEmpty()
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start Match")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start ${matchSettings.matchFormat.displayName} (${team1.players.size}v${team2.players.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Match Settings Dialog
    if (showMatchSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showMatchSettingsDialog = false },
            title = { Text("Match Settings") },
            text = { Text("Go to detailed match settings configuration?") },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(context, MatchSettingsActivity::class.java)
                        intent.putExtra("per_match", true)
                        context.startActivity(intent)
                        (context as androidx.activity.ComponentActivity).finish()
                    }
                ) {
                    Text("Configure")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMatchSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Player Selection Dialogs with Suggestions
    if (showTeam1Dialog) {
        PlayerSuggestionDialog(
            title = "Add Player to ${team1.name}",
            selectedPlayers = (team1.players + team2.players).map { it.name } +
                    listOfNotNull(jokerPlayer?.name), // Include joker player
            currentTeamName = team1.name,
            onPlayerSelected = { playerName ->
                // Create new MutableList with added player
                val newPlayers = team1.players.toMutableList()
                newPlayers.add(Player(playerName))
                team1 = team1.copy(players = newPlayers)
                showTeam1Dialog = false
            },
            onDismiss = { showTeam1Dialog = false }
        )
    }

    if (showTeam2Dialog) {
        PlayerSuggestionDialog(
            title = "Add Player to ${team2.name}",
            selectedPlayers = (team1.players + team2.players).map { it.name } +
                    listOfNotNull(jokerPlayer?.name), // Include joker player
            currentTeamName = team2.name,
            onPlayerSelected = { playerName ->
                // Create new MutableList with added player
                val newPlayers = team2.players.toMutableList()
                newPlayers.add(Player(playerName))
                team2 = team2.copy(players = newPlayers)
                showTeam2Dialog = false
            },
            onDismiss = { showTeam2Dialog = false }
        )
    }

    if (showJokerDialog) {
        PlayerSuggestionDialog(
            title = "Select Joker Player",
            selectedPlayers = (team1.players + team2.players).map { it.name }, // Exclude team players from joker selection
            currentTeamName = "Joker (Both Teams)",
            onPlayerSelected = { playerName ->
                jokerPlayer = Player(playerName, isJoker = true)
                showJokerDialog = false
            },
            onDismiss = { showJokerDialog = false }
        )
    }
}

// Enhanced team display function with settings integration
@Composable
fun EnhancedTeamCard(
    team: Team,
    jokerPlayer: Player?,
    maxPlayers: Int,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Player) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Team Header with player count and limit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = team.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${team.players.size}/$maxPlayers players",
                        fontSize = 14.sp,
                        color = if (team.players.size >= maxPlayers) Color(0xFFFF5722) else Color.Gray
                    )
                    if (team.players.size >= maxPlayers) {
                        Text(
                            text = "Team Full",
                            fontSize = 10.sp,
                            color = Color(0xFFFF5722),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Players List
            if (team.players.isNotEmpty()) {
                Text(
                    text = "Players:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                team.players.forEach { player ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Player",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = player.name,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = { onRemovePlayer(player) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove Player",
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(18.dp)
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (team.players.size < maxPlayers) Color(0xFF4CAF50) else Color.Gray
                ),
                enabled = team.players.size < maxPlayers
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Player")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (team.players.size < maxPlayers) "Add Player" else "Team Full")
            }

            // Show joker info if selected
            jokerPlayer?.let { joker ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🃏",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Joker: ${joker.name}",
                            fontSize = 12.sp,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
