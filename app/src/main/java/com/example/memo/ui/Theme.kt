package com.example.memo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 配色思路：靛蓝（Indigo）做主色与顶栏，暖粉做次强调，
 * 青绿（Teal）专门表达「已完成」这一状态色。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF4C5BD5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF101A66),
    secondary = Color(0xFFB34A7E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E6),
    onSecondaryContainer = Color(0xFF3E1129),
    tertiary = Color(0xFF00796B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB7EFE4),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFFBF9FF),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFE4E1F7),
    onSurfaceVariant = Color(0xFF49455E),
    outline = Color(0xFF8A87A6),
    outlineVariant = Color(0xFFD8D5EC),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBEC4FF),
    onPrimary = Color(0xFF232F7C),
    primaryContainer = Color(0xFF333F9B),
    onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFFFFB0CB),
    onSecondary = Color(0xFF5D1040),
    secondaryContainer = Color(0xFF7C2C58),
    onSecondaryContainer = Color(0xFFFFD9E6),
    tertiary = Color(0xFF79D9C9),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFB7EFE4),
    background = Color(0xFF141322),
    onBackground = Color(0xFFE5E1F0),
    surface = Color(0xFF1B1A2B),
    onSurface = Color(0xFFE5E1F0),
    surfaceVariant = Color(0xFF2A2842),
    onSurfaceVariant = Color(0xFFC8C4DE),
    outline = Color(0xFF928FA9),
    outlineVariant = Color(0xFF47445E),
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
