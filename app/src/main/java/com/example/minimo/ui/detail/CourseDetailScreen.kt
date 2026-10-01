package com.example.minimo.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.minimo.ui.glass.GlassButton
import com.example.minimo.ui.glass.GlassButtonStyle
import com.example.minimo.ui.glass.GlassCard
import com.example.minimo.ui.glass.GlassDivider
import com.example.minimo.ui.glass.GlassIconButton
import com.example.minimo.ui.glass.GlassNotice
import com.example.minimo.ui.glass.GlassScaffold
import com.example.minimo.ui.glass.GlassSliderThumb
import com.example.minimo.ui.glass.GlassSliderTrack
import com.example.minimo.ui.glass.GlassTextField
import com.example.minimo.ui.glass.GlassTopBar
import com.example.minimo.ui.glass.GradeBar
import com.example.minimo.ui.glass.NoticeKind
import com.example.minimo.ui.glass.topBarProgress
import com.example.minimo.ui.theme.glass
import java.math.BigDecimal

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
    val listState = rememberLazyListState()

    val content = state as? CourseDetailUiState.Content

    // If the evaluation being edited disappears (e.g. a sync replaced it), close its dialogs instead of
    // letting the form turn into a "new evaluation" form that would save a copy.
    val editingGone = content != null && editingEvaluationId.let { id ->
        id != null && id != NEW_EVALUATION && content.course.evaluations.none { it.id == id }
    }
    LaunchedEffect(editingGone) {
        if (editingGone) {
            editingEvaluationId = null
            deletingEvaluationId = null
        }
    }

    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = content?.course?.name.orEmpty(),
                progress = listState.topBarProgress(),
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
                },
                actions = {
                    // Portal courses are replaced on each sync, so they are not edited or deleted by hand.
                    if (content != null && !content.fromPortal) {
                        GlassIconButton(Icons.Filled.Edit, stringResource(R.string.action_edit), { showCourseEditor = true })
                        GlassIconButton(
                            Icons.Filled.Delete,
                            stringResource(R.string.action_delete),
                            { showCourseDelete = true },
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            CourseDetailUiState.Loading -> LoadingBox(Modifier.padding(padding))
            CourseDetailUiState.NotFound -> MessageBox(stringResource(R.string.detail_not_found), Modifier.padding(padding))
            CourseDetailUiState.Error -> MessageBox(stringResource(R.string.storage_read_error), Modifier.padding(padding))
            is CourseDetailUiState.Content -> if (content != null) {
                DetailContent(
                    content = content,
                    viewModel = viewModel,
                    listState = listState,
                    padding = padding,
                    onEditEvaluation = { editingEvaluationId = it },
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
        editingEvaluationId?.takeUnless { editingGone }?.let { id ->
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
        deletingEvaluationId?.takeUnless { editingGone }?.let { id ->
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
    listState: LazyListState,
    padding: PaddingValues,
    onEditEvaluation: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "summary") { SummaryBlock(content) }
        item(key = "evaluations") {
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
            item(key = "goal") {
                GoalBlock(
                    content = content,
                    onGoalChosen = viewModel::setGoalTenths,
                    onUseDefault = viewModel::useDefaultGoal,
                )
            }
            item(key = "calculate") {
                GlassButton(
                    text = stringResource(R.string.calculate_minimum),
                    onClick = viewModel::onCalculateMinimum,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (content.minimumShown) {
                item(key = "results") { ResultsBlock(content, content.analysis, Modifier.animateItem()) }
            }
            if (content.canSimulate) {
                item(key = "simulator") {
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                content.course.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            val origin = when {
                content.fromPortal -> stringResource(R.string.detail_portal_source, content.course.code.orEmpty())
                else -> content.course.code
            }
            origin?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (val analysis = content.analysis) {
            CourseAnalysis.NoEvaluations -> Unit
            is CourseAnalysis.Computed -> {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Stat(
                                label = stringResource(R.string.stat_accumulated),
                                value = analysis.accumulated.toPlainString(),
                                suffix = stringResource(R.string.stat_out_of),
                                modifier = Modifier.weight(1f),
                            )
                            Stat(
                                label = stringResource(R.string.stat_pending),
                                value = analysis.pendingWeight.asPercent(),
                                modifier = Modifier.weight(1f),
                            )
                            Stat(
                                label = stringResource(R.string.stat_portal),
                                value = analysis.accumulatedOnPortal.toPlainString(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        GradeBar(
                            earned = analysis.accumulated.toFloat(),
                            possible = analysis.maxPossible.toFloat(),
                            passMark = content.settings.passMark.toFloat(),
                        )
                    }
                }
                if (!analysis.weightsSumTo100) {
                    GlassNotice(
                        stringResource(R.string.detail_weights_warning, analysis.totalWeight.asPercent()),
                        kind = NoticeKind.Warning,
                        icon = Icons.Filled.Warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, suffix: String? = null) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            if (suffix != null) {
                Text(
                    suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp).heightIn(min = 52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.evaluations_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (!fromPortal) {
                    GlassButton(
                        stringResource(R.string.evaluations_add),
                        onAdd,
                        style = GlassButtonStyle.Regular,
                        icon = Icons.Filled.Add,
                        compact = true,
                    )
                }
            }
            if (fromPortal) {
                Text(
                    stringResource(R.string.detail_portal_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
            }
            if (evaluations.isEmpty()) {
                Text(
                    stringResource(if (fromPortal) R.string.courses_portal_no_activities else R.string.evaluations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    HeaderCell(stringResource(R.string.evaluations_header_name), Modifier.weight(1f))
                    HeaderCell(stringResource(R.string.evaluations_header_weight), Modifier.padding(start = 8.dp).weight(0.28f), TextAlign.End)
                    HeaderCell(stringResource(R.string.evaluations_header_grade), Modifier.padding(start = 8.dp).weight(0.4f), TextAlign.End)
                }
                evaluations.forEach { evaluation ->
                    GlassDivider(Modifier.padding(horizontal = 20.dp))
                    EvaluationRow(
                        evaluation = evaluation,
                        fromPortal = fromPortal,
                        portalZero = portalZeros[evaluation.id],
                        onEdit = { onEdit(evaluation) },
                        onRealZeroChange = { onRealZeroChange(evaluation.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EvaluationRow(
    evaluation: Evaluation,
    fromPortal: Boolean,
    portalZero: Boolean?,
    onEdit: () -> Unit,
    onRealZeroChange: (Boolean) -> Unit,
) {
    // Long names (the portal has some over 150 characters) show 4 lines; tapping a portal row shows the whole
    // name. Manual rows open the editor, which shows it whole.
    var expanded by rememberSaveable(evaluation.id) { mutableStateOf(false) }
    val expandLabel = stringResource(if (expanded) R.string.evaluation_collapse else R.string.evaluation_expand)
    val editLabel = stringResource(R.string.action_edit)
    val rowModifier = if (fromPortal) {
        Modifier.clickable(onClickLabel = expandLabel) { expanded = !expanded }
    } else {
        Modifier.clickable(onClickLabel = editLabel, onClick = onEdit)
    }
    Row(
        rowModifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                evaluation.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            portalZero?.let { isRealZero ->
                Text(
                    stringResource(R.string.evaluation_portal_zero_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GlassButton(
                    text = stringResource(if (isRealZero) R.string.evaluation_back_to_pending else R.string.evaluation_real_zero),
                    onClick = { onRealZeroChange(!isRealZero) },
                    style = GlassButtonStyle.Regular,
                    compact = true,
                    modifier = Modifier.padding(top = 6.dp),
                )
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val goalText = stringResource(R.string.goal_value, BigDecimal.valueOf(shownTenths.toLong(), 1).asThreshold())

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.goal_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(goalText, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            if (minTenths < 100) {
                Slider(
                    modifier = Modifier.semantics { stateDescription = goalText },
                    value = dragged,
                    onValueChange = {
                        if (it.toInt() != dragged.toInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        dragged = it
                    },
                    onValueChangeFinished = { onGoalChosen(dragged.toInt()) },
                    valueRange = minTenths.toFloat()..100f,
                    steps = 100 - minTenths - 1,
                    interactionSource = interaction,
                    thumb = { GlassSliderThumb(interaction) },
                    track = { GlassSliderTrack(it) },
                )
            }
            if (content.hasOwnGoal) {
                GlassButton(
                    stringResource(R.string.goal_reset),
                    onUseDefault,
                    style = GlassButtonStyle.Regular,
                    compact = true,
                )
            } else {
                Text(
                    stringResource(R.string.goal_default_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultsBlock(content: CourseDetailUiState.Content, analysis: CourseAnalysis.Computed, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TargetLine(
                label = stringResource(R.string.target_pass_label, content.settings.passMark.asThreshold()),
                outcome = analysis.toPass,
            )
            // When the goal equals the pass mark the second line would just repeat the first.
            if (content.goal.compareTo(content.settings.passMark) != 0) {
                GlassDivider()
                TargetLine(
                    label = stringResource(R.string.target_goal_label, content.goal.asThreshold()),
                    outcome = analysis.toGoal,
                )
            }
            GlassDivider()
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
    val isGood = outcome is TargetOutcome.Secured || (outcome is TargetOutcome.NoPending && outcome.reached)
    val color = when {
        isBad -> MaterialTheme.colorScheme.error
        isGood -> MaterialTheme.glass.success
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when {
        isBad -> Icons.Filled.Warning
        isGood -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Info
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(description, style = MaterialTheme.typography.titleSmall, color = color)
        }
    }
}

@Composable
private fun SimulatorBlock(
    content: CourseDetailUiState.Content,
    onTextChange: (String) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.simulator_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            val invalid = content.simulation == SimulationState.Invalid
            GlassTextField(
                value = content.simulatorText,
                onValueChange = onTextChange,
                label = stringResource(R.string.simulator_label),
                isError = invalid,
                supportingText = if (invalid) stringResource(R.string.simulator_invalid) else null,
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
                VerdictLine(
                    text = stringResource(
                        if (projection.passes) R.string.simulator_passes else R.string.simulator_fails,
                        passMark,
                    ),
                    good = projection.passes,
                )
                if (content.goal.compareTo(content.settings.passMark) != 0) {
                    VerdictLine(
                        text = stringResource(
                            if (projection.reachesGoal) R.string.simulator_goal_reached else R.string.simulator_goal_not_reached,
                            content.goal.asThreshold(),
                        ),
                        good = projection.reachesGoal,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictLine(text: String, good: Boolean) {
    val color: Color = if (good) MaterialTheme.glass.success else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (good) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
