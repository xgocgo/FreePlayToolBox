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
        val logs = mutableListOf<String>()
        if (blocked) {
            val r1 = Shell.runAsync("cmd appops set $pkg POST_NOTIFICATION ignore")
            if (!r1.ok) logs.add("appops: ${r1.message().take(80)}")
            val r2 = Shell.runAsync("pm revoke $pkg android.permission.POST_NOTIFICATIONS")
            if (!r2.ok && !r2.message().contains("not a changeable permission", true)) {
                logs.add("revoke: ${r2.message().take(80)}")
            }
        } else {
            val r1 = Shell.runAsync("cmd appops set $pkg POST_NOTIFICATION allow")
            if (!r1.ok) logs.add("appops: ${r1.message().take(80)}")
            Shell.runAsync("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        }

        // 回读校验
        val now = currentMode(pkg)
        if (blocked && now != "ignore") {
            logs.add("回读状态=$now，未生效")
        }
        logs.joinToString("；")
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

    suspend fun isBlocked(pkg: String): Boolean = currentMode(pkg) == "ignore"

    /** 该 ROM 是否能列出通知（用于诊断提示） */
    suspend fun canList(): Boolean = withContext(Dispatchers.IO) {
        val out = Shell.run("dumpsys notification").out
        out.contains("NotificationRecord") || out.contains("pkg=")
    }
}
