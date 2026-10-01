package com.example.minimo.ui.sync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.minimo.ui.glass.GlassButton
import com.example.minimo.ui.glass.GlassButtonStyle
import com.example.minimo.ui.glass.GlassCard
import com.example.minimo.ui.glass.GlassCover
import com.example.minimo.ui.glass.GlassIconButton
import com.example.minimo.ui.glass.GlassNotice
import com.example.minimo.ui.glass.GlassProgressBar
import com.example.minimo.ui.glass.GlassScaffold
import com.example.minimo.ui.glass.GlassSnackbarHost
import com.example.minimo.ui.glass.GlassTopBar
import com.example.minimo.ui.glass.rememberEntrance
import com.example.minimo.ui.theme.glass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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

    GlassScaffold(
        captureBackdrop = false,
        snackbarHost = { GlassSnackbarHost(snackbar) },
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.sync_title),
                progress = { 1f },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().navigationBarsPadding()) {
            if (state == SyncUiState.NeedsLogin) {
                GlassNotice(
                    stringResource(R.string.sync_needs_login),
                    icon = Icons.Filled.Info,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                if (progress in 1..99) {
                    GlassProgressBar(progress / 100f)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
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
    GlassCover {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (state) {
                        SyncUiState.Opening, SyncUiState.NeedsLogin -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            PanelText(stringResource(R.string.sync_opening))
                        }
                        is SyncUiState.Reading -> {
                            if (state.total == 0) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            } else {
                                // Determinate: how many courses were read so far.
                                CircularProgressIndicator(
                                    progress = { (state.done / state.total.toFloat()).coerceIn(0f, 1f) },
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            PanelText(
                                if (state.total == 0) {
                                    stringResource(R.string.sync_reading_courses)
                                } else {
                                    stringResource(R.string.sync_reading_progress, (state.done + 1).coerceAtMost(state.total), state.total)
                                },
                            )
                        }
                        is SyncUiState.Done -> {
                            ResultBadge(Icons.Filled.CheckCircle, MaterialTheme.glass.success)
                            PanelText(
                                if (state.cycle != null) {
                                    pluralStringResource(R.plurals.sync_done_cycle, state.courseCount, state.courseCount, state.cycle)
                                } else {
                                    pluralStringResource(R.plurals.sync_done, state.courseCount, state.courseCount)
                                },
                            )
                            GlassButton(
                                text = stringResource(R.string.sync_view_courses),
                                onClick = onDone,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        is SyncUiState.Failed -> {
                            ResultBadge(Icons.Filled.Warning, MaterialTheme.colorScheme.error)
                            PanelText(failureMessage(state))
                            state.detail?.let {
                                Text(
                                    stringResource(R.string.portal_load_error_detail, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton(stringResource(R.string.action_back), onBack, style = GlassButtonStyle.Regular)
                                GlassButton(stringResource(R.string.portal_retry), onRetry)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Big icon that pops in with a spring when a result appears. */
@Composable
private fun ResultBadge(icon: ImageVector, color: Color) {
    val entrance by rememberEntrance()
    Icon(
        icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = entrance
                scaleY = entrance
            },
    )
}

@Composable
private fun PanelText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
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
