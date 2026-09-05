package com.photovault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.DarkSurfaceVariant

// Mnemos Neutral Surface Tokens (Receding chrome, zero loud tints)
val GlassSurfaceBase = Color(0xF2121212)      // Neutral surface base
val GlassSurfaceElevated = Color(0xF2181818)  // Neutral elevated
val GlassSurfacePill = Color(0xF0181818)      // Neutral capsule
val GlassHighlightTop = Color(0x1AFFFFFF)     // Subtle specular edge
val GlassHighlightBottom = Color(0x06FFFFFF)  // Micro hairline bottom

/**
 * Creates a dual-tone liquid glass gradient border that refracts light from top-left to bottom-right.
 */
fun liquidGlassBorder(
    width: Dp = 1.dp,
    topAlpha: Float = 0.22f,
    bottomAlpha: Float = 0.04f,
    accentTint: Color? = null
): BorderStroke {
    val startColor = accentTint?.copy(alpha = topAlpha) ?: Color.White.copy(alpha = topAlpha)
    val endColor = accentTint?.copy(alpha = bottomAlpha) ?: Color.White.copy(alpha = bottomAlpha)
    return BorderStroke(
        width = width,
        brush = Brush.linearGradient(
            colors = listOf(startColor, endColor),
            start = Offset(0f, 0f),
            end = Offset(400f, 400f)
        )
    )
}

/**
 * Modifier for applying a liquid frosted glass texture with top specular highlight and subtle inner refraction.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = GlassSurfaceBase,
    borderColor: Color = Color.White,
    borderWidth: Dp = 1.dp,
    borderAlphaTop: Float = 0.20f,
    borderAlphaBottom: Float = 0.04f
): Modifier = this
    .clip(shape)
    .background(backgroundColor)
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = listOf(
                borderColor.copy(alpha = borderAlphaTop),
                borderColor.copy(alpha = borderAlphaBottom)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        ),
        shape = shape
    )
    .drawBehind {
        // Specular top-light sheen (simulates light striking curved glass)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.45f
            )
        )
    }

/**
 * Liquid Glass Card with spring click feedback and specular lighting.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = GlassSurfaceElevated,
    glowAccent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .liquidGlass(
                shape = shape,
                backgroundColor = backgroundColor,
                borderColor = glowAccent ?: Color.White,
                borderAlphaTop = if (glowAccent != null) 0.40f else 0.18f,
                borderAlphaBottom = 0.04f
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        content = content
    )
}

/**
 * Liquid Glass Capsule Pill for floating badges, filter chips, and top bars.
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = GlassSurfacePill,
    glowAccent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "pillScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .liquidGlass(
                shape = shape,
                backgroundColor = backgroundColor,
                borderColor = glowAccent ?: Color.White,
                borderAlphaTop = if (glowAccent != null) 0.35f else 0.22f,
                borderAlphaBottom = 0.05f
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        content = content
    )
}
