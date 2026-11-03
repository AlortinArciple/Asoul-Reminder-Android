package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesDynamicTextEmojiAndPublishTime() {
        val root = json.parseToJsonElement(
            """
            {
              "code": 0,
              "data": {
                "items": [{
                  "id_str": "9988",
                  "modules": {
                    "module_author": {"pub_ts": 1786276800},
                    "module_dynamic": {
                      "desc": {
                        "rich_text_nodes": [
                          {"type":"RICH_TEXT_NODE_TYPE_TEXT","orig_text":"今晚见 "},
                          {"type":"RICH_TEXT_NODE_TYPE_EMOJI","orig_text":"[打call]","emoji":{"icon_url":"//i0.hdslb.com/emoji.png"}}
                        ]
                      }
                    }
                  }
                }]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val item = BilibiliParser.parseDynamics(root, defaultMembers().first(), 3).single()

        assertEquals("9988", item.id)
        assertEquals("今晚见 [打call]", item.text)
        assertEquals(2, item.richText.size)
        assertEquals("https://i0.hdslb.com/emoji.png", item.richText.last().url)
        assertEquals(1786276800000L, item.publishedEpochMillis)
    }

    @Test
    fun prefersGifUrlForAnimatedEmoji() {
        val root = json.parseToJsonElement(
            """
            {
              "code": 0,
              "data": {
                "items": [{
                  "id_str": "9989",
                  "modules": {
                    "module_author": {"pub_ts": 1786276800},
                    "module_dynamic": {
                      "desc": {
                        "rich_text_nodes": [
                          {"type":"RICH_TEXT_NODE_TYPE_EMOJI","orig_text":"[打call]","emoji":{"icon_url":"//i0.hdslb.com/emoji.png","gif_url":"//i0.hdslb.com/emoji.gif"}},
                          {"type":"RICH_TEXT_NODE_TYPE_EMOJI","orig_text":"[星星]","emoji":{"icon_url":"//i0.hdslb.com/star.png"}},
                          {"type":"RICH_TEXT_NODE_TYPE_EMOJI","orig_text":"[不解]","emoji":{"gif_url":"","icon_url":"//i0.hdslb.com/bujie.png"}}
                        ]
                      }
                    }
                  }
                }]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val item = BilibiliParser.parseDynamics(root, defaultMembers().first(), 3).single()

        assertEquals("https://i0.hdslb.com/emoji.gif", item.richText[0].url)
        assertEquals("https://i0.hdslb.com/star.png", item.richText[1].url)
        // 空字符串 gif_url 视同缺失，必须回落 icon_url，否则表情退化为纯文本。
        assertEquals("https://i0.hdslb.com/bujie.png", item.richText[2].url)
    }

    @Test
    fun treatsCarouselAsNotLive() {
        val member = defaultMembers().first()
        val root = json.parseToJsonElement(
            """{"code":0,"data":{"live_status":2,"title":"轮播","live_time":""}}""",
        ).jsonObject

        val status = BilibiliParser.parseLiveStatus(root, member, 1000L)

        assertEquals(2, status.liveStatus)
        assertFalse(status.isLive)
    }
}
