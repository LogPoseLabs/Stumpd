package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RunOutDialog(
    striker: Player?,
    nonStriker: Player?,
    onConfirm: (RunOutInput) -> Unit,
    onDismiss: () -> Unit
) {
    var runsCompleted by remember { mutableStateOf(0) }
    var selectedWho by remember { mutableStateOf<Player?>(null) }
    var selectedEnd by remember { mutableStateOf<RunOutEnd?>(null) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    "Run Out",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Runs Completed (0–3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Runs completed before wicket:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (0..3).forEach { run ->
                            OutlinedCard(
                                modifier = Modifier.weight(1f),
                                onClick = { runsCompleted = run },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (runsCompleted == run)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    width = if (runsCompleted == run) 2.dp else 1.dp
                                )
                            ) {
                                Text(
                                    text = "$run",
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (runsCompleted == run) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Who got out (actual player names)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Who got out?",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    striker?.let {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedWho = striker },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (selectedWho == striker)
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    width = if (selectedWho == striker) 2.dp else 1.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            it.name,
                                            fontWeight = if (selectedWho == striker) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                    if (selectedWho == striker) {
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
                    nonStriker?.let {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedWho = nonStriker },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (selectedWho == nonStriker)
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    width = if (selectedWho == nonStriker) 2.dp else 1.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            it.name,
                                            fontWeight = if (selectedWho == nonStriker) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                    if (selectedWho == nonStriker) {
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
                }

                HorizontalDivider()

                // End of wicket
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "At which end?",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedCard(
                            modifier = Modifier.weight(1f),
                            onClick = { selectedEnd = RunOutEnd.STRIKER_END },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selectedEnd == RunOutEnd.STRIKER_END)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (selectedEnd == RunOutEnd.STRIKER_END) 2.dp else 1.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Striker's",
                                    fontWeight = if (selectedEnd == RunOutEnd.STRIKER_END) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "End",
                                    fontWeight = if (selectedEnd == RunOutEnd.STRIKER_END) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        OutlinedCard(
                            modifier = Modifier.weight(1f),
                            onClick = { selectedEnd = RunOutEnd.NON_STRIKER_END },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selectedEnd == RunOutEnd.NON_STRIKER_END)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (selectedEnd == RunOutEnd.NON_STRIKER_END) 2.dp else 1.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Non-Striker's",
                                    fontWeight = if (selectedEnd == RunOutEnd.NON_STRIKER_END) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "End",
                                    fontWeight = if (selectedEnd == RunOutEnd.NON_STRIKER_END) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val input = RunOutInput(
                        runsCompleted = runsCompleted,
                        whoOut = selectedWho?.name ?: "",
                        end = selectedEnd ?: RunOutEnd.STRIKER_END
                    )
                    onConfirm(input)
                },
                enabled = selectedWho != null && selectedEnd != null,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Confirm", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        }
    )
}
