package com.freeplay.toolbox.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonRed

private data class PLine(val text: String, val tone: Tone)

private enum class Tone { Head, Good, Bad, Body }

private val PRIVACY_LINES = listOf(
    PLine("一、这个应用不会远程控制你的手机", Tone.Head),
    PLine("本应用没有任何远程命令通道。服务器（实际是 GitHub 上的一个静态文件）只能下发两样东西：公告文字、新版本号。它无法向你的设备下达任何指令。", Tone.Good),
    PLine("所有特权操作（卸载、冻结、改权限、禁相机等）只能由你在这台手机上亲手点击触发。代码里不存在\"接收指令并执行\"的路径。", Tone.Good),
    PLine("你可以随时断网使用全部功能——除公告和在线更新外，其余功能完全离线可用。", Tone.Good),

    PLine("二、会上传什么", Tone.Head),
    PLine("仅在开启「公告」或「在线更新」时，进行标准 HTTP 请求，内容包括：", Tone.Body),
    PLine("· 应用版本号、Android 版本号（用于判断更新是否适用）", Tone.Body),
    PLine("· 一个本地随机生成的安装标识（仅在启用使用统计时使用，用于去重，非设备指纹）", Tone.Body),
    PLine("· 请求本身的 IP 地址会被 GitHub / jsDelivr 的服务器记录（任何联网行为都无法避免）", Tone.Body),

    PLine("三、绝不上传什么", Tone.Head),
    PLine("· 你的应用列表、已冻结/卸载记录了哪些应用", Tone.Bad),
    PLine("· 你的联系人、短信、通话记录、照片、文件", Tone.Bad),
    PLine("· 位置、IMEI、MAC、手机号、账号信息", Tone.Bad),
    PLine("· 设备型号之外的硬件标识", Tone.Bad),
    PLine("· 你的操作记录、崩溃日志（崩溃日志只存在本机，仅在你主动点「复制」时才会离开设备）", Tone.Bad),

    PLine("四、你可以完全不上传", Tone.Head),
    PLine("设置 → 安全与隐私 → 打开「完全离线模式」，本应用将不再发起任何网络请求。", Tone.Good),
    PLine("代价只有一个：收不到公告，也无法检查在线更新。其余所有功能照常可用。", Tone.Good),

    PLine("五、权限说明", Tone.Head),
    PLine("· 网络：拉取公告与版本信息", Tone.Body),
    PLine("· 安装应用：用于在线更新时安装新版本", Tone.Body),
    PLine("· 悬浮窗：用于性能监视器", Tone.Body),
    PLine("· 查询应用列表 / 使用情况统计：用于展示应用列表与应用大小", Tone.Body),

    PLine("六、开源可验证", Tone.Head),
    PLine("完整源码公开在 github.com/xgocgo/FreePlayToolBox。上面的每一句话都可以在代码里核对——如果你发现任何与本声明不符的行为，请提 issue。", Tone.Good),
)

/** 隐私与安全声明弹窗 */
@Composable
fun PrivacyDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    acceptText: String = "我已了解，继续使用",
    declineText: String = "改为完全离线使用",
) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("安全与隐私声明", color = NeonCyan) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PRIVACY_LINES.forEach { line ->
                    when (line.tone) {
                        Tone.Head -> {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                line.text,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Tone.Body -> Text(
                            line.text,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        Tone.Good -> Row(Modifier.padding(vertical = 2.dp)) {
                            Text("✓ ", color = NeonGreen, fontSize = 12.sp)
                            Text(
                                line.text,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Tone.Bad -> Row(Modifier.padding(vertical = 2.dp)) {
                            Text("✕ ", color = NeonRed, fontSize = 12.sp)
                            Text(
                                line.text,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        },
        confirmButton = {
            Button(onClick = onAccept) { Text(acceptText) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(declineText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
