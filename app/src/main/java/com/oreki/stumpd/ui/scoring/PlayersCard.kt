package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    shortPitch: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Players",
                    fontSize = 14.sp,
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
                onSelectBowler = onSelectBowler
            )

            jokerPlayer?.let { joker ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🃏 Joker Available: ${joker.name}",
                    fontSize = 12.sp,
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
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else if (isStriker) Alignment.Start else Alignment.End
    ) {
        Text(
            text = "🏏 ${player?.name ?: if (isStriker) "Select Striker" else "Select Non-Striker"}",
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal,
            color = if (player == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        player?.let {
            Text(
                text = "${it.runs}${if (!it.isOut && it.ballsFaced > 0) "*" else ""} (${it.ballsFaced}) - ${if (shortPitch) "4s: ${it.fours}" else "4s: ${it.fours}, 6s: ${it.sixes}"}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "SR: ${"%.1f".format(it.strikeRate)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        if (isLastBatsman) {
            Text(
                text = "⚡ Last Batsman",
                fontSize = 10.sp,
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
            fontSize = 12.sp,
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
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Economy: ${"%.1f".format(currentBowler.economy)} | Spell: $currentBowlerSpell over${if (currentBowlerSpell != 1) "s" else ""}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
