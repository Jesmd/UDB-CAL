package com.example.minimo.ui.courses

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.minimo.R
import com.example.minimo.ui.glass.GlassButton
import com.example.minimo.ui.glass.GlassButtonStyle
import com.example.minimo.ui.glass.GlassDialog
import com.example.minimo.ui.glass.GlassTextField

/** Creates a course (when [initialName] is empty) or edits one. */
@Composable
fun CourseEditorDialog(
    title: String,
    initialName: String,
    initialCode: String,
    onSave: (name: String, code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var code by rememberSaveable { mutableStateOf(initialCode) }
    val nameValid = name.isNotBlank()
    val nameError = name.isNotEmpty() && !nameValid

    GlassDialog(
        title = title,
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
                onClick = { onSave(name, code) },
                enabled = nameValid,
                modifier = Modifier.weight(1f),
            )
        },
    ) {
        GlassTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.course_name_label),
            isError = nameError,
            supportingText = if (nameError) stringResource(R.string.course_name_required) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        GlassTextField(
            value = code,
            onValueChange = { code = it },
            label = stringResource(R.string.course_code_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
