package com.example.memo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 配色思路：宝蓝（#2563EB）做唯一主色——顶栏、按钮、勾选框统一用它，
 * 浅灰（#F1F2F4）做页面底，内容全部装进白色圆角卡片；
 * 不再使用粉/青绿等第二强调色，保持「每日记录」式的克制配色。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7EEFC),
    onPrimaryContainer = Color(0xFF0F2F6B),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDF0F3),
    onSecondaryContainer = Color(0xFF1B1C1E),
    tertiary = Color(0xFF2563EB),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE7FB),
    onTertiaryContainer = Color(0xFF0F2F6B),
    background = Color(0xFFF1F2F4),
    onBackground = Color(0xFF191B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191B1F),
    surfaceVariant = Color(0xFFF1F2F4),
    onSurfaceVariant = Color(0xFF565E71),
    outline = Color(0xFF9AA0A8),
    outlineVariant = Color(0xFFE3E5E8),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB9FF),
    onPrimary = Color(0xFF0A2C6E),
    primaryContainer = Color(0xFF234A9E),
    onPrimaryContainer = Color(0xFFDCE7FB),
    secondary = Color(0xFFB9BEC6),
    onSecondary = Color(0xFF1B1C1E),
    secondaryContainer = Color(0xFF2A2E34),
    onSecondaryContainer = Color(0xFFE2E4E8),
    tertiary = Color(0xFF9DB9FF),
    onTertiary = Color(0xFF0A2C6E),
    tertiaryContainer = Color(0xFF1D3E82),
    onTertiaryContainer = Color(0xFFDCE7FB),
    background = Color(0xFF15171A),
    onBackground = Color(0xFFE2E4E8),
    surface = Color(0xFF1D2024),
    onSurface = Color(0xFFE2E4E8),
    surfaceVariant = Color(0xFF23262B),
    onSurfaceVariant = Color(0xFFB9BEC6),
    outline = Color(0xFF8F959D),
    outlineVariant = Color(0xFF3A3E44),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A)
)

@Composable
fun MemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
