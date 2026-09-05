package com.photovault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.ui.components.GlassSurfaceElevated
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.liquidGlass
import com.photovault.ui.screens.backup.BackupScreen
import com.photovault.ui.screens.devices.DevicesScreen
import com.photovault.ui.screens.gallery.GalleryScreen
import com.photovault.ui.screens.settings.SettingsScreen
import com.photovault.ui.screens.timeline.TimelineScreen
import com.photovault.ui.screens.trash.TrashScreen
import com.photovault.ui.screens.viewer.MediaViewerScreen
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.AccentGoldGlow
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextPrimary
import com.photovault.ui.theme.TextSecondary

enum class NavTab(val title: String, val icon: ImageVector) {
    PHOTOS("Photos", Icons.Default.Collections),
    TIMELINE("Timeline", Icons.Default.DateRange),
    DEVICES("Devices", Icons.Default.Devices),
    BACKUP("Backup", Icons.Default.CloudUpload),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainNavHost(
    onLogout: () -> Unit
) {
    val view = LocalView.current
    var selectedTab by remember { mutableStateOf(NavTab.PHOTOS) }
    var activeViewerFileId by remember { mutableStateOf<String?>(null) }
    var galleryFilterDeviceId by remember { mutableStateOf<String?>(null) }
    var showTrashScreen by remember { mutableStateOf(false) }

    // Back handling: if viewing media, close viewer; if on trash, close trash; if on another tab, return to Photos
    BackHandler(enabled = activeViewerFileId == null && !showTrashScreen && selectedTab != NavTab.PHOTOS) {
        selectedTab = NavTab.PHOTOS
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Main Screen Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 68.dp) // Space for floating liquid navbar
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = 500f)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = 500f))
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
                    NavTab.PHOTOS -> GalleryScreen(
                        initialDeviceId = galleryFilterDeviceId,
                        onMediaSelected = { fileId ->
                            activeViewerFileId = fileId
                        }
                    )
                    NavTab.TIMELINE -> TimelineScreen(
                        onMediaSelected = { fileId ->
                            activeViewerFileId = fileId
                        }
                    )
                    NavTab.DEVICES -> DevicesScreen(
                        onNavigateToGalleryWithDevice = { deviceId, _ ->
                            galleryFilterDeviceId = deviceId
                            selectedTab = NavTab.PHOTOS
                        }
                    )
                    NavTab.BACKUP -> BackupScreen()
                    NavTab.SETTINGS -> SettingsScreen(
                        onLogout = onLogout,
                        onOpenTrash = { showTrashScreen = true }
                    )
                }
            }
        }

        // Floating Liquid Glass Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(32.dp),
                    backgroundColor = Color(0xF0121622),
                    borderColor = Color.White,
                    borderWidth = 1.dp,
                    borderAlphaTop = 0.22f,
                    borderAlphaBottom = 0.04f
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                        label = "tabScale"
                    )

                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(AccentGoldGlow, RoundedCornerShape(20.dp))
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    HapticHelper.performClick(view)
                                    if (tab == NavTab.PHOTOS && selectedTab != NavTab.PHOTOS) {
                                        galleryFilterDeviceId = null
                                    }
                                    selectedTab = tab
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) AccentGold else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AccentGold else TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Trash & Deleted Items overlay
        if (showTrashScreen) {
            TrashScreen(
                onNavigateBack = {
                    showTrashScreen = false
                },
                onMediaSelected = { fileId ->
                    activeViewerFileId = fileId
                }
            )
        }

        // Fullscreen Progressive Media Viewer overlay
        activeViewerFileId?.let { fileId ->
            MediaViewerScreen(
                initialFileId = fileId,
                onClose = {
                    activeViewerFileId = null
                }
            )
        }
    }
}
