package com.freeplay.toolbox

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.freeplay.toolbox.core.AppState
import com.freeplay.toolbox.core.CrashLogger
import com.freeplay.toolbox.core.RemoteConfig
import com.freeplay.toolbox.core.ShizukuManager
import com.freeplay.toolbox.ui.DisclaimerGate
import com.freeplay.toolbox.ui.MainNav
import com.freeplay.toolbox.ui.components.CrashReportDialog
import com.freeplay.toolbox.ui.components.PrivacyDialog
import com.freeplay.toolbox.ui.theme.ToolBoxTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    private var accepted by mutableStateOf(true)
    private var done by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuManager.addPermissionListener(this)
        accepted = getSharedPreferences("toolbox_prefs", Context.MODE_PRIVATE)
            .getBoolean("disclaimer_accepted", false)
        done = true
        val prefs = getSharedPreferences("toolbox_prefs", Context.MODE_PRIVATE)
        val crashLog = CrashLogger.read(this)
        val crashHash = crashLog?.hashCode()?.toString()
        val seenHash = prefs.getString("crash_seen_hash", null)
        setContent {
            ToolBoxTheme {
                var showCrash by remember { mutableStateOf(crashLog != null && crashHash != seenHash) }
                if (showCrash && crashLog != null) {
                    CrashReportDialog(
                        log = crashLog,
                        onDismiss = {
                            prefs.edit().putString("crash_seen_hash", crashHash).apply()
                            showCrash = false
                        },
                    )
                } else {
                    var privacyAcked by remember {
                        mutableStateOf(
                            prefs.getBoolean("privacy_ack", false)
                        )
                    }
                    DisclaimerGate(
                        accepted = done && accepted,
                        onAccept = {
                            prefs.edit().putBoolean("disclaimer_accepted", true).apply()
                            accepted = true
                        },
                        onDecline = { finishAffinity() },
                    ) {
                        if (!privacyAcked) {
                            PrivacyDialog(
                                onAccept = {
                                    prefs.edit().putBoolean("privacy_ack", true).apply()
                                    privacyAcked = true
                                },
                                onDecline = {
                                    prefs.edit()
                                        .putBoolean("privacy_ack", true)
                                        .putBoolean(RemoteConfig.KEY_ONLINE, false)
                                        .apply()
                                    privacyAcked = true
                                },
                                acceptText = "我已了解，继续使用",
                                declineText = "完全离线使用（不上传任何数据）",
                            )
                        } else {
                            MainNav()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppState.refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.removePermissionListener(this)
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        ShizukuManager.onPermissionResult(grantResult == PackageManager.PERMISSION_GRANTED)
    }
}
