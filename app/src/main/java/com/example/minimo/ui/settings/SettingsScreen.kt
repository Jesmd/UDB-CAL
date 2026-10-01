package com.example.minimo.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimo.R
import com.example.minimo.domain.DecimalInput
import com.example.minimo.domain.GradeSettings
import com.example.minimo.domain.Validation
import com.example.minimo.ui.LoadingBox
import com.example.minimo.ui.MessageBox
import com.example.minimo.ui.asThreshold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val current = state) {
            SettingsUiState.Loading -> LoadingBox(modifier)
            SettingsUiState.Error -> MessageBox(stringResource(R.string.storage_read_error), modifier)
            is SettingsUiState.Content -> SettingsForm(current.settings, viewModel::save, modifier)
        }
    }
}

@Composable
private fun SettingsForm(
    settings: GradeSettings,
    onSave: (GradeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var passMarkText by rememberSaveable { mutableStateOf(settings.passMark.asThreshold()) }
    var goalText by rememberSaveable { mutableStateOf(settings.defaultGoal.asThreshold()) }

    val passMark = DecimalInput.parse(passMarkText)?.takeIf { Validation.isValidPassMark(it) }
    val goal = passMark?.let { mark -> DecimalInput.parse(goalText)?.takeIf { Validation.isValidGoal(it, mark) } }
    val changed = passMark != null && goal != null &&
        (passMark.compareTo(settings.passMark) != 0 || goal.compareTo(settings.defaultGoal) != 0)

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = passMarkText,
            onValueChange = { passMarkText = it },
            label = { Text(stringResource(R.string.settings_pass_mark_label)) },
            isError = passMark == null,
            supportingText = if (passMark != null) null else {
                { Text(stringResource(R.string.settings_pass_mark_invalid)) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = goalText,
            onValueChange = { goalText = it },
            label = { Text(stringResource(R.string.settings_default_goal_label)) },
            isError = passMark != null && goal == null,
            supportingText = {
                Text(
                    stringResource(
                        if (passMark != null && goal == null) {
                            R.string.settings_default_goal_invalid
                        } else {
                            R.string.settings_default_goal_hint
                        },
                    ),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = changed,
            onClick = { onSave(GradeSettings(checkNotNull(passMark), checkNotNull(goal))) },
        ) { Text(stringResource(R.string.action_save)) }
    }
}
