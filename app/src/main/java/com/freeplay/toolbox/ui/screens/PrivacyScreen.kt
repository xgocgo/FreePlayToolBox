package com.freeplay.toolbox.ui.screens

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.data.PrivacyRecord
import com.freeplay.toolbox.data.PrivacyRepository
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.theme.NeonRed

/** 隐私审计：敏感权限的最近访问记录 */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<PrivacyRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var supported by remember { mutableStateOf(true) }

    BackHandler { onBack() }

    fun reload() {
        scope.launch {
            loading = true
            supported = PrivacyRepository.supported()
            records = PrivacyRepository.audit()
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("隐私审计", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload() }) { Text("重新扫描", fontSize = 12.sp) }
        }

        Text(
            "统计相机、麦克风、位置、身体活动等敏感权限的最近访问时间",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )

        if (!AppState.shizukuGranted) {
            Text(
                "Shizuku 未授权，无法读取",
                fontSize = 12.sp,
                color = NeonRed,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (loading) {
            Text(
                "扫描中…（需要解析 appops 记录）",
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
            if (!loading && records.isEmpty()) {
                item {
                    Text(
                        if (supported) "没有解析到访问记录：多数 ROM 不公开 appops 历史，属正常现象"
                        else "该 ROM 未提供 appops 数据",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(records, key = { it.pkg + it.op }) { r ->
                NeonCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.pkg, fontSize = 13.sp, maxLines = 1)
                            Text(
                                "最近访问：${r.lastAccess}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SmallPill(r.op, true) {}
                    }
                }
            }
        }
    }
}
