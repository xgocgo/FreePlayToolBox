package com.freeplay.toolbox.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.freeplay.toolbox.ui.screens.AppsScreen
import com.freeplay.toolbox.ui.screens.DashboardScreen
import com.freeplay.toolbox.ui.screens.SettingsScreen
import com.freeplay.toolbox.ui.screens.ToolsScreen

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("仪表盘", Icons.Filled.Dashboard),
    Tab("应用", Icons.Filled.Apps),
    Tab("功能", Icons.Filled.Build),
    Tab("设置", Icons.Filled.Settings),
)

@Composable
fun MainNav() {
    var current by rememberSaveable { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var unread by remember { mutableStateOf<List<com.freeplay.toolbox.core.Announcement>>(emptyList()) }

    // 启动时只弹未读公告；关闭后标记已读，之后仅在首页常驻显示
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val list = com.freeplay.toolbox.core.RemoteConfig.fetchAnnouncements(context, onlyUnread = true)
        if (list.isNotEmpty()) unread = list
    }

    if (unread.isNotEmpty()) {
        com.freeplay.toolbox.ui.components.AnnouncementDialog(
            items = unread,
            onClose = {
                com.freeplay.toolbox.core.RemoteConfig.markRead(context, unread.map { it.id })
                unread = emptyList()
            },
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = current == index,
                        onClick = { current = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (current) {
            0 -> DashboardScreen(modifier)
            1 -> AppsScreen(modifier)
            2 -> ToolsScreen(modifier)
            else -> SettingsScreen(modifier)
        }
    }
}
