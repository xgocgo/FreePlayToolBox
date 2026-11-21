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
 * 通知管理：
 * - 列出当前活动通知（解析 dumpsys notification）
 * - 按包屏蔽全部通知（AppOps POST_NOTIFICATION）
 * - 隐藏常驻通知：Android 13+ 前台服务通知同样受 POST_NOTIFICATION 控制，
 *   撤销后「正在运行」这类常驻通知不再显示（副作用是该包所有通知一起消失）。
 */
object NotificationRepository {

    suspend fun active(): List<ActiveNotification> = withContext(Dispatchers.IO) {
        val dump = Shell.run("dumpsys notification --noredact").out
        if (dump.isBlank()) return@withContext emptyList()

        val list = mutableListOf<ActiveNotification>()
        var currentPkg: String? = null
        var currentTitle: String? = null
        var ongoing = false

        fun flush() {
            val p = currentPkg ?: return
            list.add(ActiveNotification(p, currentTitle?.take(40) ?: "(无标题)", ongoing))
            currentPkg = null
            currentTitle = null
            ongoing = false
        }

        for (line in dump.lines()) {
            val pkgMatch = Regex("""pkg=(\S+)""").find(line)
            if (pkgMatch != null) {
                flush()
                currentPkg = pkgMatch.groupValues[1]
            }
            if (currentPkg != null) {
                Regex("""android\.title=(.+)""").find(line)?.let {
                    currentTitle = it.groupValues[1].trim().trimEnd(',')
                }
                if (line.contains("flags=") && (line.contains("ONGOING") || line.contains("FOREGROUND_SERVICE"))) {
                    ongoing = true
                }
            }
        }
        flush()
        list.distinctBy { it.pkg to it.title }
    }

    /** 屏蔽/恢复该包全部通知（含常驻） */
    suspend fun setBlocked(pkg: String, blocked: Boolean) = withContext(Dispatchers.IO) {
        if (blocked) {
            Shell.runAsync("cmd appops set $pkg POST_NOTIFICATION ignore")
        } else {
            Shell.runAsync("cmd appops set $pkg POST_NOTIFICATION allow")
        }
        Unit
    }

    suspend fun isBlocked(pkg: String): Boolean = withContext(Dispatchers.IO) {
        Shell.runAsync("cmd appops get $pkg POST_NOTIFICATION").out.contains("ignore")
    }
}
