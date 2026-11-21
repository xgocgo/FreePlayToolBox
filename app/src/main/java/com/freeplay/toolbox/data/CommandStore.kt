package com.freeplay.toolbox.data

import android.content.Context

/** 自定义命令收藏，存 SharedPreferences */
object CommandStore {
    private const val PREF = "toolbox_prefs"
    private const val KEY = "fav_commands"

    fun load(context: Context): List<String> {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY, emptySet())?.sorted() ?: emptyList()
    }

    fun save(context: Context, commands: List<String>) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit().putStringSet(KEY, commands.toSet()).apply()
    }
}
