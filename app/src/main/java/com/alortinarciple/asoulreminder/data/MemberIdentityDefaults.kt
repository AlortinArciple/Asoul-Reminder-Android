package com.alortinarciple.asoulreminder.data

/**
 * 后加入的成员：老设置里 [MemberConfig.uid] 与 [MemberConfig.dynamicUrl] 是空的，
 * 两端触发层都会跳过 uid 为空的成员，动态开关会静默失效。
 */
internal val DEFAULT_MEMBER_IDENTITIES: Map<String, Pair<String, String>> = mapOf(
    "fiona" to ("3537115310721181" to "https://space.bilibili.com/3537115310721181/dynamic"),
    "gladys" to ("3537115310721781" to "https://space.bilibili.com/3537115310721781/dynamic"),
)

/**
 * 只给空字段补默认值，用户自己填过的值、以及 enabled / monitorDynamic 开关一律不动。
 * 没有改动时返回同一个列表实例，调用方可以据此判断是否需要回写。
 */
internal fun fillDefaultIdentities(members: List<MemberConfig>): List<MemberConfig> {
    var changed = false
    val filled = members.map { member ->
        val identity = DEFAULT_MEMBER_IDENTITIES[member.id] ?: return@map member
        if (member.uid.isNotBlank() && member.dynamicUrl.isNotBlank()) return@map member
        changed = true
        member.copy(
            uid = member.uid.ifBlank { identity.first },
            dynamicUrl = member.dynamicUrl.ifBlank { identity.second },
        )
    }
    return if (changed) filled else members
}
