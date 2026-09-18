package com.oreki.stumpd.viewmodel

import android.app.Application
import com.oreki.stumpd.domain.match.mainMatchDeliveries
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.ui.components.filterMatchesByGroup
import com.oreki.stumpd.ui.components.filterMatchesByPitchType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class HeadToHeadViewModel @Inject constructor(
    application: Application,
    private val matchRepo: MatchRepository,
    private val groupRepo: GroupRepository
) : AndroidViewModel(application) {

    // ── State ────────────────────────────────────────────────────────
    var isLoading by mutableStateOf(true)
    var allPlayers by mutableStateOf<List<String>>(emptyList())
    var allMatches by mutableStateOf<List<MatchHistory>>(emptyList())
    var groups by mutableStateOf<List<GroupEntity>>(emptyList())

    var selectedBatsman by mutableStateOf<String?>(null)
    var selectedBowler by mutableStateOf<String?>(null)
    var showBatsmanDropdown by mutableStateOf(false)
    var showBowlerDropdown by mutableStateOf(false)

    var selectedGroupId by mutableStateOf<String?>(null)
    var selectedGroupName by mutableStateOf("Select Group")

    var selectedFilter by mutableStateOf("All Time")
    var showFilterDialog by mutableStateOf(false)
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)

    var selectedPitchType by mutableStateOf<Boolean?>(false) // Default to Long Pitch
    var showPitchPicker by mutableStateOf(false)

    var headToHeadStats by mutableStateOf<HeadToHeadStats?>(null)
    var matchDetails by mutableStateOf<List<MatchHeadToHeadDetail>>(emptyList())

    var filteredPlayers by mutableStateOf<List<String>>(emptyList())

    // ── Init ─────────────────────────────────────────────────────────
    init {
        loadData()
    }

    /**
     * Re-reads every match. Public because a correction to a saved match changes these figures,
     * and the screen is reachable straight back from the editor.
     */
    fun loadData() {
        viewModelScope.launch {
            isLoading = true
            allMatches = matchRepo.getAllMatches()
            groups = groupRepo.listGroups()

            val playerNames = mutableSetOf<String>()
            allMatches.forEach { match ->
                match.allDeliveries.mainMatchDeliveries().forEach { delivery ->
                    if (delivery.strikerName.isNotBlank()) playerNames.add(delivery.strikerName)
                    if (delivery.bowlerName.isNotBlank()) playerNames.add(delivery.bowlerName)
                }
            }
            allPlayers = playerNames.sorted()
            filteredPlayers = allPlayers
            // Auto-select first group if none selected
            if (groups.isNotEmpty() && selectedGroupId == null) {
                // The group the app is filtered to, falling back to the first one.
                val stored = groupRepo.getDefaultGroupId()?.takeIf { id -> groups.any { it.id == id } }
                val group = groups.firstOrNull { it.id == stored } ?: groups[0]
                selectedGroupId = group.id
                selectedGroupName = group.name
                updateFilteredPlayers()
            }
            isLoading = false
        }
    }

    // ── Actions ──────────────────────────────────────────────────────
    fun onGroupSelected(id: String?, name: String) {
        selectedGroupId = id
        selectedGroupName = name
        // Remember it app-wide, so the choice holds when navigating elsewhere.
        viewModelScope.launch { groupRepo.setSelectedGroupId(id) }
        updateFilteredPlayers()
        recalculateStats()
    }

    fun onBatsmanSelected(name: String) {
        selectedBatsman = name
        showBatsmanDropdown = false
        recalculateStats()
    }

    fun onBowlerSelected(name: String) {
        selectedBowler = name
        showBowlerDropdown = false
        recalculateStats()
    }

    fun swapPlayers() {
        val temp = selectedBatsman
        selectedBatsman = selectedBowler
        selectedBowler = temp
        recalculateStats()
    }

    fun onFilterSelected(filter: String, start: LocalDate?, end: LocalDate?) {
        selectedFilter = filter
        startDate = start
        endDate = end
        showFilterDialog = false
        recalculateStats()
    }

    fun onPitchTypeSelected(type: Boolean?) {
        selectedPitchType = type
        showPitchPicker = false
        recalculateStats()
    }

    private fun updateFilteredPlayers() {
        if (allMatches.isEmpty()) return
        val matchesForGroup = filterMatchesByGroup(allMatches, selectedGroupId)
        val playerNames = mutableSetOf<String>()
        matchesForGroup.forEach { match ->
            match.allDeliveries.mainMatchDeliveries().forEach { delivery ->
                if (delivery.strikerName.isNotBlank()) playerNames.add(delivery.strikerName)
                if (delivery.bowlerName.isNotBlank()) playerNames.add(delivery.bowlerName)
            }
        }
        filteredPlayers = playerNames.sorted()
        if (selectedBatsman != null && selectedBatsman !in filteredPlayers) {
            selectedBatsman = null
        }
        if (selectedBowler != null && selectedBowler !in filteredPlayers) {
            selectedBowler = null
        }
    }

    private fun recalculateStats() {
        val batsman = selectedBatsman ?: return
        val bowler = selectedBowler ?: return
        viewModelScope.launch {
            val groupFiltered = filterMatchesByGroup(allMatches, selectedGroupId)
            val pitchFiltered = filterMatchesByPitchType(groupFiltered, selectedPitchType)
            val dateFiltered = filterMatchesByDate(pitchFiltered, selectedFilter, startDate, endDate)
            val (stats, details) = calculateHeadToHead(dateFiltered, batsman, bowler)
            headToHeadStats = stats
            matchDetails = details
        }
    }

    // ── Date filtering (same logic as HeadToHeadActivity) ────────────
    private fun filterMatchesByDate(
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
            else -> matches
        }
    }
}
