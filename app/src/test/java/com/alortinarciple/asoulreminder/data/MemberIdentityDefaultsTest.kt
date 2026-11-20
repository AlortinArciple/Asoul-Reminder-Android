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

    @Test
    fun appendsMissingOfficialMember() {
        val result = ensureDefaultMembers(listOf(member("diana")))

        val official = result.first { it.id == "official" }
        assertEquals("A-SOUL_Official", official.name)
        assertEquals("703007996", official.uid)
        assertEquals("https://space.bilibili.com/703007996/dynamic", official.dynamicUrl)
        // 只要动态，不要直播间监控。
        assertTrue(official.monitorDynamic)
        assertEquals(false, official.monitorLive)
        assertEquals("", official.liveUrl)
        // 已有成员保留且不重复。
        assertEquals("diana", result.first().id)
        assertEquals(result.size, result.map { it.id }.distinct().size)
    }

    @Test
    fun doesNotOverwriteExistingOfficial() {
        // 用户改过的 official 必须原样保留：名称、开关、UID 都不被默认值覆盖。
        val original = defaultMembers().map { member ->
            if (member.id != "official") {
                member
            } else {
                member.copy(
                    name = "我自己改的名字",
                    enabled = false,
                    monitorDynamic = false,
                    uid = "1",
                    dynamicUrl = "https://example.com",
                )
            }
        }

        assertSame(original, ensureDefaultMembers(original))
    }
}
