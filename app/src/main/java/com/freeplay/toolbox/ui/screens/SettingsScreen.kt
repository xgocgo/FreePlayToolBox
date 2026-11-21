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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.CrashLogger
import com.freeplay.toolbox.ui.components.CrashReportDialog
import com.freeplay.toolbox.ui.components.DisclaimerDialog
import com.freeplay.toolbox.ui.components.HeroCard
import com.freeplay.toolbox.ui.components.KsuListItem
import com.freeplay.toolbox.ui.components.SectionTitle
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonPurple
import com.freeplay.toolbox.ui.theme.NeonRed

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(CrashLogger.read(context)) }
    var showCrash by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }

    if (showCrash && crashLog != null) {
        CrashReportDialog(log = crashLog!!, onDismiss = { showCrash = false })
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
                    "玩机工具箱 v0.9.0",
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
            subtitle = "v0.9.0 (23) · 免 root，基于 Shizuku / Dhizuku",
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
