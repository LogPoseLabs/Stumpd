package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.ui.scoring.ScoringActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.oreki.stumpd.ui.theme.PrimaryCta
import com.oreki.stumpd.ui.theme.SectionTitle
import com.oreki.stumpd.ui.theme.StumpdTheme
import androidx.compose.foundation.layout.FlowRow
import com.oreki.stumpd.ui.theme.SectionCard
import com.oreki.stumpd.ui.theme.StumpdTopBar
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.input.KeyboardType
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.ui.theme.Label
import com.oreki.stumpd.viewmodel.TeamSetupViewModel
import com.oreki.stumpd.viewmodel.TeamSetupViewModelFactory
import com.oreki.stumpd.viewmodel.ToastEvent
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// Result class for better handling
sealed class TeamGenerationResult {
    object InsufficientPlayers : TeamGenerationResult()
    data class Success(
        val team1Players: List<Player>,
        val team2Players: List<Player>,
        val jokerPlayer: Player?,
        val team1Captain: Player,
        val team2Captain: Player
    ) : TeamGenerationResult()
}

class TeamSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val defaultGroupId = intent.getStringExtra("default_group_id")
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val factory = TeamSetupViewModelFactory(application, defaultGroupId)
                    val vm: TeamSetupViewModel = viewModel(factory = factory)
                    TeamSetupScreen(vm = vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeamSetupScreen(vm: TeamSetupViewModel) {
    val context = LocalContext.current

    val matchSettings = vm.matchSettings
    val team1 = vm.team1
    val team2 = vm.team2
    val jokerPlayer = vm.jokerPlayer
    val selectedGroup = vm.selectedGroup
    val allPlayers = vm.allPlayers
    val expandedSections = vm.expandedSections

    // Collect toast events
    LaunchedEffect(Unit) {
        vm.toastEvent.collect { event ->
            when (event) {
                is ToastEvent.Short -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ToastEvent.Long -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Quick Match Setup",
                subtitle = "Configure match settings and teams",
                onBack = {
                    val intent = Intent(context, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Group Selection",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Group",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedGroup?.name ?: "No group selected",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            // Basic Settings Section
            item {
                SettingsSection(
                    title = "Match Format",
                    isExpanded = expandedSections.contains("basic"),
                    onToggle = { vm.toggleSection("basic") },
                    icon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                ) {
                    // Total Overs with common presets
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Total Overs",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = vm.oversText,
                                onValueChange = { value ->
                                    vm.oversText = value
                                    value.toIntOrNull()?.let { overs ->
                                        if (overs > 0 && overs <= 50) {
                                            vm.matchSettings = vm.matchSettings.copy(totalOvers = overs)
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.width(110.dp),
                                placeholder = { Label("Overs") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        
                        // Quick presets
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(5, 10, 15, 20).forEach { preset ->
                                FilterChip(
                                    selected = matchSettings.totalOvers == preset,
                                    onClick = {
                                        vm.oversText = preset.toString()
                                        vm.matchSettings = vm.matchSettings.copy(totalOvers = preset)
                                    },
                                    label = { Text("${preset} overs", fontSize = 12.sp) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Extras Settings Section
            item {
                SettingsSection(
                    title = "Extras Settings",
                    isExpanded = expandedSections.contains("extras"),
                    onToggle = { vm.toggleSection("extras") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                ) {
                    SettingDropdownRow(
                        label = "Wide Runs",
                        value = matchSettings.wideRuns,
                        options = listOf(0, 1, 2),
                        onValueChange = { runs ->
                            vm.matchSettings = vm.matchSettings.copy(wideRuns = runs)
                            vm.wideRunsText = runs.toString()
                        }
                    )

                    SettingDropdownRow(
                        label = "No Ball Runs",
                        value = matchSettings.noballRuns,
                        options = listOf(0, 1),
                        onValueChange = { runs ->
                            vm.matchSettings = vm.matchSettings.copy(noballRuns = runs)
                            vm.noballRunsText = runs.toString()
                        }
                    )

                    SettingDropdownRow(
                        label = "Bye Runs",
                        value = matchSettings.byeRuns,
                        options = listOf(0, 1),
                        onValueChange = { runs ->
                            vm.matchSettings = vm.matchSettings.copy(byeRuns = runs)
                            vm.byeRunsText = runs.toString()
                        }
                    )

                    SettingDropdownRow(
                        label = "Leg Bye Runs",
                        value = matchSettings.legByeRuns,
                        options = listOf(0, 1),
                        onValueChange = { runs ->
                            vm.matchSettings = vm.matchSettings.copy(legByeRuns = runs)
                            vm.legByeRunsText = runs.toString()
                        }
                    )
                }
            }

            // Special Rules Section
            item {
                SettingsSection(
                    title = "Special Rules",
                    isExpanded = expandedSections.contains("special"),
                    onToggle = { vm.toggleSection("special") },
                    icon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                ) {
                    SwitchSettingRow(
                        label = "Single Side Batting",
                        description = "Allow one batsman to continue if others get out",
                        checked = matchSettings.allowSingleSideBatting,
                        onCheckedChange = {
                            vm.matchSettings = vm.matchSettings.copy(allowSingleSideBatting = it)
                        }
                    )
                    SwitchSettingRow(
                        label = "Short Pitch",
                        description = "0-4 runs available. No 6",
                        checked = matchSettings.shortPitch,
                        onCheckedChange = {
                            vm.matchSettings = vm.matchSettings.copy(shortPitch = it)
                        }
                    )

                    SwitchSettingRow(
                        label = "Enable Joker",
                        description = "A player who can bat and bowl for both teams",
                        checked = matchSettings.jokerCanBatAndBowl,
                        onCheckedChange = {
                            vm.matchSettings = vm.matchSettings.copy(jokerCanBatAndBowl = it)
                            if (!it) vm.jokerPlayer = null
                        }
                    )

                    if (matchSettings.jokerCanBatAndBowl) {
                        SettingRow(
                            label = "Joker Max Overs",
                            value = vm.jokerMaxOversText,
                            onValueChange = { value ->
                                vm.jokerMaxOversText = value
                                value.toIntOrNull()?.let { overs ->
                                    if (overs >= 1 && overs <= matchSettings.totalOvers) {
                                        vm.matchSettings = vm.matchSettings.copy(jokerMaxOvers = overs)
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
                    title = "Advanced Rules",
                    isExpanded = expandedSections.contains("advanced"),
                    onToggle = { vm.toggleSection("advanced") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                ) {
                    SettingDropdownRow(
                        label = "Max Overs per Bowler",
                        value = matchSettings.maxOversPerBowler,
                        options = (1..matchSettings.totalOvers).toList(),
                        onValueChange = { overs ->
                            vm.matchSettings = vm.matchSettings.copy(maxOversPerBowler = overs)
                            vm.maxOversPerBowlerText = overs.toString()
                        }
                    )

                    SettingRow(
                        label = "Powerplay Overs",
                        value = vm.powerplayOversText,
                        onValueChange = { value ->
                            vm.powerplayOversText = value
                            value.toIntOrNull()?.let { overs ->
                                if (overs >= 0 && overs <= matchSettings.totalOvers) {
                                    vm.matchSettings = vm.matchSettings.copy(powerplayOvers = overs)
                                }
                            }
                        }
                    )
                    
                    // Only show double runs option when powerplay is enabled
                    if (matchSettings.powerplayOvers > 0) {
                        SwitchSettingRow(
                            label = "Double Runs in Powerplay",
                            description = "All runs scored will be doubled once powerplay ends",
                            checked = matchSettings.doubleRunsInPowerplay,
                            onCheckedChange = {
                                vm.matchSettings = vm.matchSettings.copy(doubleRunsInPowerplay = it)
                            }
                        )
                    }
                }
            }

            // Team Names Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Team Names",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = team1.name,
                                onValueChange = { vm.team1 = vm.team1.copy(name = it) },
                                label = { Text("Team 1") },
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            OutlinedTextField(
                                value = team2.name,
                                onValueChange = { vm.team2 = vm.team2.copy(name = it) },
                                label = { Text("Team 2") },
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
                // Quick Actions Card for Team Setup
                if (selectedGroup != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Quick Actions",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Use Last Teams Button
                            OutlinedButton(
                                onClick = { vm.loadLastTeams() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        "Use Last Teams",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "from ${selectedGroup!!.name}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Generate Random Teams Button
                if (selectedGroup != null) {
                    Button(
                        onClick = { vm.generateRandomTeams() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                "Generate Random Teams",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "with auto-assigned captains",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
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
                                vm.showTeam1Dialog = true
                            }
                        },
                        onRemovePlayer = { player -> vm.removePlayerFromTeam1(player) },
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
                                vm.showTeam2Dialog = true
                            }
                        },
                        onRemovePlayer = { player -> vm.removePlayerFromTeam2(player) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Joker Section
            if (matchSettings.jokerCanBatAndBowl) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🃏", fontSize = 18.sp)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Joker Player",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Can bat & bowl for both teams (max ${matchSettings.jokerMaxOvers} overs)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (jokerPlayer == null) {
                                FilledTonalButton(
                                    onClick = { 
                                        if (selectedGroup == null) {
                                            Toast.makeText(context, "Select a group first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            vm.showJokerDialog = true
                                        } 
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Joker")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Select Joker")
                                }
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = jokerPlayer!!.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Row {
                                            TextButton(onClick = { vm.showJokerDialog = true }) { 
                                                Text("Change", fontSize = 13.sp) 
                                            }
                                            TextButton(onClick = { vm.jokerPlayer = null }) { 
                                                Text("Remove", fontSize = 13.sp) 
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Toss Section
            if (team1.players.isNotEmpty() && team2.players.isNotEmpty()) {
                item {
                    TossSelectionCard(
                        team1Name = team1.name,
                        team2Name = team2.name
                    ) { winner, choice ->
                        vm.tossWinner = winner
                        vm.tossChoice = choice
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
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                                    if (matchSettings.jokerCanBatAndBowl) append("Joker")
                                    if (matchSettings.shortPitch && isNotEmpty()) append(", ")
                                    if (matchSettings.shortPitch) append("Short pitch")
                                    if (isEmpty()) append("Standard Rules")
                                },
                                fontWeight = FontWeight.Thin,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        PrimaryCta(
                            text = "Start ${matchSettings.totalOvers} overs Match",
                            onClick = {
                                val error = vm.validateMatchSetup()
                                if (error != null) {
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    return@PrimaryCta
                                }

                                val intent = Intent(context, ScoringActivity::class.java)
                                intent.putExtra("group_id", selectedGroup?.id ?: "")
                                intent.putExtra("group_name", selectedGroup?.name ?: "")
                                intent.putExtra("team1_name", team1.name)
                                intent.putExtra("team2_name", team2.name)
                                intent.putExtra("joker_name", jokerPlayer?.name ?: "")

                                val team1Captain = vm.extractCaptainFromTeamName(team1.name) ?: ""
                                val team2Captain = vm.extractCaptainFromTeamName(team2.name) ?: ""
                                intent.putExtra("team1_captain", team1Captain)
                                intent.putExtra("team2_captain", team2Captain)

                                intent.putExtra("team1_players", team1.players.map { it.name }.toTypedArray())
                                intent.putExtra("team2_players", team2.players.map { it.name }.toTypedArray())
                                intent.putExtra("team1_player_ids", team1.players.map { it.id.value }.toTypedArray())
                                intent.putExtra("team2_player_ids", team2.players.map { it.id.value }.toTypedArray())
                                intent.putExtra("toss_winner", vm.tossWinner ?: "")
                                intent.putExtra("toss_choice", vm.tossChoice ?: "")

                                val finalMatchSettings = vm.buildFinalMatchSettings()
                                intent.putExtra("match_settings", vm.gson.toJson(finalMatchSettings))

                                vm.saveLastTeams()
                                context.startActivity(intent)
                            },
                            enabled = selectedGroup != null && team1.players.isNotEmpty() && team2.players.isNotEmpty()
                        )
                    }
                }
            }
        }
        val allowedIds = vm.allowedIds
        // Team 1 multi-add
        if (vm.showTeam1Dialog) {
            val occupied = (team1.players + team2.players).map { it.id.value }.toSet() +
                    listOfNotNull(jokerPlayer?.id?.value)
            PlayerMultiSelectDialog(
                title = "Add Players to ${team1.name}",
                occupiedIds = occupied,
                allowedPlayerIds = allowedIds,
                onConfirm = { ids -> vm.addPlayersToTeam1(ids) },
                onDismiss = { vm.showTeam1Dialog = false }
            )
        }

// Team 2 multi-add
        if (vm.showTeam2Dialog) {
            val occupied = (team1.players + team2.players).map { it.id.value }.toSet() +
                    listOfNotNull(jokerPlayer?.id?.value)
            PlayerMultiSelectDialog(
                title = "Add Players to ${team2.name}",
                occupiedIds = occupied,
                allowedPlayerIds = allowedIds,
                onConfirm = { ids -> vm.addPlayersToTeam2(ids) },
                onDismiss = { vm.showTeam2Dialog = false }
            )
        }

// Joker single-select (filtered by group)
        if (vm.showJokerDialog) {
            val occupied = (team1.players + team2.players).map { it.id.value }.toSet()
            
            AlertDialog(
                onDismissRequest = { vm.showJokerDialog = false },
                title = { Text("Select Joker") },
                text = {
                    LazyColumn(Modifier.height(400.dp)) {
                        val availablePlayers = allPlayers.filter { (id, _) -> 
                            // Only show players that are:
                            // 1. In the allowed group (if group is selected)
                            // 2. Not already in either team
                            (allowedIds.isEmpty() || allowedIds.contains(id)) && 
                            !occupied.contains(id)
                        }
                        
                        if (availablePlayers.isEmpty()) {
                            item {
                                Text(
                                    "No available players. All players are already in teams.",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(availablePlayers.toList().sortedBy { it.second }) { (playerId, playerName) ->
                                ListItem(
                                    headlineContent = { Text(playerName) },
                                    modifier = Modifier.clickable {
                                        vm.selectJoker(playerId, playerName)
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Person, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { vm.showJokerDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    
}

@Composable
fun SettingsSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                color = if (isExpanded) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else 
                    Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            icon()
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) 
                            Icons.Default.KeyboardArrowUp 
                        else 
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    content()
                }
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
                placeholder = { Label("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingDropdownRow(
    label: String,
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .width(110.dp)
                        .menuAnchor(),
                    onClick = { expanded = true },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = value.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Select",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.toString()) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            },
                            leadingIcon = {
                                if (value == option) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TossSelectionCard(
    team1Name: String,
    team2Name: String,
    onTossSelected: (winner: String, choice: String) -> Unit
) {
    var battingFirst by rememberSaveable { mutableStateOf<String?>(null) }
    var isFlipping by remember { mutableStateOf(false) }
    var coinSide by remember { mutableStateOf<String?>(null) }
    var tossWinner by remember { mutableStateOf<String?>(null) }
    var rotationAngle by remember { mutableStateOf(0f) }

    val teamNames = listOf(team1Name, team2Name)

    // Animation for coin flip
    val rotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
        label = "coin_flip"
    )

    LaunchedEffect(isFlipping) {
        if (isFlipping) {
            rotationAngle += 1800f // 5 full rotations for dramatic effect
            kotlinx.coroutines.delay(2000) // 2 seconds of flipping
            
            // Determine coin side (Heads or Tails)
            val result = listOf("Heads", "Tails").random()
            coinSide = result
            
            // Just show the result, don't auto-select
            isFlipping = false
        }
    }

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
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Who Bats First?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Coin Toss Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Flip a Coin to Decide",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // Coin animation
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                rotationY = rotation
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                coinSide != null -> MaterialTheme.colorScheme.tertiary
                                isFlipping -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.fillMaxSize(),
                            shadowElevation = if (isFlipping) 8.dp else 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = when {
                                        coinSide != null -> coinSide!! // Show "Heads" or "Tails"
                                        isFlipping -> "🪙"
                                        else -> "🪙"
                                    },
                                    fontSize = if (coinSide != null) 16.sp else 40.sp,
                                    fontWeight = if (coinSide != null) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Show coin result
                    if (coinSide != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Result: $coinSide",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    
                    Button(
                        onClick = {
                            if (!isFlipping) {
                                isFlipping = true
                            }
                        },
                        enabled = !isFlipping,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Autorenew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isFlipping) "Flipping..." else "Flip Coin")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Manual selection
            Text(
                "Select Team to Bat First:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                teamNames.forEach { name ->
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        onClick = {
                            battingFirst = name
                            onTossSelected(name, "Batting first")
                        },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (battingFirst == name)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = if (battingFirst == name) 2.dp else 1.dp
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = if (battingFirst == name) FontWeight.Bold else FontWeight.Medium,
                                    color = if (battingFirst == name)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                if (battingFirst == name) {
                                    Text(
                                        text = "Bats First",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Team Header with Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = team.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${team.players.size} players",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Players List
            if (team.players.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    team.players.forEach { player ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { 
                                Text(
                                    player.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemovePlayer(player) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No players added",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Add Player Button
            FilledTonalButton(
                onClick = onAddPlayer,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Player")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Add Player",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
