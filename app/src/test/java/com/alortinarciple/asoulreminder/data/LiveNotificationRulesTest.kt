package com.alortinarciple.asoulreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNotificationRulesTest {
    private fun status(liveStatus: Int, liveTime: String? = null, title: String = "直播") =
        LiveRoomStatus(
            memberId = "diana",
            memberName = "嘉然",
            roomId = "22637261",
            liveStatus = liveStatus,
            title = title,
            liveTime = liveTime,
            checkedEpochMillis = 1_752_000_000_000L,
        )

    @Test
    fun sameSessionNotifiesOnlyOnce() {
        val live = status(1, "2026-09-07 20:00:00")
        assertTrue(LiveNotificationRules.shouldNotifyLive(null, live))
        assertFalse(LiveNotificationRules.shouldNotifyLive("2026-09-07 20:00:00", live))
    }

    @Test
    fun newSessionNotifiesAgain() {
        val live = status(1, "2026-09-07 21:30:00")
        assertTrue(LiveNotificationRules.shouldNotifyLive("2026-09-07 20:00:00", live))
    }

    @Test
    fun titleChangeDoesNotRetriggerSameSession() {
        val first = status(1, "2026-09-07 20:00:00", title = "旧标题")
        val renamed = status(1, "2026-09-07 20:00:00", title = "新标题")
        assertTrue(LiveNotificationRules.shouldNotifyLive(null, first))
        assertFalse(
            LiveNotificationRules.shouldNotifyLive(
                LiveNotificationRules.sessionKey(first),
                renamed,
            ),
        )
    }

    @Test
    fun missingLiveTimeNotifiesOnceAndResetsAfterOffline() {
        val live = status(1, liveTime = null)
        assertTrue(LiveNotificationRules.shouldNotifyLive(null, live))
        assertFalse(LiveNotificationRules.shouldNotifyLive(LiveNotificationRules.UNKNOWN_SESSION, live))
        assertTrue(LiveNotificationRules.clearSessionAfterOffline(LiveNotificationRules.UNKNOWN_SESSION))
        assertTrue(LiveNotificationRules.shouldNotifyLive(null, live))
    }

    @Test
    fun offlineStatusNeverNotifies() {
        assertFalse(LiveNotificationRules.shouldNotifyLive(null, status(0, "2026-09-07 20:00:00")))
        assertFalse(LiveNotificationRules.shouldNotifyLive(null, status(2, "2026-09-07 20:00:00")))
        assertFalse(LiveNotificationRules.clearSessionAfterOffline("2026-09-07 20:00:00"))
    }
}
