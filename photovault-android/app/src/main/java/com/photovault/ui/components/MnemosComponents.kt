package com.photovault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.photovault.ui.theme.InterFontFamily
import com.photovault.ui.theme.IrisLight
import com.photovault.ui.theme.IrisPrimary
import com.photovault.ui.theme.IrisSubtle
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.RobotoMonoFontFamily
import com.photovault.ui.theme.Slate200
import com.photovault.ui.theme.Slate400
import com.photovault.ui.theme.Slate50
import com.photovault.ui.theme.Slate700
import com.photovault.ui.theme.Slate800
import com.photovault.ui.theme.Slate900
import com.photovault.ui.theme.Slate950
import com.photovault.ui.theme.TomatoRed
import com.photovault.ui.theme.TomatoSubtle
import com.photovault.ui.theme.WarningAmber
import com.photovault.ui.theme.WarningSubtle

/**
 * Standard Mnemos Card Container.
 * Flat, opaque Slate900 surface with 1dp Slate800 border and 12dp radius.
 */
@Composable
fun MnemosCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val cardModifier = modifier
        .clip(shape)
        .background(Slate900)
        .border(1.dp, Slate800, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )
        .padding(padding)

    Box(modifier = cardModifier) {
        content()
    }
}

enum class IconTintVariant {
    IRIS,
    WARNING,
    DESTRUCTIVE,
    NEUTRAL
}

/**
 * Standard Mnemos Row Card with 32dp circular icon token, title, subtitle, and trailing action.
 */
@Composable
fun MnemosRowCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    monoText: String? = null,
    icon: ImageVector? = null,
    iconTintVariant: IconTintVariant = IconTintVariant.IRIS,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    MnemosCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        padding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                val (bgTint, iconTint) = when (iconTintVariant) {
                    IconTintVariant.IRIS -> Pair(IrisSubtle, IrisLight)
                    IconTintVariant.WARNING -> Pair(WarningSubtle, WarningAmber)
                    IconTintVariant.DESTRUCTIVE -> Pair(TomatoSubtle, TomatoRed)
                    IconTintVariant.NEUTRAL -> Pair(Slate800, Slate400)
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(bgTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MnemosType.CardTitle15,
                    color = Slate50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MnemosType.BodySecondary13,
                        color = Slate400,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (monoText != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = monoText,
                        style = MnemosType.Mono12,
                        color = Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}

enum class ButtonVariant {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE,
    OUTLINE
}

/**
 * Standard Mnemos Button.
 * 10dp rounded corners, flat surface, precise typographic weight.
 */
@Composable
fun MnemosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val shape = RoundedCornerShape(10.dp)

    val (bgColor, contentColor, borderStroke) = when (variant) {
        ButtonVariant.PRIMARY -> Triple(IrisPrimary, Slate50, null)
        ButtonVariant.SECONDARY -> Triple(Slate800, Slate50, null)
        ButtonVariant.DESTRUCTIVE -> Triple(TomatoRed, Slate50, null)
        ButtonVariant.OUTLINE -> Triple(Color.Transparent, Slate200, 1.dp to Slate800)
    }

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = bgColor,
        contentColor = contentColor,
        disabledContainerColor = Slate800.copy(alpha = 0.5f),
        disabledContentColor = Slate400.copy(alpha = 0.5f)
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(46.dp)
            .then(
                if (borderStroke != null) {
                    Modifier.border(borderStroke.first, borderStroke.second, shape)
                } else Modifier
            ),
        shape = shape,
        colors = buttonColors,
        enabled = enabled && !isLoading,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MnemosType.CardTitle15.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}

/**
 * Standard Mnemos Switch with custom Iris track and clean thumb.
 */
@Composable
fun MnemosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Slate50,
            checkedTrackColor = IrisPrimary,
            checkedBorderColor = IrisPrimary,
            uncheckedThumbColor = Slate400,
            uncheckedTrackColor = Slate800,
            uncheckedBorderColor = Slate700
        )
    )
}

/**
 * Node badge displayed on grid items to indicate origin device.
 * Truncated, low-contrast pill at top-left.
 */
@Composable
fun NodeBadge(
    deviceName: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Slate950.copy(alpha = 0.88f))
            .border(0.5.dp, Slate800, shape)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = deviceName,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 9.sp,
            letterSpacing = 0.02.em,
            color = Slate200,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Sticky month/year timeline section header for the library gallery.
 */
@Composable
fun TimelineSectionHeader(
    title: String,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Slate950)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MnemosType.PageTitle20.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = Slate50
        )
        Text(
            text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
            style = MnemosType.BodySecondary13,
            color = Slate400
        )
    }
}

/**
 * Page Header component for standard 20px medium page header.
 */
@Composable
fun MnemosPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MnemosType.PageTitle20,
                color = Slate50
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MnemosType.BodySecondary13,
                    color = Slate400
                )
            }
        }
        if (trailingAction != null) {
            trailingAction()
        }
    }
}
