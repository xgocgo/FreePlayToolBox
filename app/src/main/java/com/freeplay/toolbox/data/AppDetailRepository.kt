package com.freeplay.toolbox.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import com.freeplay.toolbox.core.Shell
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class PermInfo(
    val fullName: String,   // 完整权限名；AppOps 项为 "op:<操作名>"
    val granted: Boolean,
    val runtime: Boolean,   // true = 可修改（危险/开发权限或 AppOps 项）
    val opName: String? = null,   // 对应的 AppOps 操作名（支持三态）
    val opMode: String? = null,   // allow / foreground / ignore / default
    val protectionLevel: Int = 0, // 原始 protectionLevel，用于说明为何不可修改
    val opOnly: Boolean = false,  // true = 仅 AppOps 项（无对应清单权限）
) {
    val short: String get() = if (opOnly) (opName ?: fullName) else fullName.substringAfterLast('.')
    val zh: String get() = if (opOnly) opZh(short) else PermissionNames.zh(short)

    /** 不可修改的原因说明 */
    val blockedReason: String
        get() {
            val base = protectionLevel and 0xF
            return when {
                base == 1 -> "系统/签名权限，仅系统或应用签名本身可授予，Shizuku 也无法修改"
                base == 2 -> "危险权限，可通过开关修改"
                base == 3 -> "签名权限，仅当应用与声明者签名一致时可授予，Shizuku 无法修改"
                base == 0x10 -> "特权权限，仅系统应用可授予，Shizuku 无法修改"
                base == 4 -> "内部权限，系统内部使用"
                else -> "非运行时权限（安装时授予），无法动态修改"
            }
        }
}

data class AppSize(
    val codeMb: Double,
    val dataMb: Double,
    val cacheMb: Double,
    val totalMb: Double,
)

data class OpInfo(val name: String, val mode: String)

data class AppDetail(
    val pkg: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val size: AppSize?,
    val perms: List<PermInfo>,
    val launchIntent: Intent?,   // null = 无界面应用
    val targetSdk: Int,
    val minSdk: Int,
    val activities: List<String>,   // 全部 Activity 组件名，可 am start 启动
    val ops: List<OpInfo>,          // AppOps 操作控制列表
)

object AppDetailRepository {

    /** 并发版本：避免 pm dump / appops / stat 串行阻塞 */
    suspend fun loadAsync(context: Context, app: AppInfo): AppDetail {
        val (dumpRes, opsRes) = coroutineScope {
            val a = async { Shell.runAsync("pm dump ${app.pkg}") }
            val b = async { Shell.runAsync("cmd appops get ${app.pkg}") }
            a.await() to b.await()
        }
        return build(context, app, dumpRes.out, opsRes.out)
    }

    fun load(context: Context, app: AppInfo): AppDetail =
        build(context, app, Shell.run("pm dump ${app.pkg}").out, Shell.run("cmd appops get ${app.pkg}").out)

    private fun build(context: Context, app: AppInfo, dump: String, opsOut: String): AppDetail {
        val pm = context.packageManager
        val pi = try {
            pm.getPackageInfo(
                app.pkg,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES,
            )
        } catch (_: Throwable) {
            null
        }

        // distinct 防止清单重复声明导致列表 key 冲突
        val requested = (pi?.requestedPermissions?.toList() ?: emptyList()).distinct()

        var inInstall = false
        var inRuntime = false
        val grantedMap = mutableMapOf<String, Boolean>()
        val runtimeSet = mutableSetOf<String>()
        for (line in dump.lines()) {
            when {
                line.contains("install permissions:") -> { inInstall = true; inRuntime = false }
                line.contains("runtime permissions:") -> { inInstall = false; inRuntime = true }
                line.startsWith("      ") && line.contains(":") && !line.contains("granted=") -> { inInstall = false; inRuntime = false }
            }
            val permMatch = Regex("""(android\.[\w.]+|com\.[\w.]+|[\w.]+\.PERMISSION?[\w_]*):""").find(line)
            val perm = permMatch?.groupValues?.get(1)
            if (perm != null && line.contains("granted=")) {
                grantedMap[perm] = line.contains("granted=true")
                if (inRuntime) runtimeSet.add(perm)
            } else if (perm != null && inRuntime) {
                runtimeSet.add(perm)
            }
        }

        // AppOps 当前模式（用于三态：允许 / 仅前台允许 / 不允许）
        val opModeMap = mutableMapOf<String, String>()
        val opsList = mutableListOf<OpInfo>()
        for (line in opsOut.lines()) {
            val m = Regex("""(\w+):\s*(allow|ignore|foreground|default|deny)""").find(line)
            if (m != null) {
                opModeMap[m.groupValues[1]] = m.groupValues[2]
                opsList.add(OpInfo(m.groupValues[1], m.groupValues[2]))
            }
        }

        val perms = requested.map { name ->
            val short = name.substringAfterLast('.')
            val op = PermissionNames.appOpsOp(short)
            // 以 protectionLevel 为准判断能否动态修改：dangerous(2) 或带 development flag(0x20)
            val prot = try {
                pm.getPermissionInfo(name, 0).protectionLevel
            } catch (_: Throwable) {
                0
            }
            val base = prot and 0xF
            val modifiable = base == 2 || (prot and 0x20) != 0
            PermInfo(
                fullName = name,
                granted = grantedMap[name] ?: false,
                runtime = modifiable || (runtimeSet.contains(name) && base == 0),
                opName = op,
                opMode = op?.let { opModeMap[it] },
                protectionLevel = prot,
            )
        }

        // 合并 AppOps：权限已映射的 op 不重复；其余 op 作为独立可控制项加入
        val coveredOps = perms.mapNotNull { it.opName }.toSet()
        val opExtras = opsList
            .filter { it.name !in coveredOps }
            .map { op ->
                PermInfo(
                    fullName = "op:${op.name}",
                    granted = op.mode == "allow" || op.mode == "foreground",
                    runtime = true,
                    opName = op.name,
                    opMode = op.mode,
                    opOnly = true,
                )
            }

        // 排序：可修改的在前，不可修改的在后
        val merged = (perms + opExtras)
            .sortedWith(compareByDescending<PermInfo> { it.runtime }.thenBy { it.zh })

        return AppDetail(
            pkg = app.pkg,
            label = app.label,
            versionName = pi?.versionName ?: app.versionName,
            versionCode = pi?.longVersionCode ?: 0L,
            size = readSizeViaStats(context, app.pkg) ?: parseSize(dump),
            perms = merged,
            launchIntent = pm.getLaunchIntentForPackage(app.pkg),
            targetSdk = pi?.applicationInfo?.targetSdkVersion ?: 0,
            minSdk = pi?.applicationInfo?.minSdkVersion ?: 0,
            activities = try {
                pi?.activities?.filterNotNull()?.map { it.name } ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            },
            ops = opsList.sortedBy { it.name },
        )
    }

    /** 常见危险权限即使 dump 解析失败也默认可控制 */
    private fun isDangerousLike(name: String): Boolean = name in setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.CALL_PHONE",
        "android.permission.READ_PHONE_STATE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE",
    )

    private fun parseSize(dump: String): AppSize? {
        var code = -1.0; var data = -1.0; var cache = -1.0; var total = -1.0
        for (line in dump.lines()) {
            val t = line.trim()
            val kv = Regex("^(codeSize|dataSize|cacheSize|totalSize):\\s*(-?\\d+)").find(t)
            if (kv != null) {
                val bytes = kv.groupValues[2].toDouble()
                when (kv.groupValues[1]) {
                    "codeSize" -> code = bytes
                    "dataSize" -> data = bytes
                    "cacheSize" -> cache = bytes
                    "totalSize" -> total = bytes
                }
            }
        }
        if (total < 0) return null
        fun mb(v: Double) = if (v < 0) 0.0 else v / 1048576.0
        return AppSize(mb(code), mb(data), mb(cache), mb(total))
    }
}

    /** 通过 StorageStatsManager 读取大小（需要 shell 授予 GET_USAGE_STATS appop） */
    private fun readSizeViaStats(context: Context, pkg: String): AppSize? {
        return try {
            Shell.run("appops set ${context.packageName} GET_USAGE_STATS allow")
            val ssm = context.getSystemService(StorageStatsManager::class.java)
                ?: return null
            val uh = UserHandle.getUserHandleForUid(Process.myUid())
            val st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, uh)
            val mb = 1048576.0
            AppSize(
                codeMb = st.appBytes / mb,
                dataMb = st.dataBytes / mb,
                cacheMb = st.cacheBytes / mb,
                totalMb = (st.appBytes + st.dataBytes + st.cacheBytes) / mb,
            )
        } catch (_: Throwable) {
            null
        }
    }
