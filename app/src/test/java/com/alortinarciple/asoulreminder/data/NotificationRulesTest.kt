package com.alortinarciple.asoulreminder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    private val start = 1_786_276_800_000L
    private val event = LiveEvent(
        id = "event-1",
        title = "测试直播",
        description = "",
        type = "official",
        format = "live",
        startEpochMillis = start,
        host = "diana",
        performers = listOf("diana"),
        status = "published",
        sourceId = "123",
        color = "#E799B0",
    )

    @Test
    fun suppressesSameMemberDynamicInsideLiveStartWindow() {
        val item = dynamic("diana", start + 3 * 60_000L)

        assertTrue(NotificationRules.shouldSuppressLiveStartDynamic("diana", item, listOf(event), 5))
    }

    @Test
    fun keepsOtherMemberLateAndCancelledDynamics() {
        assertFalse(
            NotificationRules.shouldSuppressLiveStartDynamic(
                "bella",
                dynamic("bella", start + 60_000L),
                listOf(event),
                5,
            ),
        )
        assertFalse(
            NotificationRules.shouldSuppressLiveStartDynamic(
                "diana",
                dynamic("diana", start + 6 * 60_000L),
                listOf(event),
                5,
            ),
        )
        assertFalse(
            NotificationRules.shouldSuppressLiveStartDynamic(
                "diana",
                dynamic("diana", start + 60_000L),
                listOf(event.copy(status = "cancelled")),
                5,
            ),
        )
    }

    private fun dynamic(memberId: String, publishedAt: Long) = DynamicItem(
        id = "dynamic-$memberId-$publishedAt",
        memberId = memberId,
        memberName = memberId,
        text = "测试动态",
        url = "https://www.bilibili.com/opus/1",
        publishedEpochMillis = publishedAt,
    )
}
