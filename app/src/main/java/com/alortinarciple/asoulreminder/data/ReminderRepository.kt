package com.alortinarciple.asoulreminder.data

import android.content.Context
import android.util.Log
import com.alortinarciple.asoulreminder.BuildConfig
import com.alortinarciple.asoulreminder.background.BackgroundScheduler
import com.alortinarciple.asoulreminder.background.MonitorSchedule
import com.alortinarciple.asoulreminder.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AppState(
    val settings: AppSettings,
    val cache: AppCache,
    val hasCookie: Boolean,
    val cookieExpired: Boolean = false,
    val refreshing: Boolean = false,
    val statusMessage: String = "读取本地缓存",
)

class ReminderRepository(
    private val context: Context,
    private val preferences: AppPreferences,
    private val network: NetworkClient,
    private val notifications: NotificationHelper,
) {
    private val refreshMutex = Mutex()
    private val wbiMutex = Mutex()
    private var wbiMixinKey: String? = null
    private var wbiFetchedAtMillis: Long = 0L
    private val _state = MutableStateFlow(
        AppState(
            settings = preferences.loadSettings(),
            cache = preferences.loadCache(),
            hasCookie = preferences.hasBilibiliCookie(),
            cookieExpired = preferences.cookieExpired(),
        ),
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** WebView 扫码登录成功后写入新 Cookie：保留旧指纹字段，清空失效标记并触发复验。 */
    fun onCookieUpdated(rawCookie: String) {
        val merged = BilibiliLogin.merge(
            preferences.bilibiliCookie(),
            BilibiliLogin.loginCookies(rawCookie),
        )
        if (!BilibiliLogin.hasLoginCookie(merged)) return
        preferences.saveBilibiliCookie(merged)
        preferences.setCookieExpired(false)
        preferences.recordCookieCheck("", 0L)
        _state.value = _state.value.copy(
            hasCookie = true,
            cookieExpired = false,
            statusMessage = "B 站 Cookie 已更新",
        )
    }

    /**
     * 校验 Cookie 是否仍然有效：30 分钟内复用上次结论，`force` 强制联网复验。
     * 只有 nav 返回 -101（或 code==0 且 isLogin=false）才算失效，网络异常记为未知。
     */
    suspend fun verifyCookie(force: Boolean = false): BilibiliLogin.CookieStatus {
        val cookie = preferences.bilibiliCookie()
        if (cookie.isBlank()) return BilibiliLogin.CookieStatus(BilibiliLogin.Status.MISSING)
        val now = System.currentTimeMillis()
        val cached = statusFromName(preferences.cookieStatus())
        val fresh = now - preferences.cookieCheckedAtMillis() < BilibiliLogin.COOKIE_CHECK_INTERVAL_MILLIS
        if (!force && fresh && cached != null) {
            return BilibiliLogin.CookieStatus(cached)
        }
        val result = runCatching {
            BilibiliLogin.parseNav(
                network.getJson(
                    BilibiliLogin.NAV_URL,
                    mapOf("Cookie" to cookie, "Referer" to BilibiliLogin.HOME_URL),
                ),
            )
        }.getOrElse { BilibiliLogin.CookieStatus(BilibiliLogin.Status.UNKNOWN) }
        preferences.recordCookieCheck(result.status.name.lowercase(), now)
        when (result.status) {
            BilibiliLogin.Status.EXPIRED -> markCookieExpired()
            BilibiliLogin.Status.VALID -> clearCookieExpired()
            else -> Unit
        }
        return result
    }

    fun markCookieExpired() {
        preferences.setCookieExpired(true)
        if (_state.value.cookieExpired) return
        _state.value = _state.value.copy(
            cookieExpired = true,
            statusMessage = BilibiliLogin.COOKIE_EXPIRED_MESSAGE,
        )
    }

    fun clearCookieExpired() {
        preferences.setCookieExpired(false)
        if (!_state.value.cookieExpired) return
        _state.value = _state.value.copy(cookieExpired = false)
    }

    private fun statusFromName(name: String): BilibiliLogin.Status? =
        BilibiliLogin.Status.values().firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun updateSettings(settings: AppSettings) {
        preferences.saveSettings(settings)
        _state.value = _state.value.copy(settings = settings, statusMessage = "设置已保存")
        BackgroundScheduler.schedulePeriodic(context, settings)
        BackgroundScheduler.applyMonitorMode(context, settings)
        BackgroundScheduler.scheduleLiveWindows(context, _state.value.cache.events, settings)
    }

    fun importDesktopConfig(text: String) {
        val settings = preferences.importDesktopConfig(text)
        _state.value = _state.value.copy(
            settings = settings,
            hasCookie = preferences.hasBilibiliCookie(),
            cookieExpired = preferences.cookieExpired(),
            statusMessage = "桌面配置已导入",
        )
        BackgroundScheduler.schedulePeriodic(context, settings)
        BackgroundScheduler.applyMonitorMode(context, settings)
        BackgroundScheduler.scheduleLiveWindows(context, _state.value.cache.events, settings)
    }

    suspend fun refreshAll(forceSchedule: Boolean = true) = refreshMutex.withLock {
        updateState(refreshing = true, message = "正在刷新日程、动态和直播间")
        val errors = mutableListOf<String>()
        if (forceSchedule || scheduleRefreshDue()) {
            runCatching { refreshScheduleInternal() }.onFailure { errors += readableError("日程", it) }
        }
        runCatching { refreshDynamicsInternal() }.onFailure { errors += readableError("动态", it) }
        runCatching { refreshLiveStatusesInternal() }.onFailure { errors += readableError("直播间", it) }
        val message = if (errors.isEmpty()) "刷新完成" else errors.joinToString("；")
        val cache = _state.value.cache.copy(lastError = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
        saveCache(cache)
        updateState(refreshing = false, message = message)
    }

    suspend fun refreshSchedule() = refreshMutex.withLock {
        updateState(refreshing = true, message = "正在刷新日程")
        runCatching { refreshScheduleInternal() }
            .onSuccess { updateState(refreshing = false, message = "日程已刷新") }
            .onFailure { error ->
                recordScheduleFailure()
                updateState(refreshing = false, message = readableError("日程", error))
            }
    }

    suspend fun refreshDynamics() = refreshMutex.withLock {
        updateState(refreshing = true, message = "正在刷新动态")
        runCatching { refreshDynamicsInternal() }
            .onSuccess { updateState(refreshing = false, message = "动态已刷新") }
            .onFailure { updateState(refreshing = false, message = readableError("动态", it)) }
    }

    suspend fun refreshLiveStatuses() = refreshMutex.withLock {
        updateState(refreshing = true, message = "正在刷新直播间")
        runCatching { refreshLiveStatusesInternal() }
            .onSuccess { updateState(refreshing = false, message = "直播间已刷新") }
            .onFailure { updateState(refreshing = false, message = readableError("直播间", it)) }
    }

    suspend fun refreshMemberLive(memberId: String): LiveRoomStatus? = refreshMutex.withLock {
        val member = _state.value.settings.members.firstOrNull { it.id == memberId } ?: return@withLock null
        val status = fetchLiveStatus(member)
        val cache = _state.value.cache
        val statuses = cache.liveStatuses + (member.id to status)
        val sessions = cache.notifiedLiveSessions.toMutableMap()
        val notifiedSession = sessions[member.id]
        val shouldNotify = _state.value.settings.notificationsEnabled &&
            LiveNotificationRules.shouldNotifyLive(notifiedSession, status)
        if (shouldNotify) {
            sessions[member.id] = LiveNotificationRules.sessionKey(status)
        } else if (LiveNotificationRules.clearSessionAfterOffline(notifiedSession) && !status.isLive) {
            sessions.remove(member.id)
        }
        saveCache(
            cache.copy(
                liveStatuses = statuses,
                notifiedLiveSessions = sessions,
                lastLiveRefreshEpochMillis = System.currentTimeMillis(),
            ),
        )
        if (shouldNotify) {
            notifications.notifyLive(
                "${member.name}正在直播",
                status.title.ifBlank { "直播已经开始" },
                member.liveUrl,
                _state.value.settings.headsUpLive,
            )
        }
        status
    }

    suspend fun refreshMemberDynamics(memberId: String): List<DynamicItem> {
        val member = _state.value.settings.members.firstOrNull { it.id == memberId } ?: return emptyList()
        if (!member.enabled || !member.monitorDynamic || member.uid.isBlank() || !preferences.hasBilibiliCookie()) {
            return emptyList()
        }
        val fetched = try {
            fetchDynamics(member)
        } catch (error: Throwable) {
            if (error is BilibiliApiException && BilibiliLogin.isExpiredCode(error.code)) {
                markCookieExpired()
            }
            throw error
        }
        mergeDynamicResult(member, fetched)
        return fetched
    }

    fun addScheduledReminders(items: List<ScheduledReminder>) {
        val merged = (_state.value.cache.scheduledReminders + items)
            .filter { it.triggerEpochMillis > System.currentTimeMillis() }
            .distinctBy(ScheduledReminder::key)
        saveCache(_state.value.cache.copy(scheduledReminders = merged))
    }

    /**
     * 检查 GitHub 上是否有更新版本。
     * [silent] 为自动检查：24 小时内已查过就直接跳过，返回 null 表示无需提示。
     */
    suspend fun checkUpdate(silent: Boolean = false): AppRelease? {
        val now = System.currentTimeMillis()
        if (silent && now - preferences.updateCheckedAtMillis() < AppUpdater.UPDATE_CHECK_INTERVAL_MILLIS) return null
        val release = fetchLatestRelease()
        preferences.recordUpdateCheck(now)
        return if (AppUpdater.isNewer(release.tag, BuildConfig.VERSION_NAME)) release else null
    }

    /**
     * 先读 releases.atom（不消耗 api.github.com 的按 IP 限流额度），失败再退回官方 API。
     * 两条路都失败时抛出最后一条错误。
     */
    private suspend fun fetchLatestRelease(): AppRelease {
        val feedError = try {
            return AppUpdater.parseFeed(network.getText(AppUpdater.RELEASES_FEED_URL))
        } catch (error: Throwable) {
            error
        }
        try {
            val payload = network.getJson(
                AppUpdater.LATEST_RELEASE_URL,
                mapOf("Accept" to "application/vnd.github+json"),
            )
            return AppUpdater.parseRelease(payload)
        } catch (error: UpdateException) {
            throw error
        } catch (error: Throwable) {
            Log.w("ReminderRepository", "releases.atom 与 GitHub API 均不可用", feedError)
            throw UpdateException("无法连接 GitHub：${error.message ?: error.javaClass.simpleName}")
        }
    }

    /** 下载安装包到 cacheDir/updates，返回本地文件；[progress] 回调 0..100 的整数进度。 */
    suspend fun downloadUpdate(
        release: AppRelease,
        progress: ((Int) -> Unit)? = null,
    ): File {
        val target = File(context.cacheDir, "updates/${release.assetName}")
        target.parentFile?.mkdirs()
        return try {
            network.downloadFile(
                url = release.assetUrl,
                target = target,
                progress = { done, total ->
                    if (total > 0) progress?.invoke(((done * 100) / total).toInt().coerceIn(0, 100))
                },
            )
        } catch (error: Throwable) {
            throw UpdateException("下载失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun scheduleRefreshDue(): Boolean {
        val cache = _state.value.cache
        return MonitorSchedule.scheduleDue(
            cache.lastScheduleRefreshEpochMillis,
            _state.value.settings.scheduleIntervalMinutes,
            System.currentTimeMillis(),
            cache.scheduleFailStreak,
        )
    }

    /** 记录一次日程抓取失败，供后台按失败次数退避（与 Windows 端一致）。 */
    private fun recordScheduleFailure() {
        val cache = _state.value.cache
        val now = System.currentTimeMillis()
        saveCache(
            cache.copy(
                scheduleFailStreak = cache.scheduleFailStreak + 1,
                // 失败也要落时间戳：否则每分钟一次的 tick 都会认为「从未刷新」而立刻重试。
                lastScheduleRefreshEpochMillis = now,
            ),
        )
    }

    private suspend fun refreshScheduleInternal() {
        val settings = _state.value.settings
        val document = try {
            network.getText(settings.asoulCalendarUrl)
        } catch (error: HttpStatusException) {
            if (error.statusCode != 403) throw error
            network.getText(settings.asoulHomeUrl)
        }
        val events = AsoulParser.parse(document, settings.members)
        val now = System.currentTimeMillis()
        val oldEvents = _state.value.cache.events.associateBy(LiveEvent::id)
        val notified = _state.value.cache.notifiedScheduleKeys.toMutableMap()
        if (oldEvents.isNotEmpty() && settings.notificationsEnabled) {
            events.forEach { event ->
                val old = oldEvents[event.id]
                val changed = old == null || old.fingerprint != event.fingerprint
                if (!changed) return@forEach
                // 站点重新发布日程页时会把几天前的日程放回列表，超过宽限期的历史条目不再提醒。
                if (!ScheduleNotificationRules.isNotifiable(event.startEpochMillis, now)) return@forEach
                val key = ScheduleNotificationRules.dedupeKey(event.id, event.fingerprint)
                if (notified.containsKey(key)) return@forEach
                notifications.notifySchedule(
                    if (old == null) "新增直播日程" else "直播日程有变动",
                    eventSummary(event),
                    event.dynamicUrl,
                    settings.headsUpSchedule,
                )
                notified[key] = now
            }
        }
        val cache = _state.value.cache.copy(
            events = events,
            notifiedScheduleKeys = ScheduleNotificationRules.prune(notified, now),
            lastScheduleRefreshEpochMillis = now,
            scheduleFailStreak = 0,
        )
        saveCache(cache)
        BackgroundScheduler.scheduleLiveWindows(context, events, settings)
    }

    private suspend fun refreshDynamicsInternal() {
        val members = _state.value.settings.members.filter {
            it.enabled && it.monitorDynamic && it.uid.isNotBlank()
        }
        if (members.isEmpty()) return
        if (!preferences.hasBilibiliCookie()) {
            throw IllegalStateException(BilibiliLogin.COOKIE_MISSING_MESSAGE)
        }
        if (verifyCookie().status == BilibiliLogin.Status.EXPIRED) {
            throw IllegalStateException(BilibiliLogin.COOKIE_EXPIRED_MESSAGE)
        }
        val failures = mutableListOf<String>()
        members.forEach { member ->
            runCatching { fetchDynamics(member) }
                .onSuccess { mergeDynamicResult(member, it) }
                .onFailure { error ->
                    if (error is BilibiliApiException && BilibiliLogin.isExpiredCode(error.code)) {
                        markCookieExpired()
                    }
                    failures += "${member.name}: ${error.message ?: error.javaClass.simpleName}"
                }
        }
        val cache = _state.value.cache.copy(lastDynamicRefreshEpochMillis = System.currentTimeMillis())
        saveCache(cache)
        if (failures.isNotEmpty()) throw IllegalStateException(failures.joinToString("；"))
    }

    private suspend fun refreshLiveStatusesInternal() {
        val members = _state.value.settings.members.filter {
            it.enabled && it.monitorLive && it.liveUrl.isNotBlank()
        }
        val statuses = _state.value.cache.liveStatuses.toMutableMap()
        val sessions = _state.value.cache.notifiedLiveSessions.toMutableMap()
        val pendingLiveNotifications = mutableListOf<Pair<MemberConfig, LiveRoomStatus>>()
        val failures = mutableListOf<String>()
        members.forEach { member ->
            runCatching { fetchLiveStatus(member) }
                .onSuccess { status ->
                    statuses[member.id] = status
                    val notifiedSession = sessions[member.id]
                    if (
                        _state.value.settings.notificationsEnabled &&
                        LiveNotificationRules.shouldNotifyLive(notifiedSession, status)
                    ) {
                        sessions[member.id] = LiveNotificationRules.sessionKey(status)
                        pendingLiveNotifications += member to status
                    } else if (
                        LiveNotificationRules.clearSessionAfterOffline(notifiedSession) && !status.isLive
                    ) {
                        sessions.remove(member.id)
                    }
                }
                .onFailure { failures += "${member.name}: ${it.message ?: it.javaClass.simpleName}" }
        }
        saveCache(
            _state.value.cache.copy(
                liveStatuses = statuses,
                notifiedLiveSessions = sessions,
                lastLiveRefreshEpochMillis = System.currentTimeMillis(),
            ),
        )
        pendingLiveNotifications.forEach { (member, status) ->
            notifications.notifyLive(
                "${member.name}正在直播",
                status.title.ifBlank { "直播已经开始" },
                member.liveUrl,
                _state.value.settings.headsUpLive,
            )
        }
        if (failures.isNotEmpty()) throw IllegalStateException(failures.joinToString("；"))
    }

    private suspend fun fetchDynamics(member: MemberConfig): List<DynamicItem> {
        val url = buildDynamicUrl(member)
        val payload = network.getJson(
            url,
            mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Origin" to "https://space.bilibili.com",
                "Referer" to member.dynamicUrl,
                "Cookie" to preferences.bilibiliCookie(),
            ),
        )
        return BilibiliParser.parseDynamics(payload, member, _state.value.settings.dynamicFetchLimit)
    }

    private suspend fun buildDynamicUrl(member: MemberConfig): String {
        val params = mapOf(
            "host_mid" to member.uid,
            "timezone_offset" to "-480",
            "features" to "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote," +
                "decorationCard,forwardListHidden,ugcDelete,onlyfansQaCard",
        )
        val mixin = ensureWbiMixinKey()
        val query = if (mixin != null) {
            WbiSign.signedQuery(params, mixin, System.currentTimeMillis() / 1000)
        } else {
            params.entries.joinToString("&") { (key, value) ->
                "${WbiSign.encodeValue(key)}=${WbiSign.encodeValue(value)}"
            }
        }
        return "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?$query"
    }

    /** 获取 wbi 签名 mixin key（nav 每日轮换，本地最多缓存 30 分钟；失败时返回 null 走未签名回退）。 */
    private suspend fun ensureWbiMixinKey(): String? = wbiMutex.withLock {
        val cached = wbiMixinKey
        if (cached != null && System.currentTimeMillis() - wbiFetchedAtMillis < WbiSign.KEY_TTL_MILLIS) {
            return cached
        }
        runCatching {
            val wbiImg = network.getJson(WbiSign.NAV_URL)["data"]
                ?.jsonObject?.get("wbi_img")?.jsonObject
            val imgKey = WbiSign.keyFromUrl(wbiImg?.get("img_url")?.jsonPrimitive?.contentOrNull ?: "")
            val subKey = WbiSign.keyFromUrl(wbiImg?.get("sub_url")?.jsonPrimitive?.contentOrNull ?: "")
            check(imgKey.isNotEmpty() && subKey.isNotEmpty()) { "nav 未返回 wbi_img 密钥" }
            WbiSign.mixinKey(imgKey, subKey).also {
                wbiMixinKey = it
                wbiFetchedAtMillis = System.currentTimeMillis()
            }
        }.getOrNull()
    }

    private suspend fun fetchLiveStatus(member: MemberConfig): LiveRoomStatus {
        val roomId = BilibiliParser.roomId(member.liveUrl)
            ?: throw IllegalArgumentException("直播间地址无效")
        val payload = network.getJson(
            "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$roomId",
            mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Referer" to member.liveUrl,
            ),
        )
        return BilibiliParser.parseLiveStatus(payload, member, System.currentTimeMillis())
    }

    private fun mergeDynamicResult(member: MemberConfig, fetched: List<DynamicItem>) {
        if (fetched.isEmpty()) {
            // 空结果可能是接口风控或抖动，保留缓存，避免把成员的动态整体清掉。
            return
        }
        val cache = _state.value.cache
        val seen = cache.seenDynamicIds[member.id].orEmpty()
        if (seen.isNotEmpty() && _state.value.settings.notificationsEnabled) {
            fetched.asReversed().filterNot { it.id in seen }.forEach { item ->
                if (
                    NotificationRules.shouldSuppressLiveStartDynamic(
                        member.id,
                        item,
                        cache.events,
                        _state.value.settings.suppressLiveDynamicMinutes,
                    )
                ) return@forEach
                notifications.notifyDynamic(
                    "${member.name}发布了新动态",
                    item.text.take(180),
                    item.url,
                    _state.value.settings.headsUpDynamic,
                )
            }
        }
        val nextSeen = cache.seenDynamicIds + (member.id to (seen + fetched.map(DynamicItem::id)).takeLastSet(100))
        val nextDynamics = (cache.dynamics.filterNot { it.memberId == member.id } + fetched)
            .sortedWith(compareByDescending<DynamicItem> { it.publishedEpochMillis ?: 0L }.thenByDescending { it.id })
        saveCache(cache.copy(dynamics = nextDynamics, seenDynamicIds = nextSeen))
    }

    private fun saveCache(cache: AppCache) {
        preferences.saveCache(cache)
        _state.value = _state.value.copy(cache = cache)
    }

    private fun updateState(refreshing: Boolean, message: String) {
        _state.value = _state.value.copy(refreshing = refreshing, statusMessage = message)
    }

    private fun readableError(label: String, error: Throwable): String {
        return "$label：${error.message ?: error.javaClass.simpleName}"
    }

    private fun eventSummary(event: LiveEvent): String {
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"))
        val start = Instant.ofEpochMilli(event.startEpochMillis)
        return "${formatter.format(start)} ${weekdayText(start)} ${event.title}"
    }

    private fun <T> Collection<T>.takeLastSet(limit: Int): Set<T> = this.toList().takeLast(limit).toSet()
}
