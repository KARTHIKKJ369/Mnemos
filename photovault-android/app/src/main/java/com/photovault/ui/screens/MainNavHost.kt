package com.photovault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.screens.backup.BackupScreen
import com.photovault.ui.screens.gallery.GalleryScreen
import com.photovault.ui.screens.settings.SettingsScreen
import com.photovault.ui.screens.timeline.TimelineScreen
import com.photovault.ui.screens.viewer.MediaViewerScreen
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.DarkSurfaceVariant
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextSecondary

enum class NavTab(val title: String, val icon: ImageVector) {
    PHOTOS("Photos", Icons.Default.Collections),
    TIMELINE("Timeline", Icons.Default.DateRange),
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    NavTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                HapticHelper.performClick(view)
                                selectedTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentGold,
                                selectedTextColor = AccentGold,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            },
            containerColor = DarkBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    NavTab.PHOTOS -> GalleryScreen(
                        onMediaSelected = { fileId ->
                            activeViewerFileId = fileId
                        }
                    )
                    NavTab.TIMELINE -> TimelineScreen(
                        onMediaSelected = { fileId ->
                            activeViewerFileId = fileId
                        }
                    )
                    NavTab.BACKUP -> BackupScreen()
                    NavTab.SETTINGS -> SettingsScreen(onLogout = onLogout)
                }
            }
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
