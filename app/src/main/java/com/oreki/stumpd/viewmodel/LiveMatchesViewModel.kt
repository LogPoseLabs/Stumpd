package com.oreki.stumpd.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oreki.stumpd.SharedMatchInfo
import com.oreki.stumpd.data.sync.firebase.EnhancedFirebaseAuthHelper
import com.oreki.stumpd.data.sync.sharing.MatchSharingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveMatchesViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    sealed class UiState {
        data object Loading : UiState()
        data class Content(
            val liveMatches: List<SharedMatchInfo>,
            val currentUserId: String?,
        ) : UiState()

        data class Error(val message: String) : UiState()
    }

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    var isDeleting by mutableStateOf(false)
        private set

    init {
        loadMatches()
    }

    fun loadMatches() {
        viewModelScope.launch {
            uiState = UiState.Loading
            try {
                val authHelper = EnhancedFirebaseAuthHelper(getApplication())
                var userId = authHelper.currentUserId

                if (userId == null) {
                    val user = authHelper.signInAnonymously()
                    userId = user?.uid
                    if (userId == null) {
                        uiState = UiState.Error(
                            "Authentication failed. Please check your internet connection.",
                        )
                        return@launch
                    }
                    delay(500)
                }

                val sharingManager = MatchSharingManager()
                val matches = sharingManager.listActiveSharedMatches()
                uiState = UiState.Content(matches, userId)
            } catch (e: Exception) {
                Log.e("LiveMatchesViewModel", "Error loading matches", e)
                uiState = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteMatch(match: SharedMatchInfo, onResult: (Boolean, String?) -> Unit) {
        if (isDeleting) return
        viewModelScope.launch {
            isDeleting = true
            try {
                MatchSharingManager().deleteInProgressMatch(match.matchId)
                val current = uiState
                if (current is UiState.Content) {
                    uiState = UiState.Content(
                        liveMatches = current.liveMatches.filter { it.matchId != match.matchId },
                        currentUserId = current.currentUserId,
                    )
                }
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            } finally {
                isDeleting = false
            }
        }
    }
}
