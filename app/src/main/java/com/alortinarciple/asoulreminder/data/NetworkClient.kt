package com.alortinarciple.asoulreminder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

data class NetworkResponse(
    val statusCode: Int,
    val body: String,
)

class HttpStatusException(
    val statusCode: Int,
    url: String,
) : IOException("HTTP $statusCode: $url")

class NetworkClient(
    private val timeoutMillis: Int = 20_000,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String = request(url, headers).let { response ->
        if (response.statusCode !in 200..299) throw HttpStatusException(response.statusCode, url)
        response.body
    }

    suspend fun getJson(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = json.parseToJsonElement(getText(url, headers)).jsonObject

    suspend fun request(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36",
            )
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
            // asoul.love 的日程页未压缩约 165 KB，gzip 后约 21 KB；不加这个头 HttpURLConnection
            // 不会协商压缩，每次请求都要多传 8 倍流量。
            setRequestProperty("Accept-Encoding", "gzip")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            val code = connection.responseCode
            val raw = if (code in 200..399) connection.inputStream else connection.errorStream
            val gzip = connection.contentEncoding?.contains("gzip", ignoreCase = true) == true
            val body = raw?.let { stream ->
                if (!gzip) {
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }.orEmpty()
            NetworkResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}
