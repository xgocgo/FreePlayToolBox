package com.freeplay.toolbox.data

import com.freeplay.toolbox.core.Shell

/**
 * 应用级优化开关：通知屏蔽、后台限制、电池优化白名单。
 * 全部走 shell（Shizuku），可逆。
 */
/** 各模式下的开关状态缓存，避免反复逐条查询（每条都是一次 shell 往返） */
object OptStateCache {
    private val maps = mutableMapOf<String, MutableMap<String, Boolean>>()

    fun get(mode: String, pkg: String): Boolean? = maps[mode]?.get(pkg)
    fun put(mode: String, pkg: String, value: Boolean) {
        maps.getOrPut(mode) { mutableMapOf() }[pkg] = value
    }
    fun has(mode: String): Boolean = maps[mode]?.isNotEmpty() == true
    fun invalidate() {
        maps.clear()
    }
}

object OptimizationRepository {

    // ---- 通知 ----
    suspend fun setNotifications(pkg: String, enabled: Boolean) {
        if (enabled) {
            Shell.runAsync("cmd appops set $pkg POST_NOTIFICATION allow")
            Shell.runAsync("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        } else {
            Shell.runAsync("cmd appops set $pkg POST_NOTIFICATION ignore")
            Shell.runAsync("pm revoke $pkg android.permission.POST_NOTIFICATIONS")
        }
    }

    suspend fun notificationsBlocked(pkg: String): Boolean =
        Shell.runAsync("cmd appops get $pkg POST_NOTIFICATION").out.contains("ignore")

    // ---- 后台限制 ----
    suspend fun setBackgroundLimited(pkg: String, limited: Boolean) {
        val mode = if (limited) "ignore" else "allow"
        Shell.runAsync("cmd appops set $pkg RUN_IN_BACKGROUND $mode")
        Shell.runAsync("cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode")
    }

    suspend fun backgroundLimited(pkg: String): Boolean =
        Shell.runAsync("cmd appops get $pkg RUN_IN_BACKGROUND").out.contains("ignore")

    // ---- 电池优化白名单 ----
    suspend fun setBatteryWhitelist(pkg: String, whitelisted: Boolean) {
        val op = if (whitelisted) "+" else "-"
        Shell.runAsync("cmd deviceidle whitelist $op$pkg")
    }

    suspend fun batteryWhitelisted(pkg: String): Boolean =
        Shell.runAsync("dumpsys deviceidle whitelist").out.contains(pkg)

    // ---- 无线 ADB ----
    suspend fun wifiAdbEnabled(): Boolean =
        Shell.runAsync("getprop service.adb.tcp.port").out.trim() == "5555"

    suspend fun setWifiAdb(enabled: Boolean) {
        if (enabled) {
            Shell.runAsync("setprop service.adb.tcp.port 5555")
        } else {
            Shell.runAsync("setprop service.adb.tcp.port -1")
        }
        Shell.runAsync("setprop ctl.restart adbd")
    }

    suspend fun wifiAdbIp(): String {
        val out = Shell.runAsync("ip -4 addr show wlan0").out
        return Regex("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)").find(out)?.groupValues?.get(1) ?: "未知"
    }

    // ---- 屏幕录制 ----
    suspend fun startScreenRecord(): String? {
        val path = "/sdcard/Download/DSH-Go/rec_${System.currentTimeMillis()}.mp4"
        if (!Shell.runAsync("mkdir -p /sdcard/Download/DSH-Go").ok) return null
        Shell.runAsync("pkill -f screenrecord; screenrecord --bit-rate 8000000 $path &")
        return path
    }

    suspend fun stopScreenRecord() {
        Shell.runAsync("pkill -f screenrecord")
    }
}
