package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object BilibiliParser {
    private val beijingZone = ZoneId.of("Asia/Shanghai")

    fun parseDynamics(
        root: JsonObject,
        member: MemberConfig,
        limit: Int,
    ): List<DynamicItem> {
        val code = root.int("code")
        if (code != 0) {
            throw BilibiliApiException(
                code ?: -1,
                "B 站动态接口失败：${root.string("message") ?: root.string("msg") ?: "unknown"}",
            )
        }
        val items = root.obj("data")?.array("items").orEmpty()
        return items.take(limit).mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id_str") ?: item.string("id") ?: return@mapNotNull null
            val richText = extractRichText(item)
            DynamicItem(
                id = id,
                memberId = member.id,
                memberName = member.name,
                text = summarize(item),
                url = "https://www.bilibili.com/opus/$id",
                publishedEpochMillis = parsePublishTime(item),
                richText = richText,
            )
        }
    }

    fun parseLiveStatus(root: JsonObject, member: MemberConfig, checkedAt: Long): LiveRoomStatus {
        val code = root.int("code")
        if (code != 0) {
            throw BilibiliApiException(
                code ?: -1,
                "B 站直播接口失败：${root.string("message") ?: root.string("msg") ?: "unknown"}",
            )
        }
        val data = root.obj("data") ?: JsonObject(emptyMap())
        return LiveRoomStatus(
            memberId = member.id,
            memberName = member.name,
            roomId = roomId(member.liveUrl).orEmpty(),
            liveStatus = data.int("live_status") ?: 0,
            title = data.string("title").orEmpty(),
            liveTime = data.string("live_time")?.takeIf { it.isNotBlank() },
            checkedEpochMillis = checkedAt,
        )
    }

    fun roomId(url: String): String? = Regex("live\\.bilibili\\.com/(\\d+)")
        .find(url)
        ?.groupValues
        ?.get(1)

    private fun summarize(item: JsonObject): String {
        val dynamic = item.obj("modules")?.obj("module_dynamic") ?: return "新动态"
        val desc = dynamic["desc"]
        normalizeText(desc).takeIf { it.isNotBlank() }?.let { return it }
        val major = dynamic.obj("major") ?: return "新动态"
        for (key in listOf("opus", "archive", "article", "draw", "live_rcmd")) {
            val block = major.obj(key) ?: continue
            for (field in listOf("title", "desc", "summary")) {
                normalizeText(block[field]).replace(Regex("\\s+"), " ")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
        }
        return "新动态"
    }

    private fun extractRichText(item: JsonObject): List<DynamicTextSegment> {
        val dynamic = item.obj("modules")?.obj("module_dynamic") ?: return emptyList()
        val candidates = buildList<JsonElement> {
            dynamic["desc"]?.let(::add)
            val major = dynamic.obj("major")
            major?.obj("opus")?.get("summary")?.let(::add)
            for (key in listOf("archive", "article", "draw", "live_rcmd")) {
                val block = major?.obj(key) ?: continue
                listOf("desc", "summary", "title").forEach { field -> block[field]?.let(::add) }
            }
        }
        candidates.forEach { candidate ->
            extractSegments(candidate).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return summarize(item).takeIf { it.isNotBlank() }
            ?.let { listOf(DynamicTextSegment("text", it)) }
            .orEmpty()
    }

    private fun extractSegments(element: JsonElement): List<DynamicTextSegment> {
        val desc = element as? JsonObject
            ?: return normalizeText(element).takeIf { it.isNotBlank() }
                ?.let { listOf(DynamicTextSegment("text", it)) }
                .orEmpty()
        val nodes = desc.array("rich_text_nodes")
            ?: return normalizeText(desc).takeIf { it.isNotBlank() }
                ?.let { listOf(DynamicTextSegment("text", it)) }
                .orEmpty()
        val result = mutableListOf<DynamicTextSegment>()
        nodes.forEach { nodeElement ->
            val node = nodeElement as? JsonObject ?: return@forEach
            val text = node.string("orig_text") ?: node.string("text") ?: ""
            val emoji = node.obj("emoji")
            val url = normalizeEmojiUrl(
                emoji?.firstNonBlank("gif_url", "icon_url", "url")
                    ?: node.firstNonBlank("icon_url", "url")
                    ?: "",
            )
            val isEmoji = node.string("type")?.contains("EMOJI") == true ||
                (text.startsWith("[") && text.endsWith("]"))
            val segment = if (isEmoji && url.isNotBlank()) {
                DynamicTextSegment("emoji", text, url)
            } else if (text.isNotEmpty()) {
                DynamicTextSegment("text", text)
            } else {
                null
            }
            if (segment != null && segment.type == "text" && result.lastOrNull()?.type == "text") {
                val previous = result.removeAt(result.lastIndex)
                result += previous.copy(text = previous.text + segment.text)
            } else if (segment != null) {
                result += segment
            }
        }
        return result
    }

    private fun normalizeEmojiUrl(value: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> "https://${value.removePrefix("http://")}" 
        else -> value
    }

    private fun normalizeText(value: JsonElement?): String = when (value) {
        null -> ""
        is JsonPrimitive -> value.contentOrNull.orEmpty().replace("\r\n", "\n").replace("\r", "\n").trim()
        is JsonArray -> value.map(::normalizeText).filter(String::isNotBlank).joinToString("\n")
        is JsonObject -> {
            value.string("text")?.let { return it.trim() }
            value.array("rich_text_nodes")
                ?.mapNotNull { node ->
                    (node as? JsonObject)?.let { it.string("orig_text") ?: it.string("text") }
                }
                ?.joinToString("")
                ?.trim()
                .orEmpty()
        }
        else -> ""
    }

    private fun parsePublishTime(item: JsonObject): Long? {
        val author = item.obj("modules")?.obj("module_author") ?: return null
        author["pub_ts"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }?.let { return it * 1000 }
        val raw = author.string("pub_time")?.trim().orEmpty()
        raw.toLongOrNull()?.takeIf { it > 0 }?.let { return it * 1000 }
        if (raw.isBlank()) return null
        val now = ZonedDateTime.now(beijingZone)
        listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm")
            .forEach { pattern ->
                runCatching {
                    LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern))
                        .atZone(beijingZone)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()?.let { return it }
            }
        Regex("(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})").matchEntire(raw)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val time = LocalTime.of(match.groupValues[3].toInt(), match.groupValues[4].toInt())
            var date = LocalDate.of(now.year, month, day)
            if (Duration.between(now.toInstant(), date.atTime(time).atZone(beijingZone).toInstant()).toDays() > 2) {
                date = date.minusYears(1)
            }
            return date.atTime(time).atZone(beijingZone).toInstant().toEpochMilli()
        }
        Regex("(今天|昨天)\\s*(\\d{1,2}):(\\d{2})").matchEntire(raw)?.let { match ->
            val date = now.toLocalDate().minusDays(if (match.groupValues[1] == "昨天") 1 else 0)
            val time = LocalTime.of(match.groupValues[2].toInt(), match.groupValues[3].toInt())
            return date.atTime(time).atZone(beijingZone).toInstant().toEpochMilli()
        }
        Regex("(\\d+)\\s*(分钟|小时)前").matchEntire(raw)?.let { match ->
            val amount = match.groupValues[1].toLong()
            val duration = if (match.groupValues[2] == "分钟") Duration.ofMinutes(amount) else Duration.ofHours(amount)
            return now.minus(duration).toInstant().toEpochMilli()
        }
        return if (raw == "刚刚") now.toInstant().toEpochMilli() else null
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.firstNonBlank(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this.string(key)?.takeIf(String::isNotBlank) }

    // 非基本类型一律按缺失处理，避免单个异常节点炸掉整个成员的动态解析。
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
}
