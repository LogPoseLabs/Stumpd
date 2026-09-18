package com.oreki.stumpd.ui.scoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.domain.model.SuperOverInnings

/**
 * The two moments a super over needs a decision.
 *
 * It has to come *before* the match-complete dialog, which writes the match the instant it appears
 * — so this is where a tie is either broken or accepted, and nothing is saved until it has been.
 */

/**
 * Scores level: play a super over, or call it a tie?
 *
 * Shown at the end of a tied match, and again after a super over that was itself level. The way out
 * matters as much as the way on — light fails, people go home — so declining is a first-class
 * button rather than a dismissal.
 */
@Composable
fun SuperOverOfferDialog(
    scoresLevelAt: Int,
    battingFirstInSuperOver: String,
    superOversPlayed: Int,
    onStart: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = if (superOversPlayed == 0) "SCORES LEVEL" else "STILL LEVEL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (superOversPlayed == 0) {
                        "Tied at $scoresLevelAt"
                    } else {
                        "The Super Over was tied too"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Text(
                text = "$battingFirstInSuperOver bats first — one over, two wickets.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (superOversPlayed == 0) "Play a Super Over" else "Play another",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text("Leave it a tie")
            }
        },
    )
}

/** Between the two halves of a super over: what was made, and what it takes to beat it. */
@Composable
fun SuperOverIntervalDialog(
    justBowled: SuperOverInnings,
    chasingTeam: String,
    onStart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SUPER OVER",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${justBowled.battingTeam} ${justBowled.runs}/${justBowled.wickets}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "$chasingTeam needs ${justBowled.runs + 1} to win",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "One over, two wickets. Level again and you can play another.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Start $chasingTeam's over",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
    )
}
