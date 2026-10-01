package com.example.minimo.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.minimo.AppContainer
import com.example.minimo.data.CourseRepository
import com.example.minimo.data.portal.AcademicDataSource
import com.example.minimo.data.portal.PortalAcademicDataSource
import com.example.minimo.data.portal.PortalBrowser
import com.example.minimo.data.portal.PortalException
import com.example.minimo.data.portal.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SyncFailure { Network, Timeout, PortalChanged, DetailFailed, SaveFailed }

sealed interface SyncUiState {
    /** Opening the Notas page. */
    data object Opening : SyncUiState

    /** The portal asked to log in; the login page is on screen. Sync resumes after logging in. */
    data object NeedsLogin : SyncUiState

    data class Reading(val done: Int, val total: Int) : SyncUiState

    data class Done(val courseCount: Int, val cycle: String?) : SyncUiState

    /** @property detail technical detail (browser error code, module code...) or `null`. */
    data class Failed(val reason: SyncFailure, val detail: String?) : SyncUiState
}

/**
 * Runs one sync, only when the user opened this screen. Portal courses are replaced only if the whole
 * read succeeded; on any failure nothing is changed.
 */
class SyncViewModel(
    private val repository: CourseRepository,
    sessionState: StateFlow<SessionState>,
    private val onPageLoaded: (String?) -> Unit,
    private val dataSourceFor: (PortalBrowser) -> AcademicDataSource = { PortalAcademicDataSource(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow<SyncUiState>(SyncUiState.Opening)
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private var browser: PortalBrowser? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            sessionState.collect { session ->
                if (session == SessionState.LoggedIn && _state.value == SyncUiState.NeedsLogin) start()
            }
        }
    }

    /** The screen's browser is ready: start syncing. */
    fun attach(browser: PortalBrowser) {
        this.browser = browser
        if (_state.value !is SyncUiState.Done) start()
    }

    /** The screen's browser is gone: stop whatever was running. */
    fun detach() {
        job?.cancel()
        job = null
        browser = null
    }

    fun retry() = start()

    fun pageLoaded(url: String?) = onPageLoaded(url)

    private fun start() {
        val current = browser ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = SyncUiState.Opening
            _state.value = try {
                val snapshot = dataSourceFor(current).fetchCourses { done, total ->
                    _state.value = SyncUiState.Reading(done, total)
                }
                if (repository.applyPortalSync(snapshot, clock())) {
                    SyncUiState.Done(snapshot.courses.size, snapshot.cycle)
                } else {
                    SyncUiState.Failed(SyncFailure.SaveFailed, null)
                }
            } catch (e: PortalException) {
                when (e) {
                    is PortalException.NotLoggedIn -> SyncUiState.NeedsLogin
                    is PortalException.LoadFailed -> SyncUiState.Failed(SyncFailure.Network, e.detail)
                    is PortalException.Timeout -> SyncUiState.Failed(SyncFailure.Timeout, e.what)
                    is PortalException.DetailFailed -> SyncUiState.Failed(SyncFailure.DetailFailed, e.moduleCode)
                    is PortalException.FormatChanged -> SyncUiState.Failed(SyncFailure.PortalChanged, e.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything unexpected while reading the portal must not crash the app.
                SyncUiState.Failed(SyncFailure.PortalChanged, e.javaClass.simpleName)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SyncViewModel(
                    repository = container.courseRepository,
                    sessionState = container.sessionManager.state,
                    onPageLoaded = container.sessionManager::onPageLoaded,
                )
            }
        }
    }
}
