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

    /**
     * `User 0:` 那行的字段顺序各版本不一样（通常还带 ceDataInode 前缀），
     * 所以不按位置匹配，整行抓下来再逐个字段找。
     */
    private val userLine = Regex("""^\s*User (\d+): (.*)$""")

    private data class State(val hidden: Boolean, val suspended: Boolean, val enabled: Int)

    data class ScanResult(val apps: List<GuardApp>, val packages: Int, val dumpChars: Int)

    suspend fun scan(context: Context): ScanResult = withContext(Dispatchers.IO) {
        val dump = Shell.run("dumpsys package packages").out
        val states = if (dump.isBlank()) emptyMap() else parse(dump)
        val pm = context.packageManager
        val hits = states.mapNotNull { (pkg, st) ->
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
            val label0 = ai?.let {
                runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
            } ?: pkg
            val icon = ai?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() }
            GuardApp(pkg, label0, mode, icon)
        }.sortedWith(compareBy({ it.mode.ordinal }, { it.label }))

        if (states.isEmpty()) {
            // dumpsys 解析不出来（ROM 输出格式不同/命令没输出）时退回 PackageManager，
            // 隐藏的包看不到，但暂停和停用能拿到
            val fallback = pmFlags(context)
            return@withContext ScanResult(fallback, fallback.size, dump.length)
        }
        ScanResult(hits, states.size, dump.length)
    }

    /** 兜底：直接从 ApplicationInfo 的标志位判断 */
    private fun pmFlags(context: Context): List<GuardApp> {
        val pm = context.packageManager
        val out = mutableListOf<GuardApp>()
        val pkgs = runCatching {
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }.getOrDefault(emptyList())
        for (ai in pkgs) {
            if (ai.packageName == context.packageName) continue
            val mode = when {
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_SUSPENDED) != 0 ->
                    GuardMode.SUSPENDED
                !ai.enabled -> GuardMode.DISABLED
                else -> continue
            }
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrNull()
                ?: ai.packageName
            val icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull()
            out += GuardApp(ai.packageName, label, mode, icon)
        }
        return out.sortedWith(compareBy({ it.mode.ordinal }, { it.label }))
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
            if (u.groupValues[1] != "0") continue
            val pkg = current ?: continue
            val st = parseUserState(u.groupValues[2]) ?: continue
            out[pkg] = st
        }
        return out
    }

    private fun parseUserState(rest: String): State? {
        fun flag(key: String): Boolean =
            Regex("""\b""" + key + """=(\w+)""").find(rest)
                ?.groupValues?.get(1)?.equals("true", ignoreCase = true) == true
        val enabled = Regex("""\benabled=(\d+)""").find(rest)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return State(flag("hidden"), flag("suspended"), enabled)
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
