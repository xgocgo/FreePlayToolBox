package com.freeplay.toolbox.ui.screens

import android.content.Intent
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
import com.freeplay.toolbox.data.NotificationRepository
import com.freeplay.toolbox.data.NotifyRule
import com.freeplay.toolbox.data.NotifyRules
import com.freeplay.toolbox.notify.NotifyInfo
import com.freeplay.toolbox.notify.NotifyListener
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.SectionTitle
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 通知管理。
 *
 * 两种粒度：
 * - 精准隐藏：借通知使用权，按「包名+id+tag」取消某一条通知（会闪一下）
 * - 整包关闭：撤销应用的通知权限，完全不弹（更彻底）
 */
@Composable
fun NotificationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(NotifyListener.isGranted(context)) }
    var rules by remember { mutableStateOf(NotifyRules.load(context)) }
    var toast by remember { mutableStateOf<String?>(null) }
    var manualPkg by remember { mutableStateOf("") }
    var manualMode by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    // 监听器授权状态可能被系统改，回前台时刷新
    LaunchedEffect(Unit) {
        granted = NotifyListener.isGranted(context)
        rules = NotifyRules.load(context)
        // 进页面就把已有规则重放一遍，看不见的残留通知清掉
        if (granted) NotifyListener.applyNow()
    }

    val list = NotifyListener.items.values.sortedByDescending { it.time }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("通知管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${list.size} 条", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------- 授权 ----------
            if (!granted) {
                item {
                    NeonCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("需要通知使用权", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "用于读取通知列表并隐藏指定通知。不会上传任何通知内容。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = AppState.shizukuGranted,
                                    onClick = {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) { NotifyListener.grant(context) }
                                            granted = NotifyListener.isGranted(context)
                                            toast = if (ok) "已授权" else "自动授权失败，请手动开启"
                                        }
                                    },
                                ) { Text("一键授权") }
                                OutlinedButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }) { Text("去系统设置") }
                            }
                        }
                    }
                }
            }

            // ---------- 监听状态 ----------
            if (granted) {
                item {
                    NeonCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (NotifyListener.connected) "监听器已连接" else "监听器未连接",
                                    fontSize = 13.sp,
                                    color = if (NotifyListener.connected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                                Spacer(Modifier.weight(1f))
                                SmallPill("重新应用", false) {
                                    val hits = NotifyListener.applyNow()
                                    toast = "重应用完成，命中 $hits 条"
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallPill("重启监听", false, true) {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            NotifyListener.restart(context)
                                        }
                                        granted = NotifyListener.isGranted(context)
                                        toast = if (ok) "监听器已重新绑定" else "绑定失败，点「复制诊断」"
                                    }
                                }
                                SmallPill("复制诊断", false, true) {
                                    runCatching {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                        cm.setPrimaryClip(
                                            android.content.ClipData.newPlainText(
                                                "notify_diag",
                                                NotifyListener.diagnostics(context),
                                            )
                                        )
                                    }
                                    toast = "诊断已复制"
                                }
                            }
                            if (!NotifyListener.connected) {
                                Text(
                                    "系统没有绑定监听器：先点「重启监听」，还是不行就点「复制诊断」发我",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(
                                "收到 ${NotifyListener.received} · 已拦 ${NotifyListener.suppressed} · " +
                                    "拦不掉 ${NotifyListener.stuck}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (NotifyListener.lastEvent.isNotBlank()) {
                                Text(
                                    NotifyListener.lastEvent,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            // ---------- 活动通知 ----------
            item { SectionTitle("当前通知（${list.size}）") }

            if (!granted) {
                item {
                    Text(
                        "授权后才能读取通知列表",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (list.isEmpty()) {
                item {
                    Text(
                        "当前没有通知",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(list, key = { it.key }) { n ->
                NotifyRow(
                    info = n,
                    onHide = {
                        NotifyRules.add(
                            context,
                            NotifyRule(n.pkg, n.id, n.tag, n.title.ifBlank { n.pkg }),
                        )
                        rules = NotifyRules.load(context)
                        // 规则只对「以后」生效，已经在通知栏里的这条要当场取消
                        val hits = NotifyListener.applyNow()
                        toast = if (hits > 0) {
                            "已隐藏「${n.title.ifBlank { n.pkg }}」"
                        } else {
                            "规则已存，但通知栏没命中（监听器未连接？）"
                        }
                    },
                    onBlockAll = {
                        scope.launch {
                            withContext(Dispatchers.IO) { NotificationRepository.setBlocked(n.pkg, true) }
                            toast = "已关闭 ${n.pkg.takeLast(24)} 的全部通知"
                        }
                    },
                )
            }

            // ---------- 已隐藏规则 ----------
            item { SectionTitle("已隐藏的通知（${rules.size}）") }
            if (rules.isEmpty()) {
                item {
                    Text(
                        "还没有隐藏任何通知。在上面点「隐藏这条」即可。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(rules, key = { it.serialized }) { r ->
                NeonCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, fontSize = 13.sp, maxLines = 1)
                            Text(
                                "${r.pkg} · id=${r.id}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        SmallPill("恢复", false) {
                            NotifyRules.remove(context, r)
                            rules = NotifyRules.load(context)
                            toast = if (NotifyListener.restore(r.serialized)) {
                                "已恢复「${r.label}」"
                            } else {
                                "规则已删；这条已经过期，应用下次发同样通知就会显示"
                            }
                        }
                    }
                }
            }

            // ---------- 整包关闭 ----------
            item { SectionTitle("整包关闭（更彻底）") }
            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "精准隐藏拦不住第一次出现，会闪一下。要让某个应用完全不弹通知，用这里关闭它的通知权限。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = manualPkg,
                            onValueChange = { manualPkg = it.trim() },
                            placeholder = { Text("包名，如 com.example.app") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (manualMode != null) {
                            Text("当前状态：$manualMode", fontSize = 11.sp, color = NeonCyan)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = manualPkg.isNotBlank() && AppState.shizukuGranted,
                                onClick = {
                                    scope.launch {
                                        val err = withContext(Dispatchers.IO) {
                                            NotificationRepository.setBlockedDetailed(manualPkg, true)
                                        }
                                        toast = if (err.isEmpty()) "已关闭通知权限" else err
                                    }
                                },
                            ) { Text("关闭通知") }
                            OutlinedButton(
                                enabled = manualPkg.isNotBlank() && AppState.shizukuGranted,
                                onClick = {
                                    scope.launch {
                                        val err = withContext(Dispatchers.IO) {
                                            NotificationRepository.setBlockedDetailed(manualPkg, false)
                                        }
                                        toast = if (err.isEmpty()) "已恢复通知权限" else err
                                    }
                                },
                            ) { Text("恢复") }
                            OutlinedButton(
                                enabled = manualPkg.isNotBlank(),
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

            item {
                Text(
                    "说明：精准隐藏靠通知使用权取消，会闪一下；整包关闭是撤权限，完全不弹。两者可同时使用。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotifyRow(
    info: NotifyInfo,
    onHide: () -> Unit,
    onBlockAll: () -> Unit,
) {
    NeonCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        info.title.ifBlank { "(无标题)" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        info.text.ifBlank { "(无内容)" },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (info.ongoing) {
                    SmallPill("常驻", true) {}
                }
            }
            Text(
                "${info.pkg} · id=${info.id}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill("隐藏这条", false, true, onHide)
                SmallPill("关闭该应用全部通知", false, true, onBlockAll)
            }
        }
    }
}
