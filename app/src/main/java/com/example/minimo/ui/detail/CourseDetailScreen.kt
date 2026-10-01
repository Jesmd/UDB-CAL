package com.example.minimo.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimo.R
import com.example.minimo.domain.CourseAnalysis
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.TargetOutcome
import com.example.minimo.ui.ConfirmDeleteDialog
import com.example.minimo.ui.LoadingBox
import com.example.minimo.ui.MessageBox
import com.example.minimo.ui.asNumber
import com.example.minimo.ui.asPercent
import com.example.minimo.ui.asThreshold
import com.example.minimo.ui.courses.CourseEditorDialog
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    viewModel: CourseDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCourseEditor by rememberSaveable { mutableStateOf(false) }
    var showCourseDelete by rememberSaveable { mutableStateOf(false) }
    // Evaluation being edited: its id, or NEW_EVALUATION for a new one, or null when no dialog is open.
    var editingEvaluationId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingEvaluationId by rememberSaveable { mutableStateOf<String?>(null) }

    val content = state as? CourseDetailUiState.Content

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(content?.course?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Portal courses are replaced on each sync, so they are not edited or deleted by hand.
                    if (content != null && !content.fromPortal) {
                        IconButton(onClick = { showCourseEditor = true }) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { showCourseDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            CourseDetailUiState.Loading -> LoadingBox(modifier)
            CourseDetailUiState.NotFound -> MessageBox(stringResource(R.string.detail_not_found), modifier)
            CourseDetailUiState.Error -> MessageBox(stringResource(R.string.storage_read_error), modifier)
            is CourseDetailUiState.Content -> if (content != null) {
                DetailContent(
                    content = content,
                    viewModel = viewModel,
                    onEditEvaluation = { editingEvaluationId = it },
                    modifier = modifier,
                )
            }
        }
    }

    if (content != null) {
        if (showCourseEditor) {
            CourseEditorDialog(
                title = stringResource(R.string.course_edit_title),
                initialName = content.course.name,
                initialCode = content.course.code.orEmpty(),
                onSave = { name, code ->
                    showCourseEditor = false
                    viewModel.editCourse(name, code)
                },
                onDismiss = { showCourseEditor = false },
            )
        }
        if (showCourseDelete) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.course_delete_title),
                message = stringResource(R.string.course_delete_message, content.course.name),
                onConfirm = {
                    showCourseDelete = false
                    viewModel.deleteCourse(onDeleted = onBack)
                },
                onDismiss = { showCourseDelete = false },
            )
        }
        editingEvaluationId?.let { id ->
            val existing = content.course.evaluations.firstOrNull { it.id == id }
            EvaluationEditorDialog(
                initial = existing,
                onSave = { name, weight, grade ->
                    editingEvaluationId = null
                    viewModel.saveEvaluation(existing?.id, name, weight, grade)
                },
                onDelete = existing?.let { { deletingEvaluationId = it.id } },
                onDismiss = { editingEvaluationId = null },
            )
        }
        deletingEvaluationId?.let { id ->
            val name = content.course.evaluations.firstOrNull { it.id == id }?.name.orEmpty()
            ConfirmDeleteDialog(
                title = stringResource(R.string.evaluation_delete_title),
                message = stringResource(R.string.evaluation_delete_message, name),
                onConfirm = {
                    deletingEvaluationId = null
                    editingEvaluationId = null
                    viewModel.deleteEvaluation(id)
                },
                onDismiss = { deletingEvaluationId = null },
            )
        }
    }
}

/** Marker id meaning "a new evaluation" (no existing evaluation has this id). */
private const val NEW_EVALUATION = "new"

@Composable
private fun DetailContent(
    content: CourseDetailUiState.Content,
    viewModel: CourseDetailViewModel,
    onEditEvaluation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SummaryBlock(content) }
        item {
            EvaluationsBlock(
                evaluations = content.course.evaluations,
                fromPortal = content.fromPortal,
                portalZeros = content.portalZeros,
                onAdd = { onEditEvaluation(NEW_EVALUATION) },
                onEdit = { onEditEvaluation(it.id) },
                onRealZeroChange = viewModel::setRealZero,
            )
        }
        if (content.analysis is CourseAnalysis.Computed) {
            item {
                GoalBlock(
                    content = content,
                    onGoalChosen = viewModel::setGoalTenths,
                    onUseDefault = viewModel::useDefaultGoal,
                )
            }
            item {
                Button(onClick = viewModel::onCalculateMinimum, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.calculate_minimum))
                }
            }
            if (content.minimumShown) {
                item { ResultsBlock(content, content.analysis) }
            }
            if (content.canSimulate) {
                item {
                    SimulatorBlock(
                        content = content,
                        onTextChange = viewModel::onSimulatorTextChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBlock(content: CourseDetailUiState.Content) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(content.course.name, style = MaterialTheme.typography.headlineSmall)
        val origin = when {
            content.fromPortal -> stringResource(R.string.detail_portal_source, content.course.code.orEmpty())
            else -> content.course.code
        }
        origin?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (val analysis = content.analysis) {
            CourseAnalysis.NoEvaluations -> Unit
            is CourseAnalysis.Computed -> {
                Text(
                    stringResource(
                        R.string.detail_summary,
                        analysis.accumulated.toPlainString(),
                        analysis.accumulatedOnPortal.toPlainString(),
                        analysis.pendingWeight.asPercent(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!analysis.weightsSumTo100) {
                    Text(
                        stringResource(R.string.detail_weights_warning, analysis.totalWeight.asPercent()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun EvaluationsBlock(
    evaluations: List<Evaluation>,
    fromPortal: Boolean,
    portalZeros: Map<String, Boolean>,
    onAdd: () -> Unit,
    onEdit: (Evaluation) -> Unit,
    onRealZeroChange: (String, Boolean) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.evaluations_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (!fromPortal) {
                    TextButton(onClick = onAdd) { Text(stringResource(R.string.evaluations_add)) }
                }
            }
            if (fromPortal) {
                Text(
                    stringResource(R.string.detail_portal_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (evaluations.isEmpty()) {
                Text(
                    stringResource(if (fromPortal) R.string.courses_portal_no_activities else R.string.evaluations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    HeaderCell(stringResource(R.string.evaluations_header_name), Modifier.weight(1f))
                    HeaderCell(stringResource(R.string.evaluations_header_weight), Modifier.padding(start = 8.dp).weight(0.28f), TextAlign.End)
                    HeaderCell(stringResource(R.string.evaluations_header_grade), Modifier.padding(start = 8.dp).weight(0.4f), TextAlign.End)
                }
                evaluations.forEach { evaluation ->
                    HorizontalDivider()
                    // Long names (the portal has some over 150 characters) show 4 lines; tapping a portal row shows
                    // the whole name. Manual rows open the editor, which shows it whole.
                    var expanded by rememberSaveable(evaluation.id) { mutableStateOf(false) }
                    val expandLabel = stringResource(if (expanded) R.string.evaluation_collapse else R.string.evaluation_expand)
                    val rowModifier = if (fromPortal) {
                        Modifier.clickable(onClickLabel = expandLabel) { expanded = !expanded }
                    } else {
                        Modifier.clickable(onClickLabel = stringResource(R.string.action_edit)) { onEdit(evaluation) }
                    }
                    Row(
                        rowModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                evaluation.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            portalZeros[evaluation.id]?.let { isRealZero ->
                                Text(
                                    stringResource(R.string.evaluation_portal_zero_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = { onRealZeroChange(evaluation.id, !isRealZero) },
                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                ) {
                                    Text(
                                        stringResource(
                                            if (isRealZero) R.string.evaluation_back_to_pending else R.string.evaluation_real_zero,
                                        ),
                                    )
                                }
                            }
                        }
                        Text(
                            evaluation.weight.asPercent(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 8.dp).weight(0.28f),
                        )
                        val grade = (evaluation.status as? EvaluationStatus.Graded)?.grade
                        Text(
                            text = grade?.asNumber() ?: stringResource(R.string.evaluation_pending),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (grade != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (grade != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 8.dp).weight(0.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

@Composable
private fun GoalBlock(
    content: CourseDetailUiState.Content,
    onGoalChosen: (Int) -> Unit,
    onUseDefault: () -> Unit,
) {
    val minTenths = content.settings.passMark.movePointRight(1).toInt()
    val savedTenths = content.goal.movePointRight(1).toInt()
    // The slider follows the finger; the goal is saved when the finger lifts.
    var dragged by remember(savedTenths) { mutableFloatStateOf(savedTenths.toFloat()) }
    val shownTenths = dragged.toInt()

    Column {
        Text(stringResource(R.string.goal_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.goal_value, BigDecimal.valueOf(shownTenths.toLong(), 1).asThreshold()),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (minTenths < 100) {
            val goalDescription = stringResource(R.string.goal_value, BigDecimal.valueOf(shownTenths.toLong(), 1).asThreshold())
            Slider(
                modifier = Modifier.semantics { stateDescription = goalDescription },
                value = dragged,
                onValueChange = { dragged = it },
                onValueChangeFinished = { onGoalChosen(dragged.toInt()) },
                valueRange = minTenths.toFloat()..100f,
                steps = 100 - minTenths - 1,
            )
        }
        if (content.hasOwnGoal) {
            TextButton(onClick = onUseDefault) { Text(stringResource(R.string.goal_reset)) }
        } else {
            Text(
                stringResource(R.string.goal_default_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultsBlock(content: CourseDetailUiState.Content, analysis: CourseAnalysis.Computed) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetLine(
                label = stringResource(R.string.target_pass_label, content.settings.passMark.asThreshold()),
                outcome = analysis.toPass,
            )
            // When the goal equals the pass mark the second line would just repeat the first.
            if (content.goal.compareTo(content.settings.passMark) != 0) {
                TargetLine(
                    label = stringResource(R.string.target_goal_label, content.goal.asThreshold()),
                    outcome = analysis.toGoal,
                )
            }
            Text(
                stringResource(R.string.max_possible, analysis.maxPossible.toPlainString()),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun TargetLine(label: String, outcome: TargetOutcome) {
    val description = when (outcome) {
        is TargetOutcome.Secured -> stringResource(R.string.outcome_secured)
        is TargetOutcome.Reachable -> stringResource(R.string.outcome_reachable, outcome.required.toPlainString())
        is TargetOutcome.Impossible -> stringResource(R.string.outcome_impossible, outcome.required.toPlainString())
        is TargetOutcome.NoPending -> stringResource(
            if (outcome.reached) R.string.outcome_final_reached else R.string.outcome_final_not_reached,
            outcome.finalGrade.toPlainString(),
        )
    }
    val isBad = outcome is TargetOutcome.Impossible || (outcome is TargetOutcome.NoPending && !outcome.reached)
    Text(
        text = stringResource(R.string.target_line, label, description),
        style = MaterialTheme.typography.bodyLarge,
        color = if (isBad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SimulatorBlock(
    content: CourseDetailUiState.Content,
    onTextChange: (String) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.simulator_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val invalid = content.simulation == SimulationState.Invalid
            OutlinedTextField(
                value = content.simulatorText,
                onValueChange = onTextChange,
                label = { Text(stringResource(R.string.simulator_label)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.simulator_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            val simulation = content.simulation
            if (simulation is SimulationState.Result) {
                val projection = simulation.projection
                val passMark = content.settings.passMark.asThreshold()
                Text(
                    stringResource(
                        R.string.simulator_final,
                        projection.finalGrade.toPlainString(),
                        projection.finalGradeOnPortal.toPlainString(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        if (projection.passes) R.string.simulator_passes else R.string.simulator_fails,
                        passMark,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (projection.passes) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
                if (content.goal.compareTo(content.settings.passMark) != 0) {
                    Text(
                        stringResource(
                            if (projection.reachesGoal) R.string.simulator_goal_reached else R.string.simulator_goal_not_reached,
                            content.goal.asThreshold(),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
