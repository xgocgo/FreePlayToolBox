package com.freeplay.toolbox.guard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 关机拦截的常驻服务。
 *
 * 原理是「检测 → 撤销 → 接管」三步，不是什么内核级拦截：
 * 1. 电源键长按弹出的关机菜单在窗口焦点里能读到（`GlobalActionsDialog`），
 *    无障碍服务在的话由它事件驱动告警，没有就每 700ms 轮询一次窗口焦点；
 * 2. 命中后往系统菜单注入一个 BACK，把这次关机取消掉；
 * 3. 立刻拉起 [ShutdownGuardActivity]，让用户看完未解冻的应用再决定。
 *
 * 拦不住的情况说清楚：长按电源 10 秒硬重启、或者某些 ROM 跳过菜单直接关机，
 * 这两条路没有应用层办法。
 */
class PowerGuardService : Service() {

    companion object {
        const val ACTION_STOP = "com.freeplay.toolbox.POWER_GUARD_STOP"

        @Volatile
        var running = false
            private set

        @Volatile
        private var instance: PowerGuardService? = null

        @Volatile
        private var lastHandledAt = 0L

        fun start(c: Context) {
            val i = Intent(c, PowerGuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            GuardDaemonService.start(c)
        }

        fun stop(c: Context) {
            GuardPrefs.setEnabled(c, false)
            c.stopService(Intent(c, PowerGuardService::class.java))
            c.stopService(Intent(c, GuardDaemonService::class.java))
        }

        /** 无障碍服务抓到了关机菜单，直接叫醒这里 */
        fun onPowerMenuDetected() {
            instance?.handleDetected()
        }

        fun resetCooldown() {
            lastHandledAt = 0L
        }
    }

    private val binder = Binder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(GuardNotify.MAIN_ID, GuardNotify.main(this))
        running = true
        if (watch?.isActive != true) watch = startWatch()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        if (instance === this) instance = null
        job.cancel()
        super.onDestroy()
    }

    // ---------- 检测 ----------

    private fun startWatch(): Job = scope.launch {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        while (isActive) {
            delay(700)
            if (!GuardPrefs.enabled(this@PowerGuardService)) continue
            if (!AppState.shizukuGranted) continue
            if (!pm.isInteractive) continue
            if (SystemClock.elapsedRealtime() - lastHandledAt < 4000) continue
            if (isMenuOpen()) handleDetected()
        }
    }

    private suspend fun isMenuOpen(): Boolean {
        val focused = Shell.run("dumpsys window | grep -m1 mCurrentFocus").out
        val s = if (focused.isBlank()) Shell.run("dumpsys window windows").out else focused
        if (s.isBlank()) return false
        return s.contains("globalactions", ignoreCase = true) ||
            s.contains("poweractions", ignoreCase = true)
    }

    // ---------- 撤销 + 接管 ----------

    private fun handleDetected() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHandledAt < 4000) return
        lastHandledAt = now
        scope.launch {
            // 注入 BACK 关掉系统关机菜单 —— 这一步就是"拦截"
            Shell.runAsync("input keyevent KEYCODE_BACK")
            delay(180)
            // 个别 ROM 的菜单不吃 BACK，退一步用 HOME 把菜单盖掉
            if (isMenuOpen()) Shell.runAsync("input keyevent KEYCODE_HOME")
            delay(120)
            val i = Intent(this@PowerGuardService, ShutdownGuardActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            runCatching { startActivity(i) }
        }
    }
}
