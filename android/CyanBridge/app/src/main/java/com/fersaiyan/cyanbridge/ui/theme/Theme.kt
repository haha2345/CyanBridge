package com.fersaiyan.cyanbridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.ui.accessibility.AccessibilityPrefs

// ── Brand Colors ──────────────────────────────────────────────
val CyanPrimary = Color(0xFF00BCD4)
val CyanDark = Color(0xFF0097A7)
val CyanLight = Color(0xFF80DEEA)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val OnSurfaceDark = Color(0xFFE0E0E0)

// ── High contrast for accessibility ──────────────────────────
val HighContrastYellow = Color(0xFFFFEB3B)
val HighContrastWhite = Color(0xFFFAFAFA)
val HighContrastBlack = Color(0xFF000000)
val HighContrastOrange = Color(0xFFFF9800)

private val DarkColorScheme =
        darkColorScheme(
                primary = CyanPrimary,
                onPrimary = Color.Black,
                primaryContainer = CyanDark,
                secondary = CyanLight,
                background = BackgroundDark,
                surface = SurfaceDark,
                onBackground = OnSurfaceDark,
                onSurface = OnSurfaceDark,
        )

private val LightColorScheme =
        lightColorScheme(
                primary = CyanDark,
                onPrimary = Color.White,
                primaryContainer = CyanLight,
                secondary = CyanPrimary,
                background = Color(0xFFF5F5F5),
                surface = Color.White,
                onBackground = Color(0xFF212121),
                onSurface = Color(0xFF212121),
        )

/** 高对比度配色：纯黑背景 + 黄色高亮 + 白色文字 */
private val HighContrastColorScheme =
        darkColorScheme(
                primary = HighContrastYellow,
                onPrimary = HighContrastBlack,
                primaryContainer = HighContrastOrange,
                onPrimaryContainer = HighContrastBlack,
                secondary = HighContrastYellow,
                onSecondary = HighContrastBlack,
                background = HighContrastBlack,
                onBackground = HighContrastWhite,
                surface = Color(0xFF1A1A1A),
                onSurface = HighContrastWhite,
                surfaceVariant = Color(0xFF2A2A2A),
                onSurfaceVariant = Color(0xFFE0E0E0),
                error = Color(0xFFFF5252),
                onError = HighContrastBlack,
                errorContainer = Color(0xFFCF6679),
                onErrorContainer = HighContrastWhite,
                outline = HighContrastYellow,
        )

/** 常规字体排版 */
private fun defaultTypography() = Typography()

/** 大字体排版（全盲/低视力模式） */
private fun largeTypography(): Typography {
    val base = Typography()
    return Typography(
            displayLarge =
                    base.displayLarge.merge(
                            TextStyle(fontSize = 60.sp, fontWeight = FontWeight.Bold)
                    ),
            displayMedium =
                    base.displayMedium.merge(
                            TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    ),
            displaySmall =
                    base.displaySmall.merge(
                            TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    ),
            headlineLarge =
                    base.headlineLarge.merge(
                            TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    ),
            headlineMedium =
                    base.headlineMedium.merge(
                            TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    ),
            headlineSmall =
                    base.headlineSmall.merge(
                            TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    ),
            titleLarge =
                    base.titleLarge.merge(
                            TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    ),
            titleMedium =
                    base.titleMedium.merge(
                            TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    ),
            titleSmall =
                    base.titleSmall.merge(
                            TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    ),
            bodyLarge = base.bodyLarge.merge(TextStyle(fontSize = 22.sp)),
            bodyMedium = base.bodyMedium.merge(TextStyle(fontSize = 20.sp)),
            bodySmall = base.bodySmall.merge(TextStyle(fontSize = 18.sp)),
            labelLarge =
                    base.labelLarge.merge(
                            TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    ),
            labelMedium = base.labelMedium.merge(TextStyle(fontSize = 18.sp)),
            labelSmall = base.labelSmall.merge(TextStyle(fontSize = 16.sp)),
    )
}

@Composable
fun CyanBridgeTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        dynamicColor: Boolean = false,
        content: @Composable () -> Unit
) {
    val isHighContrast by AccessibilityPrefs.highContrast.collectAsState()
    val isLargeText by AccessibilityPrefs.largeText.collectAsState()
    val isBlindMode by AccessibilityPrefs.blindMode.collectAsState()

    val useHighContrast = isHighContrast || isBlindMode
    val useLargeText = isLargeText || isBlindMode

    val colorScheme =
            when {
                useHighContrast -> HighContrastColorScheme
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }

    val typography = if (useLargeText) largeTypography() else defaultTypography()

    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}
