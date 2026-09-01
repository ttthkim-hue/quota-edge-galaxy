package com.quotaedge.galaxy.data

import android.content.Context
import android.util.Log
import com.quotaedge.galaxy.auth.ClaudeOAuth
import com.quotaedge.galaxy.auth.CodexOAuth
import com.quotaedge.galaxy.auth.GrokOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class UsageApiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchClaude(token: String): Result<Pair<QuotaWindow?, QuotaWindow?>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.anthropic.com/api/oauth/usage")
                .header("Authorization", "Bearer $token")
                .header("anthropic-beta", "oauth-2025-04-20")
                .header("Accept", "application/json")
                .header("User-Agent", "claude-cli/1.0.0")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Log.i(TAG, "Claude usage HTTP ${resp.code} len=${body.length} head=${body.take(180)}")
                if (!resp.isSuccessful) error("Claude HTTP ${resp.code}: ${body.take(160)}")
                val parsed = ClaudeUsageParser.parse(body)
                if (parsed.first == null && parsed.second == null) {
                    error("Claude parse empty: ${body.take(160)}")
                }
                parsed
            }
        }
    }

    suspend fun fetchCodex(token: String, accountId: String?): Result<CodexParseResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder()
                    .url("https://chatgpt.com/backend-api/wham/usage")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .header("User-Agent", "CodexBar/1.0")
                accountId?.let { builder.header("ChatGPT-Account-Id", it) }
                http.newCall(builder.get().build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    Log.i(TAG, "Codex usage HTTP ${resp.code} account=${accountId?.take(8)} len=${body.length} head=${body.take(180)}")
                    if (!resp.isSuccessful) error("Codex HTTP ${resp.code}: ${body.take(160)}")
                    val parsed = CodexUsageParser.parseDetailed(body)
                    if (parsed.fiveHour == null && parsed.weekly == null) {
                        error("Codex parse empty: ${body.take(160)}")
                    }
                    parsed.copy(redactedRaw = UsageJsonRedactor.redact(body))
                }
            }
        }

    suspend fun fetchGrok(token: String): Result<GrokParseResult> = withContext(Dispatchers.IO) {
        runCatching {
            val billingReq = Request.Builder()
                .url("https://cli-chat-proxy.grok.com/v1/billing?format=credits")
                .header("Authorization", "Bearer $token")
                .header("x-xai-token-auth", "xai-grok-cli")
                .header("Accept", "application/json")
                .header("User-Agent", "QuotaEdge/1.2")
                .get()
                .build()
            val billingBody = http.newCall(billingReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Log.i(TAG, "Grok billing HTTP ${resp.code} len=${body.length} head=${body.take(180)}")
                if (!resp.isSuccessful) error("Grok billing HTTP ${resp.code}: ${body.take(160)}")
                body
            }
            val plan = runCatching {
                val settingsReq = Request.Builder()
                    .url("https://cli-chat-proxy.grok.com/v1/settings")
                    .header("Authorization", "Bearer $token")
                    .header("x-xai-token-auth", "xai-grok-cli")
                    .header("Accept", "application/json")
                    .get()
                    .build()
                http.newCall(settingsReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) null else GrokUsageParser.parsePlan(body)
                }
            }.getOrNull()
            val parsed = GrokUsageParser.parseBilling(billingBody, plan)
            if (parsed.weekly == null) error("Grok parse empty: ${billingBody.take(160)}")
            parsed.copy(redactedRaw = UsageJsonRedactor.redact(billingBody))
        }
    }

    companion object {
        private const val TAG = "QuotaEdge"
    }
}

class UsageRepository(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val api: UsageApiClient = UsageApiClient(),
) {
    suspend fun refresh(): UsageSnapshot {
        var claude = ProviderQuota(Provider.CLAUDE, null, null, connected = false)
        var codex = ProviderQuota(Provider.CODEX, null, null, connected = false)
        var grok = ProviderQuota(Provider.GROK, null, null, connected = false)
        val notes = mutableListOf<String>()

        val claudeTok = ClaudeOAuth.refreshIfNeeded(tokenStore)
        if (claudeTok == null) {
            notes += "Claude: 미연동"
        } else {
            claude = api.fetchClaude(claudeTok).fold(
                onSuccess = { (fh, wk) ->
                    notes += "Claude: OK ${fh?.usedPercent ?: "-"}%/${wk?.usedPercent ?: "-"}%"
                    ProviderQuota(Provider.CLAUDE, fh, wk, connected = true)
                },
                onFailure = { e ->
                    notes += "Claude: ${e.message}"
                    Log.e("QuotaEdge", "Claude fetch failed", e)
                    ProviderQuota(Provider.CLAUDE, null, null, connected = true, error = e.message)
                },
            )
        }

        val codexTok = CodexOAuth.refreshIfNeeded(tokenStore)
        val accountId = tokenStore.getCodexAccountId()
        if (codexTok == null) {
            notes += "Codex: 미연동"
        } else {
            codex = api.fetchCodex(codexTok, accountId).fold(
                onSuccess = { parsed ->
                    val quota = ProviderQuota(
                        Provider.CODEX,
                        parsed.fiveHour,
                        parsed.weekly,
                        connected = true,
                        planType = parsed.planType,
                        extraLimits = parsed.extraLimits,
                    )
                    notes += "Codex: ${quota.glanceLine()} (plan=${parsed.planType ?: "?"})"
                    quota
                },
                onFailure = { e ->
                    notes += "Codex: ${e.message}"
                    Log.e("QuotaEdge", "Codex fetch failed", e)
                    ProviderQuota(Provider.CODEX, null, null, connected = true, error = e.message)
                },
            )
        }

        val grokTok = GrokOAuth.refreshIfNeeded(tokenStore)
        if (grokTok == null) {
            notes += "Grok: 미연동"
        } else {
            grok = api.fetchGrok(grokTok).fold(
                onSuccess = { parsed ->
                    val quota = ProviderQuota(
                        Provider.GROK,
                        fiveHour = null,
                        weekly = parsed.weekly,
                        connected = true,
                        planType = parsed.planType ?: "SuperGrok",
                    )
                    notes += "Grok: ${quota.glanceLine()} (plan=${quota.planType})"
                    quota
                },
                onFailure = { e ->
                    notes += "Grok: ${e.message}"
                    Log.e("QuotaEdge", "Grok fetch failed", e)
                    ProviderQuota(Provider.GROK, null, null, connected = true, error = e.message)
                },
            )
        }

        val status = notes.joinToString("\n")
        context.getSharedPreferences("quota_debug", Context.MODE_PRIVATE)
            .edit()
            .putString("last_status", status)
            .putLong("last_sync", System.currentTimeMillis())
            .commit()

        return UsageSnapshot(claude, codex, grok)
    }
}
