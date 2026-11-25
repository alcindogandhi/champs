package io.github.alcindogandhi.champs.ui.theme

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
import io.github.alcindogandhi.champs.data.settings.ThemeMode

/** Cor do "ao vivo", igual nos dois temas. */
val LiveRed = Color(0xFFE53935)

/** Gramado do campinho da escalação. */
val PitchGreen = Color(0xFF2E7D32)
val PitchGreenDark = Color(0xFF1F5F24)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B7F3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F4B4),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF4F6352),
    secondaryContainer = Color(0xFFD2E8D3),
    onSecondaryContainer = Color(0xFF0D1F12),
    tertiary = Color(0xFF3A6470),
    tertiaryContainer = Color(0xFFBEEAF7),
    background = Color(0xFFF7FBF4),
    surface = Color(0xFFF7FBF4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5EE),
    surfaceContainer = Color(0xFFEBF0E8),
    surfaceContainerHigh = Color(0xFFE5EAE2),
    surfaceContainerHighest = Color(0xFFE0E4DD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD89A),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFA6F4B4),
    secondary = Color(0xFFB6CCB8),
    secondaryContainer = Color(0xFF384B3B),
    onSecondaryContainer = Color(0xFFD2E8D3),
    tertiary = Color(0xFFA2CEDB),
    tertiaryContainer = Color(0xFF214C57),
    background = Color(0xFF111411),
    surface = Color(0xFF111411),
    surfaceContainerLowest = Color(0xFF0C0F0C),
    surfaceContainerLow = Color(0xFF191D19),
    surfaceContainer = Color(0xFF1D211D),
    surfaceContainerHigh = Color(0xFF282B27),
    surfaceContainerHighest = Color(0xFF323632),
)

@Composable
fun ChampsTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Ícones da barra de status e de navegação acompanham o tema do app, não o do sistema.
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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
