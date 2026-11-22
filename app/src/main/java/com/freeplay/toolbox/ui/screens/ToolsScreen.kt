package com.freeplay.toolbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import com.freeplay.toolbox.ui.components.KsuListItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.freeplay.toolbox.core.Shell
import com.freeplay.toolbox.data.BatteryInfo
import com.freeplay.toolbox.data.BatteryRepository
import com.freeplay.toolbox.data.CommandStore
import com.freeplay.toolbox.data.ScreenRepository
import com.freeplay.toolbox.data.ScreenInfo
import com.freeplay.toolbox.ui.components.NeonBanner
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.RiskConfirmDialog
import com.freeplay.toolbox.overlay.OverlayService
import com.freeplay.toolbox.ui.components.SectionTitle
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.components.StatusPill
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonPurple
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.launch

private data class Category(
    val name: String,
    val keywords: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

/** 切换悬浮窗：先确保有悬浮窗权限，再同步给服务 */
private fun syncOverlay(ctx: android.content.Context, kind: OverlayService.Kind, enable: Boolean) {
    if (enable && !android.provider.Settings.canDrawOverlays(ctx)) {
        runCatching {
            val pkg = ctx.packageName
            // shell 可直接授予 SYSTEM_ALERT_WINDOW AppOps
            kotlinx.coroutines.runBlocking { Shell.runAsync("appops set $pkg SYSTEM_ALERT_WINDOW allow") }
        }
        if (!android.provider.Settings.canDrawOverlays(ctx)) {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.packageName),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
    }
    if (enable) OverlayService.active.add(kind) else OverlayService.active.remove(kind)
    OverlayService.sync(ctx)
}

private fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    // 调参
    var animScale by remember { mutableStateOf("1") }
    var pointerSpeed by remember { mutableStateOf("0") }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var screenTimeout by remember { mutableStateOf("") }
    // 电池
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }
    var lowPower by remember { mutableStateOf(false) }
    var stayOn by remember { mutableStateOf("0") }
    var batteryLevel by remember { mutableFloatStateOf(80f) }
    var simulateCharging by remember { mutableStateOf(true) }
    // 屏幕显示
    var screen by remember { mutableStateOf<ScreenInfo?>(null) }
    var sizeW by remember { mutableStateOf("") }
    var sizeH by remember { mutableStateOf("") }
    var density by remember { mutableStateOf("") }
    var nightMode by remember { mutableStateOf("no") }
    var showTouches by remember { mutableStateOf(false) }
    var pointerLocation by remember { mutableStateOf(false) }
    var debugLayout by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf("128") }
    var brightnessSlider by remember { mutableFloatStateOf(128f) }
    var autoRotate by remember { mutableStateOf(true) }
    var wifiAdb by remember { mutableStateOf(false) }
    var wifiAdbIp by remember { mutableStateOf("-") }
    var recording by remember { mutableStateOf(false) }
    var ovLoad by remember { mutableStateOf(false) }
    var ovProcess by remember { mutableStateOf(false) }
    var ovFps by remember { mutableStateOf(false) }
    var ovTemp by remember { mutableStateOf(false) }
    var subScreen by remember { mutableStateOf<String?>(null) }
    // 命令
    var command by remember { mutableStateOf("") }
    var funcSearch by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var favs by remember { mutableStateOf<List<String>>(emptyList()) }
    var smsNum by remember { mutableStateOf("") }
    var smsBody by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var pendingRisk by remember { mutableStateOf<String?>(null) }
    var pendingRisks by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun refreshValues() {
        scope.launch {
            animScale = Shell.runAsync("settings get global window_animation_scale").out.trim()
            pointerSpeed = Shell.runAsync("settings get system pointer_speed").out.trim()
            sliderValue = pointerSpeed.toFloatOrNull() ?: 0f
            screenTimeout = Shell.runAsync("settings get system screen_off_timeout").out.trim()
            lowPower = Shell.runAsync("settings get global low_power").out.trim() == "1"
            stayOn = Shell.runAsync("settings get global stay_on_while_plugged_in").out.trim()
            nightMode = Shell.runAsync("cmd uimode night").out
                .contains("yes").let { if (it) "yes" else "no" }
            showTouches = Shell.runAsync("settings get system show_touches").out.trim() == "1"
            pointerLocation = Shell.runAsync("settings get system pointer_location").out.trim() == "1"
            debugLayout = Shell.runAsync("getprop debug.layout").out.trim().let { it == "1" || it == "true" }
            brightness = Shell.runAsync("settings get system screen_brightness").out.trim()
            brightness.toIntOrNull()?.let { brightnessSlider = it.toFloat() }
            autoRotate = Shell.runAsync("settings get system accelerometer_rotation").out.trim() == "1"
            wifiAdb = com.freeplay.toolbox.data.OptimizationRepository.wifiAdbEnabled()
            if (wifiAdb) wifiAdbIp = com.freeplay.toolbox.data.OptimizationRepository.wifiAdbIp()
            battery = BatteryRepository.load()
            screen = ScreenRepository.load()
            screen.let {
                if (it != null) {
                    if (sizeW.isBlank() && it.physicalSize.contains("x")) {
                        val parts = it.physicalSize.split("x")
                        sizeW = parts.getOrNull(0) ?: ""
                        sizeH = parts.getOrNull(1) ?: ""
                    }
                    if (density.isBlank()) density = it.overrideDensity ?: it.physicalDensity
                }
            }
            loaded = true
        }
    }

    LaunchedEffect(Unit) {
        refreshValues()
        favs = CommandStore.load(context)
    }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    fun runWithRisk(title: String, risks: List<String>, action: () -> Unit) {
        pendingRisk = title
        pendingRisks = risks
        pendingAction = action
    }

    val cat = selectedCategory
    BackHandler(enabled = cat != null) { selectedCategory = null }

    val categories = listOf(
        Category("电池", "电池 电量 电压 温度 健康 省电 充电 常亮", "电量模拟、省电模式、充电常亮", Icons.Filled.BatteryFull),
        Category("屏幕", "屏幕 分辨率 密度 亮度 旋转 截屏 触控", "分辨率/DPI、亮度、自动旋转、截屏", Icons.Filled.Devices),
        Category("显示", "显示 暗色 触摸 指针 布局 开发者", "暗色模式、触摸显示、指针位置、布局边界", Icons.Filled.Visibility),
        Category("系统设置", "动画 速度 指针 熄屏 设置", "动画速度、指针速度、熄屏时间", Icons.Filled.Settings),
        Category("系统操作", "重启 recovery bootloader 关机 adb 无线 录制 录屏", "重启、无线 ADB、屏幕录制", Icons.Filled.PowerSettingsNew),
        Category("设备控制", "dhizuku 设备 相机 静音 锁屏 状态栏 代理 隐藏 卸载", "Dhizuku 设备所有者能力", Icons.Filled.Smartphone),
        Category("工具", "工具 伪装 短信 命令 收藏 adb", "伪装短信、自定义命令、收藏夹", Icons.Filled.Build),
        Category("悬浮窗", "悬浮窗 监视 负载 进程 帧率 温度 内存 cpu", "负载/进程/帧率/温度悬浮监视器", Icons.Filled.Visibility),
        Category("网络", "网络 ip dns wifi 网关 私有dns", "IP/DNS/WiFi 信息与私有 DNS 设置", Icons.Filled.Settings),
        Category("通知", "通知 常驻 屏蔽 静音 状态栏", "活动通知列表、按包屏蔽、隐藏常驻", Icons.Filled.Visibility),
        Category("隐私", "隐私 审计 相机 麦克风 位置 传感器", "敏感权限的最近访问记录", Icons.Filled.Visibility),
    )

    if (subScreen == "network") {
        NetworkScreen(onBack = { subScreen = null }, modifier = modifier)
    } else if (subScreen == "notify") {
        NotificationScreen(onBack = { subScreen = null }, modifier = modifier)
    } else if (subScreen == "privacy") {
        PrivacyScreen(onBack = { subScreen = null }, modifier = modifier)
    } else if (cat == null) {
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeonBanner(title = "功能", subtitle = if (loaded) "Shizuku 已就绪" else "读取状态中…")
            OutlinedTextField(
                value = funcSearch,
                onValueChange = { funcSearch = it },
                placeholder = { Text("搜索功能，如：亮度、电池、重启") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            categories
                .filter {
                    funcSearch.isBlank() || it.name.contains(funcSearch, true) ||
                        it.keywords.contains(funcSearch, true)
                }
                .forEach { c ->
                    KsuListItem(
                        icon = c.icon,
                        title = c.name,
                        subtitle = c.subtitle,
                        onClick = { selectedCategory = c.name },
                    )
                }
        }
    } else {
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedCategory = null }) { Text("← 返回") }
                    Text(cat, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        // ===================== 电池 =====================
        if (cat == "电池") {
        item { SectionTitle("电池") }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text("电池状态", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "刷新",
                            fontSize = 13.sp,
                            color = NeonCyan,
                            modifier = Modifier.clickable { scope.launch { battery = BatteryRepository.load() } },
                        )
                    }
                    val b = battery
                    if (b == null) {
                        Text("读取中…（需 Shizuku）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        InfoLine("电量", "${b.level}%")
                        InfoLine("状态", "${b.statusText}（${b.powerSource}）")
                        InfoLine("温度", "${b.tempC}°C")
                        InfoLine("电压", "${b.voltageV} V")
                        InfoLine("健康", b.healthText)
                        InfoLine("技术", b.technology)
                    }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("电量模拟", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text("调试用", fontSize = 12.sp, color = NeonPurple)
                    }
                    Text(
                        "欺骗系统显示的电量与充电状态（真实充电不受影响，重启后恢复）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = simulateCharging,
                            onClick = { simulateCharging = true },
                            label = { Text("充电中", fontSize = 11.sp) },
                        )
                        FilterChip(
                            selected = !simulateCharging,
                            onClick = { simulateCharging = false },
                            label = { Text("放电中", fontSize = 11.sp) },
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            when {
                                simulateCharging && batteryLevel.toInt() >= 99 -> "已充满"
                                simulateCharging -> "往上涨"
                                else -> "往下掉"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = batteryLevel,
                        onValueChange = { batteryLevel = it },
                        valueRange = 0f..100f,
                        steps = 99,
                        enabled = AppState.shizukuGranted,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(batteryLevel.toInt(), 0..100) { batteryLevel = it.toFloat() }
                        Text("%", fontSize = 12.sp, color = NeonCyan)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = AppState.shizukuGranted,
                            onClick = {
                                val lv = batteryLevel.toInt()
                                runWithRisk(
                                    if (simulateCharging) "电量模拟 ${lv}%（充电）" else "电量模拟 ${lv}%（放电）",
                                    listOf(
                                        "状态栏电池显示将与真实电量不符，充电图标可能出现异常",
                                        "系统可能据此做出错误判断（如触发低电量省电策略）",
                                        "重启或点击「恢复真实」即可还原",
                                    ),
                                ) {
                                    scope.launch {
                                        Shell.runAsync(
                                            "dumpsys battery set level $lv && " +
                                                "dumpsys battery set status ${if (lv >= 99) 5 else 2} && " +
                                                "dumpsys battery set ac 1"
                                        )
                                        toast = if (simulateCharging) "已模拟为 ${lv}% 充电中" else "已模拟为 ${lv}% 放电中"
                                    }
                                }
                            },
                        ) { Text("模拟") }
                        OutlinedButton(
                            enabled = AppState.shizukuGranted,
                            onClick = {
                                scope.launch {
                                    Shell.runAsync("dumpsys battery reset")
                                    toast = "已恢复真实电量显示"
                                }
                            },
                        ) { Text("恢复真实") }
                    }
                }
            }
        }

        item {
            NeonCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("省电模式", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "强制开启/关闭系统省电（低电量）模式",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = lowPower,
                        onCheckedChange = { on ->
                            scope.launch {
                                Shell.runAsync("settings put global low_power ${if (on) "1" else "0"}")
                                refreshValues()
                                toast = if (on) "省电模式已开启" else "省电模式已关闭"
                            }
                        },
                        enabled = AppState.shizukuGranted,
                    )
                }
            }
        }

        item {
            NeonCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("充电时屏幕常亮", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "插电/USB 时保持唤醒，方便调试",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = stayOn == "7",
                        onCheckedChange = { on ->
                            scope.launch {
                                Shell.runAsync("settings put global stay_on_while_plugged_in ${if (on) "7" else "0"}")
                                refreshValues()
                            }
                        },
                        enabled = AppState.shizukuGranted,
                    )
                }
            }
        }

        }
        // ===================== 屏幕与显示 =====================
        if (cat == "屏幕") {
        item { SectionTitle("屏幕") }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("分辨率 / 密度", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            screen?.let { s ->
                                "物理 ${s.physicalSize}" + (s.overrideSize?.let { " → $it" } ?: "")
                            } ?: "读取中…",
                            fontSize = 11.sp,
                            color = NeonCyan,
                        )
                    }
                    Text(
                        "修改显示分辨率与 DPI（改错会 UI 错乱，可一键恢复）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sizeW, onValueChange = { sizeW = it },
                            label = { Text("宽") }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = sizeH, onValueChange = { sizeH = it },
                            label = { Text("高") }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = density, onValueChange = { density = it },
                            label = { Text("DPI") }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = AppState.shizukuGranted,
                            onClick = {
                                runWithRisk(
                                    "修改分辨率/密度",
                                    listOf(
                                        "分辨率或 DPI 设置不当会导致界面错乱、字体异常",
                                        "部分应用可能显示不全，恢复默认即可还原",
                                    ),
                                ) {
                                    scope.launch {
                                        val w = sizeW.toIntOrNull()
                                        val h = sizeH.toIntOrNull()
                                        val d = density.toIntOrNull()
                                        if (w != null && h != null && w > 0 && h > 0) {
                                            ScreenRepository.setSize(w, h)
                                        }
                                        if (d != null && d > 0) {
                                            ScreenRepository.setDensity(d)
                                        }
                                        screen = ScreenRepository.load()
                                        toast = "已应用，若界面异常请点「恢复默认」"
                                    }
                                }
                            },
                        ) { Text("应用") }
                        OutlinedButton(
                            enabled = AppState.shizukuGranted,
                            onClick = {
                                scope.launch {
                                    ScreenRepository.resetSize()
                                    ScreenRepository.resetDensity()
                                    screen = ScreenRepository.load()
                                    toast = "已恢复默认分辨率与密度"
                                }
                            },
                        ) { Text("恢复默认") }
                    }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("截屏", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "保存到 Pictures/Screenshots（会包含本应用当前画面）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = AppState.shizukuGranted,
                        onClick = {
                            scope.launch {
                                val path = ScreenRepository.screenshot()
                                toast = if (path != null) "已保存：$path" else "截屏失败"
                            }
                        },
                    ) { Text("立即截屏") }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("屏幕亮度", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text("当前 $brightness / 255", fontSize = 12.sp, color = NeonCyan)
                    }
                    Slider(
                        value = brightnessSlider,
                        onValueChange = { brightnessSlider = it },
                        valueRange = 0f..255f,
                        enabled = AppState.shizukuGranted,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(brightnessSlider.toInt(), 0..255) { brightnessSlider = it.toFloat() }
                        OutlinedButton(
                            enabled = AppState.shizukuGranted,
                            onClick = {
                                scope.launch {
                                    Shell.runAsync("settings put system screen_brightness ${brightnessSlider.toInt()}")
                                    refreshValues()
                                    toast = "亮度 → ${brightnessSlider.toInt()}"
                                }
                            },
                        ) { Text("应用") }
                        Spacer(Modifier.weight(1f))
                        Text("${brightnessSlider.toInt()}", fontSize = 12.sp, color = NeonCyan)
                    }
                }
            }
        }

        item {
            NeonCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动旋转", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "根据重力传感器自动旋转屏幕",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoRotate,
                        onCheckedChange = { on ->
                            scope.launch {
                                Shell.runAsync("settings put system accelerometer_rotation ${if (on) "1" else "0"}")
                                refreshValues()
                            }
                        },
                        enabled = AppState.shizukuGranted,
                    )
                }
            }
        }

        }
        if (cat == "显示") {
        // ---- 显示 ----
        item { SectionTitle("显示") }
                item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("强制暗色模式", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("yes" to "开启", "no" to "关闭", "auto" to "跟随系统").forEach { (v, label) ->
                            FilterChip(
                                selected = nightMode == v,
                                onClick = {
                                    scope.launch {
                                        Shell.runAsync("cmd uimode night $v")
                                        refreshValues()
                                        toast = "暗色模式 → $label"
                                    }
                                },
                                label = { Text(label) },
                                enabled = AppState.shizukuGranted,
                            )
                        }
                    }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("开发者显示选项", style = MaterialTheme.typography.titleMedium)
                    DisplaySwitch("显示触摸操作", "触摸位置显示圆点", showTouches) { on ->
                        scope.launch {
                            Shell.runAsync("settings put system show_touches ${if (on) "1" else "0"}")
                            refreshValues()
                        }
                    }
                    DisplaySwitch("指针位置", "显示十字指针与坐标", pointerLocation) { on ->
                        scope.launch {
                            Shell.runAsync("settings put system pointer_location ${if (on) "1" else "0"}")
                            refreshValues()
                        }
                    }
                    DisplaySwitch("显示布局边界", "绘制组件边界与尺寸（调试布局）", debugLayout) { on ->
                        scope.launch {
                            Shell.runAsync("setprop debug.layout ${if (on) "true" else "false"}")
                            refreshValues()
                        }
                    }
                }
            }
        }

        }
        // ===================== 系统调参 =====================
        if (cat == "系统设置") {
        item { SectionTitle("系统设置") }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("动画速度", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(if (loaded) "当前 $animScale" else "读取中…", fontSize = 12.sp, color = NeonCyan)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("0" to "关闭", "0.5" to "0.5x", "1" to "1x").forEach { (v, label) ->
                            FilterChip(
                                selected = animScale == v,
                                onClick = {
                                    scope.launch {
                                        Shell.runAsync(
                                            "settings put global window_animation_scale $v && " +
                                                "settings put global transition_animation_scale $v && " +
                                                "settings put global animator_duration_scale $v"
                                        )
                                        refreshValues()
                                        toast = "动画速度 → $label"
                                    }
                                },
                                label = { Text(label) },
                                enabled = AppState.shizukuGranted,
                            )
                        }
                    }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("指针速度", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(if (loaded) "当前 $pointerSpeed" else "读取中…", fontSize = 12.sp, color = NeonCyan)
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = -7f..7f,
                        steps = 13,
                        enabled = AppState.shizukuGranted,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(sliderValue.toInt(), -7..7) { sliderValue = it.toFloat() }
                        OutlinedButton(
                            enabled = AppState.shizukuGranted,
                            onClick = {
                                scope.launch {
                                    Shell.runAsync("settings put system pointer_speed ${sliderValue.toInt()}")
                                    refreshValues()
                                    toast = "指针速度 → ${sliderValue.toInt()}"
                                }
                            },
                        ) { Text("应用") }
                        Spacer(Modifier.weight(1f))
                        Text("${sliderValue.toInt()}", fontSize = 12.sp, color = NeonCyan)
                    }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自动熄屏时间", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (screenTimeout.isNotBlank()) "当前 ${(screenTimeout.toIntOrNull() ?: 0) / 1000}s" else "读取中…",
                            fontSize = 12.sp,
                            color = NeonCyan,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "15000" to "15s", "30000" to "30s", "60000" to "1m",
                            "300000" to "5m", "2147483647" to "永不",
                        ).forEach { (v, label) ->
                            FilterChip(
                                selected = screenTimeout == v,
                                onClick = {
                                    scope.launch {
                                        Shell.runAsync("settings put system screen_off_timeout $v")
                                        refreshValues()
                                        toast = "熄屏时间 → $label"
                                    }
                                },
                                label = { Text(label) },
                                enabled = AppState.shizukuGranted,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(screenTimeout.toIntOrNull()?.div(60000) ?: 1, 1..1440) { min ->
                            scope.launch {
                                Shell.runAsync("settings put system screen_off_timeout ${min * 60000}")
                                refreshValues()
                                toast = "熄屏时间 → ${min} 分钟"
                            }
                        }
                        Text("分钟", fontSize = 12.sp, color = NeonCyan)
                    }
                }
            }
        }

        }
        // ===================== 系统操作 =====================
        if (cat == "系统操作") {
        item { SectionTitle("系统操作") }

        item {
            NeonCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("无线 ADB", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (wifiAdb) "已开启 · $wifiAdbIp:5555" else "关闭中（开启后可用电脑无线调试）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = wifiAdb,
                        enabled = AppState.shizukuGranted,
                        onCheckedChange = { on ->
                            scope.launch {
                                com.freeplay.toolbox.data.OptimizationRepository.setWifiAdb(on)
                                wifiAdb = on
                                if (on) wifiAdbIp = com.freeplay.toolbox.data.OptimizationRepository.wifiAdbIp()
                                toast = if (on) "无线 ADB 已开启（重启后失效）" else "无线 ADB 已关闭"
                            }
                        },
                    )
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("屏幕录制", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (recording) "录制中…再点一次停止并保存"
                        else "录制屏幕，保存到 Download/DSH-Go",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = AppState.shizukuGranted,
                        onClick = {
                            scope.launch {
                                if (recording) {
                                    com.freeplay.toolbox.data.OptimizationRepository.stopScreenRecord()
                                    recording = false
                                    toast = "已停止录制，文件在 Download/DSH-Go"
                                } else {
                                    val path = com.freeplay.toolbox.data.OptimizationRepository.startScreenRecord()
                                    recording = path != null
                                    toast = if (path != null) "开始录制" else "录制启动失败"
                                }
                            }
                        },
                    ) { Text(if (recording) "停止录制" else "开始录制") }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("重启设备", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "选择重启目标模式（以 shell 身份执行）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "重启" to "svc power reboot",
                            "Recovery" to "reboot recovery",
                            "Bootloader" to "reboot bootloader",
                            "关机" to "svc power shutdown",
                        ).forEach { (label, cmd) ->
                            OutlinedButton(
                                enabled = AppState.shizukuGranted,
                                onClick = {
                                    runWithRisk(
                                        "重启 → $label",
                                        listOf(
                                            "设备将立即$label，未保存的数据可能丢失",
                                            "正在进行的下载、通话等任务会中断",
                                            "Recovery/Bootloader 模式可能需要手动重启回到系统",
                                        ),
                                    ) {
                                        scope.launch { Shell.runAsync(cmd) }
                                    }
                                },
                            ) { Text(label, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }

        }
        // ===================== 设备控制（Dhizuku） =====================
        if (cat == "设备控制") {
        item { SectionTitle("设备控制（Dhizuku）") }
        item { DeviceControlCards() }

        }
        // ===================== 伪装与命令 =====================
        if (cat == "工具") {
        item { SectionTitle("工具") }

        if (favs.isNotEmpty()) {
            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("收藏夹（${favs.size}）", style = MaterialTheme.typography.titleMedium)
                        Text("点 ▶ 执行，点 × 删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        favs.forEach { fav ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    fav,
                                    fontSize = 12.sp,
                                    color = NeonPurple,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 6.dp),
                                )
                                Text(
                                    "▶",
                                    color = NeonCyan,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clickable {
                                            running = true
                                            scope.launch {
                                                val r = Shell.runAsync(fav)
                                                output = "exit=${r.exitCode}\n" +
                                                    (r.out.ifBlank { r.err.ifBlank { "(无输出)" } }).take(2000)
                                                running = false
                                            }
                                        },
                                )
                                Text(
                                    "×",
                                    color = NeonRed,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clickable {
                                            favs = favs - fav
                                            CommandStore.save(context, favs)
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("伪装短信记录", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text("写入数据库", fontSize = 12.sp, color = NeonPurple)
                    }
                    Text(
                        "向短信收件箱写入一条假记录（不经过无线电，仅供个人调试）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = smsNum,
                        onValueChange = { smsNum = it },
                        placeholder = { Text("发送方号码，如 10086") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = smsBody,
                        onValueChange = { smsBody = it },
                        placeholder = { Text("短信内容") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = AppState.shizukuGranted && smsNum.isNotBlank() && smsBody.isNotBlank(),
                        onClick = {
                            runWithRisk(
                                "写入伪装短信记录",
                                listOf(
                                    "记录将出现在短信应用的收件箱中，与真实短信难以区分",
                                    "严禁用于欺诈、伪造官方通知等非法用途，违者责任自负",
                                    "可通过在短信应用中删除该记录还原",
                                ),
                            ) {
                                scope.launch {
                                    val ts = System.currentTimeMillis()
                                    Shell.runAsync(
                                        "content insert --uri content://sms/inbox " +
                                            "--bind address:s:${shQuote(smsNum)} " +
                                            "--bind body:s:${shQuote(smsBody)} " +
                                            "--bind date:l:$ts --bind type:i:1"
                                    )
                                    toast = "已写入（如未显示，重启短信应用）"
                                }
                            }
                        },
                    ) { Text("写入收件箱") }
                }
            }
        }

        item {
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("自定义命令", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        placeholder = { Text("如 pm list packages -3") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !running && command.isNotBlank(),
                            onClick = {
                                running = true
                                val cmd = command
                                scope.launch {
                                    val r = Shell.runAsync(cmd)
                                    output = "exit=${r.exitCode}\n" +
                                        (r.out.ifBlank { r.err.ifBlank { "(无输出)" } }).take(2000)
                                    running = false
                                }
                            },
                        ) { Text(if (running) "执行中…" else "执行") }
                        OutlinedButton(
                            enabled = command.isNotBlank(),
                            onClick = {
                                if (command.isNotBlank() && command !in favs) {
                                    favs = (favs + command).sorted()
                                    CommandStore.save(context, favs)
                                    toast = "已收藏"
                                }
                            },
                        ) { Text("收藏") }
                    }
                    if (output.isNotBlank()) {
                        Text(
                            output,
                            fontSize = 11.sp,
                            color = NeonGreen,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        }

        if (cat == "网络" || cat == "通知" || cat == "隐私") {
            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(cat, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (cat) {
                                "网络" -> "查看 IP / 网关 / DNS，设置私有 DNS（可加密查询、屏蔽广告域名）"
                                "通知" -> "列出当前活动通知，按包屏蔽全部通知；常驻通知同样可隐藏"
                                else -> "统计相机、麦克风、位置、身体活动的最近访问时间"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            enabled = AppState.shizukuGranted || cat == "网络",
                            onClick = {
                                subScreen = when (cat) {
                                    "网络" -> "network"
                                    "通知" -> "notify"
                                    else -> "privacy"
                                }
                            },
                        ) { Text("打开${cat}面板") }
                    }
                }
            }
        }

        if (cat == "悬浮窗") {
            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("悬浮监视器", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "开启后可拖动到任意位置，不会超出屏幕边界",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallPill(if (ovLoad) "负载 开" else "负载", ovLoad, AppState.shizukuGranted) {
                                ovLoad = !ovLoad; syncOverlay(context, OverlayService.Kind.LOAD, ovLoad)
                            }
                            SmallPill(if (ovProcess) "进程 开" else "进程", ovProcess, AppState.shizukuGranted) {
                                ovProcess = !ovProcess; syncOverlay(context, OverlayService.Kind.PROCESS, ovProcess)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallPill(if (ovFps) "帧率 开" else "帧率", ovFps, AppState.shizukuGranted) {
                                ovFps = !ovFps; syncOverlay(context, OverlayService.Kind.FPS, ovFps)
                            }
                            SmallPill(if (ovTemp) "温度 开" else "温度", ovTemp, AppState.shizukuGranted) {
                                ovTemp = !ovTemp; syncOverlay(context, OverlayService.Kind.TEMP, ovTemp)
                            }
                        }
                        Text(
                            "帧率为本进程测得的屏幕刷新节奏，作为全局流畅度参考；温度取决于 ROM 是否公开传感器",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }


    pendingRisk?.let { title ->
        RiskConfirmDialog(
            title = title,
            risks = pendingRisks,
            onDismiss = { pendingRisk = null; pendingAction = null },
            onConfirm = {
                pendingRisk = null
                pendingAction?.invoke()
                pendingAction = null
            },
        )
    }
}
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = NeonCyan)
    }
}

@Composable
private fun DisplaySwitch(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = AppState.shizukuGranted)
    }
}

@Composable
private fun NumberField(
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val filtered = t.filter { c -> c.isDigit() || c == '-' }.take(6)
            text = filtered
            filtered.toIntOrNull()?.let { if (it in range) onValue(it) }
        },
        singleLine = true,
        modifier = Modifier.width(76.dp),
    )
}
