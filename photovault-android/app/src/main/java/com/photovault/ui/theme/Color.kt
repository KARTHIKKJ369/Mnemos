package com.photovault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// 60/30/10 Modern Dark Palette
val DarkBackground = Color(0xFF08090D)      // OLED Midnight
val DarkSurface = Color(0xFF10131B)         // Deep Slate Base
val DarkSurfaceVariant = Color(0xFF171B26)  // Card Surface
val DarkSurfaceOverlay = Color(0xFF222838)  // Floating Elements & Dialogs

// Accents
val AccentGold = Color(0xFFF5B726)          // Cyber Amber Gold
val AccentGoldSubtle = Color(0xFFB45309)
val AccentGoldGlow = Color(0x2EF5B726)      // 18% Tint for Badges/Pills

val EmeraldGreen = Color(0xFF10B981)
val DangerRed = Color(0xFFF43F5E)

// Text Hierarchy
val TextPrimary = Color(0xFFF8FAFC)        // 100% Bright White
val TextSecondary = Color(0xFF94A3B8)      // 70% Slate
val TextMuted = Color(0xFF64748B)          // 50% Subdued Slate
val BorderSubtle = Color(0xFF222838)

val DarkColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = Color(0xFF08090D),
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
