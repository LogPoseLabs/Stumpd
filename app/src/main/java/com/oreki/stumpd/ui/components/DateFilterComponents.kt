package com.oreki.stumpd.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.data.local.entity.GroupEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
                            .clickable {
                                if (filter == "Custom") {
                                    showDatePicker = true
                                } else {
                                    onFilterSelected(filter, null, null)
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFilter == filter,
                            onClick = null // Handled by row click
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
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null &&
                            dateRangePickerState.selectedEndDateMillis != null
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
                modifier = Modifier.height(500.dp),
                title = {
                    Text(
                        "Select Date Range",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                headline = {
                    val startDate = dateRangePickerState.selectedStartDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    } ?: "Start"
                    val endDate = dateRangePickerState.selectedEndDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    } ?: "End"
                    Text(
                        "$startDate - $endDate",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            )
        }
    }
}

/**
 * Reusable group filter dropdown for statistics screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFilterDropdown(
    groups: List<GroupEntity>,
    selectedGroupId: String?,
    onGroupSelected: (String?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // If there's exactly one group, auto-select it
    LaunchedEffect(groups) {
        if (groups.size == 1 && selectedGroupId == null) {
            onGroupSelected(groups[0].id, groups[0].name)
        }
    }

    val selectedGroupName = if (selectedGroupId == null) {
        "All Groups"
    } else {
        groups.find { it.id == selectedGroupId }?.name ?: "All Groups"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedGroupName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Group") },
            leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Only show "All Groups" when user belongs to more than one group
            if (groups.size > 1) {
                DropdownMenuItem(
                    text = { Text("All Groups") },
                    onClick = {
                        onGroupSelected(null, "All Groups")
                        expanded = false
                    }
                )
            }
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onGroupSelected(group.id, group.name)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Filter bar with both group and date filters
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FilterBar(
    groups: List<GroupEntity>,
    selectedGroupId: String?,
    selectedDateFilter: String,
    onGroupSelected: (String?, String) -> Unit,
    onDateFilterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Group filter
        GroupFilterDropdown(
            groups = groups,
            selectedGroupId = selectedGroupId,
            onGroupSelected = onGroupSelected,
            modifier = Modifier.weight(1f)
        )

        // Date filter button
        OutlinedButton(
            onClick = onDateFilterClick,
            modifier = Modifier.height(56.dp)
        ) {
            Text(selectedDateFilter, maxLines = 1)
        }
    }
}

/**
 * Filter matches by group
 */
fun filterMatchesByGroup(
    matches: List<MatchHistory>,
    groupId: String?
): List<MatchHistory> {
    return if (groupId == null) {
        matches
    } else {
        matches.filter { it.groupId == groupId }
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

/**
 * Filter matches by pitch type
 */
fun filterMatchesByPitchType(
    matches: List<MatchHistory>,
    pitchType: Boolean?
): List<MatchHistory> {
    return if (pitchType == null) {
        matches
    } else {
        matches.filter { it.shortPitch == pitchType }
    }
}
