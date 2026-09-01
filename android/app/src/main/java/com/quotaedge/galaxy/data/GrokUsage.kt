package com.quotaedge.galaxy.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

data class GrokParseResult(
    val weekly: QuotaWindow?,
    val planType: String?,
    val redactedRaw: String = "",
)

object GrokUsageParser {
    fun parseBilling(body: String, planType: String? = null): GrokParseResult {
        val root = UsageJson.lenient.parseToJsonElement(body).jsonObject
        val config = (root["config"] as? JsonObject) ?: root
        val used = number(config, "creditUsagePercent")
            ?: ratio(config, "onDemandUsed", "onDemandCap")
            ?: ratio(root, "onDemandUsed", "onDemandCap")
        val endRaw = nestedString(config, "currentPeriod", "end")
            ?: string(config, "billingPeriodEnd")
            ?: nestedString(root, "currentPeriod", "end")
            ?: string(root, "billingPeriodEnd")
        val resetAt = endRaw?.let { parseInstant(it) }
        val resetAfter = resetAt?.let { (it - Instant.now().epochSecond).coerceAtLeast(0) }
        val weekly = used?.let {
            QuotaWindow(
                usedPercent = it.roundToInt().coerceIn(0, 100),
                resetAtEpochSec = resetAt,
                resetAfterSeconds = resetAfter,
                windowSeconds = 7 * 86400L,
            )
        }
        return GrokParseResult(weekly = weekly, planType = planType)
    }

    fun parsePlan(body: String): String? {
        val root = UsageJson.lenient.parseToJsonElement(body).jsonObject
        return string(root, "subscription_tier_display")
            ?: string(root, "subscriptionTierDisplay")
            ?: string(root, "plan")
    }

    private fun number(obj: JsonObject, key: String): Double? =
        obj[key]?.jsonPrimitive?.content?.toDoubleOrNull()

    private fun string(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun nestedString(obj: JsonObject, a: String, b: String): String? =
        (obj[a] as? JsonObject)?.let { string(it, b) }

    private fun ratio(obj: JsonObject, usedKey: String, capKey: String): Double? {
        val used = numericVal(obj[usedKey]) ?: return null
        val cap = numericVal(obj[capKey]) ?: return null
        if (cap <= 0.0) return null
        return (used / cap) * 100.0
    }

    private fun numericVal(el: kotlinx.serialization.json.JsonElement?): Double? {
        if (el == null) return null
        val prim = runCatching { el.jsonPrimitive.content.toDoubleOrNull() }.getOrNull()
        if (prim != null) return prim
        val obj = el as? JsonObject ?: return null
        return obj["val"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: obj["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
    }

    private fun parseInstant(raw: String): Long? = runCatching {
        OffsetDateTime.parse(raw).toEpochSecond()
    }.getOrNull() ?: runCatching {
        Instant.parse(raw).epochSecond
    }.getOrNull() ?: raw.toLongOrNull()
        ?: raw.toLongOrNull()?.takeIf { it > 10_000_000_000L }?.let { it / 1000 }
}
