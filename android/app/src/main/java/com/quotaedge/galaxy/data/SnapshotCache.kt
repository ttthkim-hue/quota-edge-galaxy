package com.quotaedge.galaxy.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persist last successful glance so HUD survives screen-off / process restart. */
object SnapshotCache {
    private const val PREFS = "quota_snapshot"
    private const val KEY = "json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Win(
        val usedPercent: Int,
        val resetAfterSeconds: Long? = null,
        val windowSeconds: Long? = null,
    )

    @Serializable
    private data class Prov(
        val connected: Boolean = false,
        val planType: String? = null,
        val error: String? = null,
        val fiveHour: Win? = null,
        val weekly: Win? = null,
    )

    @Serializable
    private data class Snap(
        val claude: Prov = Prov(),
        val codex: Prov = Prov(),
        val updatedAtEpochMs: Long = 0L,
    )

    fun save(context: Context, snapshot: UsageSnapshot) {
        val encoded = json.encodeToString(
            Snap(
                claude = snapshot.claude.toProv(),
                codex = snapshot.codex.toProv(),
                updatedAtEpochMs = snapshot.updatedAtEpochMs,
            ),
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encoded)
            .apply()
    }

    fun load(context: Context): UsageSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return runCatching {
            val s = json.decodeFromString<Snap>(raw)
            UsageSnapshot(
                claude = s.claude.toQuota(Provider.CLAUDE),
                codex = s.codex.toQuota(Provider.CODEX),
                updatedAtEpochMs = s.updatedAtEpochMs,
            )
        }.getOrNull()
    }

    private fun ProviderQuota.toProv() = Prov(
        connected = connected,
        planType = planType,
        error = error,
        fiveHour = fiveHour?.let { Win(it.usedPercent, it.resetAfterSeconds, it.windowSeconds) },
        weekly = weekly?.let { Win(it.usedPercent, it.resetAfterSeconds, it.windowSeconds) },
    )

    private fun Prov.toQuota(provider: Provider) = ProviderQuota(
        provider = provider,
        fiveHour = fiveHour?.toWindow(),
        weekly = weekly?.toWindow(),
        connected = connected,
        error = error,
        planType = planType,
    )

    private fun Win.toWindow() = QuotaWindow(
        usedPercent = usedPercent,
        resetAtEpochSec = null,
        resetAfterSeconds = resetAfterSeconds,
        windowSeconds = windowSeconds,
    )
}
