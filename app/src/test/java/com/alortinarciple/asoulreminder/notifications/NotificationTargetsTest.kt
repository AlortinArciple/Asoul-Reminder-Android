package com.alortinarciple.asoulreminder.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTargetsTest {
    private val preferred = listOf("tv.danmaku.bili", "com.bilibili.app.in")

    @Test
    fun configuredPackageWinsWhenItCanHandleLink() {
        val handlers = listOf("com.android.chrome", "tv.danmaku.bili", "com.bilibili.clone")
        assertEquals(
            "com.bilibili.clone",
            NotificationTargets.pickPackage("com.bilibili.clone", handlers, preferred),
        )
    }

    @Test
    fun configuredPackageIgnoredWhenItCannotHandleLink() {
        val handlers = listOf("tv.danmaku.bili")
        assertEquals(
            "tv.danmaku.bili",
            NotificationTargets.pickPackage("com.other.app", handlers, preferred),
        )
    }

    @Test
    fun blankConfiguredFallsBackToPreferredOrder() {
        val handlers = listOf("com.bilibili.app.in", "com.android.chrome")
        assertEquals(
            "com.bilibili.app.in",
            NotificationTargets.pickPackage("", handlers, preferred),
        )
    }

    @Test
    fun returnsNullWhenNoPreferredHandlerExists() {
        assertNull(NotificationTargets.pickPackage("", listOf("com.android.chrome"), preferred))
        assertNull(NotificationTargets.pickPackage("", emptyList(), preferred))
    }
}
