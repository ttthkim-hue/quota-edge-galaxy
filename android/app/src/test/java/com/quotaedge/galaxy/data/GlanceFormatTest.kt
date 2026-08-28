package com.quotaedge.galaxy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GlanceFormatTest {
    @Test
    fun proIsWeeklyOnlyAndKeepsSevenDayReset() {
        val q = ProviderQuota(
            provider = Provider.CODEX,
            fiveHour = QuotaWindow(10, null, 18000, 18000),
            weekly = QuotaWindow(21, null, (6.3 * 86400).toLong(), 604800),
            connected = true,
            planType = "pro",
        )
        assertEquals("Codex 79%  6.3d", q.glanceLine())
    }

    @Test
    fun plusShowsFiveHourAndWeekly() {
        val q = ProviderQuota(
            provider = Provider.CODEX,
            fiveHour = QuotaWindow(45, null, 142 * 60, 18000),
            weekly = QuotaWindow(62, null, (2.1 * 86400).toLong(), 604800),
            connected = true,
            planType = "plus",
        )
        assertEquals("Codex 55%/38%  142m/2.1d", q.glanceLine())
    }

    @Test
    fun plusWeeklyDaysCapAt7() {
        val q = ProviderQuota(
            provider = Provider.CODEX,
            fiveHour = QuotaWindow(0, null, 300 * 60, 18000),
            weekly = QuotaWindow(0, null, 7 * 86400L, 604800),
            connected = true,
            planType = "plus",
        )
        assertEquals("Codex 100%/100%  300m/7.0d", q.glanceLine())
    }

    @Test
    fun unsyncedIsOmitted() {
        val q = ProviderQuota(Provider.CLAUDE, null, null, connected = false)
        assertEquals("", q.glanceLine())
    }
}
