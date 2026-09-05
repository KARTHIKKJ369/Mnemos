package com.photovault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.screens.backup.BackupScreen
import com.photovault.ui.screens.devices.DevicesScreen
import com.photovault.ui.screens.gallery.GalleryScreen
import com.photovault.ui.screens.search.SearchScreen
import com.photovault.ui.screens.settings.SettingsScreen
import com.photovault.ui.screens.trash.TrashScreen
import com.photovault.ui.screens.viewer.MediaViewerScreen
import com.photovault.ui.theme.AccentAmber
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.NeutralCanvas
import com.photovault.ui.theme.NeutralHairline
import com.photovault.ui.theme.NeutralSurface
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextSecondary

enum class NavTab(val title: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Default.Collections),
    SEARCH("Search", Icons.Default.Search),
    BACKUP("Backup", Icons.Default.CloudUpload),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainNavHost(
    onLogout: () -> Unit
) {
    val view = LocalView.current
    var selectedTab by remember { mutableStateOf(NavTab.LIBRARY) }
    var activeViewerFileId by remember { mutableStateOf<String?>(null) }
    var galleryFilterDeviceId by remember { mutableStateOf<String?>(null) }
    var showTrashScreen by remember { mutableStateOf(false) }
    var showDevicesScreen by remember { mutableStateOf(false) }

    // Back handling: if viewing media, close viewer; if on overlay, close overlay; if on another tab, return to Library
    BackHandler(enabled = activeViewerFileId == null && !showTrashScreen && !showDevicesScreen && selectedTab != NavTab.LIBRARY) {
        selectedTab = NavTab.LIBRARY
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralCanvas)
    ) {
        // Main Content Area (padding bottom 60dp for bottom bar)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = 600f)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = 600f))
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
                    NavTab.LIBRARY -> GalleryScreen(
                        initialDeviceId = galleryFilterDeviceId,
                        onMediaSelected = { fileId ->
                            activeViewerFileId = fileId
                        }
                    )
                    NavTab.SEARCH -> SearchScreen(
                        onMediaSelected = { fileId ->
                            activeViewerFileId = fileId
                        }
                    )
                    NavTab.BACKUP -> BackupScreen()
                    NavTab.SETTINGS -> SettingsScreen(
                        onLogout = onLogout,
                        onOpenTrash = { showTrashScreen = true },
                        onOpenDevices = { showDevicesScreen = true }
                    )
                }
            }
        }

        // Bottom Navigation Bar (4 items max, amber hairline underline on active, 0 filled bubble)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(NeutralSurface)
                .border(width = 1.dp, color = NeutralHairline)
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.96f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                        label = "tabScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .scale(scale)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    HapticHelper.performClick(view)
                                    if (tab == NavTab.LIBRARY && selectedTab != NavTab.LIBRARY) {
                                        galleryFilterDeviceId = null
                                    }
                                    selectedTab = tab
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) AccentAmber else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.title,
                                style = MnemosType.Label11.copy(fontSize = 11.sp),
                                color = if (isSelected) AccentAmber else TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Crisp 1.5dp amber hairline underline on active state (replaces filled pill)
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(1.5.dp)
                                    .background(if (isSelected) AccentAmber else Color.Transparent)
                            )
                        }
                    }
                }
            }
        }

        // Connected Devices Roster overlay
        if (showDevicesScreen) {
            DevicesScreen(
                onNavigateToGalleryWithDevice = { deviceId, _ ->
                    galleryFilterDeviceId = deviceId
                    showDevicesScreen = false
                    selectedTab = NavTab.LIBRARY
                }
            )
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
