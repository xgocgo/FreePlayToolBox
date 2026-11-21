package com.freeplay.toolbox.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃日志：捕获未处理异常写入本地文件，下次启动由 UI 读取展示。
 */
object CrashLogger {

    private fun file(ctx: Context) = File(ctx.filesDir, "crash_last.txt")

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val version = try {
                    appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName ?: "?"
                } catch (_: Throwable) {
                    "?"
                }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                file(appCtx).writeText(
                    buildString {
                        appendLine("时间: $time")
                        appendLine("版本: $version")
                        appendLine("线程: ${thread.name}")
                        appendLine("异常: ${throwable.javaClass.name}")
                        appendLine("消息: ${throwable.message ?: "(无)"}")
                        appendLine()
                        appendLine(android.util.Log.getStackTraceString(throwable))
                        // 关键因果链
                        var cause = throwable.cause
                        while (cause != null && cause !== throwable) {
                            appendLine()
                            appendLine("Caused by: ${cause.javaClass.name}: ${cause.message ?: "(无)"}")
                            appendLine(android.util.Log.getStackTraceString(cause))
                            cause = cause.cause
                        }
                    }
                )
            } catch (_: Throwable) {
                // 崩溃时刻尽力而为，不能因写日志再抛错
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val f = file(context)
        return if (f.exists() && f.length() > 0) {
            try {
                f.readText()
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Throwable) {
        }
    }
}
