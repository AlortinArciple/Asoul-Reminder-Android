package com.alortinarciple.asoulreminder.data

import android.content.Context
import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secretStore = SecretStore(appContext)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadSettings(): AppSettings = preferences.getString(SETTINGS_KEY, null)
        ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
        ?: AppSettings()

    fun saveSettings(settings: AppSettings) {
        preferences.edit().putString(SETTINGS_KEY, json.encodeToString(settings)).apply()
    }

    fun loadCache(): AppCache = preferences.getString(CACHE_KEY, null)
        ?.let { runCatching { json.decodeFromString<AppCache>(it) }.getOrNull() }
        ?: AppCache()

    fun saveCache(cache: AppCache) {
        preferences.edit().putString(CACHE_KEY, json.encodeToString(cache)).apply()
    }

    fun bilibiliCookie(): String = secretStore.loadCookie()

    fun hasBilibiliCookie(): Boolean = secretStore.hasCookie()

    fun saveBilibiliCookie(cookie: String) = secretStore.saveCookie(cookie)

    fun cookieExpired(): Boolean = preferences.getBoolean(COOKIE_EXPIRED_KEY, false)

    fun setCookieExpired(expired: Boolean) {
        preferences.edit().putBoolean(COOKIE_EXPIRED_KEY, expired).apply()
    }

    /** 上一次 nav 校验结论：valid / expired / unknown。 */
    fun cookieStatus(): String = preferences.getString(COOKIE_STATUS_KEY, "").orEmpty()

    fun updateCheckedAtMillis(): Long = preferences.getLong(UPDATE_CHECKED_AT_KEY, 0L)

    fun recordUpdateCheck(checkedAtMillis: Long) {
        preferences.edit().putLong(UPDATE_CHECKED_AT_KEY, checkedAtMillis).apply()
    }

    fun cookieCheckedAtMillis(): Long = preferences.getLong(COOKIE_CHECKED_AT_KEY, 0L)

    fun recordCookieCheck(status: String, checkedAtMillis: Long) {
        preferences.edit()
            .putString(COOKIE_STATUS_KEY, status)
            .putLong(COOKIE_CHECKED_AT_KEY, checkedAtMillis)
            .apply()
    }

    fun importBundledConfigIfPresent(): Boolean {
        val rawText = runCatching {
            appContext.assets.open(BUNDLED_CONFIG_ASSET)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }.getOrNull() ?: return false
        val fingerprint = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(rawText.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
        if (preferences.getString(BUNDLED_CONFIG_HASH_KEY, null) == fingerprint) return false
        return runCatching {
            importDesktopConfig(rawText)
            check(preferences.edit().putString(BUNDLED_CONFIG_HASH_KEY, fingerprint).commit())
            true
        }.getOrDefault(false)
    }

    fun importDesktopConfig(rawText: String): AppSettings {
        val root = json.parseToJsonElement(rawText).jsonObject
        val current = loadSettings()
        val importedMembers = parseMembers(root["members"] as? JsonArray, current.members)
        val settings = current.copy(
            asoulCalendarUrl = root.string("asoul_calendar_url") ?: current.asoulCalendarUrl,
            asoulHomeUrl = root.string("asoul_home_url") ?: current.asoulHomeUrl,
            scheduleIntervalMinutes = (root.int("check_interval_minutes") ?: current.scheduleIntervalMinutes)
                .coerceAtLeast(30),
            dynamicIntervalMinutes = (root.int("dynamic_check_interval_minutes") ?: current.dynamicIntervalMinutes)
                .coerceAtLeast(15),
            dynamicHighFrequencyIntervalMinutes = root.int("dynamic_high_frequency_interval_minutes")
                ?: current.dynamicHighFrequencyIntervalMinutes,
            dynamicHighFrequencyMaxMinutes = root.int("dynamic_high_frequency_max_minutes")
                ?: current.dynamicHighFrequencyMaxMinutes,
            liveOpenIntervalSeconds = root.int("live_status_open_check_interval_seconds")
                ?: current.liveOpenIntervalSeconds,
            liveCloseIntervalMinutes = root.int("live_status_close_check_interval_minutes")
                ?: current.liveCloseIntervalMinutes,
            dynamicFetchLimit = root.int("dynamic_fetch_limit") ?: current.dynamicFetchLimit,
            suppressLiveDynamicMinutes = root.int("suppress_live_start_dynamic_window_minutes")
                ?: current.suppressLiveDynamicMinutes,
            remindBeforeMinutes = root["live_remind_before_minutes"]
                ?.let { element -> runCatching { element.jsonArray.mapNotNull { it.jsonPrimitive.intOrNull } }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: current.remindBeforeMinutes,
            notificationsEnabled = root.boolean("notifications_enabled") ?: current.notificationsEnabled,
            members = importedMembers,
        )
        root.string("bilibili_cookie")?.takeIf { it.isNotBlank() }?.let { cookie ->
            secretStore.saveCookie(cookie)
            setCookieExpired(false)
            recordCookieCheck("", 0L)
        }
        saveSettings(settings)
        return settings
    }

    private fun parseMembers(raw: JsonArray?, fallback: List<MemberConfig>): List<MemberConfig> {
        if (raw == null) return fallback
        val previous = fallback.associateBy { it.id }
        return raw.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id")?.lowercase()?.let { if (it == "sinno") "gladys" else it }
                ?: return@mapNotNull null
            val base = previous[id] ?: defaultMembers().firstOrNull { it.id == id } ?: return@mapNotNull null
            base.copy(
                name = item.string("name") ?: base.name,
                enabled = item.boolean("enabled") ?: base.enabled,
                monitorDynamic = item.boolean("monitor_dynamic") ?: base.monitorDynamic,
                monitorLive = item.boolean("monitor_live") ?: base.monitorLive,
                color = item.string("color") ?: base.color,
                uid = item.string("uid") ?: base.uid,
                dynamicUrl = item.string("dynamic_url") ?: base.dynamicUrl,
                liveUrl = item.string("live_url") ?: base.liveUrl,
            )
        }.ifEmpty { fallback }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private companion object {
        const val PREFERENCES_NAME = "asoul_preferences"
        const val SETTINGS_KEY = "settings"
        const val CACHE_KEY = "cache"
        const val BUNDLED_CONFIG_ASSET = "default_config.json"
        const val BUNDLED_CONFIG_HASH_KEY = "bundled_config_hash"
        const val COOKIE_EXPIRED_KEY = "bilibili_cookie_expired"
        const val COOKIE_STATUS_KEY = "bilibili_cookie_status"
        const val COOKIE_CHECKED_AT_KEY = "bilibili_cookie_checked_at"
        const val UPDATE_CHECKED_AT_KEY = "update_checked_at"
    }
}
