package com.freeplay.toolbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.freeplay.toolbox.ui.components.DisclaimerDialog

/**
 * 首次启动的免责声明门：未同意时只显示声明弹窗，同意后进入主界面。
 */
@Composable
fun DisclaimerGate(
    accepted: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!accepted) {
        BackHandler(enabled = true) { /* 阻止返回键绕过声明 */ }
        DisclaimerDialog(onAccepted = onAccept, onDeclined = onDecline)
    } else {
        content()
    }
}
