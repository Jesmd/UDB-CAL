package com.example.minimo.ui.portal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minimo.R
import com.example.minimo.data.portal.HtmlSnapshot
import com.example.minimo.data.portal.PortalUrls
import com.example.minimo.data.portal.SessionState
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
import com.example.minimo.ui.glass.NoticeKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

@Composable
fun PortalScreen(viewModel: PortalViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var currentUrl by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    // Technical reason of the last failed load, or null when the page loaded.
    var loadError by remember { mutableStateOf<String?>(null) }
    // The page text waiting for the user to pick where to save it. Kept only in memory, never persisted.
    var pendingHtml by remember { mutableStateOf<String?>(null) }

    val onPageLoaded by rememberUpdatedState(viewModel::onPageLoaded)
    val blockedMessage = stringResource(R.string.portal_link_blocked)
    val savedMessage = stringResource(R.string.portal_html_saved)
    val saveFailedMessage = stringResource(R.string.portal_html_failed)
    val readFailedMessage = stringResource(R.string.portal_html_read_failed)

    val webView = remember {
        createPortalWebView(
            context,
            object : PortalWebListener {
                override fun onPageStarted(url: String?) {
                    // After an error the WebView starts its own error page; keep our message for that one.
                    if (PortalUrls.isAllowed(url)) loadError = null
                }

                override fun onPageFinished(url: String?) {
                    currentUrl = url
                    onPageLoaded(url)
                }

                override fun onProgress(percent: Int) {
                    progress = percent
                }

                override fun onLoadError(detail: String) {
                    loadError = detail
                }

                override fun onLinkBlocked() {
                    scope.launch { snackbar.showSnackbar(blockedMessage) }
                }
            },
        )
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    LaunchedEffect(webView) {
        if (webView.url == null) webView.loadUrl(PortalUrls.LOGIN_URL)
    }
    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val html = pendingHtml
        pendingHtml = null
        if (uri != null && html != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
                            it.write(html.toByteArray(Charsets.UTF_8))
                        }
                    }.isSuccess
                }
                snackbar.showSnackbar(if (saved) savedMessage else saveFailedMessage)
            }
        }
    }

    fun savePageHtml() {
        // Read-only script, and only on pages behind the login: never on the login page.
        if (!PortalUrls.isPortalPage(webView.url)) return
        webView.evaluateJavascript("document.documentElement.outerHTML") { raw ->
            val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            if (html == null) {
                scope.launch { snackbar.showSnackbar(readFailedMessage) }
            } else {
                pendingHtml = HtmlSnapshot.redactAntiForgeryToken(html)
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
                saveLauncher.launch("pagina-portal-$stamp.html")
            }
        }
    }

    GlassScaffold(
        captureBackdrop = false,
        snackbarHost = { GlassSnackbarHost(snackbar) },
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.portal_title),
                progress = { 1f },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
                },
                actions = {
                    GlassButton(
                        text = stringResource(R.string.portal_notes),
                        onClick = { webView.loadUrl(PortalUrls.NOTAS_URL) },
                        style = GlassButtonStyle.Regular,
                        compact = true,
                    )
                    GlassIconButton(Icons.Filled.Refresh, stringResource(R.string.portal_reload), { webView.reload() })
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().navigationBarsPadding()) {
            if (state.session == SessionState.Expired) {
                GlassNotice(
                    stringResource(R.string.portal_expired_banner),
                    kind = NoticeKind.Error,
                    icon = Icons.Filled.Warning,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (state.diagnosticMode) {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.portal_diag_page, PortalUrls.describe(currentUrl).orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val canSave = PortalUrls.isPortalPage(currentUrl)
                        GlassButton(
                            text = stringResource(R.string.portal_save_html),
                            onClick = ::savePageHtml,
                            enabled = canSave,
                            compact = true,
                        )
                        if (!canSave) {
                            Text(
                                stringResource(R.string.portal_save_html_login_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (progress in 1..99) {
                GlassProgressBar(progress / 100f)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                loadError?.let { detail ->
                    GlassCover {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(24.dp).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(36.dp),
                                    )
                                    Text(
                                        stringResource(R.string.portal_load_error),
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        stringResource(R.string.portal_load_error_detail, detail),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    GlassButton(
                                        text = stringResource(R.string.portal_retry),
                                        onClick = {
                                            loadError = null
                                            webView.loadUrl(PortalUrls.LOGIN_URL)
                                        },
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
