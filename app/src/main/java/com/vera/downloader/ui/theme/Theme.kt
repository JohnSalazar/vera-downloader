package com.vera.downloader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val VeraPink = Color(0xFFFE2B54)
val VeraCyan = Color(0xFF25F4EE)
val VeraPurple = Color(0xFF7B2FF7)

private val DarkColors = darkColorScheme(
    primary = VeraPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C0E22),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = VeraCyan,
    onSecondary = Color(0xFF003733),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF4F4F7),
    surface = Color(0xFF151518),
    onSurface = Color(0xFFF4F4F7),
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = Color(0xFFB9B9C3),
    outline = Color(0xFF3A3A40),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD81B45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E0),
    onPrimaryContainer = Color(0xFF3B0613),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    background = Color(0xFFFDF7F8),
    onBackground = Color(0xFF1B1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF4E6EA),
    onSurfaceVariant = Color(0xFF5A5A63),
    outline = Color(0xFFD9C9CE),
    error = Color(0xFFB3261E)
)

@Composable
fun VeraTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
