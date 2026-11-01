package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF001E26),
    primaryContainer = CyanContainer,
    onPrimaryContainer = NeonCyan,
    secondary = NeonCyanDark,
    onSecondary = Color.White,
    background = SlateDark900,
    onBackground = TextPrimary,
    surface = SlateDark800,
    onSurface = TextPrimary,
    surfaceVariant = SlateDark700,
    onSurfaceVariant = TextSecondary,
    outline = SlateDark600,
    outlineVariant = SlateDark700,
    error = GuardRed,
    onError = Color.White,
    errorContainer = GuardRedContainer,
    onErrorContainer = Color(0xFFFCA5A5)
)

@Composable
fun ZalniGuardTheme(
    content: @Composable () -> Unit
) {
    // Dark theme strictly enforced for ZALNI Guard as requested
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
