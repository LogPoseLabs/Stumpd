package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.hairline
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayersCard(
    showSingleSideLayout: Boolean,
    striker: Player?,
    nonStriker: Player?,
    bowler: Player?,
    availableBatsmen: Int,
    onSelectStriker: () -> Unit,
    onSelectNonStriker: () -> Unit,
    onSelectBowler: () -> Unit,
    onSwapStrike: () -> Unit,
    currentBowlerSpell: Int,
    jokerPlayer: Player?,
    maxOversPerBowler: Int,
    shortPitch: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = hairline(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Players",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (striker != null && nonStriker != null && !showSingleSideLayout && availableBatsmen > 1) {
                    SwapStrikeButton(onSwap = onSwapStrike)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            BattingSection(
                showSingleSideLayout = showSingleSideLayout,
                striker = striker,
                nonStriker = nonStriker,
                onSelectStriker = onSelectStriker,
                onSelectNonStriker = onSelectNonStriker,
                shortPitch = shortPitch
            )

            if (showSingleSideLayout && striker != null) {
                SingleSideBattingStatus(striker.name)
            }

            Spacer(modifier = Modifier.height(12.dp))

            BowlerSection(
                bowler = bowler,
                currentBowlerSpell = currentBowlerSpell,
                maxOversPerBowler = maxOversPerBowler,
                onSelectBowler = onSelectBowler
            )

            jokerPlayer?.let { joker ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🃏 Joker Available: ${joker.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun BattingSection(
    showSingleSideLayout: Boolean,
    striker: Player?,
    nonStriker: Player?,
    onSelectStriker: () -> Unit,
    onSelectNonStriker: () -> Unit,
    shortPitch: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (showSingleSideLayout) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        BatsmanColumn(
            player = striker,
            onClick = onSelectStriker,
            center = showSingleSideLayout,
            isStriker = true,
            isLastBatsman = (showSingleSideLayout && striker != null),
            modifier = if (showSingleSideLayout) Modifier else Modifier.weight(1f),
            shortPitch = shortPitch
        )
        if (!showSingleSideLayout) {
            BatsmanColumn(
                player = nonStriker,
                onClick = onSelectNonStriker,
                center = false,
                isStriker = false,
                isLastBatsman = false,
                modifier = Modifier.weight(1f),
                shortPitch = shortPitch
            )
        }
    }
}

@Composable
fun BatsmanColumn(
    player: Player?,
    onClick: () -> Unit,
    center: Boolean = false,
    isStriker: Boolean,
    isLastBatsman: Boolean,
    modifier: Modifier = Modifier,
    shortPitch: Boolean = false
) {
    // Who is on strike was previously carried by font weight alone — bold against normal, at
    // the same size, with the same 🏏 on both names. That's the one thing on this screen you
    // cannot afford to misread: it decides which batter every run is credited to. The striker
    // now sits on a tinted panel with a label, and only the striker keeps the bat.
    val onStrike = isStriker && player != null && !center
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (onStrike) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { if (onStrike) contentDescription = "${player?.name}, on strike" },
        horizontalAlignment = if (center) Alignment.CenterHorizontally else if (isStriker) Alignment.Start else Alignment.End
    ) {
        if (onStrike) {
            Text(
                text = "ON STRIKE",
                style = MicroLabel,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = if (isStriker) {
                "🏏 ${player?.name ?: "Select Striker"}"
            } else {
                player?.name ?: "Select Non-Striker"
            },
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal,
            color = when {
                player == null -> MaterialTheme.colorScheme.error
                isStriker -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        player?.let {
            Text(
                text = "${it.runs}${if (!it.isOut && it.ballsFaced > 0) "*" else ""} (${it.ballsFaced}) - ${if (shortPitch) "4s: ${it.fours}" else "4s: ${it.fours}, 6s: ${it.sixes}"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "SR: ${"%.1f".format(it.strikeRate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        if (isLastBatsman) {
            Text(
                text = "⚡ Last Batsman",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun SingleSideBattingStatus(strikerName: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            text = "⚡ Single Side Batting: $strikerName continues alone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun SwapStrikeButton(onSwap: () -> Unit) {
    FilledTonalIconButton(
        onClick = onSwap,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = "Swap Strike",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun BowlerSection(
    bowler: Player?,
    currentBowlerSpell: Int,
    maxOversPerBowler: Int,
    onSelectBowler: () -> Unit
) {
    Column(modifier = Modifier.clickable { onSelectBowler() }) {
        Text(
            text = "⚾ Bowler: ${bowler?.name ?: "Select Bowler"}",
            fontWeight = FontWeight.Medium,
            color = if (bowler == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        bowler?.let { currentBowler ->
            Text(
                text = "${"%.1f".format(currentBowler.oversBowled)} overs, ${currentBowler.runsConceded} runs, ${currentBowler.wickets} wickets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Counted in balls, then rendered. Subtracting overs as decimals — 1 minus 0.1 — gives
            // 0.9, but 0.1 in cricket notation is *one ball*, so a bowler one ball into a one-over
            // quota has five balls left, not nine tenths of an over.
            val ballsLeft =
                (maxOversPerBowler * 6 - currentBowler.ballsBowled).coerceAtLeast(0)
            Text(
                text = buildString {
                    append("Economy: ${"%.1f".format(currentBowler.economy)}")
                    append(" | Spell: $currentBowlerSpell over${if (currentBowlerSpell != 1) "s" else ""}")
                    if (maxOversPerBowler > 0) {
                        append(" | ${formatBallsAsOvers(ballsLeft)} of $maxOversPerBowler left")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
