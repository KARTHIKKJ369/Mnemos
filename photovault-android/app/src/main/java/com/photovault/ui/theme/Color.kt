package com.photovault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF09090B)
val DarkSurfaceVariant = Color(0xFF18181B)
val DarkSurfaceOverlay = Color(0xFF27272A)
val AccentGold = Color(0xFFEAB308)
val AccentGoldSubtle = Color(0xFFCA8A04)
val TextPrimary = Color(0xFFFAFAFA)
val TextSecondary = Color(0xFFA1A1AA)
val TextMuted = Color(0xFF71717A)
val BorderSubtle = Color(0xFF27272A)
val DangerRed = Color(0xFFEF4444)

val DarkColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = Color.Black,
    secondary = DarkSurfaceVariant,
    onSecondary = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = DangerRed,
    onError = Color.White
)
