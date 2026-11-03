package com.alortinarciple.asoulreminder.notifications

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alortinarciple.asoulreminder.MainActivity
import com.alortinarciple.asoulreminder.R
import com.alortinarciple.asoulreminder.data.AppPreferences

class NotificationHelper(
    private val context: Context,
    private val preferences: AppPreferences,
) {
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                channel(CHANNEL_MONITOR, "直播监控", NotificationManager.IMPORTANCE_LOW),
                channel(
                    NotificationChannels.channelId(NotificationChannels.Category.LIVE, true),
                    "开播提醒（悬浮）",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                channel(
                    NotificationChannels.channelId(NotificationChannels.Category.LIVE, false),
                    "开播提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                channel(
                    NotificationChannels.channelId(NotificationChannels.Category.DYNAMIC, true),
                    "动态提醒（悬浮）",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                channel(
                    NotificationChannels.channelId(NotificationChannels.Category.DYNAMIC, false),
                    "动态提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                channel(
                    NotificationChannels.channelId(NotificationChannels.Category.SCHEDULE, true),
                    "日程提醒（悬浮）",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                channel(
                    NotificationChannels.channelId(NotificationChannels.Category.SCHEDULE, false),
                    "日程提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    fun notifyLive(title: String, message: String, url: String?, headsUp: Boolean) {
        notify(
            NotificationChannels.channelId(NotificationChannels.Category.LIVE, headsUp),
            "live:$title:$message",
            title,
            message,
            url,
        )
    }

    fun notifyDynamic(title: String, message: String, url: String?, headsUp: Boolean) {
        notify(
            NotificationChannels.channelId(NotificationChannels.Category.DYNAMIC, headsUp),
            "dynamic:$title:$message",
            title,
            message,
            url,
        )
    }

    fun notifySchedule(title: String, message: String, url: String?, headsUp: Boolean) {
        notify(
            NotificationChannels.channelId(NotificationChannels.Category.SCHEDULE, headsUp),
            "schedule:$title:$message",
            title,
            message,
            url,
        )
    }

    fun monitorNotification(title: String, message: String): Notification {
        return baseBuilder(CHANNEL_MONITOR, title, message, null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun notify(channel: String, key: String, title: String, message: String, url: String?) {
        if (!canNotify()) return
        try {
            NotificationManagerCompat.from(context).notify(
                key.hashCode(),
                baseBuilder(channel, title, message, url).build(),
            )
        } catch (_: SecurityException) {
            // Permission may be revoked after the explicit check.
        }
    }

    private fun baseBuilder(
        channel: String,
        title: String,
        message: String,
        url: String?,
    ): NotificationCompat.Builder {
        // 通知点击直达目标：确认 B 站客户端能处理该链接时才带上包名，否则回退系统默认(浏览器)。
        // 不能用中转 Activity（Android 12+ 禁止通知 trampoline，会打不开）。
        val targetIntent = if (url.isNullOrBlank()) {
            Intent(context, MainActivity::class.java)
        } else {
            val uri = Uri.parse(url)
            Intent(Intent.ACTION_VIEW, uri).apply {
                bilibiliPackageFor(uri)?.let(::setPackage)
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            (url ?: title).hashCode(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Color.rgb(233, 84, 146))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(channel != CHANNEL_MONITOR)
            .setPriority(
                if (NotificationChannels.isHeadsUp(channel)) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
    }

    /** 打开目标：优先用户在设置里指定的应用，其次 B 站客户端（国内版/国际版），都没有则 null 走系统默认。 */
    private fun bilibiliPackageFor(uri: Uri): String? = runCatching {
        val handlers = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_VIEW, uri), 0)
            .map { it.activityInfo.packageName }
        NotificationTargets.pickPackage(preferences.loadSettings().bilibiliPackage, handlers)
    }.getOrNull()

    private fun canNotify(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun channel(id: String, name: String, importance: Int): NotificationChannel {
        return NotificationChannel(id, name, importance).apply {
            description = name
            enableVibration(id != CHANNEL_MONITOR)
        }
    }

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val BILIBILI_PACKAGE = "tv.danmaku.bili"

        // 旧渠道常量：仅老设备系统设置里可能残留，发送统一走 NotificationChannels 选择的新渠道。
        const val CHANNEL_LIVE = "live"
        const val CHANNEL_DYNAMIC = "dynamic"
        const val CHANNEL_SCHEDULE = "schedule"
        const val MONITOR_NOTIFICATION_ID = 21001
    }
}
