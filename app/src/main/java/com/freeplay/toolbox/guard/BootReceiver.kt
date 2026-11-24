package com.freeplay.toolbox.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机自启：只拉起关机检查，别的什么都不做 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.LOCKED_BOOT_COMPLETED") {
            return
        }
        if (GuardPrefs.enabled(context)) PowerGuardService.start(context)
    }
}
