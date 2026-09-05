package com.photovault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// FRAME // OS Color System (Pitch Black + Signal Red + Technical White/Gray)
val FrameBlack       = Color(0xFF000000) // True Pitch Black Canvas
val FrameCanvas      = Color(0xFF050505) // Canvas Background
val FrameSurface     = Color(0xFF0D0D0D) // Card / Bar Surface
val FrameElevated    = Color(0xFF141414) // Hover / Elevated Surface
val FrameBorder      = Color(0xFF1F1F1F) // Hairline Borders (1dp)
val FrameBorderLight = Color(0xFF2A2A2A) // Active / Highlighted Borders

// Primary Accent: Signal Red (🔴)
val SignalRed       = Color(0xFFFF2A2A) // Active Red Dot / Highlights
val SignalRedLight  = Color(0xFFFF4D4D) // Red Hover / Focus
val SignalRedDark   = Color(0xFFCC0000) // Pressed State
val SignalRedSubtle = Color(0xFF2A0808) // Red Background Tint / Glow
val SignalRedGlow   = Color(0x33FF2A2A) // Subtle Red Glow

// Monospace & Editorial Neutrals
val FrameWhite      = Color(0xFFFFFFFF) // Pure White (Active Pill, Headlines)
val FrameGray100    = Color(0xFFE5E5E5) // High Contrast Text
val FrameGray300    = Color(0xFFA3A3A3) // Subtitles
val FrameGray500    = Color(0xFF737373) // Monospace Labels / `//` Separators
val FrameGray700    = Color(0xFF404040) // Muted Icons / Inactive Text
val FrameGray900    = Color(0xFF1A1A1A) // Pill Bar Container Background

// Warning & Status
val FrameWarning    = Color(0xFFF59E0B) // Warning Yellow/Amber
val FrameSuccess    = Color(0xFF10B981) // Sync Success Green

// Text Hierarchy
val TextPrimary     = FrameWhite
val TextSecondary   = FrameGray300
val TextMuted       = FrameGray500

// Compatibility aliases for legacy references
val Slate950        = FrameCanvas
val Slate900        = FrameSurface
val Slate800        = FrameBorder
val Slate700        = FrameBorderLight
val Slate600        = FrameGray700
val Slate400        = FrameGray300
val Slate200        = FrameGray100
val Slate50         = FrameWhite

val IrisPrimary     = SignalRed
val IrisLight       = SignalRedLight
val IrisDark        = SignalRedDark
val IrisSubtle      = SignalRedSubtle

val WarningAmber    = FrameWarning
val WarningSubtle   = Color(0xFF332000)
val TomatoRed       = SignalRed
val TomatoLight     = SignalRedLight
val TomatoSubtle    = SignalRedSubtle

val StatusSyncing   = SignalRed
val StatusSynced    = FrameGray500
val StatusError     = SignalRed

val NeutralCanvas   = FrameCanvas
val NeutralSurface  = FrameSurface
val NeutralElevated = FrameElevated
val NeutralHairline = FrameBorder
val AccentAmber     = SignalRed
val AccentGold      = SignalRed
val AccentGoldGlow  = SignalRedGlow
val DarkBackground  = FrameBlack
val DarkSurface     = FrameSurface
val DarkSurfaceVariant = FrameSurface
val DangerRed       = SignalRed
val EmeraldGreen    = FrameSuccess

val DarkColorScheme = darkColorScheme(
    primary = SignalRed,
    onPrimary = FrameWhite,
    primaryContainer = SignalRedSubtle,
    onPrimaryContainer = SignalRedLight,
    secondary = FrameSurface,
    onSecondary = FrameWhite,
    secondaryContainer = FrameElevated,
    onSecondaryContainer = FrameGray100,
    tertiary = SignalRedLight,
    onTertiary = FrameBlack,
    tertiaryContainer = SignalRedSubtle,
    onTertiaryContainer = SignalRedLight,
    background = FrameBlack,
    onBackground = FrameWhite,
    surface = FrameSurface,
    onSurface = FrameWhite,
    surfaceVariant = FrameSurface,
    onSurfaceVariant = FrameGray300,
    surfaceContainerLowest = FrameBlack,
    surfaceContainerLow = FrameCanvas,
    surfaceContainer = FrameSurface,
    surfaceContainerHigh = FrameElevated,
    surfaceContainerHighest = FrameBorderLight,
    surfaceDim = FrameBlack,
    surfaceBright = FrameElevated,
    outline = FrameBorder,
    outlineVariant = FrameBorderLight,
    error = SignalRed,
    onError = FrameWhite,
    errorContainer = SignalRedSubtle,
    onErrorContainer = SignalRedLight
)
