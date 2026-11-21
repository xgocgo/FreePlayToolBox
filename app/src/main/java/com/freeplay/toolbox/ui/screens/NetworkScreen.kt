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
import androidx.compose.material3.MaterialTheme
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
import com.freeplay.toolbox.data.NetInfo
import com.freeplay.toolbox.data.NetworkRepository
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.launch

/** 网络面板：IP / 网关 / DNS / WiFi / 私有 DNS 设置 */
@Composable
fun NetworkScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<NetInfo?>(null) }
    var dnsHost by remember { mutableStateOf("") }
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
            info = NetworkRepository.load()
            info?.let { if (it.dnsHost != "-") dnsHost = it.dnsHost }
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("网络", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload() }) { Text("刷新", fontSize = 12.sp) }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("连接信息", style = MaterialTheme.typography.titleMedium)
                        val i = info
                        if (i == null) {
                            Text("读取中…", fontSize = 12.sp)
                        } else {
                            NetRow("WiFi", i.wifiName)
                            NetRow("本机 IP", i.ip)
                            NetRow("网关", i.gateway)
                            NetRow("DNS", i.dnsServers)
                            NetRow("私有 DNS", when (i.dnsMode) {
                                "off" -> "已关闭"
                                "opportunistic" -> "自动（opportunistic）"
                                "hostname" -> i.dnsHost
                                else -> i.dnsMode
                            })
                        }
                    }
                }
            }

            item {
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("私有 DNS", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "hostname 模式可加密 DNS 查询，可用来屏蔽广告域名",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallPill("关闭", info?.dnsMode == "off", AppState.shizukuGranted) {
                                scope.launch {
                                    NetworkRepository.setDnsMode("off")
                                    toast = "私有 DNS 已关闭"
                                    reload()
                                }
                            }
                            SmallPill("自动", info?.dnsMode == "opportunistic", AppState.shizukuGranted) {
                                scope.launch {
                                    NetworkRepository.setDnsMode("opportunistic")
                                    toast = "私有 DNS → 自动"
                                    reload()
                                }
                            }
                        }
                        OutlinedTextField(
                            value = dnsHost,
                            onValueChange = { dnsHost = it },
                            placeholder = { Text("如 dns.adguard.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SmallPill("应用主机名", info?.dnsMode == "hostname", AppState.shizukuGranted && dnsHost.isNotBlank()) {
                            scope.launch {
                                NetworkRepository.setDnsMode("hostname", dnsHost.trim())
                                toast = "私有 DNS → ${dnsHost.trim()}"
                                reload()
                            }
                        }
                    }
                }
            }

            if (!AppState.shizukuGranted) {
                item {
                    Text(
                        "Shizuku 未授权，只能查看",
                        fontSize = 12.sp,
                        color = NeonRed,
                    )
                }
            }

            item {
                Text(
                    "提示：修改私有 DNS 后可能需要开关一次 WiFi 才生效",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NetRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = NeonCyan, maxLines = 1)
    }
}
