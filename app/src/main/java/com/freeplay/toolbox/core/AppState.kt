package com.freeplay.toolbox.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局状态：两个特权通道的连接情况。
 * UI 直接观察这些状态，MainActivity / ShizukuManager 负责更新。
 */
object AppState {
    /** Shizuku binder 是否存活（Shizuku 服务在运行） */
    var shizukuAlive by mutableStateOf(false)
        private set

    /** 本应用是否已获得 Shizuku 授权 */
    var shizukuGranted by mutableStateOf(false)
        private set

    /** Dhizuku 是否可用 */
    var dhizukuAvailable by mutableStateOf(false)
        private set

    fun refresh() {
        shizukuAlive = ShizukuManager.isBinderAlive()
        shizukuGranted = shizukuAlive && ShizukuManager.hasPermission()
        dhizukuAvailable = DhizukuManager.isAvailable()
    }
}
