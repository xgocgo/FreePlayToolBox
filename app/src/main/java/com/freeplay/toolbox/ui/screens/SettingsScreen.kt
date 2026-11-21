package com.freeplay.toolbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.CrashLogger
import com.freeplay.toolbox.core.RemoteConfig
import com.freeplay.toolbox.ui.components.CrashReportDialog
import com.freeplay.toolbox.ui.components.DisclaimerDialog
import com.freeplay.toolbox.ui.components.HeroCard
import com.freeplay.toolbox.ui.components.KsuListItem
import com.freeplay.toolbox.ui.components.SectionTitle
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonPurple
import com.freeplay.toolbox.ui.theme.NeonRed

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(CrashLogger.read(context)) }
    var showCrash by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var online by remember { mutableStateOf(RemoteConfig.onlineEnabled(context)) }
    var announce by remember { mutableStateOf(RemoteConfig.announceEnabled(context)) }
    var updateCheck by remember { mutableStateOf(RemoteConfig.updateEnabled(context)) }
    var showPrivacy by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.freeplay.toolbox.core.ReleaseInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            toast = null
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        backups = com.freeplay.toolbox.data.ConfigBackup.listBackups()
    }
    var showDisclaimer by remember { mutableStateOf(false) }

    updateInfo?.let { info ->
        com.freeplay.toolbox.ui.components.UpdateDialog(
            info = info,
            currentVersion = com.freeplay.toolbox.core.RemoteConfig.selfVersion(context),
            onDismiss = { updateInfo = null },
        )
    }
    if (showCrash && crashLog != null) {
        CrashReportDialog(log = crashLog!!, onDismiss = { showCrash = false })
    }
    if (showPrivacy) {
        com.freeplay.toolbox.ui.components.PrivacyDialog(
            onAccept = { showPrivacy = false },
            onDecline = {
                RemoteConfig.setOnline(context, false)
                online = false
                showPrivacy = false
            },
            acceptText = "关闭",
            declineText = "改为完全离线",
        )
    }
    if (showDisclaimer) {
        DisclaimerDialog(
            onAccepted = { showDisclaimer = false },
            onDeclined = { showDisclaimer = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeroCard {
            Column {
                Text("设置", fontSize = 26.sp, style = MaterialTheme.typography.titleLarge)
                Text(
                    "玩机工具箱 v${com.freeplay.toolbox.BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        SectionTitle("日志")

        KsuListItem(
            icon = Icons.Filled.Warning,
            title = "崩溃日志",
            subtitle = if (crashLog != null) "存在一份崩溃日志，点击查看" else "暂无崩溃日志",
            tint = if (crashLog != null) NeonRed else NeonCyan,
            onClick = {
                crashLog = CrashLogger.read(context)
                if (crashLog != null) showCrash = true
            },
            trailing = if (crashLog != null) {
                {
                    Text(
                        "清除",
                        fontSize = 13.sp,
                        color = NeonRed,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { CrashLogger.clear(context); crashLog = null },
                    )
                }
            } else null,
        )

        SectionTitle("配置备份")

        KsuListItem(
            icon = Icons.Filled.Description,
            title = "导出配置",
            subtitle = "收藏命令、筛选偏好、冻结清单 → Download/DSH-Go/backup_*.json",
            tint = NeonCyan,
            onClick = {
                scope.launch {
                    val frozen = withContext(Dispatchers.IO) {
                        com.freeplay.toolbox.data.AppListCache.getOrLoad(context)
                            .filter { it.isSuspended }
                            .map { it.pkg to it.label }
                    }
                    val f = withContext(Dispatchers.IO) {
                        com.freeplay.toolbox.data.ConfigBackup.export(context, frozen)
                    }
                    backups = com.freeplay.toolbox.data.ConfigBackup.listBackups()
                    toast = if (f != null) "已导出：${f.name}" else "导出失败（检查存储权限）"
                }
            },
        )

        backups.take(3).forEach { f ->
            KsuListItem(
                icon = Icons.Filled.Description,
                title = "导入：${f.name}",
                subtitle = "${f.length() / 1024} KB · 覆盖筛选偏好，合并收藏命令",
                tint = NeonPurple,
                onClick = {
                    scope.launch {
                        val snap = withContext(Dispatchers.IO) {
                            com.freeplay.toolbox.data.ConfigBackup.parse(f.readText())
                        }
                        if (snap == null) {
                            toast = "文件解析失败"
                        } else {
                            com.freeplay.toolbox.data.ConfigBackup.apply(context, snap)
                            toast = "已导入：收藏 ${snap.favCommands.size} 条，冻结记录 ${snap.frozenApps.size} 项"
                        }
                    }
                },
            )
        }

        SectionTitle("安全与隐私")

        KsuListItem(
            icon = Icons.Filled.Security,
            title = "安全与隐私声明",
            subtitle = "承诺无远程执行、明列上传与不上传内容",
            tint = NeonGreen,
            onClick = { showPrivacy = true },
        )

        KsuListItem(
            icon = Icons.Filled.Settings,
            title = "完全离线模式",
            subtitle = if (online) "已开启联网（公告 + 更新检查）" else "已完全离线，不发起任何请求",
            tint = if (online) NeonCyan else NeonGreen,
            onClick = {
                val next = !online
                RemoteConfig.setOnline(context, next)
                online = next
                if (next) { announce = RemoteConfig.announceEnabled(context); updateCheck = RemoteConfig.updateEnabled(context) }
            },
        )

        if (online) {
            KsuListItem(
                icon = Icons.Filled.Description,
                title = "接收公告",
                subtitle = "从仓库拉取公告文字",
                tint = NeonPurple,
                trailing = {
                    androidx.compose.material3.Switch(
                        checked = announce,
                        onCheckedChange = { RemoteConfig.setAnnounce(context, it); announce = it },
                    )
                },
            )
            KsuListItem(
                icon = Icons.Filled.Description,
                title = "检查在线更新",
                subtitle = "从 GitHub 读取版本号，下载走镜像回落",
                tint = NeonPurple,
                onClick = {
                    if (checking) return@KsuListItem
                    checking = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { RemoteConfig.latestRelease(context) }
                        checking = false
                        if (r == null) {
                            toast = "检查失败（网络不通或已关闭更新）"
                        } else if (RemoteConfig.isNewer(r.versionName, RemoteConfig.selfVersion(context))) {
                            updateInfo = r
                        } else {
                            toast = "已是最新版本 ${RemoteConfig.selfVersion(context)}"
                        }
                    }
                },
            )
        }

        SectionTitle("法律与声明")

        KsuListItem(
            icon = Icons.Filled.Description,
            title = "免责声明",
            subtitle = "重新阅读免责声明与使用条款",
            tint = NeonCyan,
            onClick = { showDisclaimer = true },
        )

        SectionTitle("关于")

        KsuListItem(
            icon = Icons.Filled.Info,
            title = "版本",
            subtitle = "v${com.freeplay.toolbox.BuildConfig.VERSION_NAME} (${com.freeplay.toolbox.BuildConfig.VERSION_CODE}) · 免 root，基于 Shizuku / Dhizuku",
            tint = NeonCyan,
        )
        KsuListItem(
            icon = Icons.Filled.Info,
            title = "连接状态",
            subtitle = "Shizuku ${if (AppState.shizukuGranted) "已授权" else "未授权"} · Dhizuku ${if (AppState.dhizukuAvailable) "已授权" else "未授权"}",
            tint = NeonPurple,
        )
    }
}
