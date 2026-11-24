package com.freeplay.toolbox.guard

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * 双进程保活的另一半：跑在 `:guard` 进程里，只做一件事 ——
 * 绑住 [PowerGuardService]，主进程一死就把它拉起来。
 *
 * 收益有限（系统真要清进程两边一起清），所以只在用户开着功能时启动，
 * 失败也不影响主服务。
 */
class GuardDaemonService : Service() {

    companion object {
        fun start(c: Context) {
            val i = Intent(c, GuardDaemonService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            revive()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        bindMain()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!GuardPrefs.enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 本进程要有个前台通知，否则后台被限时活不久；借用主服务的通知即可
        startForeground(
            GuardNotify.DAEMON_ID,
            GuardNotify.daemon(this),
        )
        if (!bound) bindMain()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unbindService(conn) }
        bound = false
        super.onDestroy()
    }

    private fun bindMain() {
        runCatching {
            bindService(
                Intent(this, PowerGuardService::class.java),
                conn,
                Context.BIND_AUTO_CREATE,
            )
        }
    }

    private fun revive() {
        if (!GuardPrefs.enabled(this)) return
        handler.postDelayed({
            runCatching { startService(Intent(this, PowerGuardService::class.java)) }
            runCatching { bindMain() }
        }, 1200)
    }
}
