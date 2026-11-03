package com.alortinarciple.asoulreminder.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.alortinarciple.asoulreminder.data.AppSettings
import com.alortinarciple.asoulreminder.data.LiveEvent
import com.alortinarciple.asoulreminder.data.ScheduledReminder
import java.util.concurrent.TimeUnit
import kotlin.math.max

object BackgroundScheduler {
    fun schedulePeriodic(context: Context, settings: AppSettings) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val scheduleWork = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
            max(30, settings.scheduleIntervalMinutes).toLong(),
            TimeUnit.MINUTES,
        ).setConstraints(constraints).build()
        val contentWork = PeriodicWorkRequestBuilder<ContentSyncWorker>(
            max(15, settings.dynamicIntervalMinutes).toLong(),
            TimeUnit.MINUTES,
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SCHEDULE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            scheduleWork,
        )
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CONTENT_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            contentWork,
        )
    }

    fun scheduleLiveWindows(context: Context, events: List<LiveEvent>, settings: AppSettings) {
        if (!settings.enhancedMonitoringEnabled) return
        val now = System.currentTimeMillis()
        val horizon = now + TimeUnit.DAYS.toMillis(8)
        events.asSequence()
            .filter { it.status != "rescheduled" }
            .filter { it.startEpochMillis + TimeUnit.MINUTES.toMillis(2) > now }
            .filter { it.startEpochMillis < horizon }
            .forEach { event -> scheduleLiveWindow(context, event) }
    }

    /** 增强监控开启时启动常驻后台监控服务(前台服务)；后台启动被系统拒绝时静默,等待下次前台/广播时机。 */
    fun ensureMonitor(context: Context, settings: AppSettings) {
        if (!settings.enhancedMonitoringEnabled) return
        runCatching { LiveMonitorService.start(context, null) }
    }

    fun stopMonitor(context: Context) {
        runCatching { LiveMonitorService.stop(context) }
    }

    /** 设置变更时按增强监控开关启停常驻服务。 */
    fun applyMonitorMode(context: Context, settings: AppSettings) {
        if (settings.enhancedMonitoringEnabled) {
            ensureMonitor(context, settings)
        } else {
            stopMonitor(context)
        }
    }

    fun scheduleEventReminders(
        context: Context,
        event: LiveEvent,
        beforeMinutes: List<Int>,
    ): List<ScheduledReminder> {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!canScheduleExact(alarmManager)) return emptyList()
        return beforeMinutes.distinct().mapNotNull { minutes ->
            val triggerAt = event.startEpochMillis - TimeUnit.MINUTES.toMillis(minutes.toLong())
            if (triggerAt <= System.currentTimeMillis()) return@mapNotNull null
            val key = "${event.id}:$minutes"
            val reminder = ScheduledReminder(
                key = key,
                eventId = event.id,
                title = event.title,
                triggerEpochMillis = triggerAt,
                liveUrl = event.liveUrl,
            )
            setReminderAlarm(context, alarmManager, reminder, minutes)
            reminder
        }
    }

    fun restoreReminders(context: Context, reminders: List<ScheduledReminder>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!canScheduleExact(alarmManager)) return
        reminders.filter { it.triggerEpochMillis > System.currentTimeMillis() }.forEach { reminder ->
            setReminderAlarm(context, alarmManager, reminder, null)
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        return canScheduleExact(context.getSystemService(AlarmManager::class.java))
    }

    private fun scheduleLiveWindow(context: Context, event: LiveEvent) {
        val triggerAt = max(
            System.currentTimeMillis() + 1_000,
            event.startEpochMillis - TimeUnit.MINUTES.toMillis(10),
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(alarmManager)) {
            val intent = Intent(context, LiveWindowReceiver::class.java)
                .putExtra(LiveMonitorService.EXTRA_EVENT_ID, event.id)
            val pending = PendingIntent.getBroadcast(
                context,
                event.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            val delay = max(0, triggerAt - System.currentTimeMillis())
            val request = OneTimeWorkRequestBuilder<LiveWindowWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(LiveMonitorService.EXTRA_EVENT_ID to event.id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "live-window-${event.id}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    private fun setReminderAlarm(
        context: Context,
        alarmManager: AlarmManager,
        reminder: ScheduledReminder,
        minutes: Int?,
    ) {
        val intent = Intent(context, EventReminderReceiver::class.java)
            .putExtra(EventReminderReceiver.EXTRA_TITLE, reminder.title)
            .putExtra(EventReminderReceiver.EXTRA_URL, reminder.liveUrl)
            .putExtra(EventReminderReceiver.EXTRA_MINUTES, minutes ?: -1)
        val pending = PendingIntent.getBroadcast(
            context,
            reminder.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerEpochMillis,
            pending,
        )
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private const val SCHEDULE_WORK = "periodic-schedule-sync"
    private const val CONTENT_WORK = "periodic-content-sync"
}
