package com.quotaedge.galaxy.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.CompletableDeferred

/**
 * In-app browser for OAuth. Intercepts localhost redirect URLs so PKCE login
 * works reliably on Android (Custom Tabs + loopback is unreliable on Galaxy).
 */
class OAuthWebViewActivity : ComponentActivity() {
    private var handled = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authUrl = intent.getStringExtra(EXTRA_AUTH_URL).orEmpty()
        val redirectPrefix = intent.getStringExtra(EXTRA_REDIRECT_PREFIX).orEmpty()
        if (authUrl.isBlank() || redirectPrefix.isBlank()) {
            finishWithError("missing auth params")
            return
        }

        val progress = ProgressBar(this).apply {
            isIndeterminate = true
        }
        val web = WebView(this)
        val root = FrameLayout(this).apply {
            addView(
                web,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                progress,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER,
                ),
            )
        }
        setContentView(root)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = userAgentString.replace("; wv", "")
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = android.view.View.VISIBLE
                url?.let { maybeCapture(it, redirectPrefix) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = android.view.View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return maybeCapture(url, redirectPrefix)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return url?.let { maybeCapture(it, redirectPrefix) } ?: false
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (web.canGoBack()) web.goBack() else finishWithError("cancelled")
                }
            },
        )

        web.loadUrl(authUrl)
    }

    private fun maybeCapture(url: String, redirectPrefix: String): Boolean {
        if (handled) return true
        if (!url.startsWith(redirectPrefix) && !matchesLocalRedirect(url, redirectPrefix)) {
            return false
        }
        handled = true
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
            ?: uri.getQueryParameter("error_description")
        val cb = OAuthCallback(code = code, state = state, error = error)
        pending?.complete(cb)
        pending = null
        setResult(Activity.RESULT_OK)
        finish()
        return true
    }

    private fun matchesLocalRedirect(url: String, redirectPrefix: String): Boolean {
        val u = Uri.parse(url)
        val r = Uri.parse(redirectPrefix)
        val hostOk = u.host == r.host ||
            (u.host == "127.0.0.1" && r.host == "localhost") ||
            (u.host == "localhost" && r.host == "127.0.0.1")
        val pathOk = (u.path ?: "/") == (r.path ?: "/")
        return hostOk && pathOk && (u.scheme == "http" || u.scheme == "https")
    }

    private fun finishWithError(msg: String) {
        if (!handled) {
            handled = true
            pending?.complete(OAuthCallback(null, null, msg))
            pending = null
        }
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        if (!handled) {
            pending?.complete(OAuthCallback(null, null, "cancelled"))
            pending = null
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTH_URL = "auth_url"
        const val EXTRA_REDIRECT_PREFIX = "redirect_prefix"

        @Volatile
        var pending: CompletableDeferred<OAuthCallback>? = null

        suspend fun launchAndAwait(context: Context, authUrl: String, redirectPrefix: String): OAuthCallback {
            val deferred = CompletableDeferred<OAuthCallback>()
            pending = deferred
            val intent = Intent(context, OAuthWebViewActivity::class.java).apply {
                putExtra(EXTRA_AUTH_URL, authUrl)
                putExtra(EXTRA_REDIRECT_PREFIX, redirectPrefix)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return deferred.await()
        }
    }
}
