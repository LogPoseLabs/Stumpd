package com.oreki.stumpd.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory that creates a [ScoringViewModel] with the required [ScoringInitParams].
 */
class ScoringViewModelFactory(
    private val application: Application,
    private val params: ScoringInitParams,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScoringViewModel::class.java)) {
            return ScoringViewModel(application, params) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
