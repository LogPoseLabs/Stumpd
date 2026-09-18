package com.oreki.stumpd.ui.scoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.domain.model.Player
import com.oreki.stumpd.viewmodel.LiveCorrections

/**
 * The mid-match "fix a mistake" surfaces.
 *
 * Three mistakes, three dialogs, and one rule throughout: each states what is currently recorded
 * before offering to change it, because the whole failure mode being fixed is somebody typing the
 * wrong name without noticing. A correction that can't be made safely says why here rather than
 * silently doing something approximate — the post-match editor recomputes the whole scorecard and
 * can do what these can't.
 */
@Composable
fun FixMenuDialog(
    corrections: LiveCorrections,
    onFixWicket: () -> Unit,
    onFixBowler: () -> Unit,
    onFixLastBall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val wicket = corrections.lastWicketSummary()
    val canSwapWicket = corrections.swapCandidatesForLastWicket().isNotEmpty()
    val over = corrections.reassignableOver()
    val overBowler = over?.let { corrections.bowlerOfOver(it) }
    val lastBall = corrections.lastBallSummary()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix a mistake", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Changes the record without losing the ball. Each one can be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                FixOption(
                    label = "Wrong batter out",
                    detail = when {
                        wicket == null -> "No wicket has fallen yet"
                        !canSwapWicket -> "$wicket — the other batter from that stand has gone, " +
                            "so this needs the editor after the match"
                        else -> wicket
                    },
                    enabled = canSwapWicket,
                    onClick = onFixWicket,
                    tag = "fix_wicket",
                )
                FixOption(
                    label = "Wrong bowler",
                    detail = when {
                        over == null -> "No over has been bowled yet"
                        overBowler == null -> "Over $over has more than one bowler recorded in it"
                        else -> "Any over of this innings — over $over is $overBowler"
                    },
                    enabled = over != null && overBowler != null,
                    onClick = onFixBowler,
                    tag = "fix_bowler",
                )
                FixOption(
                    label = "Wrong runs on the last ball",
                    detail = lastBall ?: "No ball has been bowled yet",
                    enabled = lastBall != null,
                    onClick = onFixLastBall,
                    tag = "fix_last_ball",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun FixOption(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag(tag),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Moves the last wicket to the other batter from the stand that ended. */
@Composable
fun FixWicketDialog(
    corrections: LiveCorrections,
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates = corrections.swapCandidatesForLastWicket()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who was actually out?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    corrections.lastWicketSummary() ?: "No wicket recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "The dismissal moves across; the runs, the balls faced and the bowler's " +
                        "wicket stay where they are.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                candidates.forEach { player ->
                    PlayerChoice(player) { onResult(corrections.reassignLastWicket(player.name)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Moves an over's balls, runs and wickets to another bowler.
 *
 * Any over of the innings, not only the one in progress: a wrong bowler is usually spotted a
 * couple of overs later, and the whole point is to fix it there and then rather than discovering it
 * on the saved scorecard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FixBowlerDialog(
    corrections: LiveCorrections,
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val overs = corrections.reassignableOvers()
    var selectedOver by remember { mutableStateOf(corrections.reassignableOver()) }
    val over = selectedOver
    val current = over?.let { corrections.bowlerOfOver(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wrong bowler", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (overs.size > 1) {
                    Text("Which over?", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        overs.forEach { candidate ->
                            FilterChip(
                                selected = candidate == over,
                                onClick = { selectedOver = candidate },
                                label = {
                                    Text(
                                        "$candidate · ${corrections.bowlerOfOver(candidate).orEmpty()}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    if (over == null) {
                        "No over has been bowled yet."
                    } else {
                        "Over $over is recorded as $current. Every ball of it moves, with the runs " +
                            "and any wickets it took."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (over != null) {
                    corrections.bowlerCandidates(over).forEach { player ->
                        PlayerChoice(player) {
                            onResult(corrections.reassignOverBowler(over, player.name))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Re-enters the last ball.
 *
 * The numbers cover the common slip — a two typed as a one — while "Clear it" hands the ball back
 * to the keypad for anything the numbers can't say: an extra recorded as runs, or a wicket that
 * wasn't one.
 */
@Composable
fun FixLastBallDialog(
    corrections: LiveCorrections,
    shortPitch: Boolean,
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val topRun = if (shortPitch) 4 else 6
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What was the last ball?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    corrections.lastBallSummary() ?: "No ball recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("Runs off the bat", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (0..topRun / 2).forEach { runs -> RestateButton(runs) { onResult(corrections.restateLastDelivery(runs)) } }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (topRun / 2 + 1..topRun).forEach { runs -> RestateButton(runs) { onResult(corrections.restateLastDelivery(runs)) } }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { onResult(corrections.clearLastDelivery()) }) {
                    Text("It was something else — clear the ball")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RowScope.RestateButton(runs: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).testTag("restate_$runs"),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(runs.toString(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayerChoice(player: Player, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(),
    ) {
        Text(player.name, fontWeight = FontWeight.SemiBold)
    }
}
