package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EnhancedPlayerSelectionDialog(
    title: String,
    players: List<Player>,
    jokerPlayer: Player? = null,
    currentStrikerIndex: Int? = null,
    currentNonStrikerIndex: Int? = null,
    allowSingleSide: Boolean = false,
    totalWickets: Int = 0, // Add this parameter
    battingTeamPlayers: List<Player> = emptyList(), // Add this parameter
    bowlingTeamPlayers: List<Player> = emptyList(), // Add this parameter
    jokerOversThisInnings: Double = 0.0,
    jokerOutInCurrentInnings: Boolean = false,
    onPlayerSelected: (Player) -> Unit,
    onDismiss: () -> Unit,
    matchSettings: MatchSettings,
    otherEndName: String? = null,
    /**
     * Returns why a player cannot be picked, or null when they can. Used to show the reason on
     * the row and stop the tap, instead of accepting the tap and rejecting it with a toast.
     */
    ineligibleReason: ((Player) -> String?)? = null,
) {
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
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val availablePlayers = players.filterIndexed { index, player ->
                    // Exclude jokers from the main list since they're handled separately
                    if (player.isJoker) return@filterIndexed false
                    // Allow retired players to be shown (they can return), but not out players
                    if (player.isOut && !player.isRetired) return@filterIndexed false
                    val pickingStriker = title.contains("Striker", ignoreCase = true) && !title.contains("Non", ignoreCase = true)
                    val pickingNonStriker = title.contains("Non-Striker", ignoreCase = true)

                    fun sameName(a: String?, b: String?) =
                        a != null && b != null && a.trim().equals(b.trim(), ignoreCase = true)

                    if (pickingStriker) {
                        val excludeByIndex = (index == currentNonStrikerIndex)
                        val excludeByName = sameName(player.name, otherEndName)
                        !(excludeByIndex || excludeByName)
                    } else if (pickingNonStriker) {
                        val excludeByIndex = (index == currentStrikerIndex)
                        val excludeByName = sameName(player.name, otherEndName)
                        !(excludeByIndex || excludeByName)
                    } else {
                        true
                    }
                }


                items(availablePlayers) { player ->
                    val blockedReason = ineligibleReason?.invoke(player)
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onPlayerSelected(player) },
                        enabled = blockedReason == null,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (player.isJoker)
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (player.isJoker)
                                            MaterialTheme.colorScheme.tertiary
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = player.name,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (player.isJoker) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                "🃏",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            if (title.contains("Striker", ignoreCase = true)) {
                                if (player.ballsFaced > 0 || player.runs > 0) {
                                    Text(
                                            text = "${player.runs}${if (!player.isOut && player.ballsFaced > 0) "*" else ""} (${player.ballsFaced}) • SR: ${"%.1f".format(player.strikeRate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (player.fours > 0 || player.sixes > 0) {
                                        Text(
                                                text = "4s: ${player.fours} • 6s: ${player.sixes}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Text(
                                            text = if (player.isJoker) "Available for both teams" else "Yet to bat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                            if (title.contains("Bowler", ignoreCase = true)) {
                                if (player.ballsBowled > 0 || player.wickets > 0 || player.runsConceded > 0) {
                                    Text(
                                            text = "${player.wickets}/${player.runsConceded} (${"%.1f".format(player.oversBowled)} ov) • Eco: ${"%.1f".format(player.economy)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                            text = if (player.isJoker) "Available for both teams" else "Yet to bowl",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                            if (blockedReason != null) {
                                Text(
                                    text = blockedReason,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // FIXED: Proper joker availability logic based on rules
                jokerPlayer?.let { joker ->
                    val showJoker = when {
                        title.contains("Striker", ignoreCase = true) -> {
                            // Joker can bat only if he's not already in the batting side or is marked out
                            val jokerInBatting = battingTeamPlayers.any { it.isJoker && !it.isOut }
                            val wicketsFallen = totalWickets > 0
                            !jokerInBatting && !jokerOutInCurrentInnings && wicketsFallen
                        }
                        title.contains("Bowler", ignoreCase = true) -> {
                            // Joker can bowl only if the match allows it, he's not currently
                            // batting, and he's under his over cap.
                            val jokerCurrentlyBatting = battingTeamPlayers.any { it.isJoker && !it.isOut }
                            val withinCap = jokerOversThisInnings < matchSettings.jokerMaxOvers
                            matchSettings.jokerCanBowl && !jokerCurrentlyBatting && withinCap
                        }
                        else -> false
                    }

                    if (showJoker) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onPlayerSelected(joker) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ),
                                elevation = CardDefaults.cardElevation(4.dp)
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
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.tertiary
                                        ) {
                                    Text(
                                                "🃏",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = joker.name,
                                        fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = if (title.contains("Striker", ignoreCase = true))
                                                    "Available to bat"
                                        else
                                                    "Available to bowl",
                                        style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Show empty state only if no regular players and no joker available
                if (availablePlayers.isEmpty() &&
                    (jokerPlayer == null ||
                            (title.contains("Striker", ignoreCase = true) &&
                                    (!jokerPlayer.let { joker ->
                                        val notInBattingTeam = !battingTeamPlayers.any { it.isJoker }
                                        val notOut = !joker.isOut
                                        val wicketsFallen = totalWickets > 0
                                        val notOpeningPair = !(totalWickets == 0 && title.contains("First", ignoreCase = true))
                                        notInBattingTeam && notOut && (wicketsFallen || notOpeningPair)
                                    })))) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Text(
                                text = if (title.contains("Striker", ignoreCase = true)) {
                                    if (allowSingleSide) "All batsmen are out" else "No available batsmen"
                                } else {
                                    "No available bowlers"
                                },
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
