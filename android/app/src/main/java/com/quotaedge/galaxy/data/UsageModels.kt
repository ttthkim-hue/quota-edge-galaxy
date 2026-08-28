package com.quotaedge.galaxy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import kotlin.math.roundToInt

enum class Provider { CLAUDE, CODEX }

data class QuotaWindow(
    val usedPercent: Int,
    val resetAtEpochSec: Long?,
    val resetAfterSeconds: Long?,
) {
    fun remainingMinutes(): Int? {
        resetAfterSeconds?.let { return (it / 60).toInt().coerceAtLeast(0) }
        resetAtEpochSec?.let {
            val sec = it - Instant.now().epochSecond
            if (sec > 0) return (sec / 60).toInt()
        }
        return null
    }

    fun remainingDays(): Double? {
        resetAfterSeconds?.let { return (it / 86400.0 * 10).roundToInt() / 10.0 }
        resetAtEpochSec?.let {
            val sec = it - Instant.now().epochSecond
            if (sec > 0) return ((sec / 86400.0) * 10).roundToInt() / 10.0
        }
        return null
    }
}

data class ProviderQuota(
    val provider: Provider,
    val fiveHour: QuotaWindow?,
    val weekly: QuotaWindow?,
    val connected: Boolean,
    val error: String? = null,
) {
    fun line1(): String {
        val fh = fiveHour?.usedPercent?.toString()?.padStart(2, ' ') ?: "--"
        val wk = weekly?.usedPercent?.toString()?.padStart(2, ' ') ?: "--"
        val mins = fiveHour?.remainingMinutes()?.let { "%03dm".format(it) } ?: "---m"
        return "${fh}%/${wk}%  $mins"
    }

    fun line2(): String {
        val days = weekly?.remainingDays()?.let { "%.1fd".format(it) } ?: "-.-d"
        return "            $days"
    }
}

data class UsageSnapshot(
    val claude: ProviderQuota,
    val codex: ProviderQuota,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        fun empty() = UsageSnapshot(
            claude = ProviderQuota(Provider.CLAUDE, null, null, connected = false),
            codex = ProviderQuota(Provider.CODEX, null, null, connected = false),
        )
    }
}

object UsageJson {
    val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
}

object ClaudeUsageParser {
    private val fiveHourKeys = setOf("five_hour", "5h", "session", "rolling_window_5h")
    private val weeklyKeys = setOf("seven_day", "7d", "weekly", "seven_day_all_models")

    fun parse(body: String): Pair<QuotaWindow?, QuotaWindow?> {
        val root = UsageJson.lenient.parseToJsonElement(body).jsonObject
        val buckets = mutableListOf<JsonObject>()
        root.forEach { (_, v) ->
            if (v is JsonObject && v.containsKey("utilization")) buckets += v
        }
        if (buckets.isEmpty()) {
            root.forEach { (k, v) ->
                if (v is JsonObject) buckets += v
            }
        }
        var five: QuotaWindow? = null
        var week: QuotaWindow? = null
        root.forEach { (key, value) ->
            if (value !is JsonObject) return@forEach
            val window = value.toWindow() ?: return@forEach
            when {
                fiveHourKeys.any { key.contains(it, ignoreCase = true) } -> five = window
                weeklyKeys.any { key.contains(it, ignoreCase = true) } -> week = window
            }
        }
        if (five == null || week == null) {
            val windows = root.mapNotNull { (_, v) -> (v as? JsonObject)?.toWindow() }
            val sorted = windows.sortedBy { it.resetAfterSeconds ?: Long.MAX_VALUE }
            if (five == null && sorted.isNotEmpty()) five = sorted.first()
            if (week == null && sorted.size > 1) week = sorted.last()
        }
        return five to week
    }

    private fun JsonObject.toWindow(): QuotaWindow? {
        val util = this["utilization"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: this["used_percent"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: return null
        val resetAt = this["resets_at"]?.jsonPrimitive?.content?.let { parseInstant(it) }
            ?: this["reset_at"]?.jsonPrimitive?.longOrNull
        val resetAfter = this["reset_after_seconds"]?.jsonPrimitive?.longOrNull
        return QuotaWindow(util.roundToInt().coerceIn(0, 100), resetAt, resetAfter)
    }

    private fun parseInstant(raw: String): Long? = runCatching {
        Instant.parse(raw).epochSecond
    }.getOrNull() ?: raw.toLongOrNull()
}

object CodexUsageParser {
    fun parse(body: String): Pair<QuotaWindow?, QuotaWindow?> {
        val root = UsageJson.lenient.parseToJsonElement(body).jsonObject
        val rate = root["rate_limit"]?.jsonObject ?: return null to null
        val primary = rate["primary_window"]?.jsonObject?.toWindow()
        val secondary = rate["secondary_window"]?.jsonObject?.toWindow()
        val pSec = primary?.resetAfterSeconds ?: 0
        val sSec = secondary?.resetAfterSeconds ?: 0
        return if (pSec <= sSec) primary to secondary else secondary to primary
    }

    private fun JsonObject.toWindow(): QuotaWindow? {
        val util = this["used_percent"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val resetAt = this["reset_at"]?.jsonPrimitive?.longOrNull
        val resetAfter = this["reset_after_seconds"]?.jsonPrimitive?.longOrNull
        return QuotaWindow(util.roundToInt().coerceIn(0, 100), resetAt, resetAfter)
    }
}
