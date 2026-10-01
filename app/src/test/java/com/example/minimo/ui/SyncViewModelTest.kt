package com.example.minimo.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.minimo.data.CourseRepository
import com.example.minimo.data.portal.AcademicDataSource
import com.example.minimo.data.portal.Fixtures
import com.example.minimo.data.portal.GradesParser
import com.example.minimo.data.portal.PortalBrowser
import com.example.minimo.data.portal.PortalCourseData
import com.example.minimo.data.portal.PortalException
import com.example.minimo.data.portal.PortalModule
import com.example.minimo.data.portal.PortalSnapshot
import com.example.minimo.data.portal.SessionState
import com.example.minimo.ui.sync.SyncFailure
import com.example.minimo.ui.sync.SyncUiState
import com.example.minimo.ui.sync.SyncViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val snapshot = PortalSnapshot(
        "02 2026",
        listOf(
            PortalCourseData(
                PortalModule("DMD104", "Datawarehouse y Minería de Datos", false),
                checkNotNull(GradesParser.parseDetail(Fixtures.detalleDmd104)).activities,
            ),
        ),
    )

    private val noBrowser = object : PortalBrowser {
        override suspend fun load(url: String): String? = error("unused")
        override suspend fun evaluate(script: String): String = error("unused")
    }

    /** Fails with each exception in [failures] in turn, then returns the snapshot. */
    private class ScriptedSource(failures: List<Exception>, val snapshot: PortalSnapshot) : AcademicDataSource {
        private val remaining = failures.toMutableList()
        var calls = 0
        override suspend fun fetchCourses(onProgress: (Int, Int) -> Unit): PortalSnapshot {
            calls++
            if (remaining.isNotEmpty()) throw remaining.removeAt(0)
            onProgress(1, 1)
            return snapshot
        }
    }

    /** Waits until the sync stops (saving to disk happens on a real IO thread). */
    private suspend fun SyncViewModel.finished(): SyncUiState =
        state.first { it is SyncUiState.Done || it is SyncUiState.Failed || it == SyncUiState.NeedsLogin }

    private fun TestScope.repository() =
        CourseRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "t.preferences_pb") })

    @Test
    fun aSuccessfulSyncSavesTheCourses() = runTest {
        val repository = repository()
        val source = ScriptedSource(emptyList(), snapshot)
        val viewModel = SyncViewModel(repository, MutableStateFlow(SessionState.LoggedIn), {}, { source }, { 5_000 })

        viewModel.attach(noBrowser)

        assertEquals(SyncUiState.Done(1, "02 2026"), viewModel.finished())
        assertEquals(listOf("portal-DMD104"), repository.appData.first().courses.map { it.id })
    }

    @Test
    fun whenTheLoginIsNeededItWaitsAndResumesAfterLoggingIn() = runTest {
        val session = MutableStateFlow(SessionState.LoggedOut)
        val source = ScriptedSource(listOf(PortalException.NotLoggedIn()), snapshot)
        val viewModel = SyncViewModel(repository(), session, {}, { source })

        viewModel.attach(noBrowser)
        assertEquals(SyncUiState.NeedsLogin, viewModel.finished())

        session.value = SessionState.LoggedIn
        assertTrue(viewModel.state.first { it is SyncUiState.Done } is SyncUiState.Done)
        assertEquals(2, source.calls)
    }

    @Test
    fun aFailureChangesNothing() = runTest {
        val repository = repository()
        val source = ScriptedSource(listOf(PortalException.FormatChanged("Missing #divNotasFinales")), snapshot)
        val viewModel = SyncViewModel(repository, MutableStateFlow(SessionState.LoggedIn), {}, { source })

        viewModel.attach(noBrowser)

        val state = viewModel.finished() as SyncUiState.Failed
        assertEquals(SyncFailure.PortalChanged, state.reason)
        assertTrue(repository.appData.first().courses.isEmpty())
    }

    @Test
    fun anUnexpectedErrorDoesNotCrash() = runTest {
        val source = ScriptedSource(listOf(IllegalStateException("boom")), snapshot)
        val viewModel = SyncViewModel(repository(), MutableStateFlow(SessionState.LoggedIn), {}, { source })

        viewModel.attach(noBrowser)

        assertEquals(SyncFailure.PortalChanged, (viewModel.finished() as SyncUiState.Failed).reason)
    }
}
