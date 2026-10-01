package com.example.minimo.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.minimo.R
import com.example.minimo.domain.DecimalInput
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.Validation
import com.example.minimo.ui.asNumber
import com.example.minimo.ui.glass.GlassButton
import com.example.minimo.ui.glass.GlassButtonStyle
import com.example.minimo.ui.glass.GlassDialog
import com.example.minimo.ui.glass.GlassSwitch
import com.example.minimo.ui.glass.GlassTextField
import java.math.BigDecimal

/** Creates an evaluation (when [initial] is null) or edits one. Invalid values are rejected here. */
@Composable
fun EvaluationEditorDialog(
    initial: Evaluation?,
    onSave: (name: String, weight: BigDecimal, grade: BigDecimal?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val initialGrade = (initial?.status as? EvaluationStatus.Graded)?.grade
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var weightText by rememberSaveable { mutableStateOf(initial?.weight?.asNumber().orEmpty()) }
    var graded by rememberSaveable { mutableStateOf(initialGrade != null) }
    var gradeText by rememberSaveable { mutableStateOf(initialGrade?.asNumber().orEmpty()) }

    val weight = DecimalInput.parse(weightText)?.takeIf { Validation.isValidWeight(it) }
    val grade = DecimalInput.parse(gradeText)?.takeIf { Validation.isValidGrade(it) }
    val nameValid = name.isNotBlank()
    val canSave = nameValid && weight != null && (!graded || grade != null)
    // Errors are shown only once something was typed, so an empty new form is not red.
    val nameError = name.isNotEmpty() && !nameValid
    val weightError = weightText.isNotEmpty() && weight == null
    val gradeError = gradeText.isNotEmpty() && grade == null

    GlassDialog(
        title = stringResource(if (initial == null) R.string.evaluation_new_title else R.string.evaluation_edit_title),
        onDismiss = onDismiss,
        buttons = {
            GlassButton(
                stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = GlassButtonStyle.Regular,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                stringResource(R.string.action_save),
                onClick = { onSave(name, checkNotNull(weight), if (graded) grade else null) },
                enabled = canSave,
                modifier = Modifier.weight(1f),
            )
        },
    ) {
        GlassTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.evaluation_name_label),
            isError = nameError,
            supportingText = if (nameError) stringResource(R.string.evaluation_name_required) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        GlassTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = stringResource(R.string.evaluation_weight_label),
            isError = weightError,
            supportingText = if (weightError) stringResource(R.string.evaluation_weight_invalid) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
        ) {
            Text(stringResource(R.string.evaluation_graded_label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            GlassSwitch(checked = graded, onCheckedChange = { graded = it })
        }
        if (graded) {
            GlassTextField(
                value = gradeText,
                onValueChange = { gradeText = it },
                label = stringResource(R.string.evaluation_grade_label),
                isError = gradeError,
                supportingText = if (gradeError) stringResource(R.string.evaluation_grade_invalid) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onDelete != null) {
            GlassButton(
                stringResource(R.string.action_delete),
                onClick = onDelete,
                style = GlassButtonStyle.Danger,
                compact = true,
            )
        }
    }
}
