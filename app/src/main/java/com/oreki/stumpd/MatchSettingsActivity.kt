package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.oreki.stumpd.ui.theme.StumpdTheme

class MatchSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isPerMatch = intent.getBooleanExtra("per_match", false)

        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MatchSettingsScreen(isPerMatch = isPerMatch)
                }
            }
        }
    }
}

@Composable
fun MatchSettingsScreen(isPerMatch: Boolean = false) {
    val context = LocalContext.current
    val settingsManager = remember { MatchSettingsManager(context) }

    var currentSettings by remember {
        mutableStateOf(
            if (isPerMatch) MatchSettings() else settingsManager.getDefaultMatchSettings()
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isPerMatch) {
                            val intent = Intent(context, TeamSetupActivity::class.java)
                            context.startActivity(intent)
                            (context as ComponentActivity).finish()
                        } else {
                            val intent = Intent(context, MainActivity::class.java)
                            context.startActivity(intent)
                            (context as ComponentActivity).finish()
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF2E7D32)
                    )
                }

                Column {
                    Text(
                        text = if (isPerMatch) "Match Settings" else "Default Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = if (isPerMatch) "Configure this match" else "Set default match rules",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Match Format Selection
        item {
            Text(
                text = "Match Format",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MatchFormat.values().forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currentSettings.matchFormat == format,
                                    onClick = {
                                        currentSettings = currentSettings.copy(
                                            matchFormat = format,
                                            totalOvers = if (format != MatchFormat.CUSTOM) format.defaultOvers else currentSettings.totalOvers
                                        )
                                    }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSettings.matchFormat == format,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(format.displayName)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Overs Setting
        if (currentSettings.matchFormat == MatchFormat.CUSTOM || currentSettings.matchFormat == MatchFormat.T20) {
            item {
                Text(
                    text = "Match Configuration",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Total Overs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Overs per innings")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        if (currentSettings.totalOvers > 1) {
                                            currentSettings = currentSettings.copy(totalOvers = currentSettings.totalOvers - 1)
                                        }
                                    }
                                ) {
                                    Text("-")
                                }

                                Text(
                                    text = "${currentSettings.totalOvers}",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedButton(
                                    onClick = {
                                        if (currentSettings.totalOvers < 50) {
                                            currentSettings = currentSettings.copy(totalOvers = currentSettings.totalOvers + 1)
                                        }
                                    }
                                ) {
                                    Text("+")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Max Players
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Players per team")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        if (currentSettings.maxPlayersPerTeam > 3) {
                                            currentSettings = currentSettings.copy(maxPlayersPerTeam = currentSettings.maxPlayersPerTeam - 1)
                                        }
                                    }
                                ) {
                                    Text("-")
                                }

                                Text(
                                    text = "${currentSettings.maxPlayersPerTeam}",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedButton(
                                    onClick = {
                                        if (currentSettings.maxPlayersPerTeam < 15) {
                                            currentSettings = currentSettings.copy(maxPlayersPerTeam = currentSettings.maxPlayersPerTeam + 1)
                                        }
                                    }
                                ) {
                                    Text("+")
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Extras Settings
        item {
            Text(
                text = "Extras & Penalties",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExtrasSettingRow("No Ball Runs", currentSettings.noballRuns) { newValue ->
                        currentSettings = currentSettings.copy(noballRuns = newValue)
                    }

                    ExtrasSettingRow("Leg Side Wide", currentSettings.legSideWideRuns) { newValue ->
                        currentSettings = currentSettings.copy(legSideWideRuns = newValue)
                    }

                    ExtrasSettingRow("Off Side Wide", currentSettings.offSideWideRuns) { newValue ->
                        currentSettings = currentSettings.copy(offSideWideRuns = newValue)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Special Rules
        item {
            Text(
                text = "Special Rules",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Single Side Batting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Single Side Batting")
                            Text(
                                text = "Allow one batsman to continue if partner gets out",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = currentSettings.allowSingleSideBatting,
                            onCheckedChange = { checked ->
                                currentSettings = currentSettings.copy(allowSingleSideBatting = checked)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Joker Rules
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Joker Can Bat & Bowl")
                            Text(
                                text = "Joker player can play for both teams",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = currentSettings.jokerCanBatAndBowl,
                            onCheckedChange = { checked ->
                                currentSettings = currentSettings.copy(jokerCanBatAndBowl = checked)
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Save/Apply Button
        item {
            Button(
                onClick = {
                    if (isPerMatch) {
                        val gson = Gson()
                        // Pass settings to next activity (TeamSetupActivity)
                        val intent = Intent(context, TeamSetupActivity::class.java)
                        intent.putExtra("match_settings", gson.toJson(currentSettings))
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    } else {
                        // Save as default settings
                        settingsManager.saveDefaultMatchSettings(currentSettings)
                        val intent = Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text(
                    text = if (isPerMatch) "Apply & Continue" else "Save Default Settings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ExtrasSettingRow(
    title: String,
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    if (currentValue > 0) {
                        onValueChange(currentValue - 1)
                    }
                }
            ) {
                Text("-")
            }

            Text(
                text = "$currentValue",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                onClick = {
                    if (currentValue < 5) {
                        onValueChange(currentValue + 1)
                    }
                }
            ) {
                Text("+")
            }
        }
    }

    if (title != "Off Side Wide") { // Don't add spacer after last item
        Spacer(modifier = Modifier.height(8.dp))
    }
}
