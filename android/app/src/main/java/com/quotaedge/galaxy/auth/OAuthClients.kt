package com.quotaedge.galaxy.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.quotaedge.galaxy.data.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ClaudeOAuth {
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    private const val AUTH_URL = "https://claude.ai/oauth/authorize"
    private const val TOKEN_URL = "https://platform.claude.com/v1/oauth/token"
    private const val SCOPE = "user:inference user:profile org:create_api_key"
    private const val REDIRECT = "http://localhost:8787/callback"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun login(context: Context, tokenStore: TokenStore) {
        val pkce = Pkce.generate()
        val state = pkce.verifier
        val url = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
            .toString()

        Log.i("QuotaEdge", "Claude OAuth WebView start")
        val cb = OAuthWebViewActivity.launchAndAwait(context, url, REDIRECT)
        Log.i("QuotaEdge", "Claude OAuth callback error=${cb.error} code=${cb.code != null}")
        if (cb.error != null) error(cb.error)
        val code = cb.code ?: error("No authorization code")
        if (cb.state != null && cb.state != state) error("State mismatch")
        val tokens = exchange(code, pkce.verifier, state)
        tokenStore.saveClaudeSession(tokens.accessToken, tokens.refreshToken, tokens.expiresAtMs)
        Log.i("QuotaEdge", "Claude linked OK")
    }

    suspend fun refreshIfNeeded(tokenStore: TokenStore): String? = withContext(Dispatchers.IO) {
        val access = tokenStore.getClaudeToken() ?: return@withContext null
        val exp = tokenStore.getClaudeExpiresAt()
        if (exp == 0L || System.currentTimeMillis() < exp - 60_000) return@withContext access
        val refresh = tokenStore.getClaudeRefresh() ?: return@withContext access
        runCatching {
            val body = JSONObject()
                .put("grant_type", "refresh_token")
                .put("refresh_token", refresh)
                .put("client_id", CLIENT_ID)
                .toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(TOKEN_URL).post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Claude refresh ${resp.code}")
                val json = JSONObject(text)
                val accessNew = json.getString("access_token")
                val refreshNew = if (json.has("refresh_token")) json.getString("refresh_token") else refresh
                val expiresIn = json.optLong("expires_in", 3600)
                tokenStore.saveClaudeSession(
                    accessNew,
                    refreshNew,
                    System.currentTimeMillis() + expiresIn * 1000,
                )
                accessNew
            }
        }.getOrDefault(access)
    }

    private suspend fun exchange(code: String, verifier: String, state: String): Tokens =
        withContext(Dispatchers.IO) {
            val pureCode = code.substringBefore('#')
            val body = JSONObject()
                .put("grant_type", "authorization_code")
                .put("code", pureCode)
                .put("redirect_uri", REDIRECT)
                .put("client_id", CLIENT_ID)
                .put("code_verifier", verifier)
                .put("state", state)
                .toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(TOKEN_URL).post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                Log.i("QuotaEdge", "Claude token HTTP ${resp.code}")
                if (!resp.isSuccessful) error("Claude token ${resp.code}: ${text.take(200)}")
                val json = JSONObject(text)
                Tokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null,
                    expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
                )
            }
        }
}

object CodexOAuth {
    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    private const val AUTH_URL = "https://auth.openai.com/oauth/authorize"
    private const val TOKEN_URL = "https://auth.openai.com/oauth/token"
    private const val SCOPE = "openid profile email offline_access"
    private const val REDIRECT = "http://localhost:1455/auth/callback"

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
            .appendQueryParameter("id_token_add_organizations", "true")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .appendQueryParameter("originator", "quota_edge")
            .build()
            .toString()

        Log.i("QuotaEdge", "Codex OAuth WebView start")
        val cb = OAuthWebViewActivity.launchAndAwait(context, url, REDIRECT)
        Log.i("QuotaEdge", "Codex OAuth callback error=${cb.error} code=${cb.code != null}")
        if (cb.error != null) error(cb.error)
        val code = cb.code ?: error("No authorization code")
        if (cb.state != null && cb.state != pkce.state) error("State mismatch")
        val tokens = exchange(code, pkce.verifier)
        val accountId = extractAccountId(tokens.idToken)
        tokenStore.saveCodexSession(
            tokens.accessToken,
            tokens.refreshToken,
            accountId,
            tokens.expiresAtMs,
        )
        Log.i("QuotaEdge", "Codex linked OK account=${accountId != null}")
    }

    suspend fun refreshIfNeeded(tokenStore: TokenStore): String? = withContext(Dispatchers.IO) {
        val access = tokenStore.getCodexToken() ?: return@withContext null
        val exp = tokenStore.getCodexExpiresAt()
        if (exp == 0L || System.currentTimeMillis() < exp - 60_000) return@withContext access
        val refresh = tokenStore.getCodexRefresh() ?: return@withContext access
        runCatching {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", CLIENT_ID)
                .build()
            val req = Request.Builder().url(TOKEN_URL).post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Codex refresh ${resp.code}")
                val json = JSONObject(text)
                val accessNew = json.getString("access_token")
                val refreshNew = if (json.has("refresh_token")) json.getString("refresh_token") else refresh
                val expiresIn = json.optLong("expires_in", 3600)
                val idToken = if (json.has("id_token")) json.getString("id_token") else null
                val accountId = extractAccountId(idToken) ?: tokenStore.getCodexAccountId()
                tokenStore.saveCodexSession(
                    accessNew,
                    refreshNew,
                    accountId,
                    System.currentTimeMillis() + expiresIn * 1000,
                )
                accessNew
            }
        }.getOrDefault(access)
    }

    private suspend fun exchange(code: String, verifier: String): CodexTokens =
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
                Log.i("QuotaEdge", "Codex token HTTP ${resp.code}")
                if (!resp.isSuccessful) error("Codex token ${resp.code}: ${text.take(200)}")
                val json = JSONObject(text)
                CodexTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null,
                    idToken = if (json.has("id_token")) json.getString("id_token") else null,
                    expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
                )
            }
        }

    fun extractAccountId(idToken: String?): String? {
        if (idToken.isNullOrBlank()) return null
        return runCatching {
            val payload = idToken.split(".").getOrNull(1) ?: return null
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val json = JSONObject(String(Base64.decode(padded, Base64.URL_SAFE)))
            val authKey = "https://api.openai.com/auth"
            when {
                json.optJSONObject(authKey) != null ->
                    json.getJSONObject(authKey).optString("chatgpt_account_id").takeIf { it.isNotBlank() }
                json.has(authKey) -> {
                    val nested = JSONObject(json.getString(authKey))
                    nested.optString("chatgpt_account_id").takeIf { it.isNotBlank() }
                }
                else -> json.optString("chatgpt_account_id").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}

private data class Tokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
)

private data class CodexTokens(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val expiresAtMs: Long,
)
