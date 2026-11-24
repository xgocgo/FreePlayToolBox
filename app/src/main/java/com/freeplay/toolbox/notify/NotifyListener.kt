package com.freeplay.toolbox.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.freeplay.toolbox.core.Shell
import com.freeplay.toolbox.data.NotifyRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NotifyInfo(
    val key: String,
    val pkg: String,
    val id: Int,
    val tag: String?,
    val title: String,
    val text: String,
    val ongoing: Boolean,
    val time: Long,
)

/**
 * 通知使用权监听器。
 *
 * 能力边界：只能「事后取消」，拦不住第一次出现 —— 被隐藏的通知仍会闪一下。
 * 常驻通知（前台服务 / FLAG_NO_CLEAR）系统不允许取消，会记进 [stuck] 计数并提示。
 */
class NotifyListener : NotificationListenerService() {

    companion object {
        /** 当前活动通知，key -> 信息 */
        val items = mutableStateMapOf<String, NotifyInfo>()

        var connected by mutableStateOf(false)
            private set

        /** 诊断计数：收到过多少条、成功拦掉多少条、拦不掉多少条 */
        var received by mutableStateOf(0)
            private set
        var suppressed by mutableStateOf(0)
            private set
        var stuck by mutableStateOf(0)
            private set
        var lastEvent by mutableStateOf("")
            private set

        @Volatile
        private var instance: NotifyListener? = null

        fun component(c: Context): ComponentName =
            ComponentName(c, NotifyListener::class.java)

        /** 组件名的两种写法：短写 pkg/.Cls 和全写 pkg/pkg.Cls，系统存的是后者 */
        private fun variants(c: Context): List<String> {
            val cn = component(c)
            val cls = cn.className ?: return emptyList()
            val full = "${cn.packageName}/$cls"
            val short = cls.removePrefix(cn.packageName + ".")
            return listOf(full, "${cn.packageName}/.$short")
        }

        private fun rawSetting(c: Context): String = try {
            android.provider.Settings.Secure.getString(
                c.contentResolver,
                "enabled_notification_listeners",
            ) ?: ""
        } catch (_: Throwable) {
            ""
        }

        /** 精确比对组件名（两种写法都认），避免包名前缀误判 */
        fun isGranted(c: Context): Boolean {
            val mine = variants(c).map { it.lowercase() }
            if (mine.isEmpty()) return false
            return rawSetting(c).split(':')
                .any { it.trim().lowercase() in mine }
        }

        /**
         * 授权通知使用权。
         *
         * 三条路依次走：直接写 secure 设置（最可靠，系统观察到设置变化会重新绑定）→
         * `cmd notification allow_listener`（部分 ROM 才通）→ requestRebind。
         * 写完轮询到自己真的被绑定才算成功，不再静默失败。
         */
        suspend fun grant(c: Context): Boolean {
            val cn = component(c)
            val full = "${cn.packageName}/${cn.className}"

            // 1) 写设置：保留别人的条目，去掉本包旧条目后追加
            val kept = rawSetting(c).split(':')
                .filter { it.isNotBlank() && !it.startsWith(cn.packageName + "/") }
            val merged = (kept + full).joinToString(":")
            Shell.runAsync("settings put secure enabled_notification_listeners '$merged'")

            // 2) 命令兜底
            Shell.runAsync("cmd notification allow_listener $full")

            delay(500)
            runCatching { requestRebind(cn) }

            repeat(8) { i ->
                if (connected && isGranted(c)) return true
                if (i == 2) Shell.runAsync("cmd notification allow_listener ${cn.flattenToString()}")
                if (i == 4) runCatching { requestRebind(cn) }
                delay(350)
            }
            return connected && isGranted(c)
        }

        /** 重启监听：先解绑再绑，专治「设置里有、系统不绑」的僵住状态 */
        suspend fun restart(c: Context): Boolean {
            val cn = component(c)
            Shell.runAsync("cmd notification disallow_listener ${cn.flattenToString()}")
            Shell.runAsync("cmd notification disallow_listener ${cn.packageName}/${cn.className}")
            delay(300)
            return grant(c)
        }

        /** 一段可复制的现场：设备和授权状态全在里面 */
        fun diagnostics(c: Context): String = buildString {
            appendLine("api=" + android.os.Build.VERSION.SDK_INT)
            appendLine("device=" + android.os.Build.MODEL)
            appendLine("granted=" + isGranted(c))
            appendLine("connected=" + connected)
            appendLine("expect=" + variants(c).firstOrNull())
            appendLine("setting=" + rawSetting(c))
            appendLine("received=" + received + " suppressed=" + suppressed + " stuck=" + stuck)
            append("lastEvent=" + lastEvent)
        }

        /**
         * 立刻把现有规则套到「已经在通知栏里的」通知上。
         * 规则是新加的、监听器刚连上、或用户点「重新应用」时都要走这里，
         * 否则要等下一条通知或应用自己更新才会生效。
         */
        fun applyNow(): Int = instance?.applyRulesNow() ?: 0

        private fun note(msg: String) {
            lastEvent = msg
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        job.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        note("监听已连接")
        refresh()
        applyRulesNow()
    }

    override fun onListenerDisconnected() {
        connected = false
        items.clear()
        note("监听已断开")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        received++
        if (shouldSuppress(n)) {
            hide(n)
            items.remove(n.key)
            return
        }
        items[n.key] = toInfo(n)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        items.remove(sbn.key)
    }

    /** 把规则套到当前活动通知上，返回命中的条数 */
    private fun applyRulesNow(): Int {
        val rules = NotifyRules.load(this)
        if (rules.isEmpty()) return 0
        var hits = 0
        runCatching {
            activeNotifications?.forEach { sbn ->
                if (rules.any { it.matches(sbn) }) {
                    hide(sbn)
                    items.remove(sbn.key)
                    hits++
                }
            }
        }
        if (hits > 0) note("重应用规则，命中 $hits 条")
        return hits
    }

    /**
     * 立刻取消，再补两次刀。
     * 有些应用（微信、QQ、各类前台服务）被取消后会马上重新 post 同一条，
     * 单次 cancel 看起来像"没生效"，所以这里退让式地再补两次并核对结果。
     */
    private fun hide(n: StatusBarNotification) {
        val key = n.key
        cancelNotification(key)
        suppressed++

        scope.launch {
            var still = stillActive(key)
            var attempts = 0
            while (still && attempts < 2) {
                delay(if (attempts == 0) 300L else 900L)
                cancelNotification(key)
                attempts++
                still = stillActive(key)
            }
            if (still) {
                stuck++
                note("无法取消 $key（常驻通知/前台服务）")
            }
        }
    }

    private fun stillActive(key: String): Boolean = try {
        activeNotifications?.any { it.key == key } == true
    } catch (_: Throwable) {
        false
    }

    private fun shouldSuppress(sbn: StatusBarNotification): Boolean {
        // 不要把自己应用的通知干掉
        if (sbn.packageName == packageName) return false
        return NotifyRules.load(this).any { it.matches(sbn) }
    }

    private fun refresh() {
        runCatching {
            activeNotifications?.forEach { sbn ->
                if (!shouldSuppress(sbn)) {
                    items[sbn.key] = toInfo(sbn)
                }
            }
        }
    }

    private fun toInfo(sbn: StatusBarNotification): NotifyInfo {
        val ex = sbn.notification?.extras
        val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ex?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        val flags = sbn.notification?.flags ?: 0
        val ongoing = sbn.isOngoing ||
            (flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (flags and Notification.FLAG_NO_CLEAR) != 0
        return NotifyInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            id = sbn.id,
            tag = sbn.tag,
            title = title,
            text = text,
            ongoing = ongoing,
            time = sbn.postTime,
        )
    }
}
