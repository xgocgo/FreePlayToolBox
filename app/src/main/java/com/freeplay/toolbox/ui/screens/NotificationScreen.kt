package com.freeplay.toolbox.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 通知管理：活动通知列表、按包屏蔽、隐藏常驻通知 */
@Composable
fun NotificationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ActiveNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val blocked = remember { mutableStateListOf<String>() }
    var toast by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            items = withContext(Dispatchers.IO) { NotificationRepository.active() }
            loading = false
            val list = items.map { it.pkg }.distinct()
            blocked.clear()
            list.forEach { p ->
                if (withContext(Dispatchers.IO) { NotificationRepository.isBlocked(p) }) blocked.add(p)
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("通知管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload() }) { Text("刷新", fontSize = 12.sp) }
        }

        Text(
            "开关关闭 = 屏蔽该应用全部通知；常驻通知（如「正在运行」）同样会消失",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )

        if (!AppState.shizukuGranted) {
            Text(
                "Shizuku 未授权，无法修改",
                fontSize = 12.sp,
                color = NeonRed,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (loading) {
            Text(
                "读取当前通知…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!loading && items.isEmpty()) {
                item {
                    Text(
                        "当前没有读到活动通知（部分 ROM 限制 dumpsys 输出）",
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
                                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
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
                            onCheckedChange = { allow ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        NotificationRepository.setBlocked(n.pkg, !allow)
                                    }
                                    if (allow) blocked.remove(n.pkg) else if (!blocked.contains(n.pkg)) blocked.add(n.pkg)
                                    toast = if (allow) "已恢复 ${n.pkg.takeLast(20)} 的通知" else "已隐藏 ${n.pkg.takeLast(20)} 的通知"
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
