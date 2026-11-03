package com.alortinarciple.asoulreminder.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelsTest {
    @Test
    fun channelIdFollowsHeadsUpSwitch() {
        assertEquals("live_heads_up", NotificationChannels.channelId(NotificationChannels.Category.LIVE, true))
        assertEquals("live_quiet", NotificationChannels.channelId(NotificationChannels.Category.LIVE, false))
        assertEquals("dynamic_heads_up", NotificationChannels.channelId(NotificationChannels.Category.DYNAMIC, true))
        assertEquals("schedule_quiet", NotificationChannels.channelId(NotificationChannels.Category.SCHEDULE, false))
    }

    @Test
    fun isHeadsUpDetectsChannelSuffix() {
        assertTrue(NotificationChannels.isHeadsUp("live_heads_up"))
        assertTrue(NotificationChannels.isHeadsUp("dynamic_heads_up"))
        assertFalse(NotificationChannels.isHeadsUp("schedule_quiet"))
        assertFalse(NotificationChannels.isHeadsUp("monitor"))
    }
}
