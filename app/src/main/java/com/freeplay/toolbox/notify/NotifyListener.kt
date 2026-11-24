package com.freeplay.toolbox.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.freeplay.toolbox.core.Shell
import com.freeplay.toolbox.data.NotifyRules

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
 * 要完全不闪，只能整包关闭通知（设置页里的开关）。
 */
class NotifyListener : NotificationListenerService() {

    companion object {
        /** 当前活动通知，key -> 信息 */
        val items = mutableStateMapOf<String, NotifyInfo>()

        var connected by mutableStateOf(false)
            private set

        fun isGranted(c: Context): Boolean = try {
            val flat = android.provider.Settings.Secure.getString(
                c.contentResolver,
                "enabled_notification_listeners",
            ) ?: ""
            flat.contains(c.packageName)
        } catch (_: Throwable) {
            false
        }

        fun component(c: Context): ComponentName =
            ComponentName(c, NotifyListener::class.java)

        /** 用 shell 授权通知使用权（失败时让用户手动去设置） */
        suspend fun grant(c: Context): Boolean {
            val cn = component(c)
            val r = Shell.runAsync("cmd notification allow_listener ${cn.flattenToString()}")
            return r.ok || isGranted(c)
        }

    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        items.clear()
    }

    private fun refresh() {
        try {
            activeNotifications?.forEach { sbn ->
                if (!shouldSuppress(sbn)) {
                    items[sbn.key] = toInfo(sbn)
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (shouldSuppress(n)) {
            // 命中规则：立刻取消，表现为"这条永远看不到"
            runCatching { cancelNotification(n.key) }
            items.remove(n.key)
            return
        }
        items[n.key] = toInfo(n)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        items.remove(sbn.key)
    }

    private fun shouldSuppress(sbn: StatusBarNotification): Boolean {
        // 不要把自己应用的通知干掉
        if (sbn.packageName == packageName) return false
        return NotifyRules.load(this).any { it.matches(sbn) }
    }

    private fun toInfo(sbn: StatusBarNotification): NotifyInfo {
        val ex = sbn.notification?.extras
        val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ex?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        val ongoing = sbn.isOngoing ||
            (sbn.notification?.flags ?: 0 and Notification.FLAG_ONGOING_EVENT) != 0
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
