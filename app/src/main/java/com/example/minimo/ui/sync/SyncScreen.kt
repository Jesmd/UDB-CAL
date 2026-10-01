package com.example.minimo.ui.sync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimo.R
import com.example.minimo.ui.portal.PortalWebListener
import com.example.minimo.ui.portal.WebViewPortalBrowser
import com.example.minimo.ui.portal.createPortalWebView
import kotlinx.coroutines.launch

/**
 * Sync with the portal. The portal page is shown in a WebView; if the portal asks to log in, the user does it
 * right there and the sync continues by itself. While reading, a panel covers the page so it is not touched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(viewModel: SyncViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val blockedMessage = stringResource(R.string.portal_link_blocked)
    var progress by remember { mutableIntStateOf(0) }

    val browser = remember { WebViewPortalBrowser() }
    val webView = remember {
        createPortalWebView(
            context,
            object : PortalWebListener {
                override fun onPageStarted(url: String?) = Unit

                override fun onPageFinished(url: String?) {
                    viewModel.pageLoaded(url)
                    browser.onPageFinished(url)
                }

                override fun onProgress(percent: Int) {
                    progress = percent
                }

                override fun onLoadError(detail: String) = browser.onLoadError(detail)

                override fun onLinkBlocked() {
                    browser.onLinkBlocked()
                    scope.launch { snackbar.showSnackbar(blockedMessage) }
                }
            },
        ).also { browser.webView = it }
    }
    DisposableEffect(webView) {
        viewModel.attach(browser)
        onDispose {
            viewModel.detach()
            webView.stopLoading()
            webView.destroy()
        }
    }
    BackHandler(onBack = onBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (state == SyncUiState.NeedsLogin) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.sync_needs_login),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (progress in 1..99) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                if (state != SyncUiState.NeedsLogin) {
                    SyncPanel(state, onRetry = viewModel::retry, onBack = onBack, onDone = onDone)
                }
            }
        }
    }
}

/** Covers the page while syncing, and shows the result. */
@Composable
private fun SyncPanel(state: SyncUiState, onRetry: () -> Unit, onBack: () -> Unit, onDone: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                SyncUiState.Opening, SyncUiState.NeedsLogin -> {
                    CircularProgressIndicator()
                    PanelText(stringResource(R.string.sync_opening))
                }
                is SyncUiState.Reading -> {
                    CircularProgressIndicator()
                    PanelText(
                        if (state.total == 0) {
                            stringResource(R.string.sync_reading_courses)
                        } else {
                            stringResource(R.string.sync_reading_progress, (state.done + 1).coerceAtMost(state.total), state.total)
                        },
                    )
                }
                is SyncUiState.Done -> {
                    PanelText(
                        if (state.cycle != null) {
                            pluralStringResource(R.plurals.sync_done_cycle, state.courseCount, state.courseCount, state.cycle)
                        } else {
                            pluralStringResource(R.plurals.sync_done, state.courseCount, state.courseCount)
                        },
                    )
                    Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.sync_view_courses))
                    }
                }
                is SyncUiState.Failed -> {
                    PanelText(failureMessage(state))
                    state.detail?.let {
                        Text(
                            stringResource(R.string.portal_load_error_detail, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                        Button(onClick = onRetry) { Text(stringResource(R.string.portal_retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun failureMessage(state: SyncUiState.Failed): String = when (state.reason) {
    SyncFailure.Network -> stringResource(R.string.sync_failed_network)
    SyncFailure.Timeout -> stringResource(R.string.sync_failed_timeout)
    SyncFailure.PortalChanged -> stringResource(R.string.sync_failed_format)
    SyncFailure.DetailFailed -> stringResource(R.string.sync_failed_detail)
    SyncFailure.SaveFailed -> stringResource(R.string.sync_failed_save)
}
