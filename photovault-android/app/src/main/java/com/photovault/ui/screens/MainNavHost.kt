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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.photovault.data.model.MediaItem
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.RedDotIndicator
import com.photovault.ui.screens.backup.BackupScreen
import com.photovault.ui.screens.devices.DevicesScreen
import com.photovault.ui.screens.gallery.GalleryScreen
import com.photovault.ui.screens.settings.SettingsScreen
import com.photovault.ui.screens.trash.TrashScreen
import com.photovault.ui.screens.viewer.MediaViewerScreen
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameGray700
import com.photovault.ui.theme.FrameSurface
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.RobotoMonoFontFamily
import com.photovault.ui.theme.SpaceGroteskFontFamily

enum class FrameNavTab(val number: String, val title: String) {
    VAULT("01", "VAULT"),
    NODES("02", "NODES"),
    SYSTEM("03", "SYSTEM")
}

@Composable
fun MainNavHost(
    onLogout: () -> Unit
) {
    val view = LocalView.current
    var selectedTab by remember { mutableStateOf(FrameNavTab.VAULT) }
    var activeViewerFileId by remember { mutableStateOf<String?>(null) }
    var activeViewerMediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var galleryFilterDeviceId by remember { mutableStateOf<String?>(null) }
    var showTrashScreen by remember { mutableStateOf(false) }

    // Back handling: if on a non-VAULT tab, return to (01) VAULT
    BackHandler(enabled = activeViewerFileId == null && !showTrashScreen && selectedTab != FrameNavTab.VAULT) {
        selectedTab = FrameNavTab.VAULT
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrameBlack)
    ) {
        // Main Content Area (padding bottom 48dp for FRAME bottom navigation tabs)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = 700f)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = 700f))
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
                    FrameNavTab.VAULT -> GalleryScreen(
                        initialDeviceId = galleryFilterDeviceId,
                        onMediaSelected = { fileId, list ->
                            activeViewerFileId = fileId
                            activeViewerMediaList = list
                        }
                    )
                    FrameNavTab.NODES -> DevicesScreen(
                        onNavigateToGalleryWithDevice = { deviceId, _ ->
                            galleryFilterDeviceId = deviceId
                            selectedTab = FrameNavTab.VAULT
                        },
                        onMediaSelected = { fileId, list ->
                            activeViewerFileId = fileId
                            activeViewerMediaList = list
                        }
                    )
                    FrameNavTab.SYSTEM -> SettingsScreen(
                        onLogout = onLogout,
                        onOpenTrash = { showTrashScreen = true },
                        onOpenDevices = { selectedTab = FrameNavTab.NODES }
                    )
                }
            }
        }

        // FRAME // OS Bottom Navigation Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(FrameSurface)
                .border(width = 1.dp, color = FrameBorder)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FrameNavTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.96f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                        label = "tabScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .scale(scale)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    HapticHelper.performClick(view)
                                    if (tab == FrameNavTab.VAULT && selectedTab != FrameNavTab.VAULT) {
                                        galleryFilterDeviceId = null
                                    }
                                    selectedTab = tab
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isSelected) {
                                RedDotIndicator(size = 5.dp)
                            }
                            Text(
                                text = "(${tab.number}) ${tab.title}",
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                letterSpacing = 0.04.em,
                                color = if (isSelected) FrameWhite else FrameGray500
                            )
                        }
                    }
                }
            }
        }

        // Trash Screen Overlay
        if (showTrashScreen) {
            TrashScreen(
                onNavigateBack = {
                    showTrashScreen = false
                },
                onMediaSelected = { fileId, list ->
                    activeViewerFileId = fileId
                    activeViewerMediaList = list
                }
            )
        }

        // Fullscreen Progressive Media Viewer overlay
        activeViewerFileId?.let { fileId ->
            MediaViewerScreen(
                initialFileId = fileId,
                initialMediaList = activeViewerMediaList,
                onClose = {
                    activeViewerFileId = null
                    activeViewerMediaList = emptyList()
                }
            )
        }
    }
}
