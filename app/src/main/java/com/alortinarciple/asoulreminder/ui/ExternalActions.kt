package com.alortinarciple.asoulreminder.ui

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.alortinarciple.asoulreminder.background.BackgroundScheduler
import com.alortinarciple.asoulreminder.background.container
import com.alortinarciple.asoulreminder.data.AppSettings
import com.alortinarciple.asoulreminder.data.LiveEvent
import com.alortinarciple.asoulreminder.notifications.NotificationTargets
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExternalActions {
    private val beijingZone = ZoneId.of("Asia/Shanghai")
    const val ALARM_LEAD_MINUTES = 10L
    const val CALENDAR_LEAD_MINUTES = 60L
    private val alarmTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    fun openBilibili(context: Context, url: String) {
        val uri = Uri.parse(url)
        // 与通知点击同一套解析：优先设置里指定的应用，其次 B 站客户端，最后交给系统。
        val targetPackage = resolveBilibiliPackage(context, uri)
        val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            targetPackage?.let(::setPackage)
        }
        try {
            context.startActivity(appIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "没有应用可以打开这个链接", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveBilibiliPackage(context: Context, uri: Uri): String? = runCatching {
        val handlers = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_VIEW, uri), 0)
            .map { it.activityInfo.packageName }
        val configured = context.container().preferences.loadSettings().bilibiliPackage
        NotificationTargets.pickPackage(configured, handlers)
    }.getOrNull()

    fun addToCalendar(context: Context, event: LiveEvent, settings: AppSettings) {
        val begin = event.startEpochMillis - CALENDAR_LEAD_MINUTES * 60_000L
        val end = event.startEpochMillis + settings.defaultEventDurationMinutes * 60_000L
        val description = buildString {
            append(event.description)
            event.liveUrl?.let { append("\n\n直播间：$it") }
            event.dynamicUrl?.let { append("\n相关动态：$it") }
        }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, beijingZone.id)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "没有可用的日历应用", Toast.LENGTH_SHORT).show() }
    }

    fun createSystemAlarm(context: Context, event: LiveEvent) {
        val alarmAt = event.startEpochMillis - ALARM_LEAD_MINUTES * 60_000L
        val local = Instant.ofEpochMilli(alarmAt).atZone(beijingZone)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, local.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, local.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "ASOUL：${event.title}")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        runCatching { context.startActivity(intent) }
            .onSuccess {
                val alarmText = alarmTimeFormatter.format(local)
                Toast.makeText(
                    context,
                    "已设 $alarmText 闹钟（开播前 $ALARM_LEAD_MINUTES 分钟），请在系统时钟确认日期",
                    Toast.LENGTH_LONG,
                ).show()
            }
            .onFailure { Toast.makeText(context, "没有可用的时钟应用", Toast.LENGTH_SHORT).show() }
    }

    fun scheduleInAppReminders(
        context: Context,
        event: LiveEvent,
        settings: AppSettings,
    ) = BackgroundScheduler.scheduleEventReminders(context, event, settings.remindBeforeMinutes)

    fun requestExactAlarmAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        )
        runCatching { context.startActivity(intent) }
    }

    /** 是否已允许「安装未知应用」，未允许时无法直接拉起系统安装器。 */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }

    /** 用 FileProvider 把下载好的 APK 交给系统安装器。 */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (!canInstallPackages(context)) {
            Toast.makeText(context, "请先允许本应用安装未知应用", Toast.LENGTH_LONG).show()
            requestInstallPermission(context)
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, "无法读取安装包", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "没有可用的安装器", Toast.LENGTH_SHORT).show() }
    }
}
