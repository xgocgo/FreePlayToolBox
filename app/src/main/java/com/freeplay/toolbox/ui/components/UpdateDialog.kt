package com.freeplay.toolbox.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.freeplay.toolbox.core.RemoteConfig
import com.freeplay.toolbox.core.ReleaseInfo
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 在线更新弹窗：检查结果 → 下载（镜像回落）→ 校验 → 安装 */
@Composable
fun UpdateDialog(
    info: ReleaseInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var downloadedPath by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            toast = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("发现新版本 ${info.versionName}", color = NeonCyan) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "当前版本：$currentVersion",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        info.notes.take(1200),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.size > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "安装包：%.2f MB".format(info.size / 1048576.0),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 11.sp, color = NeonCyan)
                }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val ready = downloadedPath
                    if (ready != null) {
                        if (!RemoteConfig.canInstallPackages(context)) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        android.net.Uri.parse("package:" + context.packageName),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            toast = "请先允许安装未知来源应用"
                        } else if (RemoteConfig.installApk(context, ready)) {
                            toast = "已调起安装"
                        } else {
                            toast = "安装失败，文件在 Download/DSH-Go"
                        }
                        return@Button
                    }
                    busy = true
                    status = "正在下载（直连失败会自动换镜像）…"
                    scope.launch {
                        val path = withContext(Dispatchers.IO) {
                            RemoteConfig.download(
                                context,
                                RemoteConfig.downloadCandidates(info.downloadUrl),
                                "update_${info.versionName}.apk",
                            )
                        }
                        if (path == null) {
                            busy = false
                            status = null
                            toast = "下载失败：所有源都不可达"
                        } else {
                            // 校验（GitHub 提供 sha256 digest）
                            val expected = info.digest
                            val actual = withContext(Dispatchers.IO) { RemoteConfig.sha256(path) }
                            if (expected != null && actual != null && !actual.equals(expected, true)) {
                                busy = false
                                status = "校验不通过，已丢弃"
                                toast = "安装包校验失败，可能下载损坏"
                            } else {
                                busy = false
                                downloadedPath = path
                                status = if (expected != null) "下载完成，校验通过" else "下载完成（无校验值）"
                            }
                        }
                    }
                },
            ) {
                Text(
                    when {
                        busy -> "下载中…"
                        downloadedPath != null -> "立即安装"
                        else -> "下载更新"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("以后再说", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
