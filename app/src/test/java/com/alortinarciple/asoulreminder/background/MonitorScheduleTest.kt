package com.alortinarciple.asoulreminder.background

import com.alortinarciple.asoulreminder.data.LiveEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MonitorScheduleTest {
    private val now = 1_752_000_000_000L

    @Test
    fun scheduleDueRespectsThirtyMinuteFloorAndNullAsDue() {
        val interval = 5
        val justRefreshed = now - TimeUnit.MINUTES.toMillis(10)
        assertFalse(MonitorSchedule.scheduleDue(justRefreshed, interval, now))
        assertTrue(MonitorSchedule.scheduleDue(null, interval, now))
        // 30 分钟下限叠加最多 -10% 抖动后是 27 分钟，31 分钟一定到期。
        val overdue = now - TimeUnit.MINUTES.toMillis(31)
        assertTrue(MonitorSchedule.scheduleDue(overdue, interval, now))
    }

    @Test
    fun jitterStaysWithinTenPercent() {
        val threshold = MonitorSchedule.scheduleThresholdMillis(now, 30, failStreak = 0)
        val thirtyMinutes = TimeUnit.MINUTES.toMillis(30)
        assertTrue(threshold in (thirtyMinutes * 0.9).toLong()..(thirtyMinutes * 1.1).toLong())
    }

    @Test
    fun jitterIsDeterministicForTheSameSeed() {
        val seeds = (1..20).map { now + it * 7919L }
        assertEquals(seeds.map(MonitorSchedule::jitterFraction), seeds.map(MonitorSchedule::jitterFraction))
        seeds.forEach { assertTrue(MonitorSchedule.jitterFraction(it) in -0.1..0.1) }
    }

    @Test
    fun zeroSeedHasNoJitter() {
        assertEquals(0.0, MonitorSchedule.jitterFraction(0L), 1e-9)
    }

    @Test
    fun failuresBackOffUpToFourTimes() {
        val base = TimeUnit.MINUTES.toMillis(30)

        // streak < 2 不放大
        assertTrue(MonitorSchedule.scheduleThresholdMillis(now, 30, 0) in (base * 0.9).toLong()..(base * 1.1).toLong())
        assertTrue(MonitorSchedule.scheduleThresholdMillis(now, 30, 1) in (base * 0.9).toLong()..(base * 1.1).toLong())
        // streak >= 2 逐级放大
        assertTrue(MonitorSchedule.scheduleThresholdMillis(now, 30, 2) >= (base * 1.8).toLong())
        // 封顶 4 倍
        val capped = MonitorSchedule.scheduleThresholdMillis(now, 30, 99)
        assertTrue(capped in (base * 4 * 0.9).toLong()..(base * 4 * 1.1).toLong())
    }

    @Test
    fun failedScheduleIsNotRetriedImmediately() {
        assertFalse(MonitorSchedule.scheduleDue(now, 30, now + TimeUnit.MINUTES.toMillis(1), failStreak = 3))
        // 4 次失败封顶 120 分钟，叠加最多 +10% 抖动，因此用 135 分钟断言。
        assertTrue(MonitorSchedule.scheduleDue(now, 30, now + TimeUnit.MINUTES.toMillis(135), failStreak = 4))
    }

    @Test
    fun contentDueRespectsFifteenMinuteFloor() {
        val interval = 3
        val tenMinutesAgo = now - TimeUnit.MINUTES.toMillis(10)
        assertFalse(MonitorSchedule.contentDue(tenMinutesAgo, interval, now))
        assertTrue(MonitorSchedule.contentDue(null, interval, now))
        val sixteenMinutesAgo = now - TimeUnit.MINUTES.toMillis(16)
        assertTrue(MonitorSchedule.contentDue(sixteenMinutesAgo, interval, now))
    }

    @Test
    fun liveWindowOpensTenMinutesBeforeAndClosesTwoMinutesAfter() {
        fun event(start: Long, status: String = "normal") = LiveEvent(
            id = "e1",
            title = "直播",
            description = "",
            type = "live",
            format = "",
            startEpochMillis = start,
            status = status,
            host = "diana",
            performers = listOf(),
            sourceId = "",
            color = "#E799B0",
        )
        val start = now + TimeUnit.MINUTES.toMillis(60)
        val opened = start - TimeUnit.MINUTES.toMillis(10)
        assertFalse(MonitorSchedule.liveWindowStarted(event(start), opened - 1000))
        assertTrue(MonitorSchedule.liveWindowStarted(event(start), opened))
        assertTrue(MonitorSchedule.liveWindowStarted(event(start), start))
        assertTrue(MonitorSchedule.liveWindowStarted(event(start), start + TimeUnit.MINUTES.toMillis(2)))
        assertFalse(MonitorSchedule.liveWindowStarted(event(start), start + TimeUnit.MINUTES.toMillis(2) + 1000))
        // 已改期不参与；已取消仍参与(与 Windows 端一致)
        assertFalse(MonitorSchedule.liveWindowStarted(event(start, "rescheduled"), start))
        assertTrue(MonitorSchedule.liveWindowStarted(event(start, "cancelled"), start))
    }
}
