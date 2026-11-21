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
