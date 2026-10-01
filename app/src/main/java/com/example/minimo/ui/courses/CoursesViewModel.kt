package com.example.minimo.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.minimo.AppContainer
import com.example.minimo.data.AppData
import com.example.minimo.data.CourseRepository
import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseAnalysis
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.GradeCalculator
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CourseSummary(
    val id: String,
    val name: String,
    val code: String?,
    val analysis: CourseAnalysis,
)

sealed interface CoursesUiState {
    data object Loading : CoursesUiState
    data object Empty : CoursesUiState
    data class Content(val courses: List<CourseSummary>) : CoursesUiState
    data object Error : CoursesUiState
}

class CoursesViewModel(private val repository: CourseRepository) : ViewModel() {
    val state: StateFlow<CoursesUiState> = repository.appData
        .map { it.toUiState() }
        .catch { emit(CoursesUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoursesUiState.Loading)

    /** Creates a manual course and reports its id once it is saved. */
    fun addCourse(name: String, code: String, onCreated: (String) -> Unit) {
        val course = Course(
            id = UUID.randomUUID().toString(),
            code = code.trim().ifEmpty { null },
            name = name.trim(),
            source = CourseSource.MANUAL,
            goal = null,
            evaluations = emptyList(),
        )
        viewModelScope.launch {
            repository.saveCourse(course)
            onCreated(course.id)
        }
    }

    private fun AppData.toUiState(): CoursesUiState {
        if (courses.isEmpty()) return CoursesUiState.Empty
        return CoursesUiState.Content(
            courses.map { course ->
                CourseSummary(
                    id = course.id,
                    name = course.name,
                    code = course.code,
                    analysis = GradeCalculator.analyze(
                        evaluations = course.evaluations,
                        passMark = settings.passMark,
                        goal = settings.effectiveGoal(course.goal),
                    ),
                )
            },
        )
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CoursesViewModel(container.courseRepository) }
        }
    }
}
