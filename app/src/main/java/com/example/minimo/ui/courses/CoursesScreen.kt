package com.example.minimo.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimo.R
import com.example.minimo.data.SyncInfo
import com.example.minimo.domain.CourseAnalysis
import com.example.minimo.ui.LoadingBox
import com.example.minimo.ui.MessageBox
import com.example.minimo.ui.asPercent
import com.example.minimo.ui.glass.GlassButton
import com.example.minimo.ui.glass.GlassButtonStyle
import com.example.minimo.ui.glass.GlassCard
import com.example.minimo.ui.glass.GlassChip
import com.example.minimo.ui.glass.GlassIconButton
import com.example.minimo.ui.glass.GlassScaffold
import com.example.minimo.ui.glass.GlassTopBar
import com.example.minimo.ui.glass.GradeBar
import com.example.minimo.ui.glass.LargeTitle
import com.example.minimo.ui.glass.LocalGlassBackdrop
import com.example.minimo.ui.mascot.Mimo
import com.example.minimo.ui.mascot.MimoMood
import com.example.minimo.ui.glass.topBarProgress
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CoursesScreen(
    viewModel: CoursesViewModel,
    onOpenCourse: (String) -> Unit,
    onSync: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Like adding, syncing is only offered once the stored data could be read, so nothing is overwritten.
    val ready = state is CoursesUiState.Empty || state is CoursesUiState.Content

    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.courses_title),
                progress = listState.topBarProgress(),
                actions = {
                    if (ready) {
                        GlassButton(
                            text = stringResource(R.string.sync_action),
                            onClick = onSync,
                            style = GlassButtonStyle.Regular,
                            icon = Icons.Filled.Refresh,
                            backdrop = LocalGlassBackdrop.current,
                            compact = true,
                        )
                        GlassIconButton(
                            icon = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.courses_add),
                            onClick = { showEditor = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            CoursesUiState.Loading -> Column(Modifier.padding(top = padding.calculateTopPadding())) {
                LargeTitle(stringResource(R.string.courses_title), Modifier.padding(horizontal = 16.dp))
                LoadingBox()
            }
            is CoursesUiState.Empty -> EmptyState(
                lastSync = current.lastSync,
                padding = padding,
                onSync = onSync,
                onAdd = { showEditor = true },
            )
            CoursesUiState.Error -> Column(Modifier.padding(top = padding.calculateTopPadding())) {
                LargeTitle(stringResource(R.string.courses_title), Modifier.padding(horizontal = 16.dp))
                MessageBox(stringResource(R.string.storage_read_error))
            }
            is CoursesUiState.Content -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "title") { LargeTitle(stringResource(R.string.courses_title)) }
                current.lastSync?.let { sync -> item(key = "sync") { LastSyncLine(sync, Modifier.padding(horizontal = 4.dp)) } }
                items(current.courses, key = { it.id }) { course ->
                    CourseCard(course, onClick = { onOpenCourse(course.id) }, modifier = Modifier.animateItem())
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
private fun EmptyState(lastSync: SyncInfo?, padding: PaddingValues, onSync: () -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
    ) {
        LargeTitle(stringResource(R.string.courses_title), Modifier.padding(horizontal = 16.dp))
        lastSync?.let { LastSyncLine(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mimo(mood = MimoMood.Normal, size = 150.dp, animated = true)
            Text(
                stringResource(R.string.courses_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.courses_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(stringResource(R.string.sync_action), onSync, icon = Icons.Filled.Refresh)
                GlassButton(stringResource(R.string.courses_add), onAdd, style = GlassButtonStyle.Regular, icon = Icons.Filled.Add)
            }
        }
    }
}

@Composable
private fun CourseCard(course: CourseSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.code != null || course.fromPortal) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            course.code?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (course.fromPortal) GlassChip(stringResource(R.string.courses_from_portal))
                        }
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            when (val analysis = course.analysis) {
                CourseAnalysis.NoEvaluations -> Text(
                    text = stringResource(
                        if (course.fromPortal) R.string.courses_portal_no_activities else R.string.courses_no_evaluations,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is CourseAnalysis.Computed -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    analysis.accumulated.toPlainString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.stat_out_of),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                                )
                            }
                            Text(
                                stringResource(R.string.stat_accumulated),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            GlassChip(stringResource(R.string.course_pending_chip, analysis.pendingWeight.asPercent()))
                            Text(
                                stringResource(R.string.course_on_portal, analysis.accumulatedOnPortal.toPlainString()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    GradeBar(
                        earned = analysis.accumulated.toFloat(),
                        possible = analysis.maxPossible.toFloat(),
                        passMark = course.passMark.toFloat(),
                    )
                    if (!analysis.weightsSumTo100) {
                        Text(
                            text = stringResource(R.string.weights_sum_short, analysis.totalWeight.asPercent()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LastSyncLine(sync: SyncInfo, modifier: Modifier = Modifier) {
    val date = remember(sync.syncedAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("es-SV"))
            .format(Date(sync.syncedAtMillis))
    }
    Text(
        text = if (sync.cycle != null) {
            stringResource(R.string.courses_last_sync, sync.cycle, date)
        } else {
            stringResource(R.string.courses_last_sync_no_cycle, date)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
