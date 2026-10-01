package com.example.minimo.ui.portal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { webView.loadUrl(PortalUrls.NOTAS_URL) }) {
                        Text(stringResource(R.string.portal_notes))
                    }
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.portal_reload))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (state.session == SessionState.Expired) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.portal_expired_banner),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (state.diagnosticMode) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.portal_diag_page, PortalUrls.describe(currentUrl).orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val canSave = PortalUrls.isPortalPage(currentUrl)
                    Button(onClick = ::savePageHtml, enabled = canSave, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.portal_save_html))
                    }
                    if (!canSave) {
                        Text(
                            stringResource(R.string.portal_save_html_login_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (progress in 1..99) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                loadError?.let { detail ->
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
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
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Button(
                                onClick = {
                                    loadError = null
                                    webView.loadUrl(PortalUrls.LOGIN_URL)
                                },
                                modifier = Modifier.padding(top = 16.dp),
                            ) { Text(stringResource(R.string.portal_retry)) }
                        }
                    }
                }
            }
        }
    }
}
