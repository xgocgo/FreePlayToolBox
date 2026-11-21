package com.freeplay.toolbox.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonPurple
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.delay

/**
 * 危险操作确认弹窗：列明后果，按钮「算了吧」/「我已知晓上述风险，继续」。
 */
@Composable
fun RiskConfirmDialog(
    title: String,
    risks: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(title, color = NeonRed, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("本操作可能造成以下后果：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                risks.forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("• ", color = NeonPurple, fontSize = 13.sp)
                        Text(it, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("我已知晓上述风险，继续")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("算了吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

private const val DISCLAIMER_TEXT = """
一、工具性质
本应用是一款免费提供的技术研究与设备调试工具，面向具备一定 Android 系统知识的用户。本应用不联网收集、不上传您的任何个人数据。

二、功能风险
本应用部分功能（免 root 应用卸载/冻结/清除数据、电量模拟、短信记录写入、屏幕分辨率调整、设备控制等）通过 Shizuku / Dhizuku 特权通道实现，可能对系统及第三方应用产生不可逆影响，包括但不限于：应用数据丢失、系统界面异常、设备重启、保修状态改变、部分功能失效、需要恢复出厂设置方可撤销。

三、责任声明
本应用按"现状"提供，不附带任何明示或默示的担保。您自愿使用本应用并自主决定执行相关操作，依据《中华人民共和国民法典》关于民事主体自愿从事民事活动、对自己的行为承担责任的相关规定，因使用本应用产生的任何直接或间接损失（包括但不限于数据丢失、设备故障、账号受限、经济损失），均由您自行承担，开发者不承担任何形式的责任。

四、合法使用
您承诺遵守《中华人民共和国网络安全法》《计算机信息网络国际联网安全保护管理办法》等法律法规，不利用本应用从事任何违法违规活动、不侵害他人合法权益。电量模拟、短信记录写入等功能仅供个人设备调试与研究使用，严禁用于欺诈、伪造信息、骚扰等非法用途，违者责任自负。

五、特别提示
本应用不面向未成年人提供，若您是未成年人，请立即停止使用并卸载本应用。部分厂商可能将侧载应用列为保修失效条件，安装与使用前请知悉。

六、协议变更
开发者保留随时修订本声明的权利，修订后的内容将在应用内提示。您继续使用即视为接受修订后的全部条款。
"""

/**
 * 首次启动免责声明：需滚动到底部且等待 15 秒后才能同意。
 */
@Composable
fun DisclaimerDialog(
    onAccepted: () -> Unit,
    onDeclined: () -> Unit,
) {
    val scroll = rememberScrollState()
    var countdown by remember { mutableStateOf(15) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    val scrolledToEnd = scroll.value >= scroll.maxValue
    val canAccept = scrolledToEnd && countdown <= 0

    AlertDialog(
        onDismissRequest = { /* 不允许点外部关闭 */ },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("免责声明与使用须知", color = NeonCyan) },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                DISCLAIMER_TEXT.trimIndent().split("\n\n").forEach { block ->
                    Text(
                        block.trim(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (countdown > 0) "请阅读全部内容（${countdown}s）"
                    else if (!scrolledToEnd) "请滑动到页面底部"
                    else "已满足条件，可点击同意",
                    fontSize = 11.sp,
                    color = when {
                        canAccept -> NeonGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canAccept,
                onClick = onAccepted,
            ) { Text("我已阅读并同意上述条款") }
        },
        dismissButton = {
            TextButton(onClick = onDeclined) {
                Text("不同意，退出应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
