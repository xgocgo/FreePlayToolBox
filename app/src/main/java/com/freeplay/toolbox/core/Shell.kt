package com.freeplay.toolbox.core

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * 统一的特权命令执行层，全部走 Shizuku（ADB 级权限）。
 * 所有需要特权的操作（pm / am / settings）都经过这里，便于统一处理错误。
 */
data class ShellResult(
    val exitCode: Int,
    val out: String,
    val err: String,
) {
    val ok: Boolean get() = exitCode == 0
    fun message(): String =
        if (ok) out.trim()
        else (err.trim().ifEmpty { out.trim() }.ifEmpty { "exit=$exitCode" })
}

object Shell {

    private fun readAll(pfd: ParcelFileDescriptor): String = try {
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    } catch (_: Throwable) {
        ""
    }

    /** 同步执行，必须在工作线程调用 */
    fun run(cmd: String): ShellResult {
        if (!AppState.shizukuGranted) {
            return ShellResult(-1, "", "Shizuku 未授权，请先连接")
        }
        return try {
            val binder = Shizuku.getBinder()
                ?: return ShellResult(-1, "", "Shizuku binder 不可用")
            val service = IShizukuService.Stub.asInterface(
                ShizukuBinderWrapper(binder)
            ) ?: return ShellResult(-1, "", "无法连接 Shizuku 服务")
            val proc = service.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val out = readAll(proc.inputStream)
            val err = readAll(proc.errorStream)
            val code = proc.waitFor()
            ShellResult(code, out, err)
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: "命令执行失败")
        }
    }

    suspend fun runAsync(cmd: String): ShellResult =
        withContext(Dispatchers.IO) { run(cmd) }

    /** 并发执行多条命令，按顺序返回结果（详情页等场景避免串行等待） */
    suspend fun runAll(vararg cmds: String): List<ShellResult> =
        withContext(Dispatchers.IO) {
            cmds.map { async { run(it) } }.awaitAll()
        }

    // ---- P1 应用管理常用命令封装 ----

    suspend fun uninstall(pkg: String, keepData: Boolean = false): ShellResult =
        runAsync(if (keepData) "pm uninstall -k --user 0 $pkg" else "pm uninstall --user 0 $pkg")

    suspend fun suspend(pkg: String): ShellResult = runAsync("pm suspend --user 0 $pkg")

    suspend fun unsuspend(pkg: String): ShellResult = runAsync("pm unsuspend --user 0 $pkg")

    suspend fun disable(pkg: String): ShellResult = runAsync("pm disable-user --user 0 $pkg")

    suspend fun enable(pkg: String): ShellResult = runAsync("pm enable $pkg")

    /** 依次尝试多条命令，返回第一条成功的；全失败返回最后一条结果 */
    suspend fun firstOk(vararg cmds: String): ShellResult {
        var last = ShellResult(-1, "", "未执行")
        for (c in cmds) {
            last = runAsync(c)
            if (last.ok) return last
            // 明确的"未知命令"才继续换写法，其他错误直接返回，避免掩盖真实原因
            if (!last.message().contains("Unknown command", true) &&
                !last.message().contains("usage", true) &&
                !last.message().contains("Error: no such", true)
            ) return last
        }
        return last
    }

    /** 隐藏应用：pm hide 不接受 --user 参数，先试标准写法 */
    suspend fun hide(pkg: String): ShellResult =
        firstOk("pm hide $pkg", "pm hide --user 0 $pkg")

    suspend fun unhide(pkg: String): ShellResult =
        firstOk("pm unhide $pkg", "pm unhide --user 0 $pkg")

    suspend fun grant(pkg: String, perm: String): ShellResult = runAsync("pm grant $pkg $perm")

    suspend fun revoke(pkg: String, perm: String): ShellResult = runAsync("pm revoke $pkg $perm")

    suspend fun clear(pkg: String): ShellResult = runAsync("pm clear --user 0 $pkg")

    suspend fun forceStop(pkg: String): ShellResult = runAsync("am force-stop $pkg")
}
