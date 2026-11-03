package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class AsoulParseException(message: String) : IllegalArgumentException(message)

object AsoulParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val islandPattern = Regex("<astro-island\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val componentPattern = Regex(
        "component-export\\s*=\\s*[\"']FilterableCalendar[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val doublePropsPattern = Regex("\\bprops\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val singlePropsPattern = Regex("\\bprops\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
    private val numericEntityPattern = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")
    private val beijingZone = ZoneId.of("Asia/Shanghai")

    fun parse(document: String, members: List<MemberConfig>): List<LiveEvent> {
        val props = islandPattern.findAll(document)
            .map { it.value }
            .firstOrNull(componentPattern::containsMatchIn)
            ?.let { tag ->
                doublePropsPattern.find(tag)?.groupValues?.get(1)
                    ?: singlePropsPattern.find(tag)?.groupValues?.get(1)
            }
            ?: throw AsoulParseException("未找到 FilterableCalendar initialEvents")

        val rawProps = json.parseToJsonElement(decodeHtmlEntities(props))
        val decoded = solidDeserialize(JsonArray(listOf(JsonPrimitive(0), rawProps))).jsonObject
        val payloadEvents = decoded["initialEvents"]?.jsonArray
            ?: throw AsoulParseException("日程页面缺少 initialEvents")
        val membersById = members.associateBy { it.id }

        return payloadEvents.mapNotNull { element ->
            val payload = element as? JsonObject ?: return@mapNotNull null
            if (payload.string("format") != "live") return@mapNotNull null
            val host = payload.string("host").orEmpty()
            val performers = payload["performers"]
                ?.let { runCatching { it.jsonArray.mapNotNull { value -> value.jsonPrimitive.contentOrNull } }.getOrNull() }
                .orEmpty()
            val member = membersById[host]
                ?.takeIf { it.enabled && it.monitorLive }
                ?: performers.asSequence()
                    .mapNotNull(membersById::get)
                    .firstOrNull { it.enabled && it.monitorLive }
                ?: return@mapNotNull null
            val sourceId = payload.string("sourceId").orEmpty()
            val updateSourceId = payload.string("updateSourceId")?.takeIf { it.isNotBlank() }
            val dynamicId = updateSourceId ?: sourceId
            LiveEvent(
                id = payload.string("id") ?: return@mapNotNull null,
                title = payload.string("title").orEmpty(),
                description = payload.string("description").orEmpty(),
                type = payload.string("type").orEmpty(),
                format = "live",
                startEpochMillis = parseTime(payload.string("startTime") ?: return@mapNotNull null),
                host = host,
                performers = performers,
                status = payload.string("status") ?: "published",
                sourceId = sourceId,
                updateSourceId = updateSourceId,
                dynamicUrl = dynamicId.takeIf { it.isNotBlank() }
                    ?.let { "https://www.bilibili.com/opus/$it" },
                liveUrl = member.liveUrl.takeIf { it.isNotBlank() },
                color = member.color.uppercase(),
            )
        }.sortedBy(LiveEvent::startEpochMillis)
    }

    private fun solidDeserialize(value: JsonElement): JsonElement {
        if (value !is JsonArray || value.size != 2) return value
        val tag = value[0].jsonPrimitive.intOrNull ?: return value
        val payload = value[1]
        return when (tag) {
            0 -> if (payload is JsonObject) {
                JsonObject(payload.mapValues { (_, child) -> solidDeserialize(child) })
            } else {
                payload
            }
            1 -> if (payload is JsonArray) JsonArray(payload.map(::solidDeserialize)) else payload
            3 -> payload
            else -> payload
        }
    }

    private fun parseTime(value: String): Long {
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(value).atZone(beijingZone).toInstant().toEpochMilli() }
            .getOrElse { throw AsoulParseException("无法解析日程时间：$value") }
    }

    internal fun decodeHtmlEntities(value: String): String {
        var decoded = value
        repeat(2) {
            val replaced = decoded
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
            decoded = numericEntityPattern.replace(replaced) { match ->
                val token = match.groupValues[1]
                val codePoint = if (token.startsWith("x", ignoreCase = true)) {
                    token.drop(1).toIntOrNull(16)
                } else {
                    token.toIntOrNull()
                }
                codePoint?.let(Character::toChars)?.concatToString() ?: match.value
            }
        }
        return decoded
    }

    private fun JsonObject.string(key: String): String? {
        val value = this[key] ?: return null
        if (value === JsonNull) return null
        return value.jsonPrimitive.contentOrNull
    }
}
