package com.freeplay.toolbox.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.DhizukuManager
import com.freeplay.toolbox.core.ShizukuManager
import com.freeplay.toolbox.data.BatteryInfo
import com.freeplay.toolbox.data.BatteryRepository
import com.freeplay.toolbox.data.DeviceRepository
import com.freeplay.toolbox.ui.components.HeroCard
import com.freeplay.toolbox.ui.components.KsuGroup
import com.freeplay.toolbox.ui.components.KsuRow
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.StatusPill
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonRed

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var info by remember { mutableStateOf(DeviceRepository.load(context)) }
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }
    var selinux by remember { mutableStateOf("读取中…") }
    var slotSuffix by remember { mutableStateOf("读取中…") }
    var toast by remember { mutableStateOf<String?>(null) }
    var announcements by remember { mutableStateOf<List<com.freeplay.toolbox.core.Announcement>>(emptyList()) }
    var dismissed by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        val list = com.freeplay.toolbox.core.RemoteConfig.fetchAnnouncements(context)
        if (list.isNotEmpty()) {
            announcements = list
            com.freeplay.toolbox.core.RemoteConfig.markRead(context, list.map { it.id })
        }
    }

    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    LaunchedEffect(AppState.shizukuGranted) {
        info = DeviceRepository.load(context)
        battery = BatteryRepository.load()
        if (AppState.shizukuGranted) {
            selinux = com.freeplay.toolbox.core.Shell.runAsync("getenforce").out.trim().ifBlank { "未知" }
            val slot = com.freeplay.toolbox.core.Shell.runAsync("getprop ro.boot.slot_suffix").out.trim()
            slotSuffix = when {
                slot.isEmpty() -> "非 A/B 设备"
                else -> slot
            }
        } else {
            selinux = "需 Shizuku 授权"
            slotSuffix = "需 Shizuku 授权"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Build,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            "玩机工具箱",
                            fontSize = 24.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            "v${com.freeplay.toolbox.BuildConfig.VERSION_NAME} · 免 root",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    StatusPill(
                        text = if (AppState.shizukuGranted) "工作中" else "未连接",
                        color = if (AppState.shizukuGranted) NeonGreen else NeonRed,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        text = when {
                            !AppState.shizukuAlive -> "Shizuku 离线"
                            !AppState.shizukuGranted -> "Shizuku 待授权"
                            else -> "Shizuku v${ShizukuManager.version()}"
                        },
                        color = when {
                            !AppState.shizukuAlive -> NeonRed
                            !AppState.shizukuGranted -> MaterialTheme.colorScheme.tertiary
                            else -> NeonGreen
                        },
                    )
                    StatusPill(
                        text = if (AppState.dhizukuAvailable) "Dhizuku 已连接" else "Dhizuku 离线",
                        color = if (AppState.dhizukuAvailable) NeonGreen else NeonRed,
                    )
                }
                if (AppState.shizukuAlive && !AppState.shizukuGranted) {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        (context as? Activity)?.let { ShizukuManager.requestPermission(it) }
                    }) { Text("授权 Shizuku") }
                }
            }
        }

        announcements.filter { it.id !in dismissed }.forEach { a ->
            NeonCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("公告", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "×",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { dismissed = dismissed + a.id }
                                .padding(horizontal = 6.dp),
                        )
                    }
                    Text(a.title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    if (a.body.isNotBlank()) {
                        Text(
                            a.body,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        KsuGroup(title = "状态") {
            KsuRow(
                icon = Icons.Filled.Terminal,
                title = "工作模式",
                value = if (AppState.shizukuGranted) "ADB · Shizuku" else "未连接",
            )
            KsuRow(
                icon = Icons.Filled.Security,
                title = "SELinux",
                value = selinux,
            )
            KsuRow(
                icon = Icons.Filled.Devices,
                title = "AB 插槽",
                value = slotSuffix,
            )
            KsuRow(
                icon = Icons.Filled.Devices,
                title = "Android",
                value = "${info.androidVersion} (API ${info.sdkInt})",
                showDivider = false,
            )
        }

        KsuGroup(title = "设备信息") {
            KsuRow(
                icon = Icons.Filled.BatteryFull,
                title = "电池",
                value = battery?.let { "${it.level}%" } ?: "-",
                subtitle = battery?.let { "${it.statusText} · ${it.tempC}°C · ${it.powerSource}" },
            )
            KsuRow(
                icon = Icons.Filled.Memory,
                title = "内存",
                value = "${info.memUsedPercent}%",
                subtitle = "共 ${info.memTotalGb}",
            )
            KsuRow(
                icon = Icons.Filled.Storage,
                title = "存储",
                value = "${info.storageUsedPercent}%",
                subtitle = "共 ${info.storageTotalGb}",
            )
            KsuRow(
                icon = Icons.Filled.Devices,
                title = "屏幕",
                value = info.screenPx,
                subtitle = info.abi,
                showDivider = false,
            )
        }

        KsuGroup(title = "快捷入口") {
            KsuRow(
                icon = Icons.Filled.Security,
                title = "Shizuku 授权申请",
                subtitle = "点按申请授权 · 长按打开 Shizuku",
                onClick = {
                    when {
                        !isInstalled(context, "moe.shizuku.privileged.api") ->
                            toast = "未安装 Shizuku"
                        !AppState.shizukuAlive ->
                            toast = "Shizuku 未运行，请先打开 Shizuku 并启动服务"
                        !AppState.shizukuGranted ->
                            (context as? Activity)?.let { ShizukuManager.requestPermission(it) }
                        else -> toast = "Shizuku 已授权"
                    }
                },
                onLongClick = {
                    if (isInstalled(context, "moe.shizuku.privileged.api")) {
                        openApp(context, "moe.shizuku.privileged.api")
                    } else toast = "未安装 Shizuku"
                },
            )
            KsuRow(
                icon = Icons.Filled.Security,
                title = "Dhizuku 授权申请",
                subtitle = "点按申请授权 · 长按打开 Dhizuku",
                showDivider = false,
                onClick = {
                    when {
                        !isInstalled(context, "com.rosan.dhizuku") ->
                            toast = "未安装 Dhizuku"
                        AppState.dhizukuAvailable -> toast = "Dhizuku 已授权"
                        else -> DhizukuManager.requestPermission()
                    }
                },
                onLongClick = {
                    if (isInstalled(context, "com.rosan.dhizuku")) {
                        openApp(context, "com.rosan.dhizuku")
                    } else toast = "未安装 Dhizuku"
                },
            )
        }
    }
}

private fun openApp(context: android.content.Context, pkg: String) {
    try {
        context.startActivity(context.packageManager.getLaunchIntentForPackage(pkg))
    } catch (_: Throwable) {
        android.widget.Toast.makeText(context, "未安装或无法启动：$pkg", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun isInstalled(context: android.content.Context, pkg: String): Boolean = try {
    context.packageManager.getPackageInfo(pkg, 0) != null
} catch (_: Throwable) {
    false
}
