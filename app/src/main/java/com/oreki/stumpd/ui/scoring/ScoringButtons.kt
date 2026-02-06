package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onScoreRuns: (Int) -> Unit,
    onShowExtras: () -> Unit,
    onShowWicket: () -> Unit,
    onUndo: () -> Unit,
    onWide: () -> Unit,
    onRetire: () -> Unit,
    unlimitedUndoEnabled: Boolean,
    onToggleUnlimitedUndo: (Boolean) -> Unit
) {
    val canStartScoring = striker != null && bowler != null &&
            (nonStriker != null || (matchSettings.allowSingleSideBatting && availableBatsmen == 1))

    Column(modifier = Modifier.fillMaxWidth()) {
    when {
        canStartScoring && !isInningsComplete -> {
                Text(
                    text = "Runs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))

                if (matchSettings.shortPitch) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..4).forEach { RunButton(it, onScoreRuns) }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..6).forEach { RunButton(it, onScoreRuns) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onWide,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Text("Wide", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    
                    FilledTonalButton(
                        onClick = onRetire,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Retire", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTonalButton(
                        label = "More Extras",
                        modifier = Modifier.weight(1f),
                        onClick = onShowExtras
                    )
                    Button(
                        onClick = onShowWicket,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Wicket", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                    fontSize = 18.sp,
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
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                    )
                    if (matchSettings.allowSingleSideBatting) {
                        Text(
                            text = "Single side batting enabled - only one batsman required",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
        }
        
        Spacer(Modifier.height(4.dp))
        ActionTonalButton(
            label = "Undo Last Delivery",
            modifier = Modifier.fillMaxWidth(),
            onClick = onUndo
        )

        Spacer(Modifier.height(4.dp))
        UnlimitedUndoToggle(
            isEnabled = unlimitedUndoEnabled,
            onToggle = onToggleUnlimitedUndo
        )
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
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSpecial)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSpecial)
                MaterialTheme.colorScheme.onSecondaryContainer
            else
                MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Text(
            text = value.toString(),
            fontSize = 14.sp,
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
        colors = ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
