package com.oreki.stumpd.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.data.repository.MatchCorrectionRepository
import com.oreki.stumpd.data.repository.MatchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = FullScorecardViewModel.Factory::class)
class FullScorecardViewModel @AssistedInject constructor(
    @Assisted("match_id") private val matchId: String,
    private val matchRepository: MatchRepository,
    private val correctionRepository: MatchCorrectionRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("match_id") matchId: String): FullScorecardViewModel
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Content(
            val match: MatchHistory,
            /** Corrections applied to this match, newest first; empty for most matches. */
            val corrections: List<MatchCorrectionRepository.CorrectionLogEntry> = emptyList(),
            /**
             * Whether this device may correct the match at all.
             *
             * False for a match belonging to a group somebody else owns: this phone can't upload
             * it, so the edit would be replaced at the next sync. The affordance is hidden rather
             * than shown and then refused.
             */
            val correctable: Boolean = true,
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    init {
        reload()
    }

    /**
     * Re-reads the match.
     *
     * Needed since a correction can change it while this screen is in the back stack: the load
     * used to happen only in `init`, so a corrected scorecard kept showing the old figures until
     * the activity was recreated.
     */
    fun reload() {
        viewModelScope.launch {
            uiState = UiState.Loading
            try {
                val match = matchRepository.getMatchWithStats(matchId)
                uiState = if (match != null) {
                    UiState.Content(
                        match = match,
                        corrections = correctionRepository.log(matchId),
                        correctable = correctionRepository.editability(matchId) !is
                            MatchCorrectionRepository.Editability.NotOurs,
                    )
                } else {
                    UiState.Error("Match not found")
                }
            } catch (e: Exception) {
                uiState = UiState.Error(e.message ?: "Failed to load scorecard")
            }
        }
    }
}
