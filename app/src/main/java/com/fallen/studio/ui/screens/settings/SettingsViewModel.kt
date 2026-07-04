package com.fallen.studio.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fallen.studio.data.AppSettings
import com.fallen.studio.data.SettingsRepository
import com.fallen.studio.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) = launch { repo.setThemeMode(mode) }
    fun setGridEnabled(v: Boolean) = launch { repo.setGridEnabled(v) }
    fun setGridSize(v: Int) = launch { repo.setGridSize(v) }
    fun setSnapToGrid(v: Boolean) = launch { repo.setSnapToGrid(v) }
    fun setShowRulers(v: Boolean) = launch { repo.setShowRulers(v) }
    fun setShowCenterGuides(v: Boolean) = launch { repo.setShowCenterGuides(v) }
    fun setCheckerboardBackground(v: Boolean) = launch { repo.setCheckerboardBackground(v) }
    fun setCanvasDimOutside(v: Boolean) = launch { repo.setCanvasDimOutside(v) }
    fun setAutosaveEnabled(v: Boolean) = launch { repo.setAutosaveEnabled(v) }
    fun setAutosaveInterval(v: Int) = launch { repo.setAutosaveInterval(v) }
    fun setUndoLimit(v: Int) = launch { repo.setUndoLimit(v) }
    fun setDefaultCanvas(w: Int, h: Int) = launch { repo.setDefaultCanvas(w, h) }
    fun setHapticFeedback(v: Boolean) = launch { repo.setHapticFeedback(v) }
    fun setShowSizeTooltip(v: Boolean) = launch { repo.setShowSizeTooltip(v) }
    fun setKeepAspectDefault(v: Boolean) = launch { repo.setKeepAspectDefault(v) }
    fun setDefaultExportFormat(v: String) = launch { repo.setDefaultExportFormat(v) }
    fun setExportImageScale(v: Float) = launch { repo.setExportImageScale(v) }
    fun setIncludeComments(v: Boolean) = launch { repo.setIncludeComments(v) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
