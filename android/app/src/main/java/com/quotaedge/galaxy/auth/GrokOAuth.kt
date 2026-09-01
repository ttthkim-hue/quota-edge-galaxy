package com.quotaedge.galaxy.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.quotaedge.galaxy.data.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Public Grok CLI client id (same pattern as Claude/Codex CLI clients already in this app).
 * Usage is SuperGrok weekly pool via cli-chat-proxy, not console API credits.
 */
object GrokOAuth {
    const val CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"
    private const val AUTH_URL = "https://auth.x.ai/oauth2/authorize"
    private const val TOKEN_URL = "https://auth.x.ai/oauth2/token"
    private const val SCOPE = "openid profile email offline_access api:access"
    private const val REDIRECT = "http://127.0.0.1:56121/callback"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun login(context: Context, tokenStore: TokenStore) {
        val pkce = Pkce.generate()
        val url = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", pkce.state)
            .appendQueryParameter("plan", "generic")
            .build()
            .toString()

        Log.i("QuotaEdge", "Grok OAuth WebView start")
        val cb = OAuthWebViewActivity.launchAndAwait(context, url, REDIRECT)
        Log.i("QuotaEdge", "Grok OAuth callback error=${cb.error} code=${cb.code != null}")
        if (cb.error != null) error(cb.error)
        val code = cb.code ?: error("No authorization code")
        if (cb.state != null && cb.state != pkce.state) error("State mismatch")
        val tokens = exchange(code, pkce.verifier)
        tokenStore.saveGrokSession(tokens.accessToken, tokens.refreshToken, tokens.expiresAtMs)
        Log.i("QuotaEdge", "Grok linked OK")
    }

    suspend fun refreshIfNeeded(tokenStore: TokenStore): String? = withContext(Dispatchers.IO) {
        val access = tokenStore.getGrokToken() ?: return@withContext null
        val exp = tokenStore.getGrokExpiresAt()
        if (exp == 0L || System.currentTimeMillis() < exp - 60_000) return@withContext access
        val refresh = tokenStore.getGrokRefresh() ?: return@withContext access
        runCatching {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", CLIENT_ID)
                .build()
            val req = Request.Builder().url(TOKEN_URL).post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Grok refresh ${resp.code}")
                val json = JSONObject(text)
                val accessNew = json.getString("access_token")
                val refreshNew = if (json.has("refresh_token")) json.getString("refresh_token") else refresh
                val expiresIn = json.optLong("expires_in", 3600)
                tokenStore.saveGrokSession(
                    accessNew,
                    refreshNew,
                    System.currentTimeMillis() + expiresIn * 1000,
                )
                accessNew
            }
        }.getOrDefault(access)
    }

    private suspend fun exchange(code: String, verifier: String): GrokTokens =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT)
                .add("client_id", CLIENT_ID)
                .add("code_verifier", verifier)
                .build()
            val req = Request.Builder().url(TOKEN_URL).post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                Log.i("QuotaEdge", "Grok token HTTP ${resp.code}")
                if (!resp.isSuccessful) error("Grok token ${resp.code}: ${text.take(200)}")
                val json = JSONObject(text)
                GrokTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null,
                    expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
                )
            }
        }
}

private data class GrokTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
)
