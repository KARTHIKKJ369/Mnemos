package com.photovault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameBorderLight
import com.photovault.ui.theme.FrameGray100
import com.photovault.ui.theme.FrameGray300
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameGray700
import com.photovault.ui.theme.FrameGray900
import com.photovault.ui.theme.FrameSurface
import com.photovault.ui.theme.FrameWarning
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.RobotoMonoFontFamily
import com.photovault.ui.theme.SignalRed
import com.photovault.ui.theme.SignalRedGlow
import com.photovault.ui.theme.SignalRedSubtle
import com.photovault.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Red dot status indicator 🔴
 */
@Composable
fun RedDotIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 6.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SignalRed)
    )
}

/**
 * Hero Headline Header matching `🔴 135H 56M // 05 TITLES` + `00 COMPLETED // 00 WATCHING // 05 QUEUED`
 */
@Composable
fun FrameHeroHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showRedDot: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showRedDot) {
                RedDotIndicator(size = 7.dp)
            }
            Text(
                text = title.uppercase(),
                style = MnemosType.Headline28,
                color = FrameWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle.uppercase(),
                style = MnemosType.Mono11,
                color = FrameGray500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Capsule Segmented Control: `[ ALL | PHOTOS | VIDEOS | FAVORITES ]`
 * Active: Pure white background with black text.
 * Inactive: Clean muted monospace text.
 */
@Composable
fun FrameSegmentedControl(
    items: List<Pair<String, String>>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(FrameGray900)
            .border(1.dp, FrameBorder, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (key, label) ->
            val isSelected = selectedKey == key
            val animatedBg by animateColorAsState(
                targetValue = if (isSelected) FrameWhite else Color.Transparent,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "segBg"
            )
            val animatedText by animateColorAsState(
                targetValue = if (isSelected) FrameBlack else FrameGray300,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "segText"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(animatedBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onItemSelected(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.uppercase(),
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.04.em,
                    color = animatedText
                )
            }
        }
    }
}

/**
 * FRAME Search Bar: `🔍 SEARCH // TITLES, DIRECTORS, NOTES...`
 */
@Composable
fun FrameSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String = "SEARCH // FILENAME, NODE, DATE…",
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(FrameSurface)
            .border(1.dp, FrameBorder, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = FrameGray500,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = MnemosType.Mono12.copy(color = FrameWhite),
            singleLine = true,
            cursorBrush = SolidColor(SignalRed),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = placeholder.uppercase(),
                        style = MnemosType.Mono12,
                        color = FrameGray700
                    )
                }
                innerTextField()
            }
        )

        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = FrameGray500,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Standard Frame Card Container.
 * Pitch Black / `#0D0D0D` surface with 1dp `#1F1F1F` border and 8dp radius.
 */
@Composable
fun MnemosCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val cardModifier = modifier
        .clip(shape)
        .background(FrameSurface)
        .border(1.dp, FrameBorder, shape)
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
 * Frame Row Card with 32dp circular icon token, title, subtitle, and trailing action.
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
                    IconTintVariant.IRIS -> Pair(SignalRedSubtle, SignalRed)
                    IconTintVariant.WARNING -> Pair(Color(0xFF332000), FrameWarning)
                    IconTintVariant.DESTRUCTIVE -> Pair(SignalRedSubtle, SignalRed)
                    IconTintVariant.NEUTRAL -> Pair(FrameGray900, FrameGray300)
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
                    color = FrameWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MnemosType.BodySecondary13,
                        color = FrameGray300,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (monoText != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = monoText,
                        style = MnemosType.Mono12,
                        color = FrameGray500,
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
 * Standard Frame Button with crisp 6dp radius.
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
    val shape = RoundedCornerShape(6.dp)

    val (bgColor, contentColor, borderStroke) = when (variant) {
        ButtonVariant.PRIMARY -> Triple(FrameWhite, FrameBlack, null)
        ButtonVariant.SECONDARY -> Triple(FrameGray900, FrameWhite, 1.dp to FrameBorder)
        ButtonVariant.DESTRUCTIVE -> Triple(SignalRed, FrameWhite, null)
        ButtonVariant.OUTLINE -> Triple(Color.Transparent, FrameWhite, 1.dp to FrameBorderLight)
    }

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = bgColor,
        contentColor = contentColor,
        disabledContainerColor = FrameGray900,
        disabledContentColor = FrameGray700
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .then(
                if (borderStroke != null) {
                    Modifier.border(borderStroke.first, borderStroke.second, shape)
                } else Modifier
            ),
        shape = shape,
        colors = buttonColors,
        enabled = enabled && !isLoading,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text.uppercase(),
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.04.em,
                color = contentColor
            )
        }
    }
}

/**
 * Minimal Frame Switch with Signal Red active track.
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
            checkedThumbColor = FrameWhite,
            checkedTrackColor = SignalRed,
            checkedBorderColor = SignalRed,
            uncheckedThumbColor = FrameGray500,
            uncheckedTrackColor = FrameGray900,
            uncheckedBorderColor = FrameBorder
        )
    )
}

/**
 * Node badge displayed on grid item thumbnail (top-left) in monospace.
 */
@Composable
fun NodeBadge(
    deviceName: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(FrameBlack.copy(alpha = 0.92f))
            .border(0.5.dp, FrameBorderLight, shape)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = deviceName.uppercase(),
            style = MnemosType.Mono11.copy(fontSize = 8.5.sp),
            color = FrameGray100,
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
            .background(FrameBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RedDotIndicator(size = 5.dp)
            Text(
                text = title.uppercase(),
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.02.em,
                color = FrameWhite
            )
        }
        Text(
            text = "$itemCount ITEMS".uppercase(),
            style = MnemosType.Mono11,
            color = FrameGray500
        )
    }
}

/**
 * Standard Frame Page Header with red dot indicator.
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RedDotIndicator(size = 6.dp)
                Text(
                    text = title.uppercase(),
                    style = MnemosType.PageTitle20,
                    color = FrameWhite
                )
            }
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle.uppercase(),
                    style = MnemosType.Mono11,
                    color = FrameGray500
                )
            }
        }
        if (trailingAction != null) {
            trailingAction()
        }
    }
}

/**
 * Bottom Live Time & Server Status Ticker
 */
@Composable
fun FrameBottomStatusBar(
    statusText: String = "TAILSCALE ONLINE",
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        while (true) {
            currentTime = LocalTime.now().format(formatter)
            delay(1000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FrameBlack)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentTime,
            style = MnemosType.Mono11.copy(fontSize = 10.sp),
            color = FrameGray500
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RedDotIndicator(size = 4.dp)
            Text(
                text = statusText.uppercase(),
                style = MnemosType.Mono11.copy(fontSize = 10.sp),
                color = FrameGray500
            )
        }
    }
}
