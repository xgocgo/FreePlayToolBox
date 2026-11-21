package com.freeplay.toolbox.data

import android.content.Context

/**
 * 应用列表内存缓存：加载过一次后再次进入页面不重复扫描
 * （扫描已安装应用较慢，图标解码开销大）。
 */
object AppListCache {
    @Volatile
    var apps: List<AppInfo>? = null
        private set

    @Volatile
    var loadedAt: Long = 0L
        private set

    val hasCache: Boolean get() = apps != null

    /** 命中缓存时返回结果，否则扫描并写入缓存 */
    fun getOrLoad(context: Context, force: Boolean = false): List<AppInfo> {
        val cached = apps
        if (!force && cached != null) return cached
        val fresh = AppRepository.listApps(context)
        apps = fresh
        loadedAt = System.currentTimeMillis()
        return fresh
    }

    fun invalidate() {
        apps = null
        loadedAt = 0L
    }
}
