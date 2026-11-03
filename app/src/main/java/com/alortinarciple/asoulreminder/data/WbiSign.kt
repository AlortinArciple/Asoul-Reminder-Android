package com.alortinarciple.asoulreminder.data

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * B 站 wbi 签名：参数按 key 排序、过滤 !'()*、encodeURIComponent 编码后
 * 追加 wts，与 img_key+sub_key 派生的 mixin key 拼接做 MD5 得到 w_rid。
 * 算法与文档保持一致：https://github.com/pskdje/bilibili-API-collect (wbi.md)。
 */
object WbiSign {
    const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
    const val KEY_TTL_MILLIS = 30 * 60_000L

    private val MIXIN_TABLE = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
    )

    fun keyFromUrl(url: String): String =
        url.trimEnd('/').substringAfterLast('/').substringBefore('.')

    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val out = StringBuilder(32)
        for (index in MIXIN_TABLE) {
            if (index < raw.length && out.length < 32) out.append(raw[index])
        }
        return out.toString()
    }

    fun filterValue(value: String): String = value.filterNot { it in "!'()*" }

    /** 等价 JS encodeURIComponent（!'()* 先被 filterValue 移除）。 */
    fun encodeValue(value: String): String {
        val encoded = StringBuilder()
        for (ch in value) {
            if (
                ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~'
            ) {
                encoded.append(ch)
            } else {
                for (byte in ch.toString().toByteArray(StandardCharsets.UTF_8)) {
                    encoded.append("%")
                    encoded.append("%02X".format(byte.toInt() and 0xFF))
                }
            }
        }
        return encoded.toString()
    }

    fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 对参数签名并输出已编码、按 key 排序、末尾含 w_rid 的完整 query。 */
    fun signedQuery(params: Map<String, String>, mixinKey: String, wts: Long): String {
        val merged = params + ("wts" to wts.toString())
        val query = merged.entries
            .sortedBy { it.key }
            .joinToString("&") { "${encodeValue(filterValue(it.key))}=${encodeValue(filterValue(it.value))}" }
        return "$query&w_rid=${md5Hex(query + mixinKey)}"
    }
}
