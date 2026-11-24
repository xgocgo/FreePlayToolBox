package com.freeplay.toolbox.data

import android.content.Context
import android.service.notification.StatusBarNotification

/**
 * 精准隐藏规则：按「包名 + 通知 id + tag」精确匹配某一条通知。
 * 存储格式：pkg\u0001id\u0001tag
 */
data class NotifyRule(
    val pkg: String,
    val id: Int,
    val tag: String?,
    val label: String,
) {
    val serialized: String get() = "$pkg\u0001$id\u0001${tag ?: ""}\u0001$label"

    fun matches(sbn: StatusBarNotification): Boolean =
        sbn.packageName == pkg && sbn.id == id && (sbn.tag ?: "") == (tag ?: "")

    companion object {
        fun parse(s: String): NotifyRule? {
            val f = s.split("\u0001")
            if (f.size < 3) return null
            return NotifyRule(
                pkg = f[0],
                id = f[1].toIntOrNull() ?: return null,
                tag = f[2].ifBlank { null },
                label = f.getOrElse(3) { f[0] },
            )
        }
    }
}

object NotifyRules {
    private const val PREF = "toolbox_prefs"
    private const val KEY = "notify_hide_rules"

    fun load(c: Context): List<NotifyRule> {
        val raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { NotifyRule.parse(it) }
            .sortedBy { it.label }
    }

    fun add(c: Context, rule: NotifyRule) {
        val sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = sp.getStringSet(KEY, emptySet()) ?: emptySet()
        sp.edit().putStringSet(KEY, old + rule.serialized).apply()
    }

    fun remove(c: Context, rule: NotifyRule) {
        val sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = sp.getStringSet(KEY, emptySet()) ?: emptySet()
        sp.edit().putStringSet(KEY, old - rule.serialized).apply()
    }

    fun clear(c: Context) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
