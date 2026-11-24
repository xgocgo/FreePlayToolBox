package com.freeplay.toolbox.guard

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.Shell
import com.freeplay.toolbox.ui.theme.ToolBoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 关机被拦下之后弹出来的确认面板。
 *
 * 只做一件事：把「这次关机可能弄丢的东西」摆到脸上，然后让用户决定。
 * 三个出口——解冻后关、直接关、先不关。
 */
class ShutdownGuardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        PowerGuardService.resetCooldown()
        setContent {
            ToolBoxTheme {
                GuardPanel(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun GuardPanel(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<GuardApp>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { GuardRepository.scan(context) }
        scanning = false
    }

    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_SHORT).show()

    fun powerOff() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Shell.runAsync("svc power shutdown") }
            if (!ok.ok) {
                val alt = withContext(Dispatchers.IO) { Shell.runAsync("reboot -p") }
                if (!alt.ok) toast("关机命令失败：${alt.message()}")
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2101218)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("检测到关机", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "关机前检查了未解冻、停用、隐藏的应用",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )

                Spacer(Modifier.height(12.dp))

                when {
                    scanning -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在查询…", fontSize = 12.sp)
                        }
                    }

                    apps.isEmpty() -> {
                        Text(
                            "没查到未解冻的应用。请检查还有没有未解冻的软件。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        Text(
                            "有 ${apps.size} 个应用还处于非正常状态，重启后它们不会自己恢复：",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(
                            Modifier
                                .heightIn(max = 260.dp)
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(apps, key = { it.pkg }) { app ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val bmp = remember(app.pkg) {
                                        app.icon?.let {
                                            runCatching { it.toBitmap(72, 72).asImageBitmap() }.getOrNull()
                                        }
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bmp,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, fontSize = 13.sp, maxLines = 1)
                                        Text(
                                            app.pkg,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        app.mode.badge,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    if (AppState.shizukuGranted) "Shizuku 已连接"
                    else "Shizuku 未连接，解冻和关机命令会失败",
                    fontSize = 10.sp,
                    color = if (AppState.shizukuGranted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )

                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (apps.isNotEmpty()) {
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    val (ok, fails) = withContext(Dispatchers.IO) {
                                        GuardRepository.recover(apps)
                                    }
                                    GuardPrefs.markCheck(context)
                                    toast(
                                        if (fails.isEmpty()) "已解冻 $ok 个，正在关机"
                                        else "解冻 $ok 个，失败 ${fails.size} 个，仍继续关机"
                                    )
                                    delay(500)
                                    busy = false
                                    powerOff()
                                }
                            },
                            enabled = !busy && AppState.shizukuGranted,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "处理中…" else "全部解冻并关机") }
                    }
                    OutlinedButton(
                        onClick = { powerOff() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("直接关机") }
                    TextButton(
                        onClick = onClose,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("先不关") }
                }
            }
        }
    }
}
