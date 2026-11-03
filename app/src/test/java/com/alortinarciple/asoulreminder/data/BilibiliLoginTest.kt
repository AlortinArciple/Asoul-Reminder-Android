package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliLoginTest {
    private fun payload(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun normalizesCookieText() {
        assertEquals("a=1; b=2", BilibiliLogin.normalize(" Cookie: a=1;\n b=2 ;\n"))
        assertEquals("", BilibiliLogin.normalize("   "))
        // 非键值对片段被丢弃
        assertEquals("a=1", BilibiliLogin.normalize("a=1; garbage;"))
    }

    @Test
    fun requiresSessdataForLoginCookie() {
        assertTrue(BilibiliLogin.hasLoginCookie("SESSDATA=abc; bili_jct=csrf"))
        assertFalse(BilibiliLogin.hasLoginCookie("buvid3=xyz"))
        assertFalse(BilibiliLogin.hasLoginCookie(""))
    }

    @Test
    fun loginCookiesKeepsOnlyCredentials() {
        val raw = "buvid3=B3; SESSDATA=abc; buvid4=B4; DedeUserID=42; bili_jct=csrf; b_nut=1"
        val filtered = BilibiliLogin.loginCookies(raw)
        assertEquals("SESSDATA=abc; DedeUserID=42; bili_jct=csrf", filtered)
    }

    @Test
    fun mergeKeepsExistingFingerprintFields() {
        val existing = "buvid3=OLD3; SESSDATA=old; b_nut=100; DedeUserID=1"
        val updated = BilibiliLogin.loginCookies("SESSDATA=new; DedeUserID=2; buvid3=NEW3")
        val merged = BilibiliLogin.merge(existing, updated)
        val map = BilibiliLogin.cookieMap(merged)
        assertEquals("new", map["SESSDATA"])
        assertEquals("2", map["DedeUserID"])
        // 指纹字段保留旧值，键顺序仍在原位置
        assertEquals("OLD3", map["buvid3"])
        assertEquals("100", map["b_nut"])
        assertTrue(merged.startsWith("buvid3=OLD3; SESSDATA=new"))
    }

    @Test
    fun parsesNavValidStatus() {
        val status = BilibiliLogin.parseNav(
            payload("""{"code":0,"message":"0","data":{"isLogin":true,"uname":"阿草","mid":123}}"""),
        )
        assertEquals(BilibiliLogin.Status.VALID, status.status)
        assertEquals("阿草", status.uname)
        assertEquals("123", status.mid)
    }

    @Test
    fun parsesNavExpiredStatus() {
        val status = BilibiliLogin.parseNav(
            payload("""{"code":-101,"message":"账号未登录","data":{"isLogin":false}}"""),
        )
        assertEquals(BilibiliLogin.Status.EXPIRED, status.status)
    }

    @Test
    fun treatsZeroCodeWithoutLoginAsExpired() {
        val status = BilibiliLogin.parseNav(payload("""{"code":0,"data":{"isLogin":false}}"""))
        assertEquals(BilibiliLogin.Status.EXPIRED, status.status)
    }

    @Test
    fun networkFailureIsUnknownNotExpired() {
        // code 缺失时不能当作失效，避免误判
        assertEquals(BilibiliLogin.Status.UNKNOWN, BilibiliLogin.parseNav(payload("{}")).status)
        assertEquals(BilibiliLogin.Status.UNKNOWN, BilibiliLogin.parseNav(payload("""{"code":-352}""")).status)
    }

    @Test
    fun onlyMinus101CountsAsExpiredCode() {
        assertTrue(BilibiliLogin.isExpiredCode(-101))
        assertFalse(BilibiliLogin.isExpiredCode(-352))
        assertFalse(BilibiliLogin.isExpiredCode(null))
    }

    @Test
    fun dynamicApiExceptionCarriesCode() {
        val member = MemberConfig(
            id = "diana",
            name = "嘉然",
            enabled = true,
            monitorDynamic = true,
            monitorLive = true,
            color = "#F5C542",
            uid = "672328094",
            dynamicUrl = "https://space.bilibili.com/672328094/dynamic",
            liveUrl = "https://live.bilibili.com/22625025",
        )
        val error = runCatching {
            BilibiliParser.parseDynamics(payload("""{"code":-101,"message":"账号未登录"}"""), member, 3)
        }.exceptionOrNull()
        assertTrue(error is BilibiliApiException)
        assertEquals(-101, (error as BilibiliApiException).code)
        assertTrue(error.message.orEmpty().contains("账号未登录"))
    }
}
