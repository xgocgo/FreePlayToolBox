package com.freeplay.toolbox.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.data.AppInfo
import com.freeplay.toolbox.data.AppListCache
import com.freeplay.toolbox.data.OperationLog
import com.freeplay.toolbox.data.OptStateCache
import com.freeplay.toolbox.data.OptimizationRepository
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OptMode(val title: String, val hint: String) {
    NOTIFY("通知", "关闭后该应用无法弹出通知"),
    BACKGROUND("后台", "限制后台运行可显著省电，但可能收不到消息"),
    BATTERY("电池", "开启后系统不再限制其后台，适合需要常驻的应用"),
}

/**
 * 应用优化：批量管理通知 / 后台限制 / 电池优化白名单。
 */
@Composable
fun OptimizationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(OptMode.NOTIFY) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val states = remember { mutableStateListOf<String>() }  // "pkg" = 已开启

    BackHandler { onBack() }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppListCache.getOrLoad(context) }
    }

    var loadedMode by remember { mutableStateOf<OptMode?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 切换模式：命中缓存则直接用，否则逐条查询并写入缓存
    LaunchedEffect(mode, apps, refreshKey) {
        if (!AppState.shizukuGranted || apps.isEmpty()) return@LaunchedEffect
        if (loadedMode == mode) return@LaunchedEffect
        states.clear()
        if (OptStateCache.has(mode.name)) {
            OptStateCache.let { }
            apps.filter { !it.isSystem }.forEach { app ->
                OptStateCache.get(mode.name, app.pkg)?.let { if (it) states.add(app.pkg) }
            }
            loadedMode = mode
            return@LaunchedEffect
        }
        busy = true
        val key = mode.name
        apps.filter { !it.isSystem }.forEach { app ->
            val on = withContext(Dispatchers.IO) {
                when (mode) {
                    OptMode.NOTIFY -> !OptimizationRepository.notificationsBlocked(app.pkg)
                    OptMode.BACKGROUND -> !OptimizationRepository.backgroundLimited(app.pkg)
                    OptMode.BATTERY -> OptimizationRepository.batteryWhitelisted(app.pkg)
                }
            }
            OptStateCache.put(key, app.pkg, on)
            if (on) states.add(app.pkg)
        }
        busy = false
        loadedMode = mode
    }

    fun forceReload() {
        OptStateCache.invalidate()
        loadedMode = null
        refreshKey++
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("应用优化", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OptMode.entries.forEach { m ->
                SmallPill(m.title, mode == m) { mode = m }
            }
        }
        Text(
            mode.hint,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )

        if (!AppState.shizukuGranted) {
            Text(
                "Shizuku 未授权，无法修改",
                fontSize = 12.sp,
                color = NeonRed,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (busy) {
            Text(
                "读取状态中…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        val optListState = androidx.compose.foundation.lazy.rememberLazyListState()
        com.freeplay.toolbox.ui.components.PullToRefreshBox(
            refreshing = busy,
            onRefresh = { forceReload() },
            listState = optListState,
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = optListState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(apps.filter { !it.isSystem }, key = { it.pkg }) { app ->
                NeonCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 14.sp, maxLines = 1)
                            Text(
                                app.pkg,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Switch(
                            checked = states.contains(app.pkg),
                            enabled = AppState.shizukuGranted && !busy,
                            onCheckedChange = { want ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        when (mode) {
                                            OptMode.NOTIFY -> OptimizationRepository.setNotifications(app.pkg, want)
                                            OptMode.BACKGROUND -> OptimizationRepository.setBackgroundLimited(app.pkg, !want)
                                            OptMode.BATTERY -> OptimizationRepository.setBatteryWhitelist(app.pkg, want)
                                        }
                                    }
                                    OptStateCache.put(mode.name, app.pkg, want)
                                    if (want) {
                                        if (!states.contains(app.pkg)) states.add(app.pkg)
                                    } else {
                                        states.remove(app.pkg)
                                    }
                                    val action = when (mode) {
                                        OptMode.NOTIFY -> if (want) "开启通知" else OperationLog.Action.NOTIFY_OFF.label
                                        OptMode.BACKGROUND -> if (want) "取消限制" else OperationLog.Action.BG_LIMIT.label
                                        OptMode.BATTERY -> if (want) OperationLog.Action.BATTERY_NO_OPT.label else "移出白名单"
                                    }
                                    OperationLog.append(context, app.pkg, app.label, action)
                                    toast = "${app.label}：$action"
                                }
                            },
                        )
                    }
                }
            }
        }
        }
    }
}
