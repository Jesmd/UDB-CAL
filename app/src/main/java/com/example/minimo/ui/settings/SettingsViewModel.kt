package com.example.minimo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.minimo.AppContainer
import com.example.minimo.data.CourseRepository
import com.example.minimo.data.portal.SessionManager
import com.example.minimo.data.portal.SessionState
import com.example.minimo.domain.GradeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Content(
        val settings: GradeSettings,
        val diagnosticMode: Boolean,
        val session: SessionState,
    ) : SettingsUiState
    data object Error : SettingsUiState
}

class SettingsViewModel(
    private val repository: CourseRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(repository.appData, sessionManager.state) { data, session ->
        SettingsUiState.Content(data.settings, data.diagnosticMode, session)
    }
        .catch<SettingsUiState> { emit(SettingsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun save(settings: GradeSettings) {
        viewModelScope.launch { repository.saveSettings(settings) }
    }

    fun setDiagnosticMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDiagnosticMode(enabled) }
    }

    /** Deletes the portal's cookies and storage kept by the in-app browser. */
    fun logout() {
        viewModelScope.launch { sessionManager.logout() }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container.courseRepository, container.sessionManager) }
        }
    }
}
