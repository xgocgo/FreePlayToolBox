package com.freeplay.toolbox.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val pkg: String,
    val label: String,
    val versionName: String,
    val isSystem: Boolean,
    val isSuspended: Boolean,
    val isDisabled: Boolean,
    val icon: Drawable?,
    val lastUpdateTime: Long = 0L,   // 最后一次更新时间
    val firstInstallTime: Long = 0L, // 首次安装时间
) {
    /** 列表用缩略图，首次解码后缓存，避免每次重组重复解码 */
    val thumb: ImageBitmap? by lazy {
        icon?.let { d ->
            runCatching { d.toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
}

object AppRepository {

    fun listApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val result = mutableListOf<AppInfo>()
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        for (pkgInfo in packages) {
            val ai: ApplicationInfo = pkgInfo.applicationInfo ?: continue
            val label = try {
                pm.getApplicationLabel(ai).toString()
            } catch (_: Throwable) {
                ai.packageName
            }
            val icon = try {
                pm.getApplicationIcon(ai)
            } catch (_: Throwable) {
                null
            }
            result.add(
                AppInfo(
                    pkg = ai.packageName,
                    label = label,
                    versionName = pkgInfo.versionName ?: "-",
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isSuspended = (ai.flags and ApplicationInfo.FLAG_SUSPENDED) != 0,
                    isDisabled = !ai.enabled,
                    icon = icon,
                    lastUpdateTime = pkgInfo.lastUpdateTime,
                    firstInstallTime = pkgInfo.firstInstallTime,
                )
            )
        }
        return result.sortedBy { it.label.lowercase() }
    }
}
