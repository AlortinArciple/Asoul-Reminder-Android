package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** B 站接口返回了非 0 的 code，保留原始 code 以便识别 -101 登录失效。 */
class BilibiliApiException(
    val code: Int,
    message: String,
) : IOException(message)

/**
 * 扫码登录（WebView）相关的纯逻辑：Cookie 规范化、合并、失效判定。
 * 与桌面端 asoul_reminder/bilibili_login.py 保持同一套业务语义。
 */
object BilibiliLogin {
    const val LOGIN_URL = "https://passport.bilibili.com/login"
    const val HOME_URL = "https://www.bilibili.com/"
    const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
    const val EXPIRED_CODE = -101
    const val COOKIE_EXPIRED_MESSAGE = "B 站 Cookie 已失效，已跳过动态检查，请重新登录。"
    const val COOKIE_MISSING_MESSAGE = "未配置 B 站 Cookie"
    const val COOKIE_CHECK_INTERVAL_MILLIS = 30 * 60_000L

    val LOGIN_COOKIE_KEYS = listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")

    enum class Status { VALID, EXPIRED, UNKNOWN, MISSING }

    data class CookieStatus(
        val status: Status,
        val uname: String = "",
        val mid: String = "",
    )

    fun normalize(raw: String): String {
        var text = raw.trim()
        if (text.lowercase().startsWith("cookie:")) text = text.substring("cookie:".length)
        return text.split(';', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('=') }
            .joinToString("; ")
    }

    fun cookieMap(cookie: String): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        normalize(cookie).split(';').forEach { pair ->
            val trimmed = pair.trim()
            val index = trimmed.indexOf('=')
            if (index > 0) {
                val key = trimmed.substring(0, index).trim()
                val value = trimmed.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) map[key] = value
            }
        }
        return map
    }

    fun hasLoginCookie(cookie: String): Boolean =
        cookieMap(cookie)["SESSDATA"].orEmpty().isNotBlank()

    /** 只保留登录凭证字段（按原始顺序），WebView 抓到的指纹 Cookie 不会污染已有配置。 */
    fun loginCookies(raw: String): String =
        cookieMap(raw).entries
            .filter { it.key in LOGIN_COOKIE_KEYS }
            .joinToString("; ") { "${it.key}=${it.value}" }

    /** 合并新旧 Cookie：保留旧键顺序与 buvid3/b_nut 等指纹字段，只覆盖更新的凭证。 */
    fun merge(existing: String, updates: String): String {
        val merged = cookieMap(existing)
        cookieMap(updates).forEach { (key, value) -> merged[key] = value }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun isExpiredCode(code: Int?): Boolean = code == EXPIRED_CODE

    fun codeOf(payload: JsonObject): Int? = payload["code"]?.jsonPrimitive?.intOrNull

    /** 解析 x/web-interface/nav：只有 -101 或 code==0 且 isLogin=false 才算失效。 */
    fun parseNav(payload: JsonObject): CookieStatus {
        val code = codeOf(payload)
        val data = payload["data"] as? JsonObject
        val isLogin = data?.get("isLogin")?.jsonPrimitive?.booleanOrNull ?: false
        val uname = data?.get("uname")?.jsonPrimitive?.contentOrNull.orEmpty()
        val mid = data?.get("mid")?.jsonPrimitive?.contentOrNull.orEmpty()
        return when {
            isExpiredCode(code) -> CookieStatus(Status.EXPIRED)
            code == 0 && isLogin -> CookieStatus(Status.VALID, uname, mid)
            code == 0 -> CookieStatus(Status.EXPIRED)
            else -> CookieStatus(Status.UNKNOWN)
        }
    }
}
