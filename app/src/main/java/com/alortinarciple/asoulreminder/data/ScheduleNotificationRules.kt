package com.alortinarciple.asoulreminder.data

/**
 * 日程「新增/变动」提醒的判定规则。
 *
 * 背景：asoul.love 的 `calendar/latest` 每周会整页重生成，几天到几周前的日程会被重新放回
 * `initialEvents`。如果只跟「上一次缓存」做差集，这些历史日程就会被当成新日程逐条弹窗，
 * 所以这里加两条约束：
 * 1. 只提醒还没结束的日程（开始时间超过宽限期即视为历史条目）；
 * 2. 同一条日程、同样的内容只提醒一次，去重记录随缓存持久化。
 */
object ScheduleNotificationRules {
    /** 日程开始后仍可提醒的宽限期，超过即视为历史日程。 */
    const val PAST_GRACE_MILLIS: Long = 30 * 60_000L

    /** 去重记录的保留时长；过期记录会被清理，避免缓存无限增长。 */
    const val RETENTION_MILLIS: Long = 60L * 24 * 60 * 60_000L

    fun isNotifiable(startEpochMillis: Long, nowEpochMillis: Long): Boolean =
        startEpochMillis + PAST_GRACE_MILLIS > nowEpochMillis

    fun dedupeKey(id: String, fingerprint: String): String = "$id|$fingerprint"

    /** 丢弃超过保留期的去重记录。 */
    fun prune(keys: Map<String, Long>, nowEpochMillis: Long): Map<String, Long> =
        keys.filterValues { nowEpochMillis - it < RETENTION_MILLIS }
}
