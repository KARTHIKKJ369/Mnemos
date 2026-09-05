package com.photovault.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.HealthResponse
import com.photovault.ui.components.ButtonVariant
import com.photovault.ui.components.FrameHeroHeader
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.IconTintVariant
import com.photovault.ui.components.MnemosButton
import com.photovault.ui.components.MnemosCard
import com.photovault.ui.components.MnemosRowCard
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameGray100
import com.photovault.ui.theme.FrameGray300
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameGray900
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.SignalRed
import com.photovault.ui.theme.SignalRedSubtle
import java.io.File

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import com.photovault.ui.components.MnemosSwitch

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onOpenTrash: () -> Unit = {},
    onOpenDevices: () -> Unit = {}
) {
    val app = PhotoVaultApplication.instance
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val serverUrl by app.preferenceStore.serverUrl.collectAsState()
    val deviceId by app.preferenceStore.deviceId.collectAsState()
    val autoBackup by app.preferenceStore.autoBackup.collectAsState()
    val videoQuality by app.preferenceStore.videoQuality.collectAsState()

    var healthData by remember { mutableStateOf<HealthResponse?>(null) }
    var cacheSizeMb by remember { mutableStateOf(0L) }

    fun calculateCache() {
        val cacheDir = File(context.cacheDir, "photovault_media_cache")
        val bytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        cacheSizeMb = bytes / (1024 * 1024)
    }

    LaunchedEffect(Unit) {
        calculateCache()
        val result = app.apiClient.checkHealth()
        result.onSuccess {
            healthData = it
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FrameBlack)
            .statusBarsPadding()
    ) {
        // FRAME Hero Header
        FrameHeroHeader(
            title = "SYSTEM",
            subtitle = "CLUSTER CONFIGURATION & VAULT STATUS"
        )

        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Auto-Sync Camera Roll Card
            MnemosRowCard(
                title = "AUTO-SYNC CAMERA ROLL",
                subtitle = "Automatically backup new photos & videos in background",
                icon = Icons.Default.Sync,
                iconTintVariant = IconTintVariant.IRIS,
                trailingContent = {
                    MnemosSwitch(
                        checked = autoBackup,
                        onCheckedChange = {
                            HapticHelper.performClick(view)
                            app.preferenceStore.setAutoBackup(it)
                        }
                    )
                }
            )

            // Video Playback Quality Card
            MnemosCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SignalRedSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = SignalRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "VIDEO PLAYBACK QUALITY",
                                style = MnemosType.CardTitle15,
                                color = FrameWhite
                            )
                            Text(
                                text = "Stream raw original vs fast preview",
                                style = MnemosType.BodySecondary13,
                                color = FrameGray500
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MnemosButton(
                            text = "RAW ORIGINAL (4K/HD)",
                            onClick = {
                                HapticHelper.performClick(view)
                                app.preferenceStore.setVideoQuality("original")
                            },
                            modifier = Modifier.weight(1f),
                            variant = if (videoQuality == "original") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                        )
                        MnemosButton(
                            text = "PREVIEW (720P)",
                            onClick = {
                                HapticHelper.performClick(view)
                                app.preferenceStore.setVideoQuality("preview")
                            },
                            modifier = Modifier.weight(1f),
                            variant = if (videoQuality == "preview") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                        )
                    }
                }
            }

            // Server Connection Card
            MnemosCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SignalRedSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = SignalRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "SERVER CONNECTION",
                            style = MnemosType.CardTitle15,
                            color = FrameWhite
                        )
                    }

                    SettingsDataRow(label = "HOST // URL", value = serverUrl, isMono = true)
                    SettingsDataRow(label = "NODE // ID", value = deviceId.take(16) + "…", isMono = true)
                    SettingsDataRow(
                        label = "STATUS",
                        value = if (healthData?.database == "ok") "CONNECTED // HEALTHY" else "CHECKING…",
                        isMono = true
                    )
                }
            }

            // Paired Devices Card
            MnemosRowCard(
                title = "CLUSTER HARDWARE NODES",
                subtitle = "Inspect paired devices, OS types & sync state",
                icon = Icons.Default.Devices,
                iconTintVariant = IconTintVariant.IRIS,
                onClick = {
                    HapticHelper.performClick(view)
                    onOpenDevices()
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = FrameGray500,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            // Trash & Deleted Items Card
            MnemosRowCard(
                title = "TRASH & DELETED BLOBS",
                subtitle = "Restore or permanently purge deleted items",
                icon = Icons.Default.Delete,
                iconTintVariant = IconTintVariant.DESTRUCTIVE,
                onClick = {
                    HapticHelper.performClick(view)
                    onOpenTrash()
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = FrameGray500,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            // Vault Telemetry Card
            healthData?.let { health ->
                MnemosCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SignalRedSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = SignalRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "VAULT STORAGE METRICS",
                                style = MnemosType.CardTitle15,
                                color = FrameWhite
                            )
                        }

                        SettingsDataRow(label = "TOTAL PHOTOS", value = "${health.totalPhotos}", isMono = true)
                        SettingsDataRow(label = "TOTAL VIDEOS", value = "${health.totalVideos}", isMono = true)
                        SettingsDataRow(
                            label = "VAULT USAGE",
                            value = "${(health.vaultBytes / (1024 * 1024 * 1024.0)).format(2)} GB",
                            isMono = true
                        )
                        SettingsDataRow(
                            label = "DISK FREE",
                            value = "${(health.diskFreeBytes / (1024 * 1024 * 1024.0)).format(1)} GB",
                            isMono = true
                        )
                    }
                }
            }

            // Cache Management Card
            MnemosCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(FrameGray900),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = FrameGray300,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "OFFLINE MEDIA CACHE",
                            style = MnemosType.CardTitle15,
                            color = FrameWhite
                        )
                    }

                    SettingsDataRow(
                        label = "CACHED BLOBS",
                        value = "$cacheSizeMb MB",
                        isMono = true
                    )

                    MnemosButton(
                        text = "CLEAR OFFLINE CACHE",
                        onClick = {
                            HapticHelper.performClick(view)
                            val cacheDir = File(context.cacheDir, "photovault_media_cache")
                            cacheDir.deleteRecursively()
                            calculateCache()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.SECONDARY
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Logout / Disconnect Button (Signal Red)
            MnemosButton(
                text = "DISCONNECT & REVOKE ACCESS",
                onClick = {
                    HapticHelper.vibrateWarning(context)
                    app.preferenceStore.clear()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.DESTRUCTIVE,
                icon = Icons.AutoMirrored.Filled.Logout
            )

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun SettingsDataRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MnemosType.Mono11,
            color = FrameGray500
        )
        Text(
            text = value,
            style = if (isMono) MnemosType.Mono12 else MnemosType.BodySecondary13,
            color = FrameWhite
        )
    }
}

private fun Double.format(digits: Int) = String.format("%.${digits}f", this)
