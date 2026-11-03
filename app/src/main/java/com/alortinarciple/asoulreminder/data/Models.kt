package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.Serializable

@Serializable
data class MemberConfig(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val monitorDynamic: Boolean,
    val monitorLive: Boolean,
    val color: String,
    val uid: String,
    val dynamicUrl: String,
    val liveUrl: String,
    val avatarKey: String = id,
)

@Serializable
data class LiveEvent(
    val id: String,
    val title: String,
    val description: String,
    val type: String,
    val format: String,
    val startEpochMillis: Long,
    val host: String,
    val performers: List<String>,
    val status: String,
    val sourceId: String,
    val updateSourceId: String? = null,
    val dynamicUrl: String? = null,
    val liveUrl: String? = null,
    val color: String,
) {
    val fingerprint: String
        get() = listOf(
            id,
            title,
            description,
            startEpochMillis.toString(),
            host,
            performers.joinToString(","),
            status,
            updateSourceId.orEmpty(),
        ).joinToString("|")
}

@Serializable
data class DynamicTextSegment(
    val type: String,
    val text: String,
    val url: String = "",
)

@Serializable
data class DynamicItem(
    val id: String,
    val memberId: String,
    val memberName: String,
    val text: String,
    val url: String,
    val publishedEpochMillis: Long? = null,
    val richText: List<DynamicTextSegment> = emptyList(),
)

@Serializable
data class LiveRoomStatus(
    val memberId: String,
    val memberName: String,
    val roomId: String,
    val liveStatus: Int,
    val title: String,
    val liveTime: String? = null,
    val checkedEpochMillis: Long,
) {
    val isLive: Boolean get() = liveStatus == 1
}

@Serializable
data class ScheduledReminder(
    val key: String,
    val eventId: String,
    val title: String,
    val triggerEpochMillis: Long,
    val liveUrl: String? = null,
)

@Serializable
data class AppSettings(
    val asoulCalendarUrl: String = "https://asoul.love/calendar/latest",
    val asoulHomeUrl: String = "https://asoul.love/",
    val scheduleIntervalMinutes: Int = 30,
    val dynamicIntervalMinutes: Int = 15,
    val dynamicHighFrequencyIntervalMinutes: Int = 3,
    val dynamicHighFrequencyMaxMinutes: Int = 60,
    val liveOpenIntervalSeconds: Int = 30,
    val liveCloseIntervalMinutes: Int = 20,
    val dynamicFetchLimit: Int = 3,
    val suppressLiveDynamicMinutes: Int = 5,
    val remindBeforeMinutes: List<Int> = listOf(10, 5),
    val defaultEventDurationMinutes: Int = 120,
    val notificationsEnabled: Boolean = true,
    val enhancedMonitoringEnabled: Boolean = true,
    val headsUpLive: Boolean = true,
    val headsUpDynamic: Boolean = true,
    val headsUpSchedule: Boolean = false,
    val bilibiliPackage: String = "",
    val members: List<MemberConfig> = defaultMembers(),
)

@Serializable
data class AppCache(
    val events: List<LiveEvent> = emptyList(),
    val dynamics: List<DynamicItem> = emptyList(),
    val liveStatuses: Map<String, LiveRoomStatus> = emptyMap(),
    val notifiedLiveSessions: Map<String, String> = emptyMap(),
    /** 日程提醒去重记录：key 为 `id|fingerprint`，value 为提醒发生的时间戳。 */
    val notifiedScheduleKeys: Map<String, Long> = emptyMap(),
    val seenDynamicIds: Map<String, Set<String>> = emptyMap(),
    val scheduledReminders: List<ScheduledReminder> = emptyList(),
    val lastScheduleRefreshEpochMillis: Long? = null,
    val lastDynamicRefreshEpochMillis: Long? = null,
    val lastLiveRefreshEpochMillis: Long? = null,
    /** 日程连续失败次数：用于失败退避，成功后清零。 */
    val scheduleFailStreak: Int = 0,
    val lastError: String? = null,
)

fun defaultMembers(): List<MemberConfig> = listOf(
    MemberConfig(
        id = "diana",
        name = "嘉然今天吃什么",
        enabled = true,
        monitorDynamic = true,
        monitorLive = true,
        color = "#E799B0",
        uid = "672328094",
        dynamicUrl = "https://space.bilibili.com/672328094/dynamic",
        liveUrl = "https://live.bilibili.com/22637261",
    ),
    MemberConfig(
        id = "eileen",
        name = "乃琳Queen",
        enabled = true,
        monitorDynamic = true,
        monitorLive = true,
        color = "#576690",
        uid = "672342685",
        dynamicUrl = "https://space.bilibili.com/672342685/dynamic",
        liveUrl = "https://live.bilibili.com/22625027",
    ),
    MemberConfig(
        id = "bella",
        name = "贝拉kira",
        enabled = true,
        monitorDynamic = true,
        monitorLive = true,
        color = "#DB7D74",
        uid = "672353429",
        dynamicUrl = "https://space.bilibili.com/672353429/dynamic",
        liveUrl = "https://live.bilibili.com/22632424",
    ),
    MemberConfig(
        id = "fiona",
        name = "心宜不是心仪",
        enabled = true,
        monitorDynamic = false,
        monitorLive = true,
        color = "#C93773",
        uid = "",
        dynamicUrl = "",
        liveUrl = "https://live.bilibili.com/30849777",
    ),
    MemberConfig(
        id = "gladys",
        name = "思诺snow",
        enabled = true,
        monitorDynamic = false,
        monitorLive = true,
        color = "#7252C0",
        uid = "",
        dynamicUrl = "",
        liveUrl = "https://live.bilibili.com/30858592",
    ),
)
