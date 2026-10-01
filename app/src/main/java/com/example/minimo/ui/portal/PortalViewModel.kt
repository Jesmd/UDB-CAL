package com.example.minimo.ui.portal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.minimo.AppContainer
import com.example.minimo.data.CourseRepository
import com.example.minimo.data.portal.SessionManager
import com.example.minimo.data.portal.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PortalUiState(
    val session: SessionState = SessionState.LoggedOut,
    val diagnosticMode: Boolean = false,
)

class PortalViewModel(
    repository: CourseRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    val state: StateFlow<PortalUiState> = combine(
        repository.appData.map { it.diagnosticMode }.catch { emit(false) },
        sessionManager.state,
    ) { diagnostic, session -> PortalUiState(session, diagnostic) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortalUiState())

    fun onPageLoaded(url: String?) = sessionManager.onPageLoaded(url)

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { PortalViewModel(container.courseRepository, container.sessionManager) }
        }
    }
}
