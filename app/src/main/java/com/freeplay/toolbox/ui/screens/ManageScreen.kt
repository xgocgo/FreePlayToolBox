package com.freeplay.toolbox.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.freeplay.toolbox.core.Shell
import com.freeplay.toolbox.data.AppInfo
import com.freeplay.toolbox.data.AppListCache
import com.freeplay.toolbox.data.OpRecord
import com.freeplay.toolbox.data.OperationLog
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.SectionTitle
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用总览：集中查看已冻结 / 已停用 / 已隐藏的应用，以及可还原的操作记录。
 */
@Composable
fun ManageScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var logs by remember { mutableStateOf<List<OpRecord>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    fun reload() {
        scope.launch {
            apps = withContext(Dispatchers.IO) { AppListCache.getOrLoad(context) }
            logs = OperationLog.load(context)
            loaded = true
        }
    }
    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    val suspended = apps.filter { it.isSuspended }
    val disabled = apps.filter { it.isDisabled && !it.isSuspended }

    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("应用总览", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!AppState.shizukuGranted) {
                item {
                    NeonCard {
                        Text(
                            "Shizuku 未授权，仅能查看记录，无法执行还原",
                            fontSize = 12.sp,
                            color = NeonRed,
                        )
                    }
                }
            }

            // ---- 已冻结 ----
            item {
                SectionTitle("已冻结（${suspended.size}）")
            }
            if (suspended.isEmpty()) {
                item { HintRow("没有被冻结的应用") }
            } else {
                items(suspended, key = { "s-${it.pkg}" }) { app ->
                    ActionRow(
                        title = app.label,
                        subtitle = app.pkg,
                        badge = "已冻结",
                        enabled = AppState.shizukuGranted && busy == null,
                        onUndo = {
                            busy = app.pkg
                            scope.launch {
                                val r = Shell.unsuspend(app.pkg)
                                toast = if (r.ok) "已解冻 ${app.label}" else r.message()
                                OperationLog.append(
                                    context, app.pkg, app.label,
                                    OperationLog.Action.UNFREEZE.label,
                                    "pm suspend --user 0 ${app.pkg}",
                                )
                                busy = null
                                reload()
                            }
                        },
                    )
                }
            }

            // ---- 已停用 ----
            item {
                SectionTitle("已停用（${disabled.size}）")
            }
            if (disabled.isEmpty()) {
                item { HintRow("没有被停用的应用") }
            } else {
                items(disabled, key = { "d-${it.pkg}" }) { app ->
                    ActionRow(
                        title = app.label,
                        subtitle = app.pkg,
                        badge = "已停用",
                        enabled = AppState.shizukuGranted && busy == null,
                        onUndo = {
                            busy = app.pkg
                            scope.launch {
                                val r = Shell.enable(app.pkg)
                                toast = if (r.ok) "已启用 ${app.label}" else r.message()
                                OperationLog.append(
                                    context, app.pkg, app.label,
                                    OperationLog.Action.ENABLE.label,
                                    "pm disable-user --user 0 ${app.pkg}",
                                )
                                busy = null
                                reload()
                            }
                        },
                    )
                }
            }

            // ---- 操作记录 ----
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("最近操作（${logs.size}）")
                    Spacer(Modifier.weight(1f))
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = {
                            OperationLog.clear(context)
                            logs = emptyList()
                            toast = "记录已清空"
                        }) { Text("清空", fontSize = 12.sp) }
                    }
                }
            }
            if (logs.isEmpty()) {
                item { HintRow("暂无操作记录") }
            } else {
                items(logs.take(100), key = { "l-${it.time}-${it.pkg}-${it.action}" }) { rec ->
                    NeonCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${rec.action} · ${rec.label}", fontSize = 14.sp)
                                Text(
                                    "${rec.pkg} · ${fmt.format(Date(rec.time))}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (rec.undoable) {
                                SmallPill("撤销", false, AppState.shizukuGranted) {
                                    scope.launch {
                                        val ok = OperationLog.undo(context, rec)
                                        toast = if (ok) "已撤销：${rec.action} ${rec.label}" else "撤销失败"
                                        reload()
                                    }
                                }
                            } else {
                                SmallPill("不可撤销", false, false) {}
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun HintRow(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    badge: String,
    enabled: Boolean,
    onUndo: () -> Unit,
) {
    NeonCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp)
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SmallPill(badge, true) {}
            Spacer(Modifier.padding(end = 6.dp))
            SmallPill("恢复", false, enabled, onUndo)
        }
    }
}
