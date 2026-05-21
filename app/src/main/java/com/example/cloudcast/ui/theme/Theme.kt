package com.example.cloudcast.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary            = Teal400,
    onPrimary          = Brand900,
    primaryContainer   = Color(0xFF0A3D38),
    onPrimaryContainer = Teal400,
    secondary          = Sky200,
    onSecondary        = Brand900,
    secondaryContainer = Color(0xFF0A2A3D),
    onSecondaryContainer = Sky200,
    background         = Brand900,
    onBackground       = Neutral100,
    surface            = Brand800,
    onSurface          = Neutral200,
    surfaceVariant     = Brand700,
    onSurfaceVariant   = Neutral400,
    outline            = Brand600,
    error              = Coral400,
    onError            = Brand900,
    errorContainer     = Color(0xFF4A0D1E),
    onErrorContainer   = Coral400,
)

private val LightColorScheme = lightColorScheme(
    primary            = Teal700,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF004D46),
    secondary          = Sky600,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF00344D),
    background         = Neutral50,
    onBackground       = Neutral900,
    surface            = Color.White,
    onSurface          = Neutral900,
    surfaceVariant     = Neutral100,
    onSurfaceVariant   = Neutral600,
    outline            = Neutral200,
    error              = Coral600,
    onError            = Color.White,
    errorContainer     = Color(0xFFFFE4E6),
    onErrorContainer   = Color(0xFF9F0D2A),
)

@Composable
fun CloudCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}