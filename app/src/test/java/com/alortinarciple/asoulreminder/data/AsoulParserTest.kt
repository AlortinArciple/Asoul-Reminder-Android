package com.alortinarciple.asoulreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsoulParserTest {
    @Test
    fun parsesSolidSerializedCalendarEvent() {
        val props = """
            {"initialEvents":[1,[[0,{"id":"event-1","title":"测试直播","description":"简介","type":"official","format":"live","startTime":[3,"2026-08-09T12:00:00Z"],"host":"diana","performers":[1,["diana"]],"status":"published","sourceId":"12345","updateSourceId":null}]]]}
        """.trimIndent()
        val encoded = props
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
        val document = """
            <html><body>
              <astro-island component-export="FilterableCalendar" props="$encoded"></astro-island>
            </body></html>
        """.trimIndent()

        val events = AsoulParser.parse(document, defaultMembers())

        assertEquals(1, events.size)
        assertEquals("event-1", events.single().id)
        assertEquals("测试直播", events.single().title)
        assertEquals("https://www.bilibili.com/opus/12345", events.single().dynamicUrl)
        assertEquals("https://live.bilibili.com/22637261", events.single().liveUrl)
    }

    @Test
    fun decodesNamedAndNumericEntities() {
        val decoded = AsoulParser.decodeHtmlEntities("&quot;A&amp;B&#x4E2D;&#25991;&quot;")
        assertEquals("\"A&B中文\"", decoded)
    }
}
