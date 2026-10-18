package com.alxmovies.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Red = Color(0xFFE50914)
val Bg = Color(0xFF05050A)
val Elevated = Color(0xFF0D0D14)
val CardBg = Color(0xFF12121B)
val Muted = Color(0xFFB3B3C2)
val Amber = Color(0xFFE0A11C)
val Green = Color(0xFF2E9E4A)
val ErrorRed = Color(0xFFFF6B6B)

private val Scheme = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Bg,
    onBackground = Color.White,
    surface = Elevated,
    onSurface = Color.White,
    surfaceVariant = CardBg,
    onSurfaceVariant = Muted,
    error = ErrorRed,
    outline = Color(0xFF3A3A4A),
)

@Composable
fun AlxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
