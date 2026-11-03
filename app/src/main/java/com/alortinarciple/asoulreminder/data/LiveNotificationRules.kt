package com.alortinarciple.asoulreminder.data

/**
 * 「正在直播」通知的去重规则：
 * 以 B 站 live_time 作为场次标识——同一场直播期间无论状态抖动、并发刷新还是标题变化都只通知一次；
 * live_time 缺失时用占位场次，仅通知一次，下播后清除以便下一场重新通知。
 */
object LiveNotificationRules {
    const val UNKNOWN_SESSION = "unknown"

    fun sessionKey(status: LiveRoomStatus): String {
        val key = status.liveTime?.trim().orEmpty()
        return key.ifEmpty { UNKNOWN_SESSION }
    }

    fun shouldNotifyLive(notifiedSession: String?, status: LiveRoomStatus): Boolean {
        if (!status.isLive) return false
        val key = sessionKey(status)
        if (key == UNKNOWN_SESSION) return notifiedSession == null
        return key != notifiedSession
    }

    /** 下播后是否需要清除已通知记录：仅无场次标识时需要，以便下一场重新通知。 */
    fun clearSessionAfterOffline(notifiedSession: String?): Boolean =
        notifiedSession == UNKNOWN_SESSION
}
