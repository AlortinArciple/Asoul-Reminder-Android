package com.alortinarciple.asoulreminder.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alortinarciple.asoulreminder.notifications.NotificationHelper

class LiveWindowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(LiveMonitorService.EXTRA_EVENT_ID)?.let { eventId ->
            LiveMonitorService.start(context, eventId)
        }
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "直播日程提醒" }
        val url = intent.getStringExtra(EXTRA_URL)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, -1)
        val message = if (minutes > 0) "直播将在 $minutes 分钟后开始" else "直播日程即将开始"
        // 开播前提醒与开播通知共用「开播提醒」渠道与悬浮开关。
        val container = context.container()
        container.notifications.notifyLive(
            title,
            message,
            url,
            container.repository.state.value.settings.headsUpLive,
        )
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        const val EXTRA_MINUTES = "minutes"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            return
        }
        val container = context.container()
        val state = container.repository.state.value
        BackgroundScheduler.schedulePeriodic(context, state.settings)
        BackgroundScheduler.ensureMonitor(context, state.settings)
        BackgroundScheduler.scheduleLiveWindows(context, state.cache.events, state.settings)
        BackgroundScheduler.restoreReminders(context, state.cache.scheduledReminders)
    }
}
