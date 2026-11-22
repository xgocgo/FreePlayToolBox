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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.delay

private data class Line(val text: String, val tone: Tone)
private enum class Tone { Head, Good, Bad, Body }

/** 合并后的声明正文（免责 + 隐私，已去重） */
private val LINES = listOf(
    Line("一、工具性质与风险", Tone.Head),
    Line("本应用是免费的技术研究与设备调试工具，面向具备一定 Android 知识的用户。", Tone.Body),
    Line("部分功能（免 root 卸载/冻结/清数据、电量模拟、设备控制等）通过 Shizuku / Dhizuku 特权通道实现，可能造成应用数据丢失、系统异常、保修状态改变等后果。", Tone.Body),
    Line("所有特权操作只能由你在本机亲手点击触发。本应用没有任何远程命令通道，服务端只能下发公告文字和版本号，无法向你的设备下达指令。", Tone.Good),

    Line("二、责任承担", Tone.Head),
    Line("本应用按「现状」提供，不附带任何明示或默示担保。", Tone.Body),
    Line("依据《中华人民共和国民法典》关于民事主体自愿从事民事活动、对自己行为承担责任的规定，因使用本应用产生的任何直接或间接损失（数据丢失、设备故障、账号受限等），均由你自行承担，开发者不承担任何形式的责任。", Tone.Bad),

    Line("三、联网与数据", Tone.Head),
    Line("本应用默认联网，用途仅两项：拉取公告、检查新版本。没有任何其他网络行为。", Tone.Good),
    Line("会上传的只有：应用版本号、Android 版本号，以及请求本身的 IP（任何联网行为都无法避免，由服务器记录）。", Tone.Body),
    Line("绝不上传：应用列表与冻结记录、联系人、短信、通话记录、照片文件、位置、IMEI、MAC、手机号、账号信息、你的操作记录、崩溃日志（崩溃日志只存在本机，仅在你点「复制」时才会离开设备）。", Tone.Bad),

    Line("四、你可以完全离线", Tone.Head),
    Line("选择「仅同意免责说明」或之后在设置里打开「完全离线模式」，本应用将不再发起任何网络请求。", Tone.Good),
    Line("代价只有一个：收不到公告，也无法检查在线更新。其余功能照常可用。", Tone.Body),

    Line("五、权限说明", Tone.Head),
    Line("网络：拉取公告与版本信息。安装应用：在线更新时安装新版本。悬浮窗：性能监视器。查询应用列表 / 使用情况统计：展示应用列表与大小。", Tone.Body),

    Line("六、合法使用", Tone.Head),
    Line("你承诺遵守《中华人民共和国网络安全法》等法律法规，不利用本应用从事违法违规活动、不侵害他人权益。电量模拟、伪装短信等功能仅供个人设备调试，严禁用于欺诈、伪造信息等非法用途。", Tone.Bad),
    Line("若你是未成年人，请在监护人知情下使用。", Tone.Body),

    Line("七、开源可验证", Tone.Head),
    Line("完整源码公开在 github.com/xgocgo/FreePlayToolBox，上述每一句都可在代码中核对。发现与本声明不符的行为，请提 issue。", Tone.Good),
)

/**
 * 统一声明弹窗。
 * firstTime=true：首次进入，需滚到底 + 15 秒倒计时，且有三个选择。
 * firstTime=false：设置里回看，无任何门槛，单按钮关闭。
 */
@Composable
fun ConsentDialog(
    firstTime: Boolean,
    onExit: () -> Unit,
    onAcceptOffline: () -> Unit,
    onAcceptOnline: () -> Unit,
    onClose: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    var countdown by remember { mutableIntStateOf(if (firstTime) 15 else 0) }

    LaunchedEffect(firstTime) {
        if (firstTime) {
            while (countdown > 0) {
                delay(1000)
                countdown -= 1
            }
        }
    }

    val atEnd = !firstTime || scroll.value >= scroll.maxValue
    val ready = !firstTime || (atEnd && countdown <= 0)

    AlertDialog(
        onDismissRequest = { if (!firstTime) onClose() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (firstTime) "免责与隐私声明" else "免责与隐私声明", color = NeonCyan) },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                LINES.forEach { line ->
                    when (line.tone) {
                        Tone.Head -> {
                            Spacer(Modifier.height(10.dp))
                            Text(line.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Tone.Body -> Text(
                            line.text, fontSize = 12.sp, lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        Tone.Good -> Row(Modifier.padding(vertical = 2.dp)) {
                            Text("✓ ", color = NeonGreen, fontSize = 12.sp)
                            Text(line.text, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Tone.Bad -> Row(Modifier.padding(vertical = 2.dp)) {
                            Text("✕ ", color = NeonRed, fontSize = 12.sp)
                            Text(line.text, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (firstTime) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            countdown > 0 -> "请阅读全部内容（${countdown}s）"
                            !atEnd -> "请滑动到页面底部"
                            else -> "已满足条件，可选择下方任一方式"
                        },
                        fontSize = 11.sp,
                        color = if (ready) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (firstTime) {
                Column(Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ready,
                        onClick = onAcceptOnline,
                    ) { Text("完全同意，进入") }
                    androidx.compose.material3.OutlinedButton(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        enabled = ready,
                        onClick = onAcceptOffline,
                    ) { Text("仅同意免责说明，进入离线模式", fontSize = 12.sp) }
                }
            } else {
                Button(onClick = onClose) { Text("关闭") }
            }
        },
        dismissButton = {
            if (firstTime) {
                TextButton(onClick = onExit) { Text("不同意，退出", fontSize = 12.sp) }
            }
        },
    )
}
