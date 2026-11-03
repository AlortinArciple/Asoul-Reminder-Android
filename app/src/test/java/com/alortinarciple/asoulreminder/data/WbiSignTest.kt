package com.alortinarciple.asoulreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WbiSignTest {
    private val imgKey = "7cd084941338484aae1ad9425b84077c"
    private val subKey = "4932caff0ff746eab6f01bf08b70ac45"
    private val mixinKey = "ea1db124af3c7062474693fa704f4ff8"

    @Test
    fun mixinKeyMatchesDocumentedExample() {
        assertEquals(mixinKey, WbiSign.mixinKey(imgKey, subKey))
    }

    @Test
    fun keyFromUrlExtractsFilename() {
        assertEquals(imgKey, WbiSign.keyFromUrl("https://i0.hdslb.com/bfs/wbi/$imgKey.png"))
        assertEquals(subKey, WbiSign.keyFromUrl("https://i2.hdslb.com/bfs/wbi/$subKey.jpg?x=1"))
        assertEquals("", WbiSign.keyFromUrl(""))
    }

    @Test
    fun signedQueryMatchesDocumentedVector() {
        val query = WbiSign.signedQuery(
            mapOf("foo" to "114", "bar" to "514", "zab" to "1919810"),
            mixinKey,
            1702204169,
        )
        assertEquals(
            "bar=514&foo=114&wts=1702204169&zab=1919810" +
                "&w_rid=8f6f2b5b3d485fe1886cec6a0be8c5d4",
            query,
        )
    }

    @Test
    fun encodeAndFilterMatchEncodeUriComponent() {
        assertEquals("ab", WbiSign.filterValue("a!'()*b"))
        assertEquals("a%20b", WbiSign.encodeValue("a b"))
        assertEquals("%5B%5D", WbiSign.encodeValue("[]"))
        assertEquals("%E6%B5%8B%E8%AF%95", WbiSign.encodeValue("测试"))
    }
}
