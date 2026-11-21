package com.freeplay.toolbox.data

import android.content.Context
import com.freeplay.toolbox.core.Shell

/**
 * 操作记录：记录对应用做过的可撤销改动，支持一键还原。
 * 存 SharedPreferences，单条格式：time␁pkg␁label␁action␁undoCommand
 */
data class OpRecord(
    val time: Long,
    val pkg: String,
    val label: String,
    val action: String,
    val undoCommand: String,
) {
    val undoable: Boolean get() = undoCommand.isNotBlank()
}

object OperationLog {
    private const val PREF = "toolbox_prefs"
    private const val KEY = "op_log"
    private const val SEP = "\u0001"
    private const val MAX = 300

    fun load(context: Context): List<OpRecord> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val f = line.split(SEP)
            if (f.size < 5) null
            else OpRecord(
                time = f[0].toLongOrNull() ?: 0L,
                pkg = f[1],
                label = f[2],
                action = f[3],
                undoCommand = f[4],
            )
        }.sortedByDescending { it.time }
    }

    fun append(context: Context, pkg: String, label: String, action: String, undoCommand: String = "") {
        val list = load(context).toMutableList()
        list.add(0, OpRecord(System.currentTimeMillis(), pkg, label, action, undoCommand))
        save(context, list.take(MAX))
    }

    fun save(context: Context, list: List<OpRecord>) {
        val raw = list.joinToString("\n") {
            listOf(
                it.time.toString(), it.pkg, it.label, it.action, it.undoCommand,
            ).joinToString(SEP)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, raw).apply()
    }

    fun remove(context: Context, record: OpRecord) {
        save(context, load(context).filterNot { it.time == record.time && it.pkg == record.pkg })
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** 执行撤销命令，成功则从记录中移除 */
    suspend fun undo(context: Context, record: OpRecord): Boolean {
        if (!record.undoable) return false
        val ok = Shell.runAsync(record.undoCommand).ok
        if (ok) remove(context, record)
        return ok
    }

    /** 已隐藏的应用（从记录中提取，仍处于隐藏状态） */
    fun hiddenPackages(context: Context): List<OpRecord> = load(context)
        .filter { it.action == Action.HIDE.label }
        .filterNot { rec -> load(context).any { it.pkg == rec.pkg && it.action == Action.UNHIDE.label && it.time > rec.time } }
        .distinctBy { it.pkg }

    enum class Action(val label: String, val undoLabel: String) {
        UNINSTALL("卸载", "重新安装"),
        FREEZE("冻结", "取消冻结"),
        UNFREEZE("解冻", "冻结"),
        HIDE("隐藏", "取消隐藏"),
        UNHIDE("显示", "隐藏"),
        DISABLE("停用", "启用"),
        ENABLE("启用", "停用"),
        CLEAR("清除数据", ""),
        REVOKE_PERM("撤销权限", ""),
        NOTIFY_OFF("关闭通知", "开启通知"),
        BG_LIMIT("限制后台", "取消限制"),
        BATTERY_NO_OPT("加入电池白名单", "移出白名单"),
    }
}
