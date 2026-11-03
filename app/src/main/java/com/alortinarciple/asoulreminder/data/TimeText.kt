package com.alortinarciple.asoulreminder.data

import java.time.Instant
import java.time.ZoneId

private val BeijingZone = ZoneId.of("Asia/Shanghai")
private val WeekdayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

internal fun weekdayText(instant: Instant, zone: ZoneId = BeijingZone): String {
    return WeekdayNames[instant.atZone(zone).dayOfWeek.value - 1]
}
