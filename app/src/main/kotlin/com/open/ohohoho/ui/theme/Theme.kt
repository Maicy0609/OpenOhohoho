package com.open.ohohoho.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 浅色色板（暖橙"猫系"），取自 Material Theme Builder 风格，
 * 所有颜色都映射到 M3 语义角色，由组件通过 MaterialTheme.colorScheme 使用。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF8B5000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2C1600),
    inversePrimary = Color(0xFFFFB86E),
    secondary = Color(0xFF735943),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF2A1706),
    tertiary = Color(0xFF5C6300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0EA7E),
    onTertiaryContainer = Color(0xFF1A1D00),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF3DED0),
    onSurfaceVariant = Color(0xFF52443B),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD6C3B6),
    surfaceTint = Color(0xFF8B5000),
    inverseSurface = Color(0xFF382F28),
    inverseOnSurface = Color(0xFFFFEDE1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** 深色色板。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB86E),
    onPrimary = Color(0xFF492900),
    primaryContainer = Color(0xFF693E00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    inversePrimary = Color(0xFF8B5000),
    secondary = Color(0xFFE5BFA5),
    onSecondary = Color(0xFF422B19),
    secondaryContainer = Color(0xFF5A412E),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFFC3CD65),
    onTertiary = Color(0xFF2F3300),
    tertiaryContainer = Color(0xFF454A00),
    onTertiaryContainer = Color(0xFFE0EA7E),
    background = Color(0xFF1A120B),
    onBackground = Color(0xFFEFE0D5),
    surface = Color(0xFF1A120B),
    onSurface = Color(0xFFEFE0D5),
    surfaceVariant = Color(0xFF52443B),
    onSurfaceVariant = Color(0xFFD6C3B6),
    outline = Color(0xFF9E8D82),
    outlineVariant = Color(0xFF52443B),
    surfaceTint = Color(0xFFFFB86E),
    inverseSurface = Color(0xFFEFE0D5),
    inverseOnSurface = Color(0xFF382F28),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * App 主题入口。
 *  - 浅色/深色跟随系统
 *  - Android 12+ 默认启用 Dynamic Color（壁纸取色）
 *  - 复用统一的 Typography / Shapes
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}
