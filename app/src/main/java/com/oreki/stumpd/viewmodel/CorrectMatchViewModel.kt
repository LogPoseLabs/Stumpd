package com.oreki.stumpd.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oreki.stumpd.data.repository.MatchCorrectionRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.domain.match.CorrectionOutcome
import com.oreki.stumpd.domain.match.MatchCorrection
import com.oreki.stumpd.domain.model.MatchHistory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * State for the correction screen: the match being corrected, the change being composed, and the
 * preview of what it would do.
 *
 * Deliberately a two-step flow. [stage] a correction to see its consequences; [commit] only
 * writes what was previewed, against the version it was previewed on. Nothing is written until
 * the user has read the diff.
 */
@HiltViewModel(assistedFactory = CorrectMatchViewModel.Factory::class)
class CorrectMatchViewModel @AssistedInject constructor(
    @Assisted("match_id") val matchId: String,
    private val matchRepository: MatchRepository,
    private val correctionRepository: MatchCorrectionRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("match_id") matchId: String): CorrectMatchViewModel
    }

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Content(
            val match: MatchHistory,
            val editability: MatchCorrectionRepository.Editability,
            val log: List<MatchCorrectionRepository.CorrectionLogEntry>,
        ) : UiState
    }

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    /** The correction being composed, and what it would do. Null until something is staged. */
    var staged by mutableStateOf<MatchCorrection?>(null)
        private set
    var preview by mutableStateOf<CorrectionOutcome?>(null)
        private set
    var committing by mutableStateOf(false)
        private set
    var committed by mutableStateOf<MatchCorrectionRepository.CommitResult.Committed?>(null)
        private set

    private var previewedUpdatedAt: Long = 0L

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = UiState.Loading
            runCatching {
                val match = matchRepository.getMatchWithStats(matchId)
                    ?: error("That match isn't on this device any more.")
                UiState.Content(
                    match = match,
                    editability = correctionRepository.editability(matchId),
                    log = correctionRepository.log(matchId),
                )
            }.onSuccess { uiState = it }
                .onFailure { uiState = UiState.Error(it.message ?: "Couldn't open that match.") }
        }
    }

    /** Works out what [correction] would do, without writing anything. */
    fun stage(correction: MatchCorrection) {
        staged = correction
        preview = null
        viewModelScope.launch {
            val (outcome, updatedAt) = correctionRepository.preview(matchId, listOf(correction))
            previewedUpdatedAt = updatedAt
            preview = outcome
        }
    }

    fun discard() {
        staged = null
        preview = null
        committed = null
    }

    /**
     * Writes the previewed correction.
     *
     * Passes the version the preview was computed against, so a sync that landed in between is
     * caught rather than silently overwritten.
     */
    fun commit() {
        val correction = staged ?: return
        if (preview !is CorrectionOutcome.Applied) return
        viewModelScope.launch {
            committing = true
            val result = correctionRepository.commit(
                matchId = matchId,
                corrections = listOf(correction),
                expectedUpdatedAt = previewedUpdatedAt,
            )
            committing = false
            when (result) {
                is MatchCorrectionRepository.CommitResult.Committed -> {
                    committed = result
                    staged = null
                    preview = null
                    load()
                }

                is MatchCorrectionRepository.CommitResult.Rejected -> {
                    preview = CorrectionOutcome.Rejected(result.errors)
                }
            }
        }
    }
}
