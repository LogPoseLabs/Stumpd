package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScoreHeaderCard(
    battingTeamName: String,
    currentInnings: Int,
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$battingTeamName • Innings $currentInnings",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
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
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "$calculatedTotalRuns/$totalWickets",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$currentOver.$ballsInOver",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "of ${matchSettings.totalOvers} ov",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                
                val runRate = if (currentOver == 0 && ballsInOver == 0) 0.0
                else calculatedTotalRuns.toDouble() / ((currentOver * 6 + ballsInOver) / 6.0)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${"%.2f".format(runRate)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Run Rate",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                
                if (totalExtras > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalExtras",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "Extras",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            
            if (currentInnings == 2) {
                Spacer(modifier = Modifier.height(4.dp))
                val target = firstInningsRuns + 1
                val required = target - calculatedTotalRuns
                val ballsLeft = (matchSettings.totalOvers - currentOver) * 6 - ballsInOver
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
                        fontSize = 11.sp,
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
