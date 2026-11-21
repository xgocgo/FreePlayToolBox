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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.freeplay.toolbox.data.ActiveNotification
import com.freeplay.toolbox.data.NotificationRepository
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.SectionTitle
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 通知管理：活动通知列表、按包屏蔽、隐藏常驻通知、手动指定包名 */
@Composable
fun NotificationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ActiveNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var canList by remember { mutableStateOf(true) }
    var diag by remember { mutableStateOf("") }
    val blocked = remember { mutableStateListOf<String>() }
    var toast by remember { mutableStateOf<String?>(null) }

    var manualPkg by remember { mutableStateOf("") }
    var manualMode by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            toast = null
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            canList = withContext(Dispatchers.IO) { NotificationRepository.canList() }
            items = withContext(Dispatchers.IO) { NotificationRepository.active() }
            loading = false
            val pkgs = items.map { it.pkg }.distinct()
            blocked.clear()
            pkgs.forEach { p ->
                if (withContext(Dispatchers.IO) { NotificationRepository.isBlocked(p) }) blocked.add(p)
            }
            diag = if (items.isEmpty()) {
                if (canList) "dumpsys 有输出但未解析到活动通知"
                else "该 ROM 的 dumpsys notification 不可读（常见于荣耀/华为），请用下方手动屏蔽"
            } else ""
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun apply(pkg: String, allow: Boolean) {
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                NotificationRepository.setBlockedDetailed(pkg, !allow)
            }
            if (allow) blocked.remove(pkg) else if (!blocked.contains(pkg)) blocked.add(pkg)
            toast = if (err.isEmpty()) {
                if (allow) "已恢复 $pkg 的通知" else "已隐藏 $pkg 的通知（含常驻）"
            } else {
                "未完全生效：$err"
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("通知管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload() }) { Text("刷新", fontSize = 12.sp) }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!AppState.shizukuGranted) {
                item {
                    Text("Shizuku 未授权，无法修改", fontSize = 12.sp, color = NeonRed)
                }
            }

            // ---------- 手动屏蔽 ----------
            item {
                SectionTitle("按包名隐藏（列表读不到时用这个）")
            }
            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "输入包名可屏蔽该应用全部通知，常驻通知（如「正在运行」）同样会消失",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = manualPkg,
                            onValueChange = { manualPkg = it.trim() },
                            placeholder = { Text("如 com.android.systemui") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (manualMode != null) {
                            Text(
                                "当前 appops 状态：$manualMode",
                                fontSize = 11.sp,
                                color = NeonCyan,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = manualPkg.isNotBlank() && AppState.shizukuGranted,
                                onClick = { apply(manualPkg, false); manualMode = null },
                            ) { Text("隐藏通知") }
                            OutlinedButton(
                                enabled = manualPkg.isNotBlank() && AppState.shizukuGranted,
                                onClick = { apply(manualPkg, true); manualMode = null },
                            ) { Text("恢复通知") }
                            OutlinedButton(
                                enabled = manualPkg.isNotBlank() && AppState.shizukuGranted,
                                onClick = {
                                    scope.launch {
                                        manualMode = withContext(Dispatchers.IO) {
                                            NotificationRepository.currentMode(manualPkg)
                                        }
                                    }
                                },
                            ) { Text("查状态") }
                        }
                    }
                }
            }

            // ---------- 当前通知 ----------
            item {
                SectionTitle("当前活动通知（${items.size}）")
            }
            if (loading) {
                item {
                    Text(
                        "读取中…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!loading && items.isEmpty()) {
                item {
                    Text(
                        diag.ifBlank { "当前没有活动通知" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(items, key = { it.pkg + it.title }) { n ->
                NeonCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(n.title, fontSize = 14.sp, maxLines = 1)
                                if (n.ongoing) {
                                    Spacer(Modifier.padding(start = 6.dp))
                                    SmallPill("常驻", true) {}
                                }
                            }
                            Text(
                                n.pkg,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Switch(
                            checked = !blocked.contains(n.pkg),
                            enabled = AppState.shizukuGranted,
                            onCheckedChange = { allow -> apply(n.pkg, allow) },
                        )
                    }
                }
            }

            item {
                Text(
                    "说明：隐藏能力取决于系统是否允许 shell 修改 POST_NOTIFICATION。部分 ROM 会忽略该设置，此时「查状态」会显示实际生效的模式。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
