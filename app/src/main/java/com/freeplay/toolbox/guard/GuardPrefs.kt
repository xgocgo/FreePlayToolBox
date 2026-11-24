package com.freeplay.toolbox.guard

import android.content.Context

/** 关机前检查的开关与少量状态 */
object GuardPrefs {
    private const val PREF = "guard_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_CHECK = "last_check"

    private fun sp(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean(KEY_ENABLED, false)

    fun setEnabled(c: Context, on: Boolean) {
        sp(c).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun lastCheck(c: Context): Long = sp(c).getLong(KEY_LAST_CHECK, 0L)

    fun markCheck(c: Context) {
        sp(c).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }
}
