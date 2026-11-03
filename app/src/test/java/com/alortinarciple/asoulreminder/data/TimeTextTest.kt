package com.alortinarciple.asoulreminder.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTextTest {
    @Test
    fun weekdayTextMapsBeijingDateToWeekday() {
        // 2026-06-10 UTC 12:00 == 北京时间 2026-06-10 20:00，星期三。
        assertEquals("星期三", weekdayText(Instant.parse("2026-06-10T12:00:00Z")))

        // UTC 2026-06-13 20:00 == 北京时间 2026-06-14 04:00，星期按北京日期取。
        assertEquals("星期日", weekdayText(Instant.parse("2026-06-13T20:00:00Z")))
    }
}
