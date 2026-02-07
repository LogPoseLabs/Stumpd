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
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.ui.components.filterMatchesByDate
import com.oreki.stumpd.ui.components.filterMatchesByGroup
import com.oreki.stumpd.ui.components.filterMatchesByPitchType
import kotlinx.coroutines.launch
import java.time.LocalDate

@RequiresApi(Build.VERSION_CODES.O)
class RecordsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = StumpdDb.get(application)
    private val matchRepo = MatchRepository(db, application)
    private val groupRepo = GroupRepository(db)

    // ── State ────────────────────────────────────────────────────────
    var isLoading by mutableStateOf(true)
    var allMatches by mutableStateOf<List<MatchHistory>>(emptyList())
    var groups by mutableStateOf<List<GroupEntity>>(emptyList())

    var selectedCategory by mutableStateOf<RecordCategory>(RecordCategory.BattingRecords)
    var records by mutableStateOf<List<RecordEntry>>(emptyList())

    var selectedGroupId by mutableStateOf<String?>(null)
    var selectedGroupName by mutableStateOf("All Groups")

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

    private fun loadData() {
        viewModelScope.launch {
            isLoading = true
            allMatches = matchRepo.getAllMatchesWithStats()
            groups = groupRepo.listGroups()
            // Auto-select if only one group
            if (groups.size == 1 && selectedGroupId == null) {
                selectedGroupId = groups[0].id
                selectedGroupName = groups[0].name
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
