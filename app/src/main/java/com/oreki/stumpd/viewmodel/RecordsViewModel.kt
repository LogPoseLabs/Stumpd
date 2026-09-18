package com.oreki.stumpd.viewmodel

import android.app.Application
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
import com.oreki.stumpd.ui.components.filterMatchesByDate
import com.oreki.stumpd.ui.components.filterMatchesByGroup
import com.oreki.stumpd.ui.components.filterMatchesByPitchType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    application: Application,
    private val matchRepo: MatchRepository,
    private val groupRepo: GroupRepository
) : AndroidViewModel(application) {

    // ── State ────────────────────────────────────────────────────────
    var isLoading by mutableStateOf(true)
    var allMatches by mutableStateOf<List<MatchHistory>>(emptyList())
    var groups by mutableStateOf<List<GroupEntity>>(emptyList())

    var selectedCategory by mutableStateOf<RecordCategory>(RecordCategory.BattingRecords)
    var records by mutableStateOf<List<RecordEntry>>(emptyList())

    var selectedGroupId by mutableStateOf<String?>(null)
    var selectedGroupName by mutableStateOf("Select Group")

    var selectedFilter by mutableStateOf("All Time")
    var showFilterDialog by mutableStateOf(false)
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)

    var selectedPitchType by mutableStateOf<Boolean?>(false) // Default to Long Pitch
    var showPitchPicker by mutableStateOf(false)

    var fieldingFilter by mutableStateOf(FieldingFilter.ALL)

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
            allMatches = matchRepo.getAllMatchesWithStats()
            groups = groupRepo.listGroups()
            // Auto-select first group if none selected
            if (groups.isNotEmpty() && selectedGroupId == null) {
                // The group the app is filtered to, falling back to the first one.
                val stored = groupRepo.getDefaultGroupId()?.takeIf { id -> groups.any { it.id == id } }
                val group = groups.firstOrNull { it.id == stored } ?: groups[0]
                selectedGroupId = group.id
                selectedGroupName = group.name
            }
            recalculateRecords()
            isLoading = false
        }
    }

    // ── Recalculate records when filters change ──────────────────────
    fun recalculateRecords() {
        if (allMatches.isEmpty()) return
        viewModelScope.launch {
            val groupFiltered = filterMatchesByGroup(allMatches, selectedGroupId)
            val pitchFiltered = filterMatchesByPitchType(groupFiltered, selectedPitchType)
            val dateFiltered = filterMatchesByDate(pitchFiltered, selectedFilter, startDate, endDate)
            records = calculateRecords(dateFiltered, selectedCategory, fieldingFilter)
        }
    }

    fun onGroupSelected(id: String?, name: String) {
        selectedGroupId = id
        selectedGroupName = name
        // Remember it app-wide, so the choice holds when navigating elsewhere.
        viewModelScope.launch { groupRepo.setSelectedGroupId(id) }
        recalculateRecords()
    }

    fun onCategorySelected(category: RecordCategory) {
        selectedCategory = category
        recalculateRecords()
    }

    fun onFilterSelected(filter: String, start: LocalDate?, end: LocalDate?) {
        selectedFilter = filter
        startDate = start
        endDate = end
        showFilterDialog = false
        recalculateRecords()
    }

    fun onPitchTypeSelected(type: Boolean?) {
        selectedPitchType = type
        showPitchPicker = false
        recalculateRecords()
    }

    fun onFieldingFilterSelected(filter: FieldingFilter) {
        fieldingFilter = filter
        recalculateRecords()
    }
}
