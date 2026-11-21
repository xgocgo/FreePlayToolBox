package com.freeplay.toolbox.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 配置备份与迁移：导出/导入收藏命令、应用页筛选偏好、冻结清单。
 * 文件写入 Download/DSH-Go/backup_时间戳.json
 */
object ConfigBackup {

    data class Snapshot(
        val favCommands: List<String>,
        val showSystem: Boolean,
        val sortBy: String,
        val ascending: Boolean,
        val frozenApps: List<Pair<String, String>>, // pkg to label
        val exportedAt: Long,
    )

    private const val PREF = "toolbox_prefs"
    private const val FILTER = "apps_filter"

    /** 导出到 Download/DSH-Go/ */
    fun export(context: Context, frozen: List<Pair<String, String>>): File? {
        return try {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val flt = context.getSharedPreferences(FILTER, Context.MODE_PRIVATE)
            val cmds = prefs.getStringSet("fav_commands", emptySet())?.sorted() ?: emptyList()

            val root = JSONObject().apply {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("favCommands", JSONArray(cmds))
                put("appsFilter", JSONObject().apply {
                    put("showSystem", flt.getBoolean("showSystem", false))
                    put("sortBy", flt.getString("sortBy", "NAME"))
                    put("ascending", flt.getBoolean("ascending", true))
                })
                put("frozenApps", JSONArray().apply {
                    frozen.forEach { (pkg, label) ->
                        put(JSONObject().apply {
                            put("pkg", pkg)
                            put("label", label)
                        })
                    }
                })
            }

            val dir = File("/sdcard/Download/DSH-Go")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "backup_${System.currentTimeMillis()}.json")
            f.writeText(root.toString(2))
            f
        } catch (_: Throwable) {
            null
        }
    }

    /** 从 JSON 文本读取快照 */
    fun parse(text: String): Snapshot? = try {
        val o = JSONObject(text)
        val lf = o.optJSONObject("appsFilter")
        Snapshot(
            favCommands = o.optJSONArray("favCommands")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            showSystem = lf?.optBoolean("showSystem", false) ?: false,
            sortBy = lf?.optString("sortBy", "NAME") ?: "NAME",
            ascending = lf?.optBoolean("ascending", true) ?: true,
            frozenApps = o.optJSONArray("frozenApps")?.let { arr ->
                (0 until arr.length()).map {
                    val x = arr.getJSONObject(it)
                    x.optString("pkg") to x.optString("label")
                }
            } ?: emptyList(),
            exportedAt = o.optLong("exportedAt", 0L),
        )
    } catch (_: Throwable) {
        null
    }

    /** 应用快照（合并收藏，覆盖筛选偏好） */
    fun apply(context: Context, snap: Snapshot) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val flt = context.getSharedPreferences(FILTER, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("fav_commands", emptySet()) ?: emptySet()
        val merged = (existing + snap.favCommands).toSet()
        prefs.edit().putStringSet("fav_commands", merged).apply()
        flt.edit()
            .putBoolean("showSystem", snap.showSystem)
            .putString("sortBy", snap.sortBy)
            .putBoolean("ascending", snap.ascending)
            .apply()
    }

    /** 列出已有备份文件 */
    fun listBackups(): List<File> {
        val dir = File("/sdcard/Download/DSH-Go")
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.name.startsWith("backup_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
