package com.freeplay.toolbox.data

import com.freeplay.toolbox.core.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ActiveNotification(
    val pkg: String,
    val title: String,
    val ongoing: Boolean,
)

/**
 * 通知管理。
 *
 * 屏蔽通知走两条命令并回读校验：
 * 1. `cmd appops set <pkg> POST_NOTIFICATION ignore`（Android 13+ 前台服务/常驻通知也受它控制）
 * 2. `pm revoke <pkg> android.permission.POST_NOTIFICATIONS`（targetSdk 33+ 的运行时权限）
 * 若两条都无效，返回系统原始输出供排查。
 */
object NotificationRepository {

    /** 解析当前活动通知（多策略容错） */
    suspend fun active(): List<ActiveNotification> = withContext(Dispatchers.IO) {
        val dumps = listOf(
            "dumpsys notification --noredact",
            "dumpsys notification",
        )
        for (cmd in dumps) {
            val out = Shell.run(cmd).out
            if (out.isBlank()) continue
            val parsed = parse(out)
            if (parsed.isNotEmpty()) return@withContext parsed
        }
        emptyList()
    }

    private fun parse(dump: String): List<ActiveNotification> {
        val list = mutableListOf<ActiveNotification>()
        var pkg: String? = null
        var title: String? = null
        var ongoing = false

        fun flush() {
            val p = pkg ?: return
            val t = title?.trim()?.trimEnd(',')?.takeIf { it.isNotBlank() } ?: "(无标题)"
            list.add(ActiveNotification(p, t, ongoing))
            pkg = null
            title = null
            ongoing = false
        }

        for (line in dump.lines()) {
            // 匹配 pkg=com.foo 或 pkg: com.foo
            val pkgMatch = Regex("""pkg[=:]\s*([a-zA-Z][\w.]*)""").find(line)
                ?: Regex("""^\s*NotificationRecord.*?([a-zA-Z][\w]*(?:\.[\w]+){2,})""").find(line)
            if (pkgMatch != null) {
                flush()
                pkg = pkgMatch.groupValues[1]
                continue
            }
            if (pkg != null) {
                Regex("""\(?android\.title[=:]\s*(.+)$""").find(line)?.let {
                    title = it.groupValues[1]
                }
                if (Regex("""flags[=:].*\b(ONGOING|FOREGROUND_SERVICE|NO_CLEAR|ONGOING_EVENT)""")
                        .containsMatchIn(line)
                ) {
                    ongoing = true
                }
            }
        }
        flush()
        return list.distinctBy { it.pkg to it.title }
    }

    /** 屏蔽/恢复整包通知，返回操作结果说明（空串表示成功） */
    suspend fun setBlockedDetailed(pkg: String, blocked: Boolean): String = withContext(Dispatchers.IO) {
        if (blocked) {
            // 关闭通知权限（Android 13+ 运行时权限 + AppOps 双保险）
            Shell.runAsync("pm revoke $pkg android.permission.POST_NOTIFICATIONS")
            Shell.runAsync("cmd appops set $pkg POST_NOTIFICATION ignore")
        } else {
            Shell.runAsync("pm grant $pkg android.permission.POST_NOTIFICATIONS")
            Shell.runAsync("cmd appops set $pkg POST_NOTIFICATION allow")
        }
        // 回读：任一通道生效即算成功
        val mode = currentMode(pkg)
        val granted = permissionGranted(pkg)
        if (!blocked) {
            if (mode == "ignore" && granted == false) "恢复失败：状态仍为关闭"
            else ""
        } else {
            if (mode != "ignore" && granted != false) "关闭失败：系统拒绝修改（状态=$mode）"
            else ""
        }
    }

    /** 读取 POST_NOTIFICATIONS 运行时权限是否已授予；null = 无法判断 */
    suspend fun permissionGranted(pkg: String): Boolean? = withContext(Dispatchers.IO) {
        val out = Shell.runAsync("pm check-permission $pkg android.permission.POST_NOTIFICATIONS").out
        when {
            out.contains("granted=true") -> true
            out.contains("granted=false") || out.contains("Permission") && out.contains("denied") -> false
            else -> null
        }
    }

    /** 读取该包 POST_NOTIFICATION 的当前 appops 模式 */
    suspend fun currentMode(pkg: String): String = withContext(Dispatchers.IO) {
        val out = Shell.runAsync("cmd appops get $pkg POST_NOTIFICATION").out
        when {
            out.contains("ignore") -> "ignore"
            out.contains("allow") -> "allow"
            out.contains("default") -> "default"
            out.isBlank() -> "无记录"
            else -> out.lines().firstOrNull()?.take(40) ?: "未知"
        }
    }

    suspend fun setBlocked(pkg: String, blocked: Boolean): Boolean =
        setBlockedDetailed(pkg, blocked).isEmpty()

    suspend fun isBlocked(pkg: String): Boolean =
        currentMode(pkg) == "ignore" || permissionGranted(pkg) == false

    /** 该 ROM 是否能列出通知（用于诊断提示） */
    suspend fun canList(): Boolean = withContext(Dispatchers.IO) {
        val out = Shell.run("dumpsys notification").out
        out.contains("NotificationRecord") || out.contains("pkg=")
    }
}
