package com.freeplay.toolbox.data

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import com.freeplay.toolbox.core.Shell

/** 一个可单独启停的组件 */
data class CompEntry(
    val fullName: String,   // pkg/.Cls
    val kind: String,       // Activity / Service / Receiver / Provider
    val shortName: String,
    val enabled: Boolean,
)

object ComponentRepository {

    /** 读取应用全部组件 */
    fun load(context: Context, pkg: String): List<CompEntry> {
        val pm = context.packageManager
        val out = mutableListOf<CompEntry>()
        val pi = try {
            pm.getPackageInfo(
                pkg,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS,
            )
        } catch (_: Throwable) {
            return emptyList()
        }

        fun add(list: Array<out ComponentInfo>?, kind: String) {
            list?.filterNotNull()?.forEach { c ->
                val name = c.name ?: return@forEach
                if (c.packageName != pkg) return@forEach
                val enabled = c.isEnabled
                out.add(
                    CompEntry(
                        fullName = name,
                        kind = kind,
                        shortName = name.removePrefix("$pkg."),
                        enabled = enabled,
                    )
                )
            }
        }
        add(pi.activities, "Activity")
        add(pi.services, "Service")
        add(pi.receivers, "Receiver")
        add(pi.providers, "Provider")
        return out.sortedWith(compareBy({ it.kind }, { it.shortName }))
    }

    /**
     * 启用/禁用单个组件。
     * 禁用后该组件不再被系统调用，但整包其他功能不受影响。
     */
    suspend fun setEnabled(pkg: String, fullName: String, enabled: Boolean) {
        val cmd = if (enabled) "pm enable" else "pm disable"
        Shell.runAsync("$cmd $pkg/$fullName")
    }

    /** 该应用是否有被禁用的组件 */
    fun disabledCount(list: List<CompEntry>): Int = list.count { !it.enabled }
}
