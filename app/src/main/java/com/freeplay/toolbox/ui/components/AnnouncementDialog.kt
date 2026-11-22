package com.freeplay.toolbox.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.core.Announcement
import com.freeplay.toolbox.ui.theme.KsuOrange
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonRed

/**
 * 启动时的未读公告弹窗：只对未读的一次性弹出，关闭即标记已读。
 */
@Composable
fun AnnouncementDialog(
    items: List<Announcement>,
    onClose: () -> Unit,
) {
    if (items.isNotEmpty()) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                if (items.size == 1) "新公告" else "有 ${items.size} 条新公告",
                color = NeonCyan,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { i, a ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    val accent = when (a.level) {
                        "important" -> NeonRed
                        "warn" -> KsuOrange
                        else -> NeonCyan
                    }
                    Text(
                        a.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                    )
                    if (a.image.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        RemoteImage(
                            url = a.image,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        )
                    }
                    if (a.body.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            a.body,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "关闭后这些公告会标记为已读，之后在首页仍可随时查看。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("知道了") }
        },
    )
    }
}
