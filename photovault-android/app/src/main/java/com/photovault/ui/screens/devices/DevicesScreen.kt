package com.photovault.ui.screens.devices

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.DeviceItem
import com.photovault.ui.components.ButtonVariant
import com.photovault.ui.components.FrameHeroHeader
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.MnemosButton
import com.photovault.ui.components.MnemosCard
import com.photovault.ui.components.RedDotIndicator
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameBorderLight
import com.photovault.ui.theme.FrameGray100
import com.photovault.ui.theme.FrameGray300
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameGray900
import com.photovault.ui.theme.FrameSurface
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.RobotoMonoFontFamily
import com.photovault.ui.theme.SignalRed
import com.photovault.ui.theme.SignalRedSubtle
import com.photovault.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.launch

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
    var deviceToDelete by remember { mutableStateOf<DeviceItem?>(null) }

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
                Toast.makeText(context, "Saved $completed files from ${device.name} to gallery", Toast.LENGTH_LONG).show()
            }.onFailure {
                downloadingDeviceId = null
                downloadProgress = null
                Toast.makeText(context, "Download failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteDevice(device: DeviceItem) {
        scope.launch {
            val res = app.apiClient.deleteDevice(device.id)
            res.onSuccess {
                HapticHelper.vibrateSuccess(context)
                Toast.makeText(context, "Removed node ${device.name}", Toast.LENGTH_SHORT).show()
                loadDevices()
            }.onFailure {
                HapticHelper.vibrateWarning(context)
                Toast.makeText(context, "Failed to remove node: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrameBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar: FRAME Hero Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RedDotIndicator(size = 7.dp)
                        Text(
                            text = String.format("%02d CLUSTER NODES // ONLINE", devices.size),
                            style = MnemosType.Headline28,
                            color = FrameWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "BROWSE CAPTURED MEDIA & REMOTE HARDWARE NODES",
                        style = MnemosType.Mono11,
                        color = FrameGray500
                    )
                }

                IconButton(onClick = {
                    HapticHelper.performClick(view)
                    loadDevices()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = FrameGray300,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isLoading && devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SignalRed, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp, top = 4.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        val isCurrent = device.id == currentDeviceId
                        val itemCount = deviceMediaCounts[device.id] ?: 0
                        val isDownloading = downloadingDeviceId == device.id
                        val isAdmin = device.name.contains("Admin", ignoreCase = true) || device.deviceType.lowercase() == "mac"

                        DeviceNodeCard(
                            device = device,
                            isCurrentDevice = isCurrent,
                            isAdmin = isAdmin,
                            mediaCount = itemCount,
                            isDownloading = isDownloading,
                            downloadProgress = if (isDownloading) downloadProgress else null,
                            onBrowseMedia = {
                                HapticHelper.performClick(view)
                                onNavigateToGalleryWithDevice(device.id, device.name)
                            },
                            onDownloadAll = {
                                downloadAllFromDevice(device)
                            },
                            onDeleteDevice = {
                                HapticHelper.vibrateWarning(context)
                                deviceToDelete = device
                            }
                        )
                    }
                }
            }
        }

        // Delete Device Confirmation Dialog
        deviceToDelete?.let { dev ->
            AlertDialog(
                onDismissRequest = { deviceToDelete = null },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RedDotIndicator(size = 6.dp)
                        Text("REMOVE NODE '${dev.name.uppercase()}'?", color = FrameWhite, style = MnemosType.CardTitle15)
                    }
                },
                text = {
                    Text(
                        "Are you sure you want to revoke '${dev.name}' (${dev.deviceType.uppercase()})? Files uploaded by this node will remain securely preserved on your vault server.",
                        color = FrameGray300,
                        style = MnemosType.BodySecondary13
                    )
                },
                confirmButton = {
                    MnemosButton(
                        text = "REVOKE NODE",
                        onClick = {
                            deleteDevice(dev)
                            deviceToDelete = null
                        },
                        variant = ButtonVariant.DESTRUCTIVE
                    )
                },
                dismissButton = {
                    TextButton(onClick = { deviceToDelete = null }) {
                        Text("CANCEL", style = MnemosType.Mono11, color = FrameGray500)
                    }
                },
                containerColor = FrameSurface
            )
        }
    }
}

@Composable
private fun DeviceNodeCard(
    device: DeviceItem,
    isCurrentDevice: Boolean,
    isAdmin: Boolean,
    mediaCount: Int,
    isDownloading: Boolean,
    downloadProgress: Pair<Int, Int>?,
    onBrowseMedia: () -> Unit,
    onDownloadAll: () -> Unit,
    onDeleteDevice: () -> Unit
) {
    val icon = when (device.deviceType.lowercase()) {
        "mac", "desktop" -> Icons.Default.Laptop
        "ios" -> Icons.Default.PhoneIphone
        "android" -> Icons.Default.PhoneAndroid
        "web" -> Icons.Default.Language
        else -> Icons.Default.Computer
    }

    MnemosCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onBrowseMedia
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrentDevice) SignalRedSubtle else FrameGray900)
                        .border(1.dp, if (isCurrentDevice) SignalRed else FrameBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isCurrentDevice) SignalRed else FrameWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = device.name.uppercase(),
                            style = MnemosType.CardTitle15,
                            color = FrameWhite
                        )
                        if (isCurrentDevice) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SignalRedSubtle)
                                    .border(0.5.dp, SignalRed, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "THIS PHONE",
                                    color = SignalRed,
                                    style = MnemosType.Mono11.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${device.deviceType.uppercase()} // $mediaCount ITEMS IN VAULT",
                        style = MnemosType.Mono11,
                        color = FrameGray500
                    )
                }

                // Delete Device Button
                if (!isAdmin && !isCurrentDevice) {
                    IconButton(
                        onClick = onDeleteDevice,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove Device",
                            tint = SignalRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (isDownloading && downloadProgress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = SignalRed
                    )
                    Text(
                        text = "DOWNLOADING ${downloadProgress.first}/${downloadProgress.second} FILES…",
                        style = MnemosType.Mono11,
                        color = FrameWhite
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MnemosButton(
                    text = "BROWSE MEDIA ($mediaCount)",
                    onClick = onBrowseMedia,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.Default.PhotoLibrary
                )

                MnemosButton(
                    text = if (isDownloading) "SAVING…" else "DOWNLOAD",
                    onClick = onDownloadAll,
                    enabled = !isDownloading && mediaCount > 0,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.SECONDARY,
                    icon = Icons.Default.CloudDownload
                )
            }
        }
    }
}
