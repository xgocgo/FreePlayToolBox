package com.freeplay.toolbox.guard

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.freeplay.toolbox.core.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 「这次关机可能会丢什么」的三类状态 */
enum class GuardMode(val label: String, val badge: String) {
    SUSPENDED("暂停", "已冻结"),
    DISABLED("停用", "已停用"),
    HIDDEN("隐藏", "已隐藏"),
}

data class GuardApp(
    val pkg: String,
    val label: String,
    val mode: GuardMode,
    val icon: Drawable?,
)

/**
 * 关机前检查的数据层。
 *
 * 三类状态的判据都来自 `dumpsys package packages` 里的
 * `User 0: installed=true hidden=false suspended=false stopped=.. enabled=0`
 * 那一行 —— 一条命令覆盖三种，比分别调 pm 查询快得多。
 * 图标和名字走 PackageManager（带 MATCH_UNINSTALLED_PACKAGES，
 * 隐藏的包也能取到）。
 */
object GuardRepository {

    private val pkgLine = Regex("""^\s*Package \[([\w.]+)\]""")
    private val userLine = Regex(
        """^\s*User (\d+): installed=(\w+) hidden=(\w+) suspended=(\w+).*enabled=(\d+)"""
    )

    private data class State(val hidden: Boolean, val suspended: Boolean, val enabled: Int)

    suspend fun scan(context: Context): List<GuardApp> = withContext(Dispatchers.IO) {
        val dump = Shell.run("dumpsys package packages").out
        if (dump.isBlank()) return@withContext emptyList()
        val states = parse(dump)
        val pm = context.packageManager
        states.mapNotNull { (pkg, st) ->
            val mode = when {
                st.suspended -> GuardMode.SUSPENDED
                st.hidden -> GuardMode.HIDDEN
                // enabled: 0=默认 1=启用 2=停用 3=停用且用户不可开启 4=停用直到重启
                st.enabled == 2 || st.enabled == 3 || st.enabled == 4 -> GuardMode.DISABLED
                else -> null
            } ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val ai = runCatching {
                pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }.getOrNull()
            val label = ai?.let {
                runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
            } ?: pkg
            val icon = ai?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() }
            GuardApp(pkg, label, mode, icon)
        }.sortedWith(compareBy({ it.mode.ordinal }, { it.label }))
    }

    private fun parse(dump: String): Map<String, State> {
        val out = LinkedHashMap<String, State>()
        var current: String? = null
        for (line in dump.lineSequence()) {
            val p = pkgLine.find(line)
            if (p != null) {
                current = p.groupValues[1]
                continue
            }
            val u = userLine.find(line) ?: continue
            val pkg = current ?: continue
            if (u.groupValues[1] != "0") continue
            out[pkg] = State(
                hidden = u.groupValues[3].equals("true", true),
                suspended = u.groupValues[4].equals("true", true),
                enabled = u.groupValues[5].toIntOrNull() ?: 0,
            )
        }
        return out
    }

    /** 解冻/启用/取消隐藏，返回 (成功数, 失败的应用名) */
    suspend fun recover(apps: List<GuardApp>): Pair<Int, List<String>> =
        withContext(Dispatchers.IO) {
            var ok = 0
            val fails = mutableListOf<String>()
            for (a in apps) {
                val r = when (a.mode) {
                    GuardMode.SUSPENDED -> Shell.runAsync("pm unsuspend --user 0 ${a.pkg}")
                    GuardMode.DISABLED -> Shell.runAsync("pm enable ${a.pkg}")
                    GuardMode.HIDDEN -> Shell.runAsync("pm unhide ${a.pkg}")
                }
                if (r.ok) ok++ else fails += a.label
            }
            ok to fails
        }
}
