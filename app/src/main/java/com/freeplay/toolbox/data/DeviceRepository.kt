package com.freeplay.toolbox.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val batteryPercent: Int,
    val memUsedPercent: Int,
    val memTotalGb: String,
    val storageUsedPercent: Int,
    val storageTotalGb: String,
    val screenPx: String,
    val abi: String,
)

object DeviceRepository {

    fun load(context: Context): DeviceInfo {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 1..100 } ?: run {
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                ?.times(100)?.div(sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1).coerceAtLeast(1)) ?: 0
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val memTotal = mem.totalMem
        val memUsedPct = ((memTotal - mem.availMem) * 100 / memTotal).toInt()

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val usedPct = ((totalBytes - stat.availableBytes) * 100 / totalBytes).toInt()

        val metrics = context.resources.displayMetrics

        return DeviceInfo(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            batteryPercent = battery,
            memUsedPercent = memUsedPct,
            memTotalGb = String.format("%.1f GB", memTotal / 1073741824.0),
            storageUsedPercent = usedPct,
            storageTotalGb = String.format("%.0f GB", totalBytes / 1073741824.0),
            screenPx = "${metrics.widthPixels} × ${metrics.heightPixels}",
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "-",
        )
    }
}
