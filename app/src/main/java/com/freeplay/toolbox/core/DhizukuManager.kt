package com.freeplay.toolbox.core

import android.content.Context
import com.rosan.dhizuku.api.Dhizuku

/**
 * Dhizuku 连接管理（设备所有者能力委托）。
 * P0 阶段只做初始化、状态检测和授权请求，具体能力在 P3 扩展。
 */
object DhizukuManager {

    fun init(context: Context) {
        try {
            Dhizuku.init(context)
        } catch (_: Exception) {
        }
    }

    /** 申请 Dhizuku 授权（异步，结果刷新全局状态） */
    fun requestPermission() {
        try {
            Dhizuku.requestPermission(object : com.rosan.dhizuku.api.DhizukuRequestPermissionListener() {
                override fun onRequestPermission(code: Int) {
                    AppState.refresh()
                }
            })
        } catch (_: Throwable) {
        }
    }

    /** Dhizuku 服务是否可用（Dhizuku App 已装且是设备所有者） */
    fun isAvailable(): Boolean = try {
        Dhizuku.isPermissionGranted()
    } catch (_: Exception) {
        false
    }
}
