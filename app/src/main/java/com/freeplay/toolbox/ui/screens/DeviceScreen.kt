package com.freeplay.toolbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.DeviceControl
import com.freeplay.toolbox.ui.components.NeonBanner
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.RiskConfirmDialog
import com.freeplay.toolbox.ui.components.StatusPill
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RiskAction(val title: String, val risks: List<String>) {
    CAMERA_OFF(
        "禁用相机", listOf(
            "全部应用（含相机、微信、扫码等）都将无法使用摄像头",
            "解禁前无法拍照、录像、视频通话",
        )
    ),
    SCREEN_CAPTURE_OFF(
        "禁止截屏", listOf(
            "全系统（含所有应用）无法截屏和录屏",
            "部分 ROM 的快捷截屏手势也会失效",
        )
    ),
    MUTE(
        "全局静音", listOf(
            "铃声、媒体、闹钟音量将被设备策略静音",
            "可能听不到来电与闹钟提醒",
        )
    ),
    LOCK(
        "立即锁屏", listOf(
            "屏幕将立刻锁定",
            "未设置锁屏密码时可能无法再解锁（需通过本工具解除）",
        )
    ),
    HIDE_ON(
        "隐藏应用", listOf(
            "目标应用将从桌面和启动器消失（不卸载，数据保留）",
            "需记住包名以便恢复显示",
        )
    ),
    KEYGUARD_OFF(
        "禁用锁屏", listOf(
            "设备将不再需要密码/指纹即可进入，处于完全不设防状态",
            "任何人拿起手机即可查看全部内容",
            "请务必在使用后重新启用",
        )
    ),
    STATUS_BAR_OFF(
        "隐藏状态栏", listOf(
            "状态栏（时间、电量、通知图标）将被隐藏",
            "部分应用全屏显示会出现异常",
        )
    ),
    REVOKE_PERM(
        "强制拒绝权限", listOf(
            "应用将立即失去该权限，相关功能可能失效或崩溃",
            "用户无法在系统设置中自行重新授予",
        )
    ),
    UNINSTALL_BLOCK(
        "阻止卸载", listOf(
            "目标应用将无法被普通用户卸载或停用",
            "需通过本功能或设备所有者才能恢复",
        )
    ),
}

@Composable
fun DeviceControlCards() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingRisk by remember { mutableStateOf<RiskAction?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hidePkg by remember { mutableStateOf("") }
    var blockPkg by remember { mutableStateOf("") }
    var cameraOn by remember { mutableStateOf<Boolean?>(null) }
    var captureOn by remember { mutableStateOf<Boolean?>(null) }
    var muteOn by remember { mutableStateOf<Boolean?>(null) }
    var statusBarOff by remember { mutableStateOf<Boolean?>(null) }
    var keyguardOff by remember { mutableStateOf<Boolean?>(null) }
    var proxyText by remember { mutableStateOf("") }
    var updateDays by remember { mutableStateOf("30") }
    var grantPkg by remember { mutableStateOf("") }
    var grantPerm by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    val ready = AppState.dhizukuAvailable && DeviceControl.supported()

    // 回读系统真实状态，避免开关显示与实际不符
    suspend fun readback() {
        if (!ready) return
        withContext(Dispatchers.IO) {
            runCatching { DeviceControl.isCameraDisabled() }.getOrNull()?.let { cameraOn = it }
            runCatching { DeviceControl.isScreenCaptureDisabled() }.getOrNull()?.let { captureOn = !it }
            runCatching { DeviceControl.isMasterVolumeMuted() }.getOrNull()?.let { muteOn = it }
        }
    }
    LaunchedEffect(ready) { readback() }

    fun run(risk: RiskAction?, action: () -> Unit) {
        if (risk == null) {
            scope.launch { withContext(Dispatchers.IO) { action() } }
        } else {
            pendingRisk = risk
            pendingAction = action
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusPill(
            text = if (ready) "Dhizuku 已连接"
            else if (!DeviceControl.supported()) "仅支持 Android 13 及以上"
            else "Dhizuku 未授权（需安装 Dhizuku 并激活为设备所有者）",
            color = if (ready) NeonGreen else NeonRed,
        )

        ToggleCard("禁用相机", "禁止全部应用使用摄像头", cameraOn, ready) { want ->
            run(if (want) RiskAction.CAMERA_OFF else null) {
                val ok = runCatching { DeviceControl.setCameraDisabled(want) }.getOrDefault(false)
                cameraOn = if (want && ok) true else if (ok) false else cameraOn
                toast = if (ok) (if (want) "相机已禁用" else "相机已解禁") else "执行失败"
            }
        }

        ToggleCard("允许截屏", "关闭后全系统无法截屏/录屏", captureOn, ready) { want ->
            run(if (!want) RiskAction.SCREEN_CAPTURE_OFF else null) {
                val ok = runCatching { DeviceControl.setScreenCaptureDisabled(!want) }.getOrDefault(false)
                captureOn = if (want && ok) true else if (ok) false else captureOn
                toast = if (ok) (if (want) "已允许截屏" else "已禁止截屏") else "执行失败"
            }
        }

        ToggleCard("全局静音", "设备策略级静音", muteOn, ready) { want ->
            run(if (want) RiskAction.MUTE else null) {
                val ok = runCatching { DeviceControl.setMasterVolumeMuted(want) }.getOrDefault(false)
                muteOn = if (want && ok) true else if (ok) false else muteOn
                toast = if (ok) (if (want) "已静音" else "已恢复声音") else "执行失败"
            }
        }

        ToggleCard("禁用锁屏", "设备不再需要密码即可进入（注意安全）", keyguardOff, ready) { want ->
            run(if (want) RiskAction.KEYGUARD_OFF else null) {
                val ok = runCatching { DeviceControl.setKeyguardDisabled(want) }.getOrDefault(false)
                keyguardOff = if (want && ok) true else if (ok) false else keyguardOff
                toast = if (ok) (if (want) "已禁用锁屏" else "已恢复锁屏") else "执行失败"
            }
        }

        ToggleCard("隐藏状态栏", "隐藏时间/电量/通知图标", statusBarOff, ready) { want ->
            run(if (want) RiskAction.STATUS_BAR_OFF else null) {
                val ok = runCatching { DeviceControl.setStatusBarDisabled(want) }.getOrDefault(false)
                statusBarOff = if (want && ok) true else if (ok) false else statusBarOff
                toast = if (ok) (if (want) "已隐藏状态栏" else "已恢复状态栏") else "执行失败"
            }
        }

        NeonCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("全局代理", style = MaterialTheme.typography.titleMedium)
                Text(
                    "设备所有者级别 HTTP 代理，格式 host:port",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = proxyText,
                    onValueChange = { proxyText = it },
                    placeholder = { Text("如 192.168.1.2:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = proxyText.isNotBlank() && ready,
                        onClick = {
                            val hp = proxyText.trim()
                            run(null) {
                                val ok = runCatching { DeviceControl.setGlobalProxy(hp) }.getOrDefault(false)
                                toast = if (ok) "代理已设置：$hp" else "执行失败"
                            }
                        },
                    ) { Text("设置代理") }
                    OutlinedButton(
                        enabled = ready,
                        onClick = {
                            run(null) {
                                val ok = runCatching { DeviceControl.clearGlobalProxy() }.getOrDefault(false)
                                toast = if (ok) "代理已清除" else "执行失败"
                            }
                        },
                    ) { Text("清除代理") }
                }
            }
        }

        NeonCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("立即锁屏", style = MaterialTheme.typography.titleMedium)
                Button(
                    enabled = ready,
                    onClick = { run(RiskAction.LOCK) { runCatching { DeviceControl.lockNow() } } },
                ) { Text("锁屏") }
            }
        }

        NeonCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("隐藏应用", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = hidePkg,
                    onValueChange = { hidePkg = it },
                    placeholder = { Text("输入完整包名，如 com.example.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = hidePkg.isNotBlank() && ready,
                        onClick = {
                            val p = hidePkg.trim()
                            run(RiskAction.HIDE_ON) {
                                val ok = runCatching { DeviceControl.setApplicationHidden(p, true) }.getOrDefault(false)
                                toast = if (ok) "已隐藏 $p" else "执行失败"
                            }
                        },
                    ) { Text("隐藏") }
                    Button(
                        enabled = hidePkg.isNotBlank() && ready,
                        onClick = {
                            val p = hidePkg.trim()
                            run(null) {
                                val ok = runCatching { DeviceControl.setApplicationHidden(p, false) }.getOrDefault(false)
                                toast = if (ok) "已恢复显示 $p" else "执行失败"
                            }
                        },
                    ) { Text("恢复显示") }
                }
            }
        }

        NeonCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("阻止卸载", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = blockPkg,
                    onValueChange = { blockPkg = it },
                    placeholder = { Text("输入完整包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = blockPkg.isNotBlank() && ready,
                        onClick = {
                            val p = blockPkg.trim()
                            run(RiskAction.UNINSTALL_BLOCK) {
                                val ok = runCatching { DeviceControl.setUninstallBlocked(p, true) }.getOrDefault(false)
                                toast = if (ok) "已阻止卸载 $p" else "执行失败"
                            }
                        },
                    ) { Text("阻止") }
                    Button(
                        enabled = blockPkg.isNotBlank() && ready,
                        onClick = {
                            val p = blockPkg.trim()
                            run(null) {
                                val ok = runCatching { DeviceControl.setUninstallBlocked(p, false) }.getOrDefault(false)
                                toast = if (ok) "已允许卸载 $p" else "执行失败"
                            }
                        },
                    ) { Text("允许卸载") }
                }
            }
        }
        NeonCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("权限强授", style = MaterialTheme.typography.titleMedium)
                Text(
                    "以设备所有者身份强制设置权限授予状态（可覆盖用户拒绝）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = grantPkg,
                    onValueChange = { grantPkg = it },
                    placeholder = { Text("包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = grantPerm,
                    onValueChange = { grantPerm = it },
                    placeholder = { Text("权限名，如 android.permission.CAMERA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = grantPkg.isNotBlank() && grantPerm.isNotBlank() && ready,
                        onClick = {
                            val p = grantPkg.trim()
                            val perm = grantPerm.trim()
                            run(null) {
                                val ok = runCatching { DeviceControl.setPermissionGrantState(p, perm, 1) }
                                    .getOrDefault(false)
                                toast = if (ok) "已强制授予" else "执行失败（系统可能不支持）"
                            }
                        },
                    ) { Text("强制授予") }
                    OutlinedButton(
                        enabled = grantPkg.isNotBlank() && grantPerm.isNotBlank() && ready,
                        onClick = {
                            val p = grantPkg.trim()
                            val perm = grantPerm.trim()
                            run(RiskAction.REVOKE_PERM) {
                                val ok = runCatching { DeviceControl.setPermissionGrantState(p, perm, 2) }
                                    .getOrDefault(false)
                                toast = if (ok) "已强制拒绝" else "执行失败（系统可能不支持）"
                            }
                        },
                    ) { Text("强制拒绝") }
                }
            }
        }

        NeonCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("暂停系统更新", style = MaterialTheme.typography.titleMedium)
                Text(
                    "以设备所有者身份推迟 OTA 安装（实验性，部分 ROM 无效）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = updateDays,
                        onValueChange = { updateDays = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("天数") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.4f),
                    )
                    Button(
                        enabled = ready,
                        onClick = {
                            val d = updateDays.toIntOrNull() ?: 30
                            run(null) {
                                val ok = runCatching { DeviceControl.setUpdatePostpone(d) }.getOrDefault(false)
                                toast = if (ok) "已暂停系统更新 $d 天" else "执行失败（当前 ROM 不支持）"
                            }
                        },
                    ) { Text("暂停") }
                    OutlinedButton(
                        enabled = ready,
                        onClick = {
                            run(null) {
                                val ok = runCatching { DeviceControl.setUpdatePostpone(0) }.getOrDefault(false)
                                toast = if (ok) "已恢复系统更新" else "执行失败"
                            }
                        },
                    ) { Text("恢复") }
                }
            }
        }
    }

    pendingRisk?.let { risk ->
        RiskConfirmDialog(
            title = risk.title,
            risks = risk.risks,
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
private fun ToggleCard(
    title: String,
    desc: String,
    checked: Boolean?,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    NeonCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (checked == null) "$desc · 当前状态未读取到" else desc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked == true, onCheckedChange = onChange, enabled = enabled)
        }
    }
}
