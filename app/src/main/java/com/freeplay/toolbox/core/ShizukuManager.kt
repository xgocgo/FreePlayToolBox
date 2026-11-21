package com.freeplay.toolbox.core

import android.app.Activity
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 连接与授权管理。
 * 宿主 Activity 需实现 Shizuku.OnRequestPermissionResultListener 并把结果回传给 onPermissionResult。
 */
object ShizukuManager {

    const val REQUEST_CODE = 1001

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** 弹出 Shizuku 授权对话框（异步，结果回调到 Activity 的 listener） */
    fun requestPermission(activity: Activity) {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }

    fun onPermissionResult(granted: Boolean) {
        AppState.refresh()
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    /** Shizuku 服务版本，不可用时返回 -1 */
    fun version(): Int = try {
        Shizuku.getVersion()
    } catch (_: Throwable) {
        -1
    }
}
