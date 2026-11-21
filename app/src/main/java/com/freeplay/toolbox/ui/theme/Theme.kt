package com.freeplay.toolbox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// KSU 风格回退配色：深灰底 + 绿色强调（Android 12+ 会改用壁纸动态取色）
val KsuGreen = Color(0xFF69D49A)
val KsuRed = Color(0xFFFF6B6B)
val KsuOrange = Color(0xFFFFB86B)

// 兼容旧引用
val NeonCyan = KsuGreen
val NeonPurple = Color(0xFFB0B0B8)
val NeonGreen = KsuGreen
val NeonRed = KsuRed

private val FallbackDark = darkColorScheme(
    primary = KsuGreen,
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF1B4B30),
    onPrimaryContainer = Color(0xFFC8F5D9),
    secondary = Color(0xFFB8B8C0),
    onSecondary = Color(0xFF101014),
    secondaryContainer = Color(0xFF2C2C33),
    onSecondaryContainer = Color(0xFFE4E4E9),
    tertiary = KsuOrange,
    onTertiary = Color(0xFF3A2200),
    background = Color(0xFF0F0F11),
    onBackground = Color(0xFFEAEAEE),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFEAEAEE),
    surfaceVariant = Color(0xFF25252B),
    onSurfaceVariant = Color(0xFF9A9AA2),
    outline = Color(0xFF3A3A42),
    outlineVariant = Color(0xFF2A2A30),
    error = KsuRed,
    onError = Color(0xFF2B0208),
    surfaceTint = Color.Transparent,
)

/** Material You 动态取色（KSU 同款策略），不可用时用绿色主题回退 */
@Composable
fun ToolBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (darkTheme) FallbackDark else lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
