package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    private fun payload(json: String) = Json.parseToJsonElement(json).jsonObject

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <id>tag:github.com,2008:Repository/1444186488/v0.5.0</id>
            <link rel="alternate" type="text/html" href="https://github.com/x/y/releases/tag/v0.5.0"/>
            <title>Asoul Reminder for Android v0.5.0</title>
            <content type="html">&lt;h2&gt;本次更新&lt;/h2&gt;
        &lt;ul&gt;
        &lt;li&gt;&lt;strong&gt;应用内检查更新&lt;/strong&gt;：启动后静默检查&lt;/li&gt;
        &lt;li&gt;半成品以 &lt;code&gt;.part&lt;/code&gt; 结尾&lt;/li&gt;
        &lt;/ul&gt;</content>
          </entry>
          <entry>
            <id>tag:github.com,2008:Repository/1444186488/v0.4.0</id>
            <title>old</title>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun parseFeedReadsFirstEntry() {
        val release = AppUpdater.parseFeed(feed)
        assertEquals("v0.5.0", release.tag)
        assertEquals("0.5.0", release.versionText)
        assertEquals("Asoul Reminder for Android v0.5.0", release.name)
        assertEquals("https://github.com/x/y/releases/tag/v0.5.0", release.htmlUrl)
        assertEquals(AppUpdater.APK_ASSET_NAME, release.assetName)
        assertEquals(
            "https://github.com/AlortinArciple/Asoul-Reminder-Android/releases/download/v0.5.0/${AppUpdater.APK_ASSET_NAME}",
            release.assetUrl,
        )
        assertEquals(0L, release.assetSize)
        assertEquals("大小未知", release.sizeText)
    }

    @Test
    fun parseFeedTurnsHtmlIntoPlainText() {
        val notes = AppUpdater.parseFeed(feed).notes
        assertFalse(notes.contains("<"))
        assertTrue(notes.contains("应用内检查更新"))
        assertTrue(notes.contains("- 半成品以 .part 结尾"))
    }

    @Test
    fun parseFeedRejectsGarbage() {
        var failed = false
        try {
            AppUpdater.parseFeed("")
        } catch (error: UpdateException) {
            failed = true
        }
        assertTrue(failed)

        failed = false
        try {
            AppUpdater.parseFeed("not xml at all")
        } catch (error: UpdateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun parseFeedWithoutEntriesRejects() {
        var failed = false
        try {
            AppUpdater.parseFeed("<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>")
        } catch (error: UpdateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun htmlToTextDecodesEntities() {
        assertEquals("a & b < c", AppUpdater.htmlToText("a &amp; b &lt; c"))
        assertEquals("中文", AppUpdater.htmlToText("&#20013;&#25991;"))
    }

    @Test
    fun parseVersionStripsPrefixAndSplits() {
        assertEquals(listOf(1, 2, 0), AppUpdater.parseVersion("v1.2.0"))
        assertEquals(listOf(0, 4, 0), AppUpdater.parseVersion("0.4.0"))
        assertEquals(listOf(2, 1), AppUpdater.parseVersion("02.01"))
        assertEquals(listOf(1, 0), AppUpdater.parseVersion("1.0-private"))
        assertEquals(listOf(3), AppUpdater.parseVersion("3+abc"))
    }

    @Test
    fun parseVersionReturnsEmptyForGarbage() {
        assertTrue(AppUpdater.parseVersion("").isEmpty())
        assertTrue(AppUpdater.parseVersion("abc").isEmpty())
    }

    @Test
    fun isNewerComparesNumerically() {
        assertTrue(AppUpdater.isNewer("v1.2.0", "1.1.0"))
        assertTrue(AppUpdater.isNewer("0.10.0", "0.9.0"))
        assertFalse(AppUpdater.isNewer("v1.0.0", "1.0.0"))
        assertFalse(AppUpdater.isNewer("v1.0.0", "1.1.0"))
    }

    @Test
    fun isNewerPadsShortVersions() {
        assertTrue(AppUpdater.isNewer("1.2", "1.1.9"))
        assertFalse(AppUpdater.isNewer("1.0", "1.0.1"))
    }

    @Test
    fun isNewerIgnoresUnparsableVersions() {
        assertFalse(AppUpdater.isNewer("", "1.0.0"))
        assertFalse(AppUpdater.isNewer("abc", "1.0.0"))
    }

    @Test
    fun parseReleaseReadsAssetAndNotes() {
        val release = AppUpdater.parseRelease(
            payload(
                """
                {
                  "tag_name": "v0.5.0",
                  "name": "0.5.0",
                  "body": "修复若干问题",
                  "html_url": "https://example.invalid/releases/v0.5.0",
                  "assets": [
                    {
                      "name": "AsoulReminder-Android-debug.apk",
                      "browser_download_url": "https://example.invalid/download.apk",
                      "size": 12345678
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        assertEquals("v0.5.0", release.tag)
        assertEquals("0.5.0", release.versionText)
        assertEquals("修复若干问题", release.notes)
        assertEquals(AppUpdater.APK_ASSET_NAME, release.assetName)
        assertEquals(12345678L, release.assetSize)
        assertEquals("11.8 MB", release.sizeText)
    }

    @Test
    fun parseReleaseFallsBackToAnyApkAsset() {
        val release = AppUpdater.parseRelease(
            payload(
                """
                {
                  "tag_name": "v0.5.0",
                  "assets": [
                    {"name": "notes.txt", "browser_download_url": "https://example.invalid/n.txt", "size": 10},
                    {"name": "signed-release.apk", "browser_download_url": "https://example.invalid/a.apk", "size": 2048}
                  ]
                }
                """.trimIndent(),
            ),
        )
        assertEquals("signed-release.apk", release.assetName)
    }

    @Test
    fun parseReleaseRejectsMissingTag() {
        val error = runCatching { AppUpdater.parseRelease(payload("""{"assets": []}""")) }.exceptionOrNull()
        assertTrue(error is UpdateException)
        assertTrue(error?.message?.contains("版本号") == true)
    }

    @Test
    fun parseReleaseSurfacesGithubErrorMessage() {
        // GitHub 按 IP 限流时返回 {"message": "..."}，错误提示要带出来。
        val error = runCatching {
            AppUpdater.parseRelease(payload("""{"message": "API rate limit exceeded for 1.2.3.4."}"""))
        }.exceptionOrNull()
        assertTrue(error is UpdateException)
        assertTrue(error?.message?.contains("API rate limit exceeded") == true)
    }

    @Test
    fun parseReleaseRejectsMissingApk() {        val error = runCatching {
            AppUpdater.parseRelease(
                payload(
                    """
                    {
                      "tag_name": "v0.5.0",
                      "assets": [{"name": "notes.txt", "browser_download_url": "https://example.invalid/n.txt"}]
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()
        assertTrue(error is UpdateException)
        assertTrue(error?.message?.contains("APK") == true)
    }

    @Test
    fun sizeTextHandlesUnknownSize() {
        val release = AppRelease(
            tag = "v0.5.0",
            name = "",
            notes = "",
            assetName = "a.apk",
            assetUrl = "https://example.invalid/a.apk",
            assetSize = 0L,
            htmlUrl = "",
        )
        assertEquals("大小未知", release.sizeText)
    }
}
