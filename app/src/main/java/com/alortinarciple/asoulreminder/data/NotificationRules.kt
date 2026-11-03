package com.alortinarciple.asoulreminder.data

import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal object NotificationRules {
    fun shouldSuppressLiveStartDynamic(
        memberId: String,
        item: DynamicItem,
        events: List<LiveEvent>,
        windowMinutes: Int,
    ): Boolean {
        val publishedAt = item.publishedEpochMillis ?: return false
        if (windowMinutes <= 0) return false
        val windowMillis = TimeUnit.MINUTES.toMillis(windowMinutes.toLong())
        return events.any { event ->
            event.status !in setOf("cancelled", "rescheduled") &&
                (event.host == memberId || memberId in event.performers) &&
                abs(publishedAt - event.startEpochMillis) <= windowMillis
        }
    }
}
