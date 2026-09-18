package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.match.isSuperOverInnings
import com.oreki.stumpd.domain.match.inningsLabel
import com.oreki.stumpd.domain.match.SUPER_OVER_OVERS
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.ScoreLarge
import com.oreki.stumpd.ui.theme.StatValue
import com.oreki.stumpd.ui.theme.animatedInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScoreHeaderCard(
    battingTeamName: String,
    currentInnings: Int,
    /** What the side in progress must beat, or null while it is setting the target. */
    runsToChase: Int? = null,
    matchSettings: MatchSettings,
    calculatedTotalRuns: Int,
    totalWickets: Int,
    currentOver: Int,
    ballsInOver: Int,
    totalExtras: Int,
    battingTeamPlayers: List<Player>,
    firstInningsRuns: Int,
    isPowerplayActive: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${battingTeamName.uppercase()} • ${inningsLabel(currentInnings).uppercase()}",
                style = MicroLabel,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            
            if (isPowerplayActive) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "⚡ PP (${currentOver + 1}/${matchSettings.powerplayOvers})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${animatedInt(calculatedTotalRuns)}/$totalWickets",
                style = ScoreLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$currentOver.$ballsInOver",
                        style = StatValue,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "OF ${matchSettings.totalOvers} OV",
                        style = MicroLabel,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                
                val runRate = if (currentOver == 0 && ballsInOver == 0) 0.0
                else calculatedTotalRuns.toDouble() / ((currentOver * 6 + ballsInOver) / 6.0)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${"%.2f".format(runRate)}",
                        style = StatValue,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "RUN RATE",
                        style = MicroLabel,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                
                if (totalExtras > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalExtras",
                            style = StatValue,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "EXTRAS",
                            style = MicroLabel,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            
            // Any side chasing, not just the second innings — a super over chases too, and its
            // balls remaining come from its own one-over allotment, not the match's.
            if (runsToChase != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val target = runsToChase + 1
                val required = target - calculatedTotalRuns
                val oversAllotted =
                    if (isSuperOverInnings(currentInnings)) SUPER_OVER_OVERS else matchSettings.totalOvers
                val ballsLeft = (oversAllotted - currentOver) * 6 - ballsInOver
                val requiredRunRate = if (ballsLeft > 0) (required.toDouble() / ballsLeft) * 6 else 0.0
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (required > 0) 
                            MaterialTheme.colorScheme.tertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = if (required > 0) {
                            "Need $required in $ballsLeft balls • RRR: ${"%.2f".format(requiredRunRate)}"
                        } else {
                            "🎉 Target achieved!"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (required > 0) 
                            MaterialTheme.colorScheme.onTertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
