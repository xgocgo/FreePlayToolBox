package com.freeplay.toolbox.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 悬浮窗监视器：负载 / 进程 / 帧率，可自由拖动且限制在屏幕内。
 * 数据源：/proc/stat、dumpsys cpuinfo、Choreographer 帧回调。
 */
class OverlayService : Service() {

    enum class Kind { LOAD, PROCESS, FPS, TEMP }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIFY_ID = 4101
        const val ACTION_SYNC = "com.freeplay.toolbox.OVERLAY_SYNC"

        /** 当前开启的悬浮窗（进程内状态，重启后失效） */
        val active: MutableSet<Kind> = mutableSetOf()

        fun sync(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_SYNC)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val views = mutableMapOf<Kind, TextView>()
    private var job: Job? = null
    private var started = false

    private var lastCpuIdle = 0.0
    private var lastCpuTotal = 0.0

    private var frameCount = 0
    private var fps = 0f
    private var frameCallback: Choreographer.FrameCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            active.isEmpty() -> {
                stopSelf()
                return START_NOT_STICKY
            }
            !started -> {
                startForeground(NOTIFY_ID, buildNotification())
                started = true
            }
        }
        rebuildViews()
        return START_STICKY
    }

    private fun buildNotification() = androidx.core.app.NotificationCompat.Builder(this, ensureChannel())
        .setContentTitle("工具箱悬浮窗运行中")
        .setContentText("负载 / 进程 / 帧率监视")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setOngoing(true)
        .build()

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "悬浮窗", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
        return CHANNEL
    }

    private fun rebuildViews() {
        // 移除已关闭的
        views.keys.filter { it !in active }.forEach { kind ->
            views.remove(kind)?.let { v -> runCatching { wm.removeView(v) } }
        }
        // 添加新开的
        active.filter { it !in views.keys }.forEach { addView(it) }
        if (active.isEmpty()) stopSelf()
    }

    private fun addView(kind: Kind) {
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(Typeface.MONOSPACE)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xCC101014.toInt())
                setStroke(dp(1), 0x66FFFFFF)
            }
            text = "…"
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(160 + views.size * 68)
        }
        attachDrag(tv, params)
        runCatching { wm.addView(tv, params) }
        views[kind] = tv
        ensureTicker()
    }

    /** 拖动，并把位置限制在屏幕内 */
    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX - params.x
                    downY = e.rawY - params.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val m = resources.displayMetrics
                    val maxX = (m.widthPixels - v.width).coerceAtLeast(0)
                    val maxY = (m.heightPixels - v.height).coerceAtLeast(0)
                    params.x = (e.rawX - downX).toInt().coerceIn(0, maxX)
                    params.y = (e.rawY - downY).toInt().coerceIn(0, maxY)
                    runCatching { wm.updateViewLayout(v, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun ensureTicker() {
        if (job != null) return
        // 帧率统计
        if (Build.VERSION.SDK_INT >= 16) {
            frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    frameCount++
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            Choreographer.getInstance().postFrameCallback(frameCallback!!)
        }
        job = scope.launch {
            var tick = 0
            while (true) {
                delay(1000)
                tick++
                if (OverlayService.Kind.LOAD in views) updateLoad()
                if (OverlayService.Kind.FPS in views) updateFps()
                if (OverlayService.Kind.PROCESS in views && tick % 2 == 0) updateProcesses()
                if (OverlayService.Kind.TEMP in views) updateTemp()
            }
        }
    }

    private suspend fun updateLoad() {
        val tv = views[Kind.LOAD] ?: return
        val cpu = with(Dispatchers.IO) { readCpu() }
        val mem = memPercent()
        tv.text = "CPU ${"%.0f".format(cpu)}%\n内存 ${mem}%"
    }

    private fun updateFps() {
        val tv = views[Kind.FPS] ?: return
        fps = frameCount.toFloat()
        frameCount = 0
        tv.text = "FPS ${fps.toInt()}"
    }

    private suspend fun updateProcesses() {
        val tv = views[Kind.PROCESS] ?: return
        val out = with(Dispatchers.IO) { Shell.run("dumpsys cpuinfo").out }
        val lines = out.lines()
            .mapNotNull { line ->
                val m = Regex("""^\s*([\d.]+)% (\d+)/(\S+):""").find(line) ?: return@mapNotNull null
                val pct = m.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val name = m.groupValues[3].trim().trimEnd(':')
                pct to name
            }
            .filter { it.first > 0.4f }
            .take(6)
        tv.text = if (lines.isEmpty()) "进程 · 空闲" else
            "进程（CPU）\n" + lines.joinToString("\n") {
                "%.0f%% %s".format(it.first, it.second.take(18))
            }
    }

    /** 温度：解析 dumpsys thermalservice 的 HAL 温度列表 */
    private suspend fun updateTemp() {
        val tv = views[Kind.TEMP] ?: return
        val out = with(Dispatchers.IO) { Shell.run("dumpsys thermalservice").out }
        val temps = Regex("""mValue=([\d.]+).*?mName=([^,}]+)""")
            .findAll(out)
            .mapNotNull { m ->
                val v = m.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val name = m.groupValues[2].trim()
                if (v <= 0f || v > 150f) null else name to v
            }
            .distinctBy { it.first }
            .sortedByDescending { it.second }
            .take(4)
            .toList()
        tv.text = if (temps.isEmpty()) {
            "温度 · 不可用\n（该 ROM 未公开传感器）"
        } else {
            "温度（°C）\n" + temps.joinToString("\n") { "%.1f %s".format(it.second, it.first.take(12)) }
        }
    }

    /** 读取 /proc/stat 计算整体 CPU 占用 */
    private fun readCpu(): Double {
        val line = try {
            java.io.File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") }
        } catch (_: Throwable) {
            Shell.run("/proc/stat").out.lines().firstOrNull { it.startsWith("cpu ") }
        } ?: return 0.0
        val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toDoubleOrNull() }
        if (parts.size < 4) return 0.0
        val idle = parts[3] + (parts.getOrNull(4) ?: 0.0)
        val total = parts.sum()
        val dIdle = idle - lastCpuIdle
        val dTotal = total - lastCpuTotal
        lastCpuIdle = idle
        lastCpuTotal = total
        if (dTotal <= 0) return 0.0
        return ((1.0 - dIdle / dTotal) * 100).coerceIn(0.0, 100.0)
    }

    private fun memPercent(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return ((info.totalMem - info.availMem) * 100 / info.totalMem).toInt()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        job?.cancel()
        frameCallback?.let { runCatching { Choreographer.getInstance().removeFrameCallback(it) } }
        views.values.forEach { v -> runCatching { wm.removeView(v) } }
        views.clear()
        scope.cancel()
        super.onDestroy()
    }
}
