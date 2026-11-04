package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Locale

/** 检查更新或下载安装包失败，消息可直接展示给用户。 */
class UpdateException(message: String) : Exception(message)

/** 一次 GitHub Release 里与安卓端相关的信息。 */
data class AppRelease(
    val tag: String,
    val name: String,
    val notes: String,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long,
    val htmlUrl: String,
) {
    val versionText: String get() = tag.removePrefix("v").removePrefix("V")

    val sizeText: String
        get() = if (assetSize > 0) {
            String.format(Locale.US, "%.1f MB", assetSize / 1024.0 / 1024.0)
        } else {
            "大小未知"
        }
}

/**
 * 检查更新相关的纯逻辑：版本号比较与 Release 解析。
 * 与桌面端 asoul_reminder/updater.py 保持同一套语义，网络与文件操作留在调用方。
 */
object AppUpdater {
    const val ANDROID_REPO = "AlortinArciple/Asoul-Reminder-Android"
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/$ANDROID_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$ANDROID_REPO/releases"
    const val APK_ASSET_NAME = "AsoulReminder-Android-debug.apk"

    /** 启动时的静默检查间隔；手动检查不受限制。 */
    const val UPDATE_CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L

    /** 版本号只取各段开头的数字，遇到第一段非数字就停止，与桌面端解析保持一致。 */
    fun parseVersion(text: String?): List<Int> {
        val raw = (text ?: "").trim().removePrefix("v").removePrefix("V")
        if (raw.isEmpty()) return emptyList()
        val parts = mutableListOf<Int>()
        raw.split('.', '+', '-', '_', ' ').forEach { segment ->
            val digits = segment.takeWhile(Char::isDigit)
            if (digits.isEmpty()) return parts
            parts += digits.toIntOrNull() ?: return parts
        }
        return parts
    }

    fun isNewer(remote: String?, current: String?): Boolean {
        val remoteParts = parseVersion(remote)
        val currentParts = parseVersion(current)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false
        val size = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until size) {
            val left = remoteParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    fun parseRelease(payload: JsonObject, assetName: String = APK_ASSET_NAME): AppRelease {
        val tag = payload["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (tag.isEmpty()) throw UpdateException("GitHub 返回的版本信息缺少版本号")
        val assets = runCatching { payload["assets"]?.jsonArray }.getOrNull().orEmpty()
        val asset = assets
            .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == assetName }
            ?: assets
                .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            ?: throw UpdateException("该版本没有可下载的 APK")
        val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (url.isEmpty()) throw UpdateException("该版本的安装包没有下载地址")
        return AppRelease(
            tag = tag,
            name = payload["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            notes = payload["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            assetName = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            assetUrl = url,
            assetSize = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            htmlUrl = payload["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}
