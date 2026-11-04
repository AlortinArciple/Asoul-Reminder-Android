package com.alortinarciple.asoulreminder.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    private fun payload(json: String) = Json.parseToJsonElement(json).jsonObject

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
    fun parseReleaseRejectsMissingApk() {
        val error = runCatching {
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
