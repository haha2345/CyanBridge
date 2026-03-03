package com.fersaiyan.cyanbridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Brand Colors ──────────────────────────────────────────────
val CyanPrimary     = Color(0xFF00BCD4)
val CyanDark        = Color(0xFF0097A7)
val CyanLight       = Color(0xFF80DEEA)
val BackgroundDark  = Color(0xFF121212)
val SurfaceDark     = Color(0xFF1E1E1E)
val OnSurfaceDark   = Color(0xFFE0E0E0)

// ── High contrast for accessibility ──────────────────────────
val HighContrastYellow = Color(0xFFFFEB3B)
val HighContrastWhite  = Color(0xFFFAFAFA)

private val DarkColorScheme = darkColorScheme(
    primary            = CyanPrimary,
    onPrimary          = Color.Black,
    primaryContainer   = CyanDark,
    secondary          = CyanLight,
    background         = BackgroundDark,
    surface            = SurfaceDark,
    onBackground       = OnSurfaceDark,
    onSurface          = OnSurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary            = CyanDark,
    onPrimary          = Color.White,
    primaryContainer   = CyanLight,
    secondary          = CyanPrimary,
    background         = Color(0xFFF5F5F5),
    surface            = Color.White,
    onBackground       = Color(0xFF212121),
    onSurface          = Color(0xFF212121),
)

@Composable
fun CyanBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
