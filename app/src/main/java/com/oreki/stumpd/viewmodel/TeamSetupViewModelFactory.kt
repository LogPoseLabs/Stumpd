package com.oreki.stumpd.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.oreki.stumpd.domain.model.TeamSetupPreset

class TeamSetupViewModelFactory(
    private val application: Application,
    private val defaultGroupId: String?,
    /** A tournament fixture to set up, when the screen was opened from one. */
    private val preset: TeamSetupPreset? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TeamSetupViewModel::class.java)) {
            return TeamSetupViewModel(application, defaultGroupId, preset) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
