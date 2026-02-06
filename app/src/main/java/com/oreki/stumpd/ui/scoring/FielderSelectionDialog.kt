package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FielderSelectionDialog(
    wicketType: WicketType,
    bowlingTeamPlayers: List<Player>,
    jokerPlayer: Player?,
    jokerAvailableForFielding: Boolean,
    currentBowler: Player?,
    onFielderSelected: (Player?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFielder by remember { mutableStateOf<Player?>(null) }
    
    // Build the list of available fielders (bowling team + joker if available)
    // For stumping, exclude the bowler (bowler cannot stump)
    val availableFielders = buildList {
        addAll(bowlingTeamPlayers)
        // Only add joker if they're not already in the bowling team (prevents duplicates)
        if (jokerAvailableForFielding && jokerPlayer != null &&
            !bowlingTeamPlayers.any { it.name == jokerPlayer.name }) {
            add(jokerPlayer)
        }
    }.filter { player ->
        // Bowler cannot stump the batsman
        if (wicketType == WicketType.STUMPED) {
            player.name != currentBowler?.name
        } else {
            true
        }
    }.distinctBy { it.name } // Extra safety to prevent any duplicates
    
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
                        when (wicketType) {
                            WicketType.CAUGHT -> Icons.Default.SportsHandball
                            WicketType.STUMPED -> Icons.Default.Bolt
                            else -> Icons.Default.SportsHandball
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            Column {
                Text(
                    text = when (wicketType) {
                        WicketType.CAUGHT -> "Who took the catch?"
                        WicketType.STUMPED -> "Who stumped?"
                            WicketType.RUN_OUT -> "Who ran them out?"
                        else -> "Select Fielder"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (wicketType == WicketType.STUMPED) {
                    Text(
                            text = "Bowler cannot stump",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option to skip fielder selection
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedFielder = null },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selectedFielder == null) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = if (selectedFielder == null) 2.dp else 1.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Skip (No fielder credit)",
                                fontWeight = if (selectedFielder == null) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp
                        )
                        }
                    }
                }
                
                // Fielding team players (including joker if available, no duplicates)
                items(availableFielders) { player ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedFielder = player },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selectedFielder == player) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = if (selectedFielder == player) 2.dp else 1.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (player.isJoker)
                                        MaterialTheme.colorScheme.tertiary
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                            Text(
                                text = player.name,
                                    fontWeight = if (selectedFielder == player) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp
                            )
                            }
                            if (player.isJoker) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        "🃏",
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (selectedFielder == player) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onFielderSelected(selectedFielder) }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
