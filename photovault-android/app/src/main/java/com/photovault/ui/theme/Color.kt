package com.photovault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Mnemos Neutral Scale (True black is banned; calibrated OLED dark palette)
val NeutralCanvas          = Color(0xFF0D0D0D) // Canvas beneath grid & detail viewer
val NeutralSurface         = Color(0xFF121212) // Base app bar, bottom nav, card surface
val NeutralElevated        = Color(0xFF181818) // Sheets, elevated containers
val NeutralHighest         = Color(0xFF202020) // Floating action bar, dialogue surfaces
val NeutralHairline        = Color(0xFF262626) // 1dp dividers, hairline borders
val NeutralHairlineSubtle  = Color(0xFF1C1C1C) // Micro hairline gutters

// Sole Accent (Amber / Gold)
val AccentAmber            = Color(0xFFD4A017) // Primary accent
val AccentAmberLight       = Color(0xFFF2C94C) // Aperture ring highlight
val AccentAmberGlow        = Color(0x2ED4A017) // 18% Amber tint for subtle indicator
val AccentAmberBorder      = Color(0xFFD4A017) // 4dp selection inset border

// Status Indicators (Weight & type only, no loud banners)
val StatusSyncing          = Color(0xFFD4A017) // "Syncing…" in amber
val StatusSynced           = Color(0xFF8E8E93) // "Up to date" in secondary slate
val StatusError            = Color(0xFFCF6679) // Restrained error red

// Text & Typography Hierarchy
val TextPrimary            = Color(0xFFEDEDED) // 93% white, high-contrast readable
val TextSecondary          = Color(0xFF8E8E93) // 56% slate-gray, secondary metadata
val TextMuted              = Color(0xFF555555) // 33% subdued gray, inactive chrome
val TextMonoTechnical      = Color(0xFFAAAAAA) // Monospace numerals and timestamps

// Backwards-compatible aliases
val DarkBackground         = NeutralCanvas
val DarkSurface            = NeutralSurface
val DarkSurfaceVariant     = NeutralElevated
val DarkSurfaceOverlay     = NeutralHighest
val AccentGold             = AccentAmber
val AccentGoldSubtle       = Color(0xFF9A7410)
val AccentGoldGlow         = AccentAmberGlow
val BorderSubtle           = NeutralHairline
val DangerRed              = StatusError
val EmeraldGreen           = Color(0xFF10B981)

val DarkColorScheme = darkColorScheme(
    primary = AccentAmber,
    onPrimary = NeutralCanvas,
    primaryContainer = Color(0xFF332608),
    onPrimaryContainer = AccentAmberLight,
    secondary = NeutralElevated,
    onSecondary = TextPrimary,
    secondaryContainer = NeutralHighest,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentAmber,
    onTertiary = NeutralCanvas,
    tertiaryContainer = Color(0xFF332608),
    onTertiaryContainer = AccentAmberLight,
    background = NeutralCanvas,
    onBackground = TextPrimary,
    surface = NeutralSurface,
    onSurface = TextPrimary,
    surfaceVariant = NeutralElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = NeutralCanvas,
    surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = NeutralSurface,
    surfaceContainerHigh = NeutralElevated,
    surfaceContainerHighest = NeutralHighest,
    surfaceDim = NeutralCanvas,
    surfaceBright = NeutralHighest,
    outline = NeutralHairline,
    outlineVariant = NeutralHairlineSubtle,
    error = StatusError,
    onError = Color.White,
    errorContainer = Color(0xFF3E1A20),
    onErrorContainer = Color(0xFFFDA4AF)
)
