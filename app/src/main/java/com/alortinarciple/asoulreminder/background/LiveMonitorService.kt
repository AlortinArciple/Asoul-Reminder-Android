package com.alortinarciple.asoulreminder.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.alortinarciple.asoulreminder.data.LiveEvent
import com.alortinarciple.asoulreminder.data.MemberConfig
import com.alortinarciple.asoulreminder.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 全天常驻的后台监控前台服务：
 * - 每 60 秒 tick 一次,按节流判断到期后刷新日程/动态/直播间状态；
 * - 滚动检查每场直播的开播检测窗口(开播前 10 分钟起),窗口内启动单场监控协程
 *   (开播检测 30 秒/下播检查 20 分钟/下播后 3 分钟高频动态,行为与原先一致)；
 * - 通知栏常驻低优先级「后台运行中」通知(monitor 渠道)。
 * 进程被系统杀死后由 START_STICKY、开机广播与开播闹钟路径尽力重建；
 * 若 ROM 限制仍无法拉起,请在系统设置中允许本应用自启动与后台运行。
 */
class LiveMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private var mainLoop: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationHelper.MONITOR_NOTIFICATION_ID,
            container().notifications.monitorNotification("后台监控运行中", "自动刷新日程、动态与直播间状态"),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureMainLoop()
        intent?.getStringExtra(EXTRA_EVENT_ID)?.let { eventId ->
            if (jobs[eventId]?.isActive != true) {
                jobs[eventId] = serviceScope.launch {
                    monitor(eventId)
                    jobs.remove(eventId)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainLoop?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureMainLoop() {
        if (mainLoop?.isActive == true) return
        mainLoop = serviceScope.launch {
            while (serviceScope.isActive) {
                runCatching { tick() }
                delay(MonitorSchedule.TICK_MILLIS)
            }
        }
    }

    private suspend fun tick() {
        val repository = container().repository
        val state = repository.state.value
        val settings = state.settings
        if (!settings.enhancedMonitoringEnabled) {
            // 增强监控已被关闭：常驻服务应停止(正常由设置变更路径停止,此处兜底)。
            stopSelf()
            return
        }
        val now = System.currentTimeMillis()
        if (
            MonitorSchedule.scheduleDue(
                state.cache.lastScheduleRefreshEpochMillis,
                settings.scheduleIntervalMinutes,
                now,
                state.cache.scheduleFailStreak,
            )
        ) {
            runCatching { repository.refreshSchedule() }
        }
        if (MonitorSchedule.contentDue(state.cache.lastDynamicRefreshEpochMillis, settings.dynamicIntervalMinutes, now)) {
            runCatching { repository.refreshDynamics() }
        }
        if (MonitorSchedule.contentDue(state.cache.lastLiveRefreshEpochMillis, settings.liveCloseIntervalMinutes, now)) {
            runCatching { repository.refreshLiveStatuses() }
        }
        // 滚动检查直播窗口,窗口内且无活跃监控协程的日程启动单场监控。
        state.cache.events.forEach { event ->
            val eventId = event.id
            if (jobs[eventId]?.isActive != true && MonitorSchedule.liveWindowStarted(event, now)) {
                jobs[eventId] = serviceScope.launch {
                    monitor(eventId)
                    jobs.remove(eventId)
                }
            }
        }
    }

    private suspend fun monitor(eventId: String) {
        val repository = container().repository
        val state = repository.state.value
        val event = state.cache.events.firstOrNull { it.id == eventId } ?: return
        if (!state.settings.enhancedMonitoringEnabled || event.status == "rescheduled") return
        val member = memberForEvent(event, state.settings.members) ?: return
        val openWindowEnd = event.startEpochMillis + TimeUnit.MINUTES.toMillis(2)
        var observedLive = false

        while (serviceScope.isActive) {
            val status = runCatching { repository.refreshMemberLive(member.id) }.getOrNull()
            val now = System.currentTimeMillis()
            if (status?.isLive == true) {
                observedLive = true
                updateNotification("${member.name}正在直播", status.title.ifBlank { "每 20 分钟确认下播状态" })
                delay(TimeUnit.MINUTES.toMillis(state.settings.liveCloseIntervalMinutes.coerceAtLeast(1).toLong()))
                continue
            }
            if (observedLive) {
                updateNotification("${member.name}直播已结束", "正在查找直播后的新动态")
                monitorPostLiveDynamics(member, now)
                // 查找结束(找到动态或超时)：常驻通知恢复默认文案。
                updateNotification("后台监控运行中", "自动刷新日程、动态与直播间状态")
                return
            }
            if (now > openWindowEnd) return
            delay(TimeUnit.SECONDS.toMillis(state.settings.liveOpenIntervalSeconds.coerceAtLeast(15).toLong()))
        }
    }

    private suspend fun monitorPostLiveDynamics(member: MemberConfig, endedAt: Long) {
        val repository = container().repository
        val settings = repository.state.value.settings
        if (!member.enabled || !member.monitorDynamic || member.uid.isBlank() || !repository.state.value.hasCookie) {
            return
        }
        val expiresAt = endedAt + TimeUnit.MINUTES.toMillis(settings.dynamicHighFrequencyMaxMinutes.toLong())
        while (System.currentTimeMillis() < expiresAt && serviceScope.isActive) {
            val items = runCatching { repository.refreshMemberDynamics(member.id) }.getOrDefault(emptyList())
            if (items.any { (it.publishedEpochMillis ?: 0L) >= endedAt }) return
            delay(TimeUnit.MINUTES.toMillis(settings.dynamicHighFrequencyIntervalMinutes.coerceAtLeast(1).toLong()))
        }
    }

    private fun memberForEvent(event: LiveEvent, members: List<MemberConfig>): MemberConfig? {
        val byId = members.associateBy(MemberConfig::id)
        return byId[event.host]?.takeIf { it.enabled && it.monitorLive }
            ?: event.performers.asSequence()
                .mapNotNull(byId::get)
                .firstOrNull { it.enabled && it.monitorLive }
    }

    private fun updateNotification(title: String, message: String) {
        val notification = container().notifications.monitorNotification(title, message)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NotificationHelper.MONITOR_NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"

        fun start(context: Context, eventId: String?) {
            val intent = Intent(context, LiveMonitorService::class.java)
            if (eventId != null) intent.putExtra(EXTRA_EVENT_ID, eventId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveMonitorService::class.java))
        }
    }
}
