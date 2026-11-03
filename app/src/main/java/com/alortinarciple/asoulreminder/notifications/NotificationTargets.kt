package com.alortinarciple.asoulreminder.notifications

/**
 * 通知/链接的打开目标解析：优先用户在设置里指定的应用，其次 B 站客户端，最后交给系统默认。
 * handlerPackages 是「能处理该链接的应用」包名集合（由 PackageManager 查询得到），
 * 因此双开形成的不同包名实例可以作为独立候选被显式选择。
 */
object NotificationTargets {
    val PREFERRED_BILIBILI_PACKAGES = listOf("tv.danmaku.bili", "com.bilibili.app.in")
    const val PROBE_URL = "https://www.bilibili.com"

    fun pickPackage(
        configured: String,
        handlerPackages: List<String>,
        preferred: List<String> = PREFERRED_BILIBILI_PACKAGES,
    ): String? {
        val configuredPackage = configured.trim()
        if (configuredPackage.isNotEmpty() && configuredPackage in handlerPackages) {
            return configuredPackage
        }
        return preferred.firstOrNull { it in handlerPackages }
    }
}
