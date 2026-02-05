package com.oreki.stumpd.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.MatchHistory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reusable date filter dialog for statistics screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateFilterDialog(
    currentFilter: String,
    onFilterSelected: (String, LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val filters = listOf("All Time", "Last 7 Days", "Last 30 Days", "Last 3 Months", "This Year", "Custom")
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Date") },
        text = {
            Column {
                filters.forEach { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFilter == filter,
                            onClick = {
                                if (filter == "Custom") {
                                    showDatePicker = true
                                } else {
                                    onFilterSelected(filter, null, null)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(filter)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val start = Instant.ofEpochMilli(startMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            val end = Instant.ofEpochMilli(endMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onFilterSelected("Custom", start, end)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.height(500.dp)
            )
        }
    }
}

/**
 * Filter matches by date range
 */
@RequiresApi(Build.VERSION_CODES.O)
fun filterMatchesByDate(
    matches: List<MatchHistory>,
    filter: String,
    customStart: LocalDate?,
    customEnd: LocalDate?
): List<MatchHistory> {
    val now = LocalDate.now()

    return when (filter) {
        "Last 7 Days" -> {
            val cutoff = now.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            matches.filter { it.matchDate >= cutoff }
        }
        "Last 30 Days" -> {
            val cutoff = now.minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            matches.filter { it.matchDate >= cutoff }
        }
        "Last 3 Months" -> {
            val cutoff = now.minusMonths(3).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            matches.filter { it.matchDate >= cutoff }
        }
        "This Year" -> {
            val startOfYear = LocalDate.of(now.year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            matches.filter { it.matchDate >= startOfYear }
        }
        "Custom" -> {
            if (customStart != null && customEnd != null) {
                val startMillis = customStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMillis = customEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                matches.filter { it.matchDate >= startMillis && it.matchDate < endMillis }
            } else {
                matches
            }
        }
        else -> matches // "All Time"
    }
}
