package com.focusbyrj.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.focusbyrj.app.util.AppThemeManager
import com.focusbyrj.app.util.ThemeMode

@Composable
fun FocusByRjTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val currentAppTheme by AppThemeManager.themeFlow.collectAsState()
    val currentThemeMode by AppThemeManager.themeModeFlow.collectAsState()
    val systemInDark = isSystemInDarkTheme()
    val effectiveDarkTheme = currentThemeMode.isDarkTheme ?: systemInDark

    val bgColor = when(currentThemeMode) {
        ThemeMode.SYSTEM -> if(systemInDark) Color(0xFF0E1116) else CanvasLight
        ThemeMode.DARK -> Color(0xFF0E1116)
        ThemeMode.OLED -> Color(0xFF000000)
        ThemeMode.GRAPHITE -> Color(0xFF13161C)
        ThemeMode.OBSIDIAN -> Color(0xFF0A0C0F)
        ThemeMode.LIGHT -> Color(0xFFFFFFFF)
        ThemeMode.FROST -> Color(0xFFF1F5F9)
        ThemeMode.PAPER -> Color(0xFFFBFBF9)
        ThemeMode.WARM -> Color(0xFFF6F3EE)
        ThemeMode.IVORY -> Color(0xFFFAFAF7)
    }

    val surfaceColor = when(currentThemeMode) {
        ThemeMode.SYSTEM -> if(systemInDark) Color(0xFF151921) else SurfaceLight
        ThemeMode.DARK -> Color(0xFF151921)
        ThemeMode.OLED -> Color(0xFF0A0A0A)
        ThemeMode.GRAPHITE -> Color(0xFF1B1F27)
        ThemeMode.OBSIDIAN -> Color(0xFF12151B)
        ThemeMode.LIGHT -> Color(0xFFF8F9FA)
        ThemeMode.FROST -> Color(0xFFFFFFFF)
        ThemeMode.PAPER -> Color(0xFFF2F2F0)
        ThemeMode.WARM -> Color(0xFFEFEBE4)
        ThemeMode.IVORY -> Color(0xFFF3F4F1)
    }

    val surfaceVariantColor = when(currentThemeMode) {
        ThemeMode.SYSTEM -> if(systemInDark) Color(0xFF1E232E) else SurfaceVariantLight
        ThemeMode.DARK -> Color(0xFF1E232E)
        ThemeMode.OLED -> Color(0xFF141414)
        ThemeMode.GRAPHITE -> Color(0xFF262C38)
        ThemeMode.OBSIDIAN -> Color(0xFF1A1E26)
        ThemeMode.LIGHT -> Color(0xFFF1F3F5)
        ThemeMode.FROST -> Color(0xFFE2E8F0)
        ThemeMode.PAPER -> Color(0xFFE8E8E5)
        ThemeMode.WARM -> Color(0xFFE6E1D8)
        ThemeMode.IVORY -> Color(0xFFE5E7EB)
    }

    val colorScheme = if (effectiveDarkTheme) {
        darkColorScheme(
            primary = currentAppTheme.primary,
            secondary = currentAppTheme.secondary,
            tertiary = currentAppTheme.tertiary,
            primaryContainer = currentAppTheme.primary.copy(alpha = 0.2f),
            onPrimaryContainer = currentAppTheme.primary,
            surfaceContainerHigh = surfaceVariantColor,
            background = bgColor,
            surface = surfaceColor,
            surfaceVariant = surfaceVariantColor,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary,
            outline = BorderGlass,
            outlineVariant = Color(0x0AFFFFFF)
        )
    } else {
        lightColorScheme(
            primary = currentAppTheme.tertiary,
            secondary = currentAppTheme.primary,
            tertiary = currentAppTheme.secondary,
            primaryContainer = currentAppTheme.tertiary.copy(alpha = 0.15f),
            onPrimaryContainer = currentAppTheme.tertiary,
            surfaceContainerHigh = surfaceVariantColor,
            background = bgColor,
            surface = surfaceColor,
            surfaceVariant = surfaceVariantColor,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.Black,
            onBackground = TextPrimaryLight,
            onSurface = TextPrimaryLight,
            onSurfaceVariant = TextSecondaryLight,
            outline = BorderLight,
            outlineVariant = BorderLightVariant
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !effectiveDarkTheme
            insetsController.isAppearanceLightNavigationBars = !effectiveDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
