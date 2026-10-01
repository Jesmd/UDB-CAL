package com.example.minimo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.minimo.AppContainer
import com.example.minimo.data.CourseRepository
import com.example.minimo.domain.GradeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Content(val settings: GradeSettings) : SettingsUiState
    data object Error : SettingsUiState
}

class SettingsViewModel(private val repository: CourseRepository) : ViewModel() {
    val state: StateFlow<SettingsUiState> = repository.appData
        .map<_, SettingsUiState> { SettingsUiState.Content(it.settings) }
        .catch { emit(SettingsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun save(settings: GradeSettings) {
        viewModelScope.launch { repository.saveSettings(settings) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container.courseRepository) }
        }
    }
}
