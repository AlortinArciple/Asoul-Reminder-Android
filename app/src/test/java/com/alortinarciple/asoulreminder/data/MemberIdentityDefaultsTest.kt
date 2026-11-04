package com.alortinarciple.asoulreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberIdentityDefaultsTest {

    private fun member(
        id: String,
        uid: String = "",
        dynamicUrl: String = "",
        enabled: Boolean = true,
        monitorDynamic: Boolean = false,
    ) = MemberConfig(
        id = id,
        name = id,
        enabled = enabled,
        monitorDynamic = monitorDynamic,
        monitorLive = true,
        color = "#FFFFFF",
        uid = uid,
        dynamicUrl = dynamicUrl,
        liveUrl = "",
    )

    @Test
    fun fillsEmptyUidAndDynamicUrl() {
        val filled = fillDefaultIdentities(listOf(member("fiona"), member("gladys")))

        val fiona = filled.first { it.id == "fiona" }
        val gladys = filled.first { it.id == "gladys" }
        assertEquals("3537115310721181", fiona.uid)
        assertEquals("https://space.bilibili.com/3537115310721181/dynamic", fiona.dynamicUrl)
        assertEquals("3537115310721781", gladys.uid)
        assertEquals("https://space.bilibili.com/3537115310721781/dynamic", gladys.dynamicUrl)
    }

    @Test
    fun keepsUserFilledValues() {
        val original = listOf(
            member(
                "fiona",
                uid = "999999",
                dynamicUrl = "https://space.bilibili.com/999999/dynamic",
            ),
        )

        assertSame(original, fillDefaultIdentities(original))
        assertEquals("999999", fillDefaultIdentities(original).single().uid)
    }

    @Test
    fun fillsOnlyTheMissingField() {
        val filled = fillDefaultIdentities(listOf(member("gladys", uid = "123"))).single()

        assertEquals("123", filled.uid)
        assertEquals("https://space.bilibili.com/3537115310721781/dynamic", filled.dynamicUrl)
    }

    @Test
    fun leavesOtherMembersUntouched() {
        val original = listOf(member("diana"))

        assertSame(original, fillDefaultIdentities(original))
        assertEquals("", fillDefaultIdentities(original).single().uid)
    }

    @Test
    fun doesNotFlipSwitches() {
        val filled = fillDefaultIdentities(
            listOf(member("fiona", enabled = false, monitorDynamic = false)),
        ).single()

        assertTrue(filled.uid.isNotBlank())
        assertEquals(false, filled.enabled)
        assertEquals(false, filled.monitorDynamic)
    }
}
