package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.oreki.stumpd.ui.theme.StatValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScoringButtons(
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    isInningsComplete: Boolean,
    matchSettings: MatchSettings,
    availableBatsmen: Int,
    calculatedTotalRuns: Int,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onScoreRuns: (Int) -> Unit,
    onShowExtras: () -> Unit,
    onShowWicket: () -> Unit,
    onUndo: () -> Unit,
    onWide: () -> Unit,
    onRetire: () -> Unit,
    /**
     * Opens the "fix a mistake" menu. Sits next to Undo because that's where the eye already goes
     * when something has gone wrong — and because the two are the same gesture at different
     * depths: Undo takes the ball back, Fix changes what was recorded without losing it.
     */
    onFix: (() -> Unit)? = null,
) {
    val canStartScoring = striker != null && bowler != null &&
            (nonStriker != null || (matchSettings.allowSingleSideBatting && availableBatsmen == 1))

    val useWideRunLayout = widthSizeClass != WindowWidthSizeClass.Compact

    Column(modifier = Modifier.fillMaxWidth()) {
    when {
        canStartScoring && !isInningsComplete -> {
                Text(
                    text = "Runs",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))

                if (matchSettings.shortPitch) {
                    if (useWideRunLayout) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (0..2).forEach { RunButton(it, onScoreRuns) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (3..4).forEach { RunButton(it, onScoreRuns) }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (0..4).forEach { RunButton(it, onScoreRuns) }
                        }
                    }
                } else {
                    if (useWideRunLayout) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (0..3).forEach { RunButton(it, onScoreRuns) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (4..6).forEach { RunButton(it, onScoreRuns) }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (0..6).forEach { RunButton(it, onScoreRuns) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onWide,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_wide"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Wide", style = MaterialTheme.typography.labelLarge)
                    }
                    
                    FilledTonalButton(
                        onClick = onRetire,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Retire", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTonalButton(
                        label = "More Extras",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_extras"),
                        onClick = onShowExtras
                    )
                    Button(
                        onClick = onShowWicket,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_wicket"),
                        shape = RoundedCornerShape(12.dp),
                        // errorContainer rather than error: this key is pressed ten-plus times
                        // a match, and `error` in a dark scheme is a light salmon that shouted
                        // louder than the score itself. The container pair keeps the meaning
                        // (it's still the only red key) at a volume that suits a normal event.
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Wicket", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        isInningsComplete -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ),
            ) {
                Text(
                    text = "Innings Complete! Total: $calculatedTotalRuns runs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        else -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "⚠️ Please select players to start scoring",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                    )
                    if (matchSettings.allowSingleSideBatting) {
                        Text(
                            text = "Single side batting enabled - only one batsman required",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
        }
        
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionTonalButton(
                label = if (onFix != null) "Undo Last Ball" else "Undo Last Delivery",
                modifier = Modifier
                    .weight(if (onFix != null) 1.4f else 1f)
                    .testTag("btn_undo"),
                onClick = onUndo
            )
            if (onFix != null) {
                ActionTonalButton(
                    label = "Fix…",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_fix"),
                    onClick = onFix
                )
            }
        }

    }
}

@Composable
internal fun RowScope.RunButton(
    value: Int,
    onClick: (Int) -> Unit,
) {
    val isSpecial = value == 4 || value == 6
    Button(
        onClick = { onClick(value) },
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .testTag("run_$value"),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            // A boundary is worth marking; 1, 2 and 3 are the ordinary case and stay neutral.
            containerColor = if (isSpecial)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (isSpecial)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isSpecial) 2.dp else 0.dp,
            pressedElevation = 6.dp
        )
    ) {
        Text(
            text = value.toString(),
            // Tabular so 0-6 sit on the same optical grid across the row.
            style = StatValue,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ActionTonalButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
