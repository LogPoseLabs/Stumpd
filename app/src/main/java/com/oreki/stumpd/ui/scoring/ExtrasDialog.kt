package com.oreki.stumpd.ui.scoring

import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtrasDialog(
    matchSettings: MatchSettings,
    onExtraSelected: (ExtraType, Int) -> Unit,
    onDismiss: () -> Unit,
    striker: Player?,
    nonStriker: Player?,
    onWideWithStumping: ((ExtraType, Int) -> Unit)? = null
) {
    var selectedExtraType by remember { mutableStateOf<ExtraType?>(null) }
    var showNoBallOutcomeStep by remember { mutableStateOf(false) }
    var showRunOutOnNoBall by remember { mutableStateOf(false) }
    var showBoundaryOutOnNoBall by remember { mutableStateOf(false) }
    var showWideOutcomeStep by remember { mutableStateOf(false) }

    fun resetNoBallHolders() {
        NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
        NoBallOutcomeHolders.noBallRunOutInput.value = null
        NoBallOutcomeHolders.noBallBoundaryOutInput.value = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "🏏",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text("Extras", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            when {
                selectedExtraType == null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Select extra type",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        ExtraType.entries.forEach { extraType ->
                            val baseRuns = when (extraType) {
                                ExtraType.OFF_SIDE_WIDE -> matchSettings.wideRuns
                                ExtraType.LEG_SIDE_WIDE  -> matchSettings.wideRuns
                                ExtraType.NO_BALL        -> matchSettings.noballRuns
                                ExtraType.BYE            -> matchSettings.byeRuns
                                ExtraType.LEG_BYE        -> matchSettings.legByeRuns
                            }
                            val isPrimary = extraType == ExtraType.NO_BALL || extraType == ExtraType.OFF_SIDE_WIDE || extraType == ExtraType.LEG_SIDE_WIDE

                            if (isPrimary) {
                                Button(
                                    onClick = {
                                        selectedExtraType = extraType
                                        showNoBallOutcomeStep = (extraType == ExtraType.NO_BALL)
                                        showWideOutcomeStep = (extraType == ExtraType.OFF_SIDE_WIDE || extraType == ExtraType.LEG_SIDE_WIDE)
                                        if (!showNoBallOutcomeStep) resetNoBallHolders()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(extraType.displayName, fontWeight = FontWeight.SemiBold)
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                "+$baseRuns",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        selectedExtraType = extraType
                                        showNoBallOutcomeStep = (extraType == ExtraType.NO_BALL)
                                        showWideOutcomeStep = (extraType == ExtraType.OFF_SIDE_WIDE || extraType == ExtraType.LEG_SIDE_WIDE)
                                        if (!showNoBallOutcomeStep) resetNoBallHolders()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(extraType.displayName, fontWeight = FontWeight.Medium)
                                        Text("+$baseRuns", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No-ball outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.NONE
                                showNoBallOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Just additional runs") }
                        Button(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.RUN_OUT
                                showRunOutOnNoBall = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Run out on No ball") }
                        FilledTonalButton(
                            onClick = {
                                NoBallOutcomeHolders.noBallSubOutcome.value = NoBallSubOutcome.BOUNDARY_OUT
                                NoBallOutcomeHolders.noBallBoundaryOutInput.value = NoBallBoundaryOutInput(outBatterName = null)
                                val base = matchSettings.noballRuns
                                onExtraSelected(ExtraType.NO_BALL, base)
                                showBoundaryOutOnNoBall = false
                                showNoBallOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) { Text("Boundary out on No ball") }
                        if (showRunOutOnNoBall) {
                            RunOutDialog(
                                striker = striker,
                                nonStriker = nonStriker,
                                onConfirm = { input ->
                                    NoBallOutcomeHolders.noBallRunOutInput.value = input
                                    showRunOutOnNoBall = false
                                    showNoBallOutcomeStep = false
                                },
                                onDismiss = { showRunOutOnNoBall = false }
                            )
                        }
                    }
                }
                
                (selectedExtraType == ExtraType.OFF_SIDE_WIDE || selectedExtraType == ExtraType.LEG_SIDE_WIDE) && showWideOutcomeStep -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Wide ball outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(
                            onClick = { showWideOutcomeStep = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Clean wide (runs only)") }
                        Button(
                            onClick = {
                                val baseRuns = matchSettings.wideRuns
                                onWideWithStumping?.invoke(selectedExtraType!!, baseRuns)
                                showWideOutcomeStep = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Stumped on Wide") }
                    }
                }
                
                else -> {
                    val baseRuns = when (selectedExtraType!!) {
                        ExtraType.OFF_SIDE_WIDE -> matchSettings.wideRuns
                        ExtraType.LEG_SIDE_WIDE -> matchSettings.wideRuns
                        ExtraType.NO_BALL       -> matchSettings.noballRuns
                        ExtraType.BYE           -> matchSettings.byeRuns
                        ExtraType.LEG_BYE       -> matchSettings.legByeRuns
                    }
                    val isBoundaryOut =
                        selectedExtraType == ExtraType.NO_BALL &&
                                NoBallOutcomeHolders.noBallSubOutcome.value == NoBallSubOutcome.BOUNDARY_OUT

                    if (isBoundaryOut) {
                        Text("No ball + Boundary out recorded", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@AlertDialog
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${selectedExtraType!!.displayName} · pick additional runs",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 5
                        ) {
                            val additionalOptions =
                                if (selectedExtraType == ExtraType.NO_BALL) (0..6).toList()
                                else (0..4).toList()
                            additionalOptions.forEach { add ->
                                val total = baseRuns + add
                                val colors =
                                    when (add) {
                                        0 -> ButtonDefaults.filledTonalButtonColors()
                                        4 -> ButtonDefaults.buttonColors()
                                        6 -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                        else -> ButtonDefaults.outlinedButtonColors()
                                    }
                                val shape = RoundedCornerShape(12.dp)
                                val btn: @Composable (@Composable () -> Unit) -> Unit =
                                    if (add in listOf(1,2,3,5)) {
                                        { content -> OutlinedButton(
                                            onClick = { onExtraSelected(selectedExtraType!!, total) },
                                            shape = shape) { content() } }
                                    } else {
                                        { content -> Button(
                                            onClick = { onExtraSelected(selectedExtraType!!, total) },
                                            colors = colors,
                                            shape = shape) { content() } }
                                    }
                                btn { Text("+$add = $total") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val label = when {
                selectedExtraType == null -> "Cancel"
                selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> "Back"
                else -> "Back"
            }
            TextButton(onClick = {
                when {
                    selectedExtraType == null -> onDismiss()
                    selectedExtraType == ExtraType.NO_BALL && showNoBallOutcomeStep -> {
                        showNoBallOutcomeStep = false
                        resetNoBallHolders()
                        selectedExtraType = null
                    }
                    else -> {
                        resetNoBallHolders()
                        selectedExtraType = null
                    }
                }
            }) { Text(label) }
        },
        dismissButton = if (selectedExtraType != null) {
            {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        } else {
            null
        }
    )
}

@Composable
fun QuickWideDialog(
    matchSettings: MatchSettings,
    onWideConfirmed: (Int) -> Unit,
    onStumpingOnWide: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var showOutcomeStep by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "🏏",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text("Wide Ball", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (showOutcomeStep) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(
                        onClick = { showOutcomeStep = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Just runs") }
                    Button(
                        onClick = { onStumpingOnWide(matchSettings.wideRuns) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Stumped on Wide") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Runs by batsmen (wide penalty added automatically)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0, 1, 2, 3, 4).forEach { batsmenRuns ->
                            val totalRuns = matchSettings.wideRuns + batsmenRuns
                            Button(
                                onClick = { onWideConfirmed(totalRuns) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) { 
                                Text(
                                    text = "$batsmenRuns",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun QuickNoBallDialog(
    matchSettings: MatchSettings,
    striker: Player?,
    nonStriker: Player?,
    onNoBallConfirmed: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var showOutcomeStep by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No-ball", style = MaterialTheme.typography.titleLarge) },
        text = {
            if (showOutcomeStep) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select outcome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(
                        onClick = { showOutcomeStep = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Just additional runs") }
                    Text(
                        "Note: For run-out or boundary-out on no-ball, use 'More Extras' button",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Runs by batsmen (no-ball penalty added automatically)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val maxBatsmenRuns = if (matchSettings.shortPitch) 4 else 6
                        listOf(0, 1, 2, 3, 4, maxBatsmenRuns).distinct().forEach { batsmenRuns ->
                            val totalRuns = matchSettings.noballRuns + batsmenRuns
                            Button(
                                onClick = { onNoBallConfirmed(totalRuns) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) { 
                                Text(
                                    text = "$batsmenRuns",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
