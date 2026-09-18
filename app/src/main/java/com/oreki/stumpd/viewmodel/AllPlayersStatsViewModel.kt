package com.oreki.stumpd.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.data.manager.PlayerDetailedStats
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.PlayerGroup
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel(assistedFactory = AllPlayersStatsViewModel.Factory::class)
class AllPlayersStatsViewModel @AssistedInject constructor(
    @Assisted("filter_group_id") initialGroupId: String?,
    @Assisted("filter_group_name") initialGroupName: String,
    @Assisted("filter_pitch_type") initialPitchType: Boolean?,
    @Assisted("filter_date") initialDateFilter: String,
    @Assisted("sort_by") initialSortBy: String,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("filter_group_id") filterGroupId: String?,
            @Assisted("filter_group_name") filterGroupName: String,
            @Assisted("filter_pitch_type") filterPitchType: Boolean?,
            @Assisted("filter_date") filterDate: String,
            @Assisted("sort_by") sortBy: String,
        ): AllPlayersStatsViewModel
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Content(
            val players: List<PlayerDetailedStats>,
            val groupMatchContext: List<Pair<String, Long>>?,
            val baseMatchesForFilter: List<MatchHistory>,
            /** Player of the Match awards keyed by player name, for the current filters. */
            val potmCounts: Map<String, Int> = emptyMap(),
        ) : UiState()

        data class Error(val message: String) : UiState()
    }

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    var sortBy by mutableStateOf(initialSortBy)
        private set

    var selectedGroupId by mutableStateOf(initialGroupId)
        private set

    var selectedGroupName by mutableStateOf(initialGroupName)
        private set

    var selectedPitchType by mutableStateOf(initialPitchType)
        private set

    var selectedFilter by mutableStateOf(initialDateFilter)
        private set

    var groups by mutableStateOf<List<PlayerGroup>>(emptyList())
        private set

    /**
     * The score this group counts as a milestone, for the "20s" style stat. Falls back to the
     * default when looking at all groups at once, since they may disagree.
     */
    val battingMilestone: Int
        get() = groups.firstOrNull { it.id == selectedGroupId }
            ?.defaults?.matchSettings?.battingMilestone
            ?: MatchSettings().battingMilestone

    fun updateSortBy(value: String) {
        sortBy = value
    }

    fun updateSelectedGroup(id: String?, name: String) {
        selectedGroupId = id
        selectedGroupName = name
        // Remember it app-wide, so the choice holds when navigating elsewhere.
        viewModelScope.launch { groupRepository.setSelectedGroupId(id) }
        refreshStats()
    }

    fun updatePitchType(value: Boolean?) {
        selectedPitchType = value
        refreshStats()
    }

    fun updateDateFilter(value: String) {
        selectedFilter = value
        refreshStats()
    }

    init {
        if (selectedFilter in listOf("Today", "This Week", "This Month")) {
            selectedFilter = "All Time"
        }
        viewModelScope.launch {
            loadGroups()
            refreshStatsInternal()
        }
    }

    private suspend fun loadGroups() {
        try {
            val summaries = groupRepository.listGroupSummaries()
            groups = summaries.map { (g, d, _) -> g.toDomain(d, emptyList()) }
            // No group passed in: use the one the app is filtered to.
            if (selectedGroupId == null && selectedGroupName.isEmpty()) {
                groupRepository.getDefaultGroupId()
                    ?.takeIf { id -> groups.any { it.id == id } }
                    ?.let { selectedGroupId = it }
            }
            if (selectedGroupId != null && selectedGroupName.isEmpty()) {
                selectedGroupName =
                    groups.firstOrNull { it.id == selectedGroupId }?.name ?: "All Groups"
            }
            if (groups.size == 1 && selectedGroupId == null) {
                selectedGroupId = groups[0].id
                selectedGroupName = groups[0].name
            }
        } catch (_: Exception) {
            groups = emptyList()
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            refreshStatsInternal()
        }
    }

    private suspend fun refreshStatsInternal() {
        uiState = UiState.Loading
        try {
            val all = matchRepository.getAllMatches()
            var filtered = all

            selectedGroupId?.let { gId ->
                filtered = filtered.filter { it.groupId == gId }
            }

            selectedPitchType?.let { type ->
                filtered = filtered.filter { it.shortPitch == type }
            }

            val baseMatchesForFilter = filtered

            filtered = when {
                selectedFilter == "All Time" -> filtered
                selectedFilter.startsWith("Date:") -> {
                    val iso = selectedFilter.removePrefix("Date:")
                    val selDate = LocalDate.parse(iso)
                    filtered.filter {
                        val d = Instant.ofEpochMilli(it.matchDate)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        d == selDate
                    }
                }

                selectedFilter.startsWith("CustomRange:") -> {
                    val parts = selectedFilter.removePrefix("CustomRange:").split("|")
                    val start = LocalDate.parse(parts[0])
                    val end = LocalDate.parse(parts[1])
                    filtered.filter {
                        val d = Instant.ofEpochMilli(it.matchDate)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        d in start..end
                    }
                }

                else -> filtered
            }

            val groupMatchContext = if (selectedGroupId != null) {
                filtered.map { Pair(it.id, it.matchDate) }
            } else {
                null
            }

            val players = playerRepository.getPlayerDetailedStats(filtered)
            // Counted from the same filtered set the player stats come from, so the award
            // totals always agree with the rows on screen (baseMatchesForFilter is the
            // pre-date-filter set and would over-count once a date filter is applied).
            val potmCounts = filtered
                .mapNotNull { it.playerOfTheMatchName?.takeIf { name -> name.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
            uiState = UiState.Content(players, groupMatchContext, baseMatchesForFilter, potmCounts)
        } catch (e: Exception) {
            uiState = UiState.Error(e.message ?: "Failed to load player stats")
        }
    }
}
