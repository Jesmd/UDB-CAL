package com.example.minimo.ui.courses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimo.R
import com.example.minimo.domain.CourseAnalysis
import com.example.minimo.ui.LoadingBox
import com.example.minimo.ui.MessageBox
import com.example.minimo.ui.asPercent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    viewModel: CoursesViewModel,
    onOpenCourse: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEditor by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.courses_title)) }) },
        floatingActionButton = {
            // Adding is only offered once the stored data could be read, so nothing is overwritten.
            if (state is CoursesUiState.Empty || state is CoursesUiState.Content) {
                FloatingActionButton(onClick = { showEditor = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.courses_add))
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val current = state) {
            CoursesUiState.Loading -> LoadingBox(modifier)
            CoursesUiState.Empty -> MessageBox(
                title = stringResource(R.string.courses_empty_title),
                hint = stringResource(R.string.courses_empty_hint),
                modifier = modifier,
            )
            CoursesUiState.Error -> MessageBox(
                title = stringResource(R.string.storage_read_error),
                modifier = modifier,
            )
            is CoursesUiState.Content -> LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(current.courses, key = { it.id }) { course ->
                    CourseCard(course, onClick = { onOpenCourse(course.id) })
                }
            }
        }
    }

    if (showEditor) {
        CourseEditorDialog(
            title = stringResource(R.string.course_new_title),
            initialName = "",
            initialCode = "",
            onSave = { name, code ->
                showEditor = false
                viewModel.addCourse(name, code, onCreated = onOpenCourse)
            },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun CourseCard(course: CourseSummary, onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.code != null) {
                Text(
                    text = course.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val analysis = course.analysis) {
                CourseAnalysis.NoEvaluations -> Text(
                    text = stringResource(R.string.courses_no_evaluations),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                is CourseAnalysis.Computed -> {
                    Text(
                        text = stringResource(
                            R.string.courses_summary,
                            analysis.accumulated.toPlainString(),
                            analysis.pendingWeight.asPercent(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (!analysis.weightsSumTo100) {
                        Text(
                            text = stringResource(R.string.weights_sum_short, analysis.totalWeight.asPercent()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
