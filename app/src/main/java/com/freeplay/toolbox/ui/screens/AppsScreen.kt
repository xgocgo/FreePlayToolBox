package com.freeplay.toolbox.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.Shell
import com.freeplay.toolbox.data.AppInfo
import com.freeplay.toolbox.data.AppListCache
import com.freeplay.toolbox.data.AppRepository
import com.freeplay.toolbox.ui.components.NeonBanner
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.components.RiskConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SortBy { NAME, UPDATED, INSTALLED }

private enum class BatchAction(val title: String, val risks: List<String>) {
    UNINSTALL(
        "卸载", listOf(
            "应用将立即从当前用户移除，不会弹出系统确认框",
            "卸载系统应用可能影响系统功能，个别组件需恢复出厂设置才能还原",
            "此操作不可撤销（用户应用需重新安装）",
        )
    ),
    CLEAR(
        "清除数据", listOf(
            "应用的登录状态、存档、聊天记录等全部数据将被永久清除，无法恢复",
            "应用将回到首次安装状态",
        )
    ),
    FREEZE(
        "冻结", listOf(
            "应用将无法启动、桌面图标消失、通知被隐藏",
            "冻结输入法、桌面等系统关键组件可能导致系统异常",
        )
    ),
    UNFREEZE(
        "解冻", listOf(
            "被冻结的应用将恢复为可用状态",
        )
    ),
    FORCE_STOP(
        "强停", listOf(
            "应用未保存的数据将丢失",
            "应用的后台任务、下载将被中断",
        )
    ),
}

@Composable
fun AppsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    val flt = remember { context.getSharedPreferences("apps_filter", android.content.Context.MODE_PRIVATE) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(flt.getBoolean("showSystem", false)) }
    var openApp by remember { mutableStateOf<AppInfo?>(null) }
    var sortBy by remember {
        mutableStateOf(
            runCatching { SortBy.valueOf(flt.getString("sortBy", SortBy.NAME.name)!!) }
                .getOrDefault(SortBy.NAME)
        )
    }
    var ascending by remember { mutableStateOf(flt.getBoolean("ascending", true)) }
    val selected = remember { mutableStateMapOf<String, AppInfo>() }
    var confirmAction by remember { mutableStateOf<BatchAction?>(null) }
    var running by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var refreshing by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var showOptimize by remember { mutableStateOf(false) }

    fun load(force: Boolean) {
        scope.launch {
            val hadCache = AppListCache.hasCache
            if (force) refreshing = true
            val list = withContext(Dispatchers.IO) { AppListCache.getOrLoad(context, force) }
            apps = list
            refreshing = false
            if (!force && hadCache) {
                toast = "已使用缓存的列表，在列表上向下拖动可刷新"
            }
        }
    }
    LaunchedEffect(Unit) { load(false) }

    // 筛选条件变化即持久化，下次进入保持上次的选择
    LaunchedEffect(showSystem, sortBy, ascending) {
        flt.edit()
            .putBoolean("showSystem", showSystem)
            .putString("sortBy", sortBy.name)
            .putBoolean("ascending", ascending)
            .apply()
    }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            toast = null
        }
    }

    val visible = apps
        ?.filter { if (showSystem) it.isSystem else !it.isSystem }
        ?.filter {
            query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
        }
        ?.let { list ->
            val sorted = when (sortBy) {
                SortBy.NAME ->
                    if (ascending) list.sortedBy { it.label.lowercase() }
                    else list.sortedByDescending { it.label.lowercase() }
                SortBy.UPDATED ->
                    if (ascending) list.sortedBy { it.lastUpdateTime }
                    else list.sortedByDescending { it.lastUpdateTime }
                SortBy.INSTALLED ->
                    if (ascending) list.sortedBy { it.firstInstallTime }
                    else list.sortedByDescending { it.firstInstallTime }
            }
            sorted.sortedByDescending { it in selected.values }
        }

    if (showManage) {
        ManageScreen(onBack = { showManage = false; load(true) }, modifier = modifier)
    } else if (showOptimize) {
        OptimizationScreen(onBack = { showOptimize = false }, modifier = modifier)
    } else if (openApp != null) {
        AppDetailScreen(app = openApp!!, onBack = { openApp = null }, modifier = modifier)
    } else {
        Column(modifier.fillMaxSize()) {
        NeonBanner(
            title = "应用",
            subtitle = "免 root 管理已安装应用",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            trailing = {
                Row {
                    TextButton(onClick = { showManage = true }) { Text("总览", fontSize = 12.sp) }
                    TextButton(onClick = { showOptimize = true }) { Text("优化", fontSize = 12.sp) }
                }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索应用名或包名") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showSystem,
                onClick = { showSystem = false; selected.clear() },
                label = { Text("用户应用") },
            )
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = true; selected.clear() },
                label = { Text("系统应用") },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${visible?.size ?: "-"} 个",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { load(true) }, enabled = !refreshing) {
                Text(if (refreshing) "刷新中…" else "刷新", fontSize = 12.sp)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    SortBy.NAME to "首字母",
                    SortBy.UPDATED to "最后更新",
                    SortBy.INSTALLED to "首次安装",
                ).forEach { (s, label) ->
                    FilterChip(
                        selected = sortBy == s,
                        onClick = { sortBy = s },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
            TextButton(onClick = { ascending = !ascending }) {
                Text(if (ascending) "正序 ↑" else "倒序 ↓", fontSize = 12.sp)
            }
        }

        Box(Modifier.weight(1f)) {
            val list = visible
            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            progressText?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            when {
                AppState.shizukuAlive == false && apps != null -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Shizuku 未连接", style = MaterialTheme.typography.titleMedium)
                        Text("浏览可用，但操作需要 Shizuku")
                    }
                }
                apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    val appsListState = androidx.compose.foundation.lazy.rememberLazyListState()
                    com.freeplay.toolbox.ui.components.PullToRefreshBox(
                        refreshing = refreshing,
                        onRefresh = { load(true) },
                        listState = appsListState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    LazyColumn(Modifier.fillMaxSize(), state = appsListState) {
                    items(list!!, key = { it.pkg }) { app ->
                        AppItem(
                            app = app,
                            selected = selected.containsKey(app.pkg),
                            inSelectMode = selected.isNotEmpty(),
                            onClick = {
                                if (selected.isNotEmpty()) {
                                    if (selected.containsKey(app.pkg)) selected.remove(app.pkg)
                                    else selected[app.pkg] = app
                                } else {
                                    openApp = app
                                    selected.clear()
                                }
                            },
                            onLongClick = { selected[app.pkg] = app },
                        )
                    }
                    }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(
                        "已选 ${selected.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { confirmAction = BatchAction.UNINSTALL }) { Text("卸载") }
                        TextButton(onClick = { confirmAction = BatchAction.FREEZE }) { Text("冻结") }
                        TextButton(onClick = { confirmAction = BatchAction.UNFREEZE }) { Text("解冻") }
                        TextButton(onClick = { confirmAction = BatchAction.CLEAR }) { Text("清数据") }
                        TextButton(onClick = { confirmAction = BatchAction.FORCE_STOP }) { Text("强停") }
                        TextButton(onClick = { selected.clear() }) { Text("取消") }
                    }
                }
            }
        }
    }
    }

    confirmAction?.let { action ->
        RiskConfirmDialog(
            title = "批量${action.title}（${selected.size} 个应用）",
            risks = action.risks,
            onDismiss = { confirmAction = null },
            onConfirm = {
                running = true
                confirmAction = null
                val targets = selected.values.toList()
                scope.launch {
                    val result = runBatch(action, targets) { done, total ->
                        progressText = "${action.title}中 $done/$total"
                    }
                    targets.forEach { app ->
                        com.freeplay.toolbox.data.OperationLog.append(
                            context, app.pkg, app.label,
                            logActionOf(action), undoCommandOf(action, app.pkg),
                        )
                    }
                    selected.clear()
                    progressText = null
                    load(true)
                    running = false
                    toast = result
                }
            },
        )
    }
}

private suspend fun runBatch(
    action: BatchAction,
    apps: List<com.freeplay.toolbox.data.AppInfo>,
    onProgress: (Int, Int) -> Unit,
): String {
    var okCount = 0
    val fails = mutableListOf<String>()
    val total = apps.size
    apps.forEachIndexed { i, app ->
        onProgress(i + 1, total)
        val r = when (action) {
            BatchAction.UNINSTALL -> Shell.uninstall(app.pkg)
            BatchAction.CLEAR -> Shell.clear(app.pkg)
            BatchAction.FREEZE -> Shell.suspend(app.pkg)
            BatchAction.UNFREEZE -> Shell.unsuspend(app.pkg)
            BatchAction.FORCE_STOP -> Shell.forceStop(app.pkg)
        }
        if (r.ok) okCount++ else fails.add("${app.pkg}: ${r.message()}")
    }
    var msg = "${action.title}完成 $okCount/$total"
    if (fails.isNotEmpty()) msg += "\n" + fails.take(3).joinToString("\n")
    return msg
}

/** 各操作的撤销命令（空 = 不可撤销） */
private fun undoCommandOf(action: BatchAction, pkg: String): String = when (action) {
    BatchAction.FREEZE -> "pm unsuspend --user 0 $pkg"
    BatchAction.UNFREEZE -> "pm suspend --user 0 $pkg"
    else -> ""
}

private fun logActionOf(action: BatchAction): String = when (action) {
    BatchAction.UNINSTALL -> com.freeplay.toolbox.data.OperationLog.Action.UNINSTALL.label
    BatchAction.CLEAR -> com.freeplay.toolbox.data.OperationLog.Action.CLEAR.label
    BatchAction.FREEZE -> com.freeplay.toolbox.data.OperationLog.Action.FREEZE.label
    BatchAction.UNFREEZE -> com.freeplay.toolbox.data.OperationLog.Action.UNFREEZE.label
    BatchAction.FORCE_STOP -> "强行停止"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppItem(
    app: AppInfo,
    selected: Boolean,
    inSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            app.thumb?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    app.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (app.isSuspended || app.isDisabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (app.isSuspended) {
                            com.freeplay.toolbox.ui.components.StatusPill(
                                "已冻结", com.freeplay.toolbox.ui.theme.NeonPurple,
                            )
                        }
                        if (app.isDisabled) {
                            com.freeplay.toolbox.ui.components.StatusPill(
                                "已停用", com.freeplay.toolbox.ui.theme.NeonRed,
                            )
                        }
                    }
                }
            }
            Text(
                app.versionName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (inSelectMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
        }
    }
}
