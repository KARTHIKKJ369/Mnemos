package com.photovault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Radix Slate Neutral Scale
val Slate950 = Color(0xFF020617) // Background / Canvas
val Slate900 = Color(0xFF0F172A) // Cards / Navigation Bar / Surfaces
val Slate800 = Color(0xFF1E293B) // Card Borders / Hover / Dividers
val Slate700 = Color(0xFF334155) // Inactive Borders / Strong Dividers
val Slate600 = Color(0xFF475569) // Muted Icons / Secondary Text Muted
val Slate400 = Color(0xFF94A3B8) // Secondary Text / Subheadings
val Slate200 = Color(0xFFE2E8F0) // High Contrast Text
val Slate50  = Color(0xFFF8FAFC) // Primary Text / White

// Primary Accent: Iris / Indigo Scale
val IrisPrimary = Color(0xFF6366F1) // Active States / Primary CTA / Toggles
val IrisLight   = Color(0xFF818CF8) // Active Icon Fill / Focus Rings
val IrisDark    = Color(0xFF4F46E5) // Pressed States
val IrisSubtle  = Color(0xFF1E1B4B) // Icon Circle Background (Tint)
val IrisTrack   = Color(0xFF312E81) // Switch Track Tint

// Warning Accent: Amber (Reserved strictly for warnings)
val WarningAmber  = Color(0xFFF59E0B) // Warning Text / Status
val WarningSubtle = Color(0xFF451A03) // Warning Background Tint

// Destructive Accent: Tomato Red (Used strictly for Trash / Disconnect)
val TomatoRed    = Color(0xFFEF4444) // Destructive Buttons / Delete Icons
val TomatoLight  = Color(0xFFF87171) // Destructive Hover
val TomatoSubtle = Color(0xFF450A0A) // Destructive Background Tint

// Status Indicators
val StatusSyncing = IrisPrimary
val StatusSynced  = Slate400
val StatusError   = TomatoRed

// Text Hierarchy
val TextPrimary   = Slate50
val TextSecondary = Slate400
val TextMuted     = Slate600

// Backwards-compatible aliases for legacy references
val NeutralCanvas      = Slate950
val NeutralSurface     = Slate900
val NeutralElevated    = Slate900
val NeutralHighest     = Slate800
val NeutralHairline    = Slate800
val NeutralHairlineSubtle = Slate800
val AccentAmber        = IrisPrimary
val AccentGold         = IrisPrimary
val AccentGoldGlow     = IrisSubtle
val DarkBackground     = Slate950
val DarkSurface        = Slate900
val DarkSurfaceVariant = Slate900
val DarkSurfaceOverlay = Slate800
val BorderSubtle       = Slate800
val DangerRed          = TomatoRed
val EmeraldGreen       = Color(0xFF10B981)

val DarkColorScheme = darkColorScheme(
    primary = IrisPrimary,
    onPrimary = Slate50,
    primaryContainer = IrisSubtle,
    onPrimaryContainer = IrisLight,
    secondary = Slate900,
    onSecondary = Slate50,
    secondaryContainer = Slate800,
    onSecondaryContainer = Slate200,
    tertiary = IrisLight,
    onTertiary = Slate950,
    tertiaryContainer = IrisSubtle,
    onTertiaryContainer = IrisLight,
    background = Slate950,
    onBackground = Slate50,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate900,
    onSurfaceVariant = Slate400,
    surfaceContainerLowest = Slate950,
    surfaceContainerLow = Slate950,
    surfaceContainer = Slate900,
    surfaceContainerHigh = Slate900,
    surfaceContainerHighest = Slate800,
    surfaceDim = Slate950,
    surfaceBright = Slate800,
    outline = Slate800,
    outlineVariant = Slate800,
    error = TomatoRed,
    onError = Color.White,
    errorContainer = TomatoSubtle,
    onErrorContainer = TomatoLight
)
