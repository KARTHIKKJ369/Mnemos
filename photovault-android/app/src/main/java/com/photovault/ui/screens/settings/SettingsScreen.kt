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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.HealthResponse
import com.photovault.ui.components.ButtonVariant
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.IconTintVariant
import com.photovault.ui.components.MnemosButton
import com.photovault.ui.components.MnemosCard
import com.photovault.ui.components.MnemosPageHeader
import com.photovault.ui.components.MnemosRowCard
import com.photovault.ui.theme.IrisLight
import com.photovault.ui.theme.IrisSubtle
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.Slate200
import com.photovault.ui.theme.Slate400
import com.photovault.ui.theme.Slate50
import com.photovault.ui.theme.Slate800
import com.photovault.ui.theme.Slate900
import com.photovault.ui.theme.Slate950
import com.photovault.ui.theme.TomatoRed
import com.photovault.ui.theme.TomatoSubtle
import java.io.File

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
            .background(Slate950)
            .statusBarsPadding()
    ) {
        // 20px Page Header
        MnemosPageHeader(
            title = "Settings",
            subtitle = "Node telemetry, cluster & cache management"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Server Connection Card
            MnemosCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IrisSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = IrisLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Server Connection",
                            style = MnemosType.CardTitle15,
                            color = Slate50
                        )
                    }

                    SettingsDataRow(label = "Host URL", value = serverUrl, isMono = true)
                    SettingsDataRow(label = "Device ID", value = deviceId.take(16) + "…", isMono = true)
                    SettingsDataRow(
                        label = "Vault Status",
                        value = if (healthData?.database == "ok") "Connected & Healthy" else "Checking…",
                        isMono = false
                    )
                }
            }

            // Paired Devices Card
            MnemosRowCard(
                title = "Paired Cluster Devices",
                subtitle = "Inspect node roster, device types & sync status",
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
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            // Trash & Deleted Items Card
            MnemosRowCard(
                title = "Trash & Deleted Items",
                subtitle = "Restore or permanently erase vault media",
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
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            // Vault Telemetry Card
            healthData?.let { health ->
                MnemosCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(IrisSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = IrisLight,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "Vault Telemetry",
                                style = MnemosType.CardTitle15,
                                color = Slate50
                            )
                        }

                        SettingsDataRow(label = "Total Photos", value = "${health.totalPhotos}", isMono = true)
                        SettingsDataRow(label = "Total Videos", value = "${health.totalVideos}", isMono = true)
                        SettingsDataRow(
                            label = "Vault Storage",
                            value = "${(health.vaultBytes / (1024 * 1024 * 1024.0)).format(2)} GB",
                            isMono = true
                        )
                        SettingsDataRow(
                            label = "Disk Free",
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Slate800),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Offline Media Cache",
                            style = MnemosType.CardTitle15,
                            color = Slate50
                        )
                    }

                    SettingsDataRow(
                        label = "Cached Thumbnails & Blobs",
                        value = "$cacheSizeMb MB",
                        isMono = true
                    )

                    MnemosButton(
                        text = "Clear Offline Cache",
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

            Spacer(modifier = Modifier.height(6.dp))

            // Logout / Disconnect Button (Destructive Tomato Red)
            MnemosButton(
                text = "Disconnect & Log Out",
                onClick = {
                    HapticHelper.vibrateWarning(context)
                    app.preferenceStore.clear()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.DESTRUCTIVE,
                icon = Icons.AutoMirrored.Filled.Logout
            )

            Spacer(modifier = Modifier.height(24.dp))
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
            style = MnemosType.BodySecondary13,
            color = Slate400
        )
        Text(
            text = value,
            style = if (isMono) MnemosType.Mono12 else MnemosType.BodySecondary13.copy(fontWeight = FontWeight.Medium),
            color = Slate200
        )
    }
}

private fun Double.format(digits: Int) = String.format("%.${digits}f", this)
