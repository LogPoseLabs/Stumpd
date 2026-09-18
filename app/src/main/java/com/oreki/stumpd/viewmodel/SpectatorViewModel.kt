package com.oreki.stumpd.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oreki.stumpd.data.local.entity.InProgressMatchEntity
import com.oreki.stumpd.data.sync.realtime.RealTimeMatchListener
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = SpectatorViewModel.Factory::class)
class SpectatorViewModel @AssistedInject constructor(
    @Assisted("matchId") private val matchId: String,
    @Assisted("ownerId") private val ownerId: String,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("matchId") matchId: String,
            @Assisted("ownerId") ownerId: String,
        ): SpectatorViewModel
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Content(
            val match: InProgressMatchEntity,
            val lastUpdatedMs: Long,
        ) : UiState()

        data class Error(val message: String) : UiState()
    }

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    private val listener = RealTimeMatchListener()

    init {
        viewModelScope.launch {
            try {
                listener.listenToInProgressMatch(ownerId, matchId).collect { match ->
                    if (match != null) {
                        uiState = UiState.Content(match, System.currentTimeMillis())
                    } else {
                        Log.w("SpectatorViewModel", "Received null match update")
                        uiState = when (val s = uiState) {
                            is UiState.Loading -> UiState.Error("Match not found or ended")
                            is UiState.Content -> UiState.Error("Match not found or ended")
                            else -> s
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SpectatorViewModel", "Failed to start listener", e)
                uiState = UiState.Error("Failed to connect: ${e.message}")
            }
        }
    }
}
