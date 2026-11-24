package com.freeplay.toolbox.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.freeplay.toolbox.MainActivity

/** 主服务与守护进程共用的前台通知 */
object GuardNotify {
    private const val CHANNEL = "power_guard"
    const val MAIN_ID = 4102
    const val DAEMON_ID = 4103

    private fun channel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "关机前检查", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = "常驻检测关机行为，关掉它就不再拦截"
            }
        )
    }

    fun build(c: Context, id: Int, title: String, text: String): Notification {
        channel(c)
        val pi = PendingIntent.getActivity(
            c,
            0,
            Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(c, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    fun main(c: Context): Notification =
        build(c, MAIN_ID, "关机前检查已开启", "按电源键关机时会先检查未解冻的应用")

    fun daemon(c: Context): Notification =
        build(c, DAEMON_ID, "关机检查守护进程", "主服务被杀时负责拉起")
}
