package com.freeplay.toolbox.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.Shell
import com.freeplay.toolbox.data.AppDetail
import com.freeplay.toolbox.data.AppDetailRepository
import com.freeplay.toolbox.data.AppInfo
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.NeonBanner
import com.freeplay.toolbox.ui.components.RiskConfirmDialog
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.components.StatusPill
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonPurple
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppDetailScreen(
    app: AppInfo,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 只读一次授权状态，避免每个列表项各读一次全局 state 造成大面积重组
    val granted = AppState.shizukuGranted

    var detail by remember { mutableStateOf<AppDetail?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var pendingRisk by remember { mutableStateOf<String?>(null) }
    var pendingRisks by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var comps by remember { mutableStateOf<List<com.freeplay.toolbox.data.CompEntry>>(emptyList()) }
    var compKind by remember { mutableStateOf("Activity") }
    var compExpanded by remember(app.pkg) { mutableStateOf(false) }

    BackHandler { onBack() }

    fun refresh() {
        scope.launch { detail = AppDetailRepository.loadAsync(context, app) }
    }
    LaunchedEffect(app.pkg) { refresh() }

    LaunchedEffect(app.pkg, granted) {
        if (granted) {
            comps = withContext(Dispatchers.IO) {
                com.freeplay.toolbox.data.ComponentRepository.load(context, app.pkg)
            }
        }
    }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    fun runWithRisk(title: String, risks: List<String>, action: () -> Unit) {
        pendingRisk = title
        pendingRisks = risks
        pendingAction = action
    }

    fun run(action: () -> Unit) {
        action()
    }

    Column(modifier.fillMaxSize()) {
        NeonBanner(title = app.label, subtitle = "应用详情", modifier = Modifier.padding(16.dp))

        val d = detail
        if (d == null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("读取详情中…", modifier = Modifier.padding(8.dp), fontSize = 12.sp)
            }
        } else {
            LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                val iconBitmap = remember(app.pkg) {
                    app.icon?.let { d -> runCatching { d.toBitmap(128, 128).asImageBitmap() }.getOrNull() }
                }
                NeonCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                )
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(d.versionName, color = NeonCyan, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "v${d.versionCode} · target SDK ${d.targetSdk}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        Text("包名", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            d.pkg,
                            fontSize = 13.sp,
                            color = NeonCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    copyToClipboard(context, d.pkg)
                                    toast = "包名已复制"
                                },
                        )
                        Spacer(Modifier.size(4.dp))
                        InfoRow(
                            "总大小",
                            if (d.size != null) "%.1f MB".format(d.size.totalMb) else "获取失败",
                        )
                        InfoRow(
                            "应用本体",
                            if (d.size != null) "%.1f MB".format(d.size.codeMb) else "-",
                        )
                        InfoRow(
                            "数据大小",
                            if (d.size != null) "%.1f MB".format(d.size.dataMb) else "-",
                        )
                        InfoRow(
                            "缓存大小",
                            if (d.size != null) "%.1f MB".format(d.size.cacheMb) else "-",
                        )
                    }
                }
            }

            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("应用操作", style = MaterialTheme.typography.titleMedium)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = granted,
                                onClick = {
                                    if (d.launchIntent == null) {
                                        toast = "该应用没有可打开的界面（模块 / 后台服务类应用）"
                                    } else {
                                        context.startActivity(d.launchIntent)
                                    }
                                },
                            ) { Text("打开") }
                            OutlinedButton(
                                enabled = granted,
                                onClick = {
                                    runWithRisk("强行停止 ${app.label}", listOf(
                                        "应用未保存的数据将丢失",
                                        "应用正在进行的下载、任务将被中断",
                                    )) {
                                        scope.launch {
                                            val r = Shell.forceStop(app.pkg)
                                            toast = if (r.ok) "已强行停止" else r.message()
                                        }
                                    }
                                },
                            ) { Text("强行停止") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = granted,
                                onClick = {
                                    runWithRisk("暂停 ${app.label}", listOf(
                                        "应用将无法启动、通知被隐藏、桌面图标消失",
                                        "通过「恢复」可还原",
                                    )) {
                                        scope.launch {
                                            val r = Shell.suspend(app.pkg)
                                            toast = if (r.ok) "已暂停（suspend）" else r.message()
                                            refresh()
                                        }
                                    }
                                },
                            ) { Text(if (app.isSuspended) "重新暂停" else "暂停") }
                            OutlinedButton(
                                enabled = granted,
                                onClick = {
                                    run {
                                        scope.launch {
                                            val r = Shell.unsuspend(app.pkg)
                                            toast = if (r.ok) "已恢复" else r.message()
                                            refresh()
                                        }
                                    }
                                },
                            ) { Text("恢复") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = granted,
                                onClick = {
                                    runWithRisk("隐藏 ${app.label}", listOf(
                                        "应用将从桌面与启动器消失（不卸载，数据保留）",
                                        "通过「取消隐藏」还原",
                                    )) {
                                        scope.launch {
                                            val r = Shell.hide(app.pkg)
                                            toast = if (r.ok) "已隐藏" else r.message()
                                        }
                                    }
                                },
                            ) { Text("隐藏") }
                            OutlinedButton(
                                enabled = granted,
                                onClick = {
                                    run {
                                        scope.launch {
                                            val r = Shell.unhide(app.pkg)
                                            toast = if (r.ok) "已取消隐藏" else r.message()
                                        }
                                    }
                                },
                            ) { Text("取消隐藏") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = granted,
                                onClick = {
                                    runWithRisk("停用 ${app.label}", listOf(
                                        "应用将被禁用，所有组件不可运行",
                                        "停用系统关键组件可能导致系统异常",
                                        "通过「启用」还原",
                                    )) {
                                        scope.launch {
                                            val r = Shell.disable(app.pkg)
                                            toast = if (r.ok) "已停用" else r.message()
                                        }
                                    }
                                },
                            ) { Text("停用") }
                            OutlinedButton(
                                enabled = granted,
                                onClick = {
                                    run {
                                        scope.launch {
                                            val r = Shell.enable(app.pkg)
                                            toast = if (r.ok) "已启用" else r.message()
                                        }
                                    }
                                },
                            ) { Text("启用") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = granted,
                                onClick = {
                                    runWithRisk("卸载 ${app.label}", listOf(
                                        "应用将被立即卸载，应用数据一并删除，不可撤销",
                                        "卸载系统应用可能影响系统功能",
                                    )) {
                                        scope.launch {
                                            val r = Shell.uninstall(app.pkg)
                                            toast = if (r.ok) "已卸载" else r.message()
                                            if (r.ok) onBack()
                                        }
                                    }
                                },
                            ) { Text("卸载", color = NeonRed) }
                            OutlinedButton(
                                enabled = granted,
                                onClick = {
                                    scope.launch {
                                        val paths = Shell.runAsync("pm path ${app.pkg}").out
                                            .lines()
                                            .filter { it.startsWith("package:") }
                                            .map { it.removePrefix("package:") }
                                        if (paths.isEmpty()) {
                                            toast = "未找到安装包路径"
                                        } else {
                                            val dst = "/sdcard/Download/DSH-Go/apk/${app.pkg}"
                                            Shell.runAsync("mkdir -p $dst")
                                            var copied = 0
                                            paths.forEach { p ->
                                                if (Shell.runAsync("cp $p $dst/").ok) copied++
                                            }
                                            toast = "已提取 $copied 个文件到 $dst"
                                        }
                                    }
                                },
                            ) { Text("提取 APK") }
                        }
                    }
                }
            }

            item {
                var actsExpanded by remember(d.pkg) { mutableStateOf(false) }
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { actsExpanded = !actsExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Activity（${d.activities.size}）", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (actsExpanded) "收起" else "展开",
                                fontSize = 12.sp,
                                color = NeonCyan,
                            )
                        }
                        if (d.activities.isEmpty()) {
                            Text(
                                "该应用没有 Activity 组件（纯后台服务/模块）",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (actsExpanded) {
                            Text(
                                "点击直接启动对应界面",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            d.activities.forEach { act ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        act.substringAfterLast('.'),
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "▶",
                                        color = NeonCyan,
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .clickable {
                                                scope.launch {
                                                    val r = Shell.runAsync("am start -n ${d.pkg}/${act}")
                                                    toast = if (r.ok) "已启动 ${act.substringAfterLast('.')}"
                                                    else "启动失败：${r.message().take(80)}"
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { compExpanded = !compExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("组件管理（${comps.size}）", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text(if (compExpanded) "收起" else "展开", fontSize = 12.sp, color = NeonCyan)
                        }
                        Text(
                            "单独禁用某个组件，其余功能不受影响（常用于只关推送服务或开机自启）",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (compExpanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Activity", "Service", "Receiver", "Provider").forEach { k ->
                                    val has = comps.any { it.kind == k }
                                    SmallPill(k, compKind == k, has) { compKind = k }
                                }
                            }
                            val list = comps.filter { it.kind == compKind }
                            if (list.isEmpty()) {
                                Text(
                                    "该类下没有组件",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            list.forEach { c ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        c.shortName,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    Switch(
                                        checked = c.enabled,
                                        enabled = granted,
                                        onCheckedChange = { want ->
                                            scope.launch {
                                                com.freeplay.toolbox.data.ComponentRepository
                                                    .setEnabled(app.pkg, c.fullName, want)
                                                comps = withContext(Dispatchers.IO) {
                                                    com.freeplay.toolbox.data.ComponentRepository.load(context, app.pkg)
                                                }
                                                toast = if (want) "已启用 ${c.shortName}" else "已禁用 ${c.shortName}"
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "权限控制（${d.perms.size} 项）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                if (d.perms.isEmpty()) {
                    Text(
                        "该应用未申请任何权限",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            items(d.perms, key = { it.fullName }, contentType = { "perm" }) { perm ->
                PermRow(
                    perm = perm,
                    enabled = granted && perm.runtime,
                    onToggle = { want ->
                        runWithRisk(
                            if (want) "授予权限 ${perm.zh}" else "撤销权限 ${perm.zh}",
                            listOf(
                                if (want) "应用将获得对应能力（如相机、位置、短信）"
                                else "应用对应功能可能失效甚至崩溃",
                                "部分系统应用恢复后可能行为异常",
                            ),
                        ) {
                            scope.launch {
                                val r = if (want) Shell.grant(app.pkg, perm.fullName)
                                else Shell.revoke(app.pkg, perm.fullName)
                                toast = if (r.ok) (if (want) "已授予" else "已撤销") else r.message()
                                refresh()
                            }
                        }
                    },
                    onSetState = { state ->
                        val label = when (state) {
                            "allow" -> "允许"
                            "foreground" -> "仅开屏时允许"
                            else -> "不允许"
                        }
                        runWithRisk(
                            "${perm.zh} → $label",
                            listOf(
                                when (state) {
                                    "allow" -> "应用将始终可以使用该能力（包括后台）"
                                    "foreground" -> "应用开屏在前台时可使用，切到后台后系统将忽略其使用（可阻止后台滥用，例如摇一摇广告、后台偷录）"
                                    else -> "应用将无法使用该能力，相关功能可能失效甚至崩溃"
                                },
                                "部分系统应用调整后可能行为异常",
                            ),
                        ) {
                            scope.launch {
                                if (!perm.opOnly) {
                                    if (state == "ignore") {
                                        Shell.revoke(app.pkg, perm.fullName)
                                    } else {
                                        Shell.grant(app.pkg, perm.fullName)
                                    }
                                }
                                perm.opName?.let { op ->
                                    Shell.runAsync("cmd appops set ${app.pkg} $op $state")
                                }
                                refresh()
                                toast = "已设置：${perm.zh} → $label"
                            }
                        }
                    },
                )
            }

        }
        }
    }

    pendingRisk?.let { title ->
        RiskConfirmDialog(
            title = title,
            risks = pendingRisks,
            onDismiss = { pendingRisk = null; pendingAction = null },
            onConfirm = {
                pendingRisk = null
                pendingAction?.invoke()
                pendingAction = null
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 13.sp,
            color = NeonCyan,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f, fill = false),
        )
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    try {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
    } catch (_: Throwable) {
    }
}

@Composable
private fun PermRow(
    perm: com.freeplay.toolbox.data.PermInfo,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onSetState: (String) -> Unit,
) {
    NeonCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(perm.zh, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        perm.short,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    !perm.runtime -> StatusPill("不可修改", MaterialTheme.colorScheme.outline)
                    perm.opName == null -> Switch(
                        checked = perm.granted,
                        onCheckedChange = onToggle,
                        enabled = enabled,
                    )
                }
            }
            if (!perm.runtime) {
                Text(
                    perm.blockedReason,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (perm.runtime && perm.opName != null) {
                val mode = perm.opMode
                val allowSel = mode == "allow" || (mode == null && perm.granted)
                val fgSel = mode == "foreground"
                val denySel = mode == "ignore"
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallPill("允许", allowSel, enabled) { onSetState("allow") }
                    SmallPill("仅开屏时允许", fgSel, enabled) { onSetState("foreground") }
                    SmallPill("不允许", denySel, enabled) { onSetState("ignore") }
                }
                if (fgSel) {
                    Text(
                        "前台可用，后台自动忽略（适合限制摇一摇/运动识别类权限）",
                        fontSize = 10.sp,
                        color = NeonPurple,
                    )
                }
            }
        }
    }
}
