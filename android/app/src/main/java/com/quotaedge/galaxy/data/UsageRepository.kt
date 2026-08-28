package com.quotaedge.galaxy.data

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
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Claude HTTP ${resp.code}: ${body.take(120)}")
                ClaudeUsageParser.parse(body)
            }
        }
    }

    suspend fun fetchCodex(token: String, accountId: String?): Result<Pair<QuotaWindow?, QuotaWindow?>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder()
                    .url("https://chatgpt.com/backend-api/wham/usage")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                accountId?.let { builder.header("ChatGPT-Account-Id", it) }
                http.newCall(builder.get().build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Codex HTTP ${resp.code}: ${body.take(120)}")
                    CodexUsageParser.parse(body)
                }
            }
        }
}

class UsageRepository(
    private val tokenStore: TokenStore,
    private val api: UsageApiClient = UsageApiClient(),
) {
    suspend fun refresh(): UsageSnapshot {
        var claude = ProviderQuota(Provider.CLAUDE, null, null, connected = false)
        var codex = ProviderQuota(Provider.CODEX, null, null, connected = false)

        tokenStore.getClaudeToken()?.let { token ->
            claude = api.fetchClaude(token).fold(
                onSuccess = { (fh, wk) ->
                    ProviderQuota(Provider.CLAUDE, fh, wk, connected = true)
                },
                onFailure = { e ->
                    ProviderQuota(Provider.CLAUDE, null, null, connected = true, error = e.message)
                },
            )
        }

        tokenStore.getCodexToken()?.let { token ->
            codex = api.fetchCodex(token, tokenStore.getCodexAccountId()).fold(
                onSuccess = { (fh, wk) ->
                    ProviderQuota(Provider.CODEX, fh, wk, connected = true)
                },
                onFailure = { e ->
                    ProviderQuota(Provider.CODEX, null, null, connected = true, error = e.message)
                },
            )
        }

        return UsageSnapshot(claude, codex)
    }
}
