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
    /** 日程节拍抖动比例：打散不同设备在同一分钟请求 asoul.love 的情况。 */
    const val SCHEDULE_JITTER_RATIO = 0.1
    /** 连续失败退避倍数上限(与 Windows 端一致)。 */
    const val MAX_BACKOFF_FACTOR = 4
    val LIVE_WINDOW_BEFORE_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)
    val LIVE_WINDOW_AFTER_MILLIS: Long = TimeUnit.MINUTES.toMillis(2)

    fun scheduleDue(lastRefreshEpochMillis: Long?, intervalMinutes: Int, now: Long): Boolean =
        scheduleDue(lastRefreshEpochMillis, intervalMinutes, now, failStreak = 0)

    /**
     * 日程是否到期。阈值 = 基础间隔(不低于 30 分钟) × 失败退避倍数 × (1 + 抖动)。
     * 抖动只由上次刷新时间推导，同一个 last 反复调用结果一致，不会因为每分钟一次的
     * tick 重新掷骰子而提前请求。
     */
    fun scheduleDue(
        lastRefreshEpochMillis: Long?,
        intervalMinutes: Int,
        now: Long,
        failStreak: Int,
    ): Boolean {
        val last = lastRefreshEpochMillis ?: return true
        return now - last >= scheduleThresholdMillis(last, intervalMinutes, failStreak)
    }

    fun scheduleThresholdMillis(lastRefreshEpochMillis: Long, intervalMinutes: Int, failStreak: Int): Long {
        val base = maxOf(SCHEDULE_MIN_INTERVAL_MINUTES, intervalMinutes.toLong())
        val backoff = if (failStreak >= 2) minOf(failStreak, MAX_BACKOFF_FACTOR).toLong() else 1L
        val millis = TimeUnit.MINUTES.toMillis(base * backoff).toDouble()
        return (millis * (1.0 + jitterFraction(lastRefreshEpochMillis))).toLong()
    }

    /** 由种子稳定推导出 ±10% 的偏移；同一 last 值每次结果相同。 */
    fun jitterFraction(seed: Long): Double {
        if (seed == 0L) return 0.0
        var mixed = seed * -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        mixed = mixed xor (seed ushr 33)
        val unit = (mixed ushr 11).toDouble() / (1L shl 53).toDouble() // [0, 1)
        return (unit * 2.0 - 1.0) * SCHEDULE_JITTER_RATIO
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
