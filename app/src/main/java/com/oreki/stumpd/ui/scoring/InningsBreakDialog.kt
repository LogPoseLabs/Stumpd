package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EnhancedInningsBreakDialog(
    runs: Int, wickets: Int, overs: Int, balls: Int,
    battingTeam: String,
    battingPlayers: List<Player>, bowlingPlayers: List<Player>,
    totalOvers: Int,
    onStartSecondInnings: () -> Unit,
    shortPitch: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            Button(
                onClick = onStartSecondInnings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start 2nd Innings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        "🏏",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "First Innings Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(battingTeam, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            Text("$runs/$wickets ($overs.$balls/$totalOvers ov)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                item { Text("Top Batting", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary) }
                items(battingPlayers.sortedByDescending { it.runs }.take(3)) { p ->
                    StatRowCompact(name = p.name, left = "${p.runs}${if (!p.isOut && p.ballsFaced > 0) "*" else ""} (${p.ballsFaced})", right = if (shortPitch) "4s:${p.fours}" else "4s:${p.fours} 6s:${p.sixes}")
                }
                item { Text("Top Bowling", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFF5722)) }
                items(bowlingPlayers.sortedByDescending { it.wickets }.take(3)) { p ->
                    StatRowCompact(name = p.name, left = "${p.wickets}/${p.runsConceded}", right = "${"%.1f".format(p.oversBowled)} ov - Eco ${"%.1f".format(p.economy)}")
                }
            }
        }
    )
}

@Composable
internal fun StatRowCompact(name: String, left: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(left, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(right, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
