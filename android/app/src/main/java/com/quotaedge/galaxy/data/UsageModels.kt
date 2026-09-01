package com.quotaedge.galaxy.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

enum class Provider { CLAUDE, CODEX, GROK }

data class QuotaWindow(
    val usedPercent: Int,
    val resetAtEpochSec: Long?,
    val resetAfterSeconds: Long?,
    val windowSeconds: Long? = null,
) {
    fun remainingPercent(): Int = (100 - usedPercent).coerceIn(0, 100)

    fun remainingMinutes(maxMinutes: Int = Int.MAX_VALUE): Int? {
        val minutes = resetAfterSeconds?.let { (it / 60).toInt() }
            ?: resetAtEpochSec?.let {
                val sec = it - Instant.now().epochSecond
                if (sec > 0) (sec / 60).toInt() else 0
            }
        return minutes?.coerceIn(0, maxMinutes)
    }

    fun remainingDays(maxDays: Double = Double.MAX_VALUE): Double? {
        val days = resetAfterSeconds?.let { it / 86400.0 }
            ?: resetAtEpochSec?.let {
                val sec = it - Instant.now().epochSecond
                if (sec > 0) sec / 86400.0 else 0.0
            }
            ?: return null
        return ((days.coerceIn(0.0, maxDays) * 10).roundToInt() / 10.0)
    }
}

data class ExtraLimit(
    val name: String,
    val fiveHour: QuotaWindow?,
    val weekly: QuotaWindow?,
)

data class ProviderQuota(
    val provider: Provider,
    val fiveHour: QuotaWindow?,
    val weekly: QuotaWindow?,
    val connected: Boolean,
    val error: String? = null,
    val planType: String? = null,
    val extraLimits: List<ExtraLimit> = emptyList(),
) {
    fun displayName(): String = when (provider) {
        Provider.CLAUDE -> "Claude"
        Provider.CODEX -> "Codex"
        Provider.GROK -> "Grok"
    }

    fun shownFiveHour(): QuotaWindow? =
        if (provider == Provider.CODEX && planType.equals("pro", ignoreCase = true)) null
        else if (provider == Provider.GROK) null
        else fiveHour

    private fun weeklyDayCap(): Double = 7.0

    fun isGlanceReady(): Boolean =
        connected && error == null && (shownFiveHour() != null || weekly != null)

    fun line1(): String {
        val fh = shownFiveHour()?.remainingPercent()?.toString()?.plus("%")
        val wk = weekly?.remainingPercent()?.toString()?.plus("%")
        return when {
            fh != null && wk != null -> "$fh/$wk"
            fh != null -> fh
            wk != null -> wk
            else -> ""
        }
    }

    fun line2(): String {
        val mins = shownFiveHour()?.remainingMinutes(maxMinutes = 5 * 60)?.let { "%03dm".format(it) }
        val days = weekly?.remainingDays(maxDays = weeklyDayCap())?.let { "%.1fd".format(it) }
        return when {
            mins != null && days != null -> "$mins/$days"
            mins != null -> mins
            days != null -> days
            else -> ""
        }
    }

    fun glanceLine(): String {
        if (!isGlanceReady()) return ""
        val a = line1()
        val b = line2()
        return when {
            a.isNotEmpty() && b.isNotEmpty() -> "${displayName()} $a  $b"
            a.isNotEmpty() -> "${displayName()} $a"
            else -> ""
        }
    }

    fun detailSummary(): String {
        if (!connected) return "미연동"
        if (error != null) return error
        val parts = mutableListOf<String>()
        planType?.let { parts += "plan=$it" }
        parts += windowDetail("5h", shownFiveHour(), missing = when {
            provider == Provider.CODEX && planType.equals("pro", ignoreCase = true) ->
                "5h 없음 (Pro는 주간만)"
            provider == Provider.GROK -> "5h 없음 (SuperGrok 주간 풀)"
            else -> "5h 없음"
        })
        parts += windowDetail("주간", weekly)
        extraLimits.forEach { extra ->
            parts += "${extra.name}:"
            parts += "  " + windowDetail("5h", extra.fiveHour)
            parts += "  " + windowDetail("주간", extra.weekly)
        }
        return parts.joinToString("\n")
    }

    private fun windowDetail(
        label: String,
        window: QuotaWindow?,
        missing: String = "$label 없음",
    ): String {
        if (window == null) return missing
        val left = window.remainingPercent()
        val isShort = (window.windowSeconds ?: 0) in 1..(12 * 3600)
        val reset = if (isShort || label == "5h") {
            window.remainingMinutes(maxMinutes = 5 * 60)?.let { "${it}m" } ?: "?"
        } else {
            window.remainingDays(maxDays = weeklyDayCap())?.let { "%.1fd".format(it) } ?: "?"
        }
        return "$label 남음${left}%/사용${window.usedPercent}% · 리셋 $reset"
    }
}

data class UsageSnapshot(
    val claude: ProviderQuota,
    val codex: ProviderQuota,
    val grok: ProviderQuota = ProviderQuota(Provider.GROK, null, null, connected = false),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        fun empty() = UsageSnapshot(
            claude = ProviderQuota(Provider.CLAUDE, null, null, connected = false),
            codex = ProviderQuota(Provider.CODEX, null, null, connected = false),
            grok = ProviderQuota(Provider.GROK, null, null, connected = false),
        )
    }
}

object UsageJson {
    val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
}

object ClaudeUsageParser {
    fun parse(body: String): Pair<QuotaWindow?, QuotaWindow?> {
        val root = UsageJson.lenient.parseToJsonElement(body).jsonObject
        val five = (root["five_hour"] as? JsonObject)?.toWindow()
        val week = (root["seven_day"] as? JsonObject)?.toWindow()
            ?: (root["seven_day_sonnet"] as? JsonObject)?.toWindow()
        return five to week
    }

    private fun JsonObject.toWindow(): QuotaWindow? {
        val utilEl = this["utilization"] ?: return null
        if (utilEl.toString() == "null") return null
        val util = utilEl.jsonPrimitive.content.toDoubleOrNull() ?: return null
        val pct = util.roundToInt().coerceIn(0, 100)
        val resetAt = this["resets_at"]?.jsonPrimitive?.content?.let { parseInstant(it) }
            ?: this["reset_at"]?.jsonPrimitive?.longOrNull
        val resetAfter = this["reset_after_seconds"]?.jsonPrimitive?.longOrNull
            ?: resetAt?.let { (it - Instant.now().epochSecond).coerceAtLeast(0) }
        return QuotaWindow(pct, resetAt, resetAfter)
    }

    private fun parseInstant(raw: String): Long? = runCatching {
        OffsetDateTime.parse(raw).toEpochSecond()
    }.getOrNull() ?: runCatching {
        Instant.parse(raw).epochSecond
    }.getOrNull() ?: raw.toLongOrNull()
}

data class CodexParseResult(
    val fiveHour: QuotaWindow?,
    val weekly: QuotaWindow?,
    val planType: String?,
    val extraLimits: List<ExtraLimit> = emptyList(),
    val redactedRaw: String = "",
)

object CodexUsageParser {
    fun parseDetailed(body: String): CodexParseResult {
        val root = UsageJson.lenient.parseToJsonElement(body).jsonObject
        val planType = root["plan_type"]?.jsonPrimitive?.content
        val rate = root["rate_limit"] as? JsonObject
        val main = classifyWindows(rate)
        val extra = parseExtraLimits(root["additional_rate_limits"] as? JsonArray)
        return CodexParseResult(main.first, main.second, planType, extra)
    }

    fun parse(body: String): Pair<QuotaWindow?, QuotaWindow?> {
        val d = parseDetailed(body)
        return d.fiveHour to d.weekly
    }

    private fun parseExtraLimits(arr: JsonArray?): List<ExtraLimit> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = obj["limit_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val inner = obj["rate_limit"] as? JsonObject
            val (fh, wk) = classifyWindows(inner)
            ExtraLimit(name, fh, wk)
        }
    }

    private data class Win(val window: QuotaWindow, val limitSec: Long)

    private fun classifyWindows(rate: JsonObject?): Pair<QuotaWindow?, QuotaWindow?> {
        if (rate == null) return null to null
        val wins = listOfNotNull(
            (rate["primary_window"] as? JsonObject)?.toWin(),
            (rate["secondary_window"] as? JsonObject)?.toWin(),
        )
        if (wins.isEmpty()) return null to null
        if (wins.size == 1) {
            val only = wins.first()
            return if (only.limitSec in 1..(6 * 3600)) {
                only.window to null
            } else {
                null to only.window
            }
        }
        val sorted = wins.sortedBy { it.limitSec }
        return sorted.first().window to sorted.last().window
    }

    private fun JsonObject.toWin(): Win? {
        val util = this["used_percent"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val resetAt = this["reset_at"]?.jsonPrimitive?.longOrNull
        val resetAfter = this["reset_after_seconds"]?.jsonPrimitive?.longOrNull
            ?: resetAt?.let { (it - Instant.now().epochSecond).coerceAtLeast(0) }
        val limitSec = this["limit_window_seconds"]?.jsonPrimitive?.longOrNull
            ?: resetAfter
            ?: 0L
        return Win(
            QuotaWindow(util.roundToInt().coerceIn(0, 100), resetAt, resetAfter, limitSec),
            limitSec,
        )
    }
}

object UsageJsonRedactor {
    fun redact(body: String): String {
        return runCatching {
            val obj = UsageJson.lenient.parseToJsonElement(body).jsonObject
            val drop = setOf("user_id", "account_id", "email", "name")
            JsonObject(obj.filterKeys { it !in drop }).toString().take(4000)
        }.getOrElse { body.take(400) }
    }
}
