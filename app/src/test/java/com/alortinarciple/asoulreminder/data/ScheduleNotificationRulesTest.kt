package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleNotificationRulesTest {
    private val now = 1_800_000_000_000L

    @Test
    fun notifiesUpcomingSchedule() {
        assertTrue(ScheduleNotificationRules.isNotifiable(now + 60 * 60_000L, now))
        assertTrue(ScheduleNotificationRules.isNotifiable(now, now))
    }

    @Test
    fun stillNotifiesScheduleThatJustStarted() {
        // 开始 10 分钟内的日程仍值得提醒（直播可能正在进行）。
        assertTrue(ScheduleNotificationRules.isNotifiable(now - 10 * 60_000L, now))
    }

    @Test
    fun skipsHistoryReaddedBySiteRebuild() {
        // 站点每周重建日历页会把几周前的日程重新放回列表，这些不应再提醒。
        assertFalse(ScheduleNotificationRules.isNotifiable(now - 45 * 60_000L, now))
        assertFalse(ScheduleNotificationRules.isNotifiable(now - 5L * 24 * 60 * 60_000L, now))
    }

    @Test
    fun dedupeKeyCombinesIdAndFingerprint() {
        assertEquals("e1|fp", ScheduleNotificationRules.dedupeKey("e1", "fp"))
    }

    @Test
    fun pruneDropsExpiredEntriesOnly() {
        val fresh = now - 60 * 60_000L
        val stale = now - ScheduleNotificationRules.RETENTION_MILLIS - 1
        val pruned = ScheduleNotificationRules.prune(mapOf("fresh" to fresh, "stale" to stale), now)
        assertEquals(setOf("fresh"), pruned.keys)
    }

    @Test
    fun cacheWithoutDedupeFieldStillDecodes() {
        // 升级用户旧缓存没有 notifiedScheduleKeys，必须能正常反序列化。
        val legacy = """
            {"events":[],"dynamics":[],"liveStatuses":{},"seenDynamicIds":{},"scheduledReminders":[]}
        """.trimIndent()
        val cache = Json { ignoreUnknownKeys = true }.decodeFromString<AppCache>(legacy)
        assertTrue(cache.notifiedScheduleKeys.isEmpty())
    }
}
