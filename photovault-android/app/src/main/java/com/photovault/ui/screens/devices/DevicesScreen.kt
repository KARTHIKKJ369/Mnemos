package com.photovault.ui.screens.devices

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.DeviceItem
import com.photovault.data.model.MediaItem
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.DarkSurfaceVariant
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextPrimary
import com.photovault.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onNavigateToGalleryWithDevice: (deviceId: String, deviceName: String) -> Unit
) {
    val app = PhotoVaultApplication.instance
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val currentDeviceId by app.preferenceStore.deviceId.collectAsState()

    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var deviceMediaCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var downloadingDeviceId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun loadDevices() {
        isLoading = true
        scope.launch {
            val result = app.apiClient.fetchDevices()
            val allMediaResult = app.apiClient.fetchMedia(limit = 1000)
            isLoading = false
            result.onSuccess { devList ->
                devices = devList
            }
            allMediaResult.onSuccess { mediaList ->
                val counts = mutableMapOf<String, Int>()
                for (item in mediaList) {
                    val id = item.uploadedByDeviceId ?: "unknown"
                    counts[id] = (counts[id] ?: 0) + 1
                }
                deviceMediaCounts = counts
            }
        }
    }

    LaunchedEffect(Unit) {
        loadDevices()
    }

    fun downloadAllFromDevice(device: DeviceItem) {
        scope.launch {
            HapticHelper.performClick(view)
            downloadingDeviceId = device.id
            val mediaResult = app.apiClient.fetchMedia(deviceId = device.id, limit = 1000)
            mediaResult.onSuccess { list ->
                val total = list.size
                var completed = 0
                downloadProgress = 0 to total
                for (item in list) {
                    app.apiClient.downloadMediaToGallery(
                        fileId = item.fileId,
                        filename = item.filename,
                        mimeType = item.mimeType
                    )
                    completed++
                    downloadProgress = completed to total
                }
                downloadingDeviceId = null
                downloadProgress = null
                HapticHelper.vibrateSuccess(context)
                Toast.makeText(context, "Downloaded $completed files from ${device.name} to device gallery!", Toast.LENGTH_LONG).show()
            }.onFailure {
                downloadingDeviceId = null
                downloadProgress = null
                Toast.makeText(context, "Failed to download media: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Vault Devices & Sync",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${devices.size} connected nodes",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        HapticHelper.performClick(view)
                        loadDevices()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (isLoading && devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGold)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Banner
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(AccentGold.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cross-Device Media Hub",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Browse or download original photos and videos uploaded from any device in your vault.",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Device Cards
                items(devices, key = { it.id }) { device ->
                    val isCurrent = device.id == currentDeviceId
                    val itemCount = deviceMediaCounts[device.id] ?: 0
                    val isDownloading = downloadingDeviceId == device.id

                    DeviceCard(
                        device = device,
                        isCurrentDevice = isCurrent,
                        mediaCount = itemCount,
                        isDownloading = isDownloading,
                        downloadProgress = if (isDownloading) downloadProgress else null,
                        onViewPhotos = {
                            HapticHelper.performClick(view)
                            onNavigateToGalleryWithDevice(device.id, device.name)
                        },
                        onDownloadAll = {
                            downloadAllFromDevice(device)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceItem,
    isCurrentDevice: Boolean,
    mediaCount: Int,
    isDownloading: Boolean,
    downloadProgress: Pair<Int, Int>?,
    onViewPhotos: () -> Unit,
    onDownloadAll: () -> Unit
) {
    val icon = when (device.deviceType.lowercase()) {
        "mac", "desktop" -> Icons.Default.Laptop
        "ios" -> Icons.Default.PhoneIphone
        "android" -> Icons.Default.PhoneAndroid
        "web" -> Icons.Default.Language
        else -> Icons.Default.Computer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isCurrentDevice) AccentGold.copy(alpha = 0.2f) else DarkBackground,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isCurrentDevice) AccentGold else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = device.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (isCurrentDevice) {
                            Box(
                                modifier = Modifier
                                    .background(AccentGold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "THIS PHONE",
                                    color = AccentGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "Type: ${device.deviceType.uppercase()} • $mediaCount items in vault",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            if (isDownloading && downloadProgress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AccentGold
                    )
                    Text(
                        text = "Downloading ${downloadProgress.first}/${downloadProgress.second} files to gallery...",
                        color = AccentGold,
                        fontSize = 12.sp
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewPhotos,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentGold)
                        Text("View Photos", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onDownloadAll,
                    enabled = !isDownloading && mediaCount > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkBackground,
                        contentColor = TextPrimary,
                        disabledContainerColor = DarkBackground.copy(alpha = 0.5f),
                        disabledContentColor = TextMuted
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentGold)
                        Text(if (isDownloading) "Downloading..." else "Download All", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
