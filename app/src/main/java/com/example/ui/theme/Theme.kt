package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DxpLightColorScheme = lightColorScheme(
    primary = DxpRoyalPlum,
    onPrimary = Color.White,
    primaryContainer = DxpSoftViolet,
    onPrimaryContainer = DxpRoyalPlum,
    secondary = DxpAccentViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = DxpRichViolet,
    tertiary = DxpRichViolet,
    onTertiary = Color.White,
    background = DxpLavenderSurface,
    onBackground = Color(0xFF1B0729),
    surface = DxpCardWhite,
    onSurface = Color(0xFF1B0729),
    surfaceVariant = Color(0xFFF3ECF9),
    onSurfaceVariant = Color(0xFF4E3861),
    error = StatusReturnedRed,
    onError = Color.White
)

private val DxpDarkColorScheme = darkColorScheme(
    primary = DxpNightPrimary,
    onPrimary = DxpDeepPurple,
    primaryContainer = DxpRichViolet,
    onPrimaryContainer = Color.White,
    secondary = DxpNightSecondary,
    onSecondary = DxpDeepPurple,
    secondaryContainer = Color(0xFF3B125E),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = DxpAccentViolet,
    onTertiary = Color.White,
    background = DxpNightBackground,
    onBackground = Color(0xFFF5F3FF),
    surface = DxpNightSurface,
    onSurface = Color(0xFFF5F3FF),
    surfaceVariant = Color(0xFF311047),
    onSurfaceVariant = Color(0xFFD8B4FE),
    error = StatusReturnedRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DxpDarkColorScheme else DxpLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
