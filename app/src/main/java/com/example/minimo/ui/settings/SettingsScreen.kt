package com.example.minimo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimo.R
import com.example.minimo.data.portal.ParserCheckResult
import com.example.minimo.data.portal.SessionState
import com.example.minimo.domain.DecimalInput
import com.example.minimo.domain.GradeSettings
import com.example.minimo.domain.Validation
import com.example.minimo.ui.LoadingBox
import com.example.minimo.ui.MessageBox
import com.example.minimo.ui.asPercent
import com.example.minimo.ui.asThreshold
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenPortal: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val parserCheck by viewModel.parserCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openPage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val html = withContext(Dispatchers.IO) { readText(context, uri) }
                viewModel.checkSavedPage(html)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val current = state) {
            SettingsUiState.Loading -> LoadingBox(modifier)
            SettingsUiState.Error -> MessageBox(stringResource(R.string.storage_read_error), modifier)
            is SettingsUiState.Content -> Column(modifier.imePadding().verticalScroll(rememberScrollState())) {
                GradeSettingsForm(current.settings, viewModel::save)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                PortalSection(
                    session = current.session,
                    diagnosticMode = current.diagnosticMode,
                    onOpenPortal = onOpenPortal,
                    onLogout = viewModel::logout,
                    onDiagnosticChange = viewModel::setDiagnosticMode,
                    onCheckSavedPage = { openPage.launch(arrayOf("text/html", "text/plain", "application/octet-stream")) },
                )
            }
        }
    }

    parserCheck?.let { ParserCheckDialog(it, onDismiss = viewModel::dismissParserCheck) }
}

/** Reads a small text file chosen by the user; `null` if it cannot be read or is too big. */
private fun readText(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > MAX_PAGE_BYTES) return@use null
        }
        buffer.toString(Charsets.UTF_8.name())
    }
}.getOrNull()

private const val MAX_PAGE_BYTES = 10 * 1024 * 1024

@Composable
private fun ParserCheckDialog(check: ParserCheckUi, onDismiss: () -> Unit) {
    val message = when (check) {
        ParserCheckUi.Unreadable -> stringResource(R.string.parser_check_unreadable)
        is ParserCheckUi.Done -> when (val result = check.result) {
            is ParserCheckResult.Changed -> stringResource(R.string.parser_check_changed, result.reason)
            is ParserCheckResult.Read -> listOfNotNull(
                stringResource(R.string.parser_check_cycle, result.cycle ?: "?"),
                stringResource(R.string.parser_check_modules, result.activeModules, result.withdrawnModules),
                result.detail?.let {
                    stringResource(R.string.parser_check_detail, it.moduleName, it.activities, it.totalWeight.asPercent())
                } ?: stringResource(R.string.parser_check_no_detail),
            ).joinToString("\n")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parser_check_title)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun GradeSettingsForm(
    settings: GradeSettings,
    onSave: (GradeSettings) -> Unit,
) {
    var passMarkText by rememberSaveable { mutableStateOf(settings.passMark.asThreshold()) }
    var goalText by rememberSaveable { mutableStateOf(settings.defaultGoal.asThreshold()) }

    val passMark = DecimalInput.parse(passMarkText)?.takeIf { Validation.isValidPassMark(it) }
    val goal = passMark?.let { mark -> DecimalInput.parse(goalText)?.takeIf { Validation.isValidGoal(it, mark) } }
    val changed = passMark != null && goal != null &&
        (passMark.compareTo(settings.passMark) != 0 || goal.compareTo(settings.defaultGoal) != 0)

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun PortalSection(
    session: SessionState,
    diagnosticMode: Boolean,
    onOpenPortal: () -> Unit,
    onLogout: () -> Unit,
    onDiagnosticChange: (Boolean) -> Unit,
    onCheckSavedPage: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.settings_portal_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.settings_portal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val stateText = stringResource(
            when (session) {
                SessionState.LoggedOut -> R.string.session_logged_out
                SessionState.LoggedIn -> R.string.session_logged_in
                SessionState.Expired -> R.string.session_expired
            },
        )
        Text(stringResource(R.string.settings_portal_state, stateText), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onOpenPortal) {
            Text(
                stringResource(if (session == SessionState.LoggedIn) R.string.portal_open else R.string.portal_open_login),
            )
        }
        OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.portal_logout)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.diagnostic_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.diagnostic_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = diagnosticMode, onCheckedChange = onDiagnosticChange, modifier = Modifier.padding(start = 8.dp))
        }
        if (diagnosticMode) {
            OutlinedButton(onClick = onCheckSavedPage) { Text(stringResource(R.string.parser_check_action)) }
            Text(
                stringResource(R.string.parser_check_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
