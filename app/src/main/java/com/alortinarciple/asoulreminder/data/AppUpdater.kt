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

    /**
     * 优先读 releases.atom：GitHub 的 api.github.com 按出口 IP 限流 60 次/小时，
     * 共享代理下经常被他人耗尽，而 atom 源与下载链接都不消耗额度。
     */
    const val RELEASES_FEED_URL = "https://github.com/$ANDROID_REPO/releases.atom"
    const val DOWNLOAD_URL_TEMPLATE = "https://github.com/$ANDROID_REPO/releases/download/{tag}/{asset}"
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

    /**
     * 从 releases.atom 里解析最新版本。feed 里没有附件大小，先记 0（展示为「大小未知」）。
     * 用文本切分而不是 XmlPullParser，既能覆盖真实 feed，也便于在 JVM 单测里直接跑。
     */
    fun parseFeed(text: String?, assetName: String = APK_ASSET_NAME): AppRelease {
        val raw = text.orEmpty()
        if (raw.isBlank()) throw UpdateException("Release 源返回了空内容")
        val firstEntry = ENTRY_REGEX.find(raw)?.value ?: throw UpdateException("Release 源里没有任何版本")

        val id = ID_REGEX.find(firstEntry)?.groupValues?.getOrNull(1).orEmpty().trim()
        // id 形如 tag:github.com,2008:Repository/1444186488/v0.5.0，末尾即 tag。
        val tag = id.substringAfterLast('/').trim()
        if (tag.isEmpty()) throw UpdateException("Release 源缺少版本号")

        val title = TITLE_REGEX.find(firstEntry)?.groupValues?.getOrNull(1).orEmpty().trim()
        val link = LINK_ALT_REGEX.find(firstEntry)?.groupValues?.getOrNull(1).orEmpty().trim()
        val content = CONTENT_REGEX.find(firstEntry)?.groupValues?.getOrNull(1).orEmpty()
        return AppRelease(
            tag = tag,
            name = title.ifEmpty { tag },
            notes = htmlToText(content),
            assetName = assetName,
            assetUrl = DOWNLOAD_URL_TEMPLATE
                .replace("{tag}", tag)
                .replace("{asset}", assetName),
            assetSize = 0L,
            htmlUrl = link.ifEmpty { RELEASES_PAGE_URL },
        )
    }

    /** 把 Release 说明里的 HTML 片段压成可读纯文本。 */
    fun htmlToText(markup: String?): String {
        var text = unescapeHtml(markup.orEmpty())
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</(p|div|li|h[1-6])>"), "\n")
        text = text.replace(Regex("(?i)<li[^>]*>"), "- ")
        text = text.replace(Regex("<[^>]+>"), "")
        text = unescapeHtml(text)
        val lines = text.split('\n').map { it.trimEnd() }
        return lines.dropWhile(String::isEmpty).dropLastWhile(String::isEmpty).joinToString("\n")
    }

    private fun unescapeHtml(text: String): String {
        if ('&' !in text) return text
        return ENTITY_REGEX.replace(text) { match ->
            val entity = match.groupValues[1]
            when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity == "nbsp" -> " "
                entity.startsWith("#x", ignoreCase = true) ->
                    entity.drop(2).toIntOrNull(16)?.let(::codePointToString) ?: match.value
                entity.startsWith("#") ->
                    entity.drop(1).toIntOrNull()?.let(::codePointToString) ?: match.value
                else -> match.value
            }
        }
    }

    private fun codePointToString(codePoint: Int): String? =
        runCatching { String(Character.toChars(codePoint)) }.getOrNull()

    private val ENTITY_REGEX = Regex("&(#?[0-9A-Za-z]+);")

    private val ENTRY_REGEX = Regex("(?s)<entry>.*?</entry>")
    private val ID_REGEX = Regex("(?s)<id>(.*?)</id>")
    private val TITLE_REGEX = Regex("(?s)<title>(.*?)</title>")
    private val CONTENT_REGEX = Regex("(?s)<content[^>]*>(.*?)</content>")
    private val LINK_ALT_REGEX =
        Regex("(?s)<link[^>]*rel=\"alternate\"[^>]*href=\"([^\"]+)\"")

    fun parseRelease(payload: JsonObject, assetName: String = APK_ASSET_NAME): AppRelease {
        val tag = payload["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (tag.isEmpty()) {
            // GitHub 出错时返回 {"message": "..."}，例如公开接口按 IP 限流。
            val hint = payload["message"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            throw UpdateException(
                if (hint.isNotEmpty()) "GitHub 返回错误：$hint" else "GitHub 返回的版本信息缺少版本号",
            )
        }
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
