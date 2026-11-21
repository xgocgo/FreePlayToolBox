package com.freeplay.toolbox

import android.app.Application
import com.freeplay.toolbox.core.CrashLogger
import com.freeplay.toolbox.core.DhizukuManager

class ToolBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        DhizukuManager.init(this)
    }
}
