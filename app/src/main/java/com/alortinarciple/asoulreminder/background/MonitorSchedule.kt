package com.alortinarciple.asoulreminder.background

import com.alortinarciple.asoulreminder.data.LiveEvent
import java.util.concurrent.TimeUnit

/**
 * 后台监控服务的到期与开播窗口判断(纯函数,便于单测)。
 * 节流下限与 Windows 端业务规则一致：日程 30 分钟、动态/直播 15 分钟；
 * 开播检测窗口为开播前 10 分钟至开播后 2 分钟。
 */
object MonitorSchedule {
    const val TICK_MILLIS = 60_000L
    const val SCHEDULE_MIN_INTERVAL_MINUTES = 30L
    const val CONTENT_MIN_INTERVAL_MINUTES = 15L
    val LIVE_WINDOW_BEFORE_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)
    val LIVE_WINDOW_AFTER_MILLIS: Long = TimeUnit.MINUTES.toMillis(2)

    fun scheduleDue(lastRefreshEpochMillis: Long?, intervalMinutes: Int, now: Long): Boolean {
        val last = lastRefreshEpochMillis ?: return true
        val minInterval = maxOf(SCHEDULE_MIN_INTERVAL_MINUTES, intervalMinutes.toLong())
        return now - last >= TimeUnit.MINUTES.toMillis(minInterval)
    }

    fun contentDue(lastRefreshEpochMillis: Long?, intervalMinutes: Int, now: Long): Boolean {
        val last = lastRefreshEpochMillis ?: return true
        val minInterval = maxOf(CONTENT_MIN_INTERVAL_MINUTES, intervalMinutes.toLong())
        return now - last >= TimeUnit.MINUTES.toMillis(minInterval)
    }

    /** 是否已进入该场直播的开播检测窗口(已改期的不参与；已取消的仍参与,与 Windows 端规则一致)。 */
    fun liveWindowStarted(event: LiveEvent, now: Long): Boolean {
        if (event.status == "rescheduled") return false
        return now >= event.startEpochMillis - LIVE_WINDOW_BEFORE_MILLIS &&
            now <= event.startEpochMillis + LIVE_WINDOW_AFTER_MILLIS
    }
}
