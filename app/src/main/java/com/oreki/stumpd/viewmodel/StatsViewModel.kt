package com.oreki.stumpd.viewmodel

import com.oreki.stumpd.data.manager.*
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
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = StumpdDb.get(application)
    private val matchRepo = MatchRepository(db, application)
    private val playerRepo = PlayerRepository(db)
    private val groupRepo = GroupRepository(db)

    private val prefs = application.getSharedPreferences("stumpd_prefs", android.content.Context.MODE_PRIVATE)
    private val defaultGroupId = prefs.getString("default_group_id", null)

    // ── State ────────────────────────────────────────────────────────
    var isLoading by mutableStateOf(true)
    var players by mutableStateOf<List<PlayerDetailedStats>>(emptyList())
    var matches by mutableStateOf<List<MatchHistory>>(emptyList())

    var selectedFilter by mutableStateOf("All Time")
    var showFilterDialog by mutableStateOf(false)
    var showDateRangePicker by mutableStateOf(false)

    var selectedGroupId by mutableStateOf<String?>(defaultGroupId)
    var selectedGroupName by mutableStateOf(if (defaultGroupId != null) "" else "All Groups")
    var showGroupPicker by mutableStateOf(false)

    var selectedPitchType by mutableStateOf<Boolean?>(false) // Default to Long Pitch
    var showPitchPicker by mutableStateOf(false)

    var groups by mutableStateOf<List<PlayerGroup>>(emptyList())

    // ── Init ─────────────────────────────────────────────────────────
    init {
        reloadData()
    }

    fun reloadData() {
        viewModelScope.launch {
            isLoading = true
            val all = matchRepo.getAllMatches()
            android.util.Log.d("StatsViewModel", "Loaded ${all.size} matches from database")

            var filtered = all

            selectedGroupId?.let { gId ->
                android.util.Log.d("StatsViewModel", "Filtering by groupId: $gId")
                filtered = filtered.filter { it.groupId == gId }
                android.util.Log.d("StatsViewModel", "After group filter: ${filtered.size} matches")
            }

            selectedPitchType?.let { type ->
                filtered = filtered.filter { it.shortPitch == type }
            }

            when {
                selectedFilter == "All Time" -> { /* no-op */ }
                selectedFilter.startsWith("Date:") -> {
                    val iso = selectedFilter.removePrefix("Date:")
                    val selDate = LocalDate.parse(iso)
                    filtered = filtered.filter {
                        val d = Instant.ofEpochMilli(it.matchDate).atZone(ZoneId.systemDefault()).toLocalDate()
                        d == selDate
                    }
                }
                selectedFilter.startsWith("CustomRange:") -> {
                    val parts = selectedFilter.removePrefix("CustomRange:").split("|")
                    val start = LocalDate.parse(parts[0])
                    val end = LocalDate.parse(parts[1])
                    filtered = filtered.filter {
                        val d = Instant.ofEpochMilli(it.matchDate).atZone(ZoneId.systemDefault()).toLocalDate()
                        d in start..end
                    }
                }
            }
            matches = filtered

            val summaries = groupRepo.listGroupSummaries()
            groups = summaries.map { (g, d, _) -> g.toDomain(d, emptyList()) }

            // Set group name if default group is set
            if (selectedGroupId != null && selectedGroupName.isEmpty()) {
                selectedGroupName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "All Groups"
            }
            // Auto-select if only one group
            if (groups.size == 1 && selectedGroupId == null) {
                selectedGroupId = groups[0].id
                selectedGroupName = groups[0].name
            }

            players = playerRepo.getPlayerDetailedStats(filtered)
            android.util.Log.d("StatsViewModel", "Computed ${players.size} player stats from ${filtered.size} matches")
            isLoading = false
        }
    }

    // ── Actions ──────────────────────────────────────────────────────
    fun onGroupSelected(id: String?, name: String) {
        selectedGroupId = id
        selectedGroupName = name
        showGroupPicker = false
        reloadData()
    }

    fun onPitchTypeSelected(type: Boolean?) {
        selectedPitchType = type
        showPitchPicker = false
        reloadData()
    }

    fun onFilterSelected(filter: String) {
        selectedFilter = filter
        showFilterDialog = false
        reloadData()
    }

    fun onDateRangeSelected(startMillis: Long, endMillis: Long) {
        val startLocal = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val endLocal = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        selectedFilter = "CustomRange:${startLocal}|${endLocal}"
        showDateRangePicker = false
        showFilterDialog = false
        reloadData()
    }
}
