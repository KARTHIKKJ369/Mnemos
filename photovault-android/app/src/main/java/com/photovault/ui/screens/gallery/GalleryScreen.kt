package com.photovault.ui.screens.gallery

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.DeviceItem
import com.photovault.data.model.MediaItem
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.DarkSurfaceOverlay
import com.photovault.ui.theme.DarkSurfaceVariant
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextPrimary
import com.photovault.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    initialDeviceId: String? = null,
    onMediaSelected: (fileId: String) -> Unit
) {
    val app = PhotoVaultApplication.instance
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val columns by app.preferenceStore.gridColumns.collectAsState()
    val currentDeviceId by app.preferenceStore.deviceId.collectAsState()

    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTypeFilter by remember { mutableStateOf("all") } // "all", "photos", "videos", "favorites"
    var selectedDeviceFilter by remember { mutableStateOf(initialDeviceId ?: "") } // "" = all, or deviceId

    fun loadData() {
        isLoading = true
        scope.launch {
            // Load devices for filter chips
            val devResult = app.apiClient.fetchDevices()
            devResult.onSuccess { devices = it }

            // Load media with filters
            val mimeFilter = when (selectedTypeFilter) {
                "photos" -> "image/"
                "videos" -> "video/"
                else -> ""
            }
            val favoriteOnly = selectedTypeFilter == "favorites"
            val result = app.apiClient.fetchMedia(
                mimeType = mimeFilter,
                favoriteOnly = favoriteOnly,
                deviceId = selectedDeviceFilter
            )
            isLoading = false
            result.onSuccess {
                mediaList = it
            }
        }
    }

    LaunchedEffect(selectedTypeFilter, selectedDeviceFilter) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Library",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${mediaList.size} items" + if (selectedDeviceFilter.isNotEmpty()) {
                                val devName = devices.find { it.id == selectedDeviceFilter }?.name ?: "Filtered Device"
                                " • $devName"
                            } else "",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    // Density switcher (2 -> 3 -> 4 -> 5 -> 2)
                    IconButton(onClick = {
                        HapticHelper.performSelection(view)
                        val nextCols = if (columns >= 5) 2 else columns + 1
                        app.preferenceStore.setGridColumns(nextCols)
                    }) {
                        Icon(
                            imageVector = Icons.Default.ViewModule,
                            contentDescription = "Grid Density",
                            tint = AccentGold
                        )
                    }

                    // Refresh
                    IconButton(onClick = {
                        HapticHelper.performClick(view)
                        loadData()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Row 1: Media Type Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "all" to "All",
                    "photos" to "Photos",
                    "videos" to "Videos",
                    "favorites" to "Favorites"
                ).forEach { (key, label) ->
                    val isSelected = selectedTypeFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            HapticHelper.performClick(view)
                            selectedTypeFilter = key
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGold,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        border = null,
                        shape = CircleShape
                    )
                }
            }

            // Row 2: Device Source Filter Chips (All Devices, This Phone, Remote Devices)
            if (devices.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // All Devices Chip
                    FilterChip(
                        selected = selectedDeviceFilter.isEmpty(),
                        onClick = {
                            HapticHelper.performClick(view)
                            selectedDeviceFilter = ""
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text("All Devices", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TextPrimary,
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black,
                            containerColor = DarkSurfaceVariant.copy(alpha = 0.6f),
                            labelColor = TextMuted,
                            iconColor = TextMuted
                        ),
                        border = null,
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Individual Devices
                    devices.forEach { dev ->
                        val isSelected = selectedDeviceFilter == dev.id
                        val isThisPhone = dev.id == currentDeviceId
                        val devIcon = when (dev.deviceType.lowercase()) {
                            "mac", "desktop" -> Icons.Default.Laptop
                            "ios" -> Icons.Default.PhoneIphone
                            "android" -> Icons.Default.PhoneAndroid
                            "web" -> Icons.Default.Language
                            else -> Icons.Default.Computer
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                HapticHelper.performClick(view)
                                selectedDeviceFilter = if (isSelected) "" else dev.id
                            },
                            leadingIcon = {
                                Icon(devIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            label = {
                                Text(
                                    if (isThisPhone) "${dev.name} (This Phone)" else dev.name,
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGold,
                                selectedLabelColor = Color.Black,
                                selectedLeadingIconColor = Color.Black,
                                containerColor = DarkSurfaceVariant.copy(alpha = 0.6f),
                                labelColor = TextMuted,
                                iconColor = TextMuted
                            ),
                            border = null,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            if (isLoading && mediaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentGold)
                }
            } else if (mediaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No media found in vault", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (selectedDeviceFilter.isNotEmpty()) "No files found for this selected device"
                            else "Upload or backup photos to populate your vault",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mediaList, key = { it.fileId }) { item ->
                        GalleryTile(
                            media = item,
                            onClick = {
                                HapticHelper.performClick(view)
                                onMediaSelected(item.fileId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryTile(
    media: MediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val client = PhotoVaultApplication.instance.apiClient
    val thumbnailUrl = remember(media.fileId) {
        if (media.thumbnailAvailable) client.getThumbnailUrl(media.fileId) else client.getOriginalUrl(media.fileId)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(200)
                .build(),
            contentDescription = media.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video duration / play badge
        if (media.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(DarkSurfaceOverlay, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    media.durationMs?.let { ms ->
                        val seconds = (ms / 1000) % 60
                        val minutes = (ms / (1000 * 60))
                        Text(
                            text = String.format("%d:%02d", minutes, seconds),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Uploaded device badge if available
        media.uploadedByDeviceName?.let { devName ->
            if (devName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = devName,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // Favorite Heart Indicator
        if (media.favorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(18.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = Color.Red,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
