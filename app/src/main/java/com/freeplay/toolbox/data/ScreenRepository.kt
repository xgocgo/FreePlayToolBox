package com.freeplay.toolbox.data

import com.freeplay.toolbox.core.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScreenInfo(
    val physicalSize: String,     // 如 1260x2720
    val overrideSize: String?,    // 用户覆盖，无则 null
    val physicalDensity: String,  // 如 480
    val overrideDensity: String?,
)

object ScreenRepository {

    suspend fun load(): ScreenInfo {
        val sizeOut = Shell.runAsync("wm size").out
        val denOut = Shell.runAsync("wm density").out
        return ScreenInfo(
            physicalSize = Regex("Physical size:\\s*(\\S+)").find(sizeOut)?.groupValues?.get(1) ?: "-",
            overrideSize = Regex("Override size:\\s*(\\S+)").find(sizeOut)?.groupValues?.get(1),
            physicalDensity = Regex("Physical density:\\s*(\\S+)").find(denOut)?.groupValues?.get(1) ?: "-",
            overrideDensity = Regex("Override density:\\s*(\\S+)").find(denOut)?.groupValues?.get(1),
        )
    }

    suspend fun setSize(w: Int, h: Int): Boolean =
        Shell.runAsync("wm size ${w}x${h}").ok

    suspend fun resetSize(): Boolean = Shell.runAsync("wm size reset").ok

    suspend fun setDensity(d: Int): Boolean = Shell.runAsync("wm density $d").ok

    suspend fun resetDensity(): Boolean = Shell.runAsync("wm density reset").ok

    /** 截屏保存到相册截图目录，返回文件路径或 null */
    suspend fun screenshot(): String? = withContext(Dispatchers.IO) {
        val name = "ToolBox_${System.currentTimeMillis()}.png"
        Shell.runAsync(
            "mkdir -p /sdcard/Pictures/Screenshots && screencap -p /sdcard/Pictures/Screenshots/$name"
        ).ok.let { if (it) "/sdcard/Pictures/Screenshots/$name" else null }
    }
}
