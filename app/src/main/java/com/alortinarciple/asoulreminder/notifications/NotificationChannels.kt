package com.alortinarciple.asoulreminder.notifications

/**
 * 通知渠道选择：Android 8+ 的渠道 importance 创建后不可程序化修改，
 * 因此每类通知准备「悬浮(HIGH)」与「普通(DEFAULT)」两个渠道，由设置决定用哪个。
 */
object NotificationChannels {
    enum class Category(val base: String) {
        LIVE("live"),
        DYNAMIC("dynamic"),
        SCHEDULE("schedule"),
    }

    fun channelId(category: Category, headsUp: Boolean): String =
        if (headsUp) "${category.base}_heads_up" else "${category.base}_quiet"

    fun isHeadsUp(channelId: String): Boolean = channelId.endsWith("_heads_up")
}
