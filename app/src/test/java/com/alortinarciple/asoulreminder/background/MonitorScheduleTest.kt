package com.alortinarciple.asoulreminder.background

import com.alortinarciple.asoulreminder.data.LiveEvent
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
        val overdue = now - TimeUnit.MINUTES.toMillis(31)
        assertTrue(MonitorSchedule.scheduleDue(overdue, interval, now))
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
