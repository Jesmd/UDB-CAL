package com.example.minimo.data.portal

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Holds the portal session state. The login itself happens on the university's page inside a WebView;
 * the app never sees or stores the password.
 */
class SessionManager(private val appContext: Context) {
    private val _state = MutableStateFlow(SessionState.LoggedOut)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Call when a page finished loading in the in-app browser. */
    fun onPageLoaded(url: String?) {
        _state.value = _state.value.afterPageLoaded(url)
    }

    /** Deletes the WebView's cookies, storage and cache, and marks the session as closed. Main thread only. */
    suspend fun logout() {
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
        }
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        WebView(appContext).apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
        _state.value = SessionState.LoggedOut
    }
}
