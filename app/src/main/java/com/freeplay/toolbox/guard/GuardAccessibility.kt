package com.freeplay.toolbox.guard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 只做侦测，不参与点击。
 *
 * 系统弹出关机菜单时会发一条 TYPE_WINDOW_STATE_CHANGED，
 * class 是 `com.android.systemui.globalactions.GlobalActionsDialog`。
 * 抓到就通知常驻服务去处理；撤销动作由服务通过 Shizuku 注入按键完成。
 *
 * 没有这个服务也能用 —— 主服务会退回 700ms 轮询窗口焦点，只是慢一点。
 */
class GuardAccessibility : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val cls = e.className?.toString() ?: return
        val pkg = e.packageName?.toString() ?: ""
        if (!cls.contains("globalactions", ignoreCase = true)) return
        if (pkg != "com.android.systemui" && pkg != "android") return
        PowerGuardService.onPowerMenuDetected()
    }

    override fun onInterrupt() {}
}
