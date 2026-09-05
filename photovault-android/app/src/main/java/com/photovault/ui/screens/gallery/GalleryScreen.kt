package com.photovault.ui.screens.gallery

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.FilterQuality
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
import com.photovault.ui.theme.AccentGoldGlow
import com.photovault.ui.theme.BorderSubtle
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.DarkSurfaceOverlay
import com.photovault.ui.theme.DarkSurfaceVariant
import com.photovault.ui.theme.DangerRed
import com.photovault.ui.theme.EmeraldGreen
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextPrimary
import com.photovault.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class SortOption(val label: String, val sortField: String, val sortOrder: String) {
    NEWEST("Newest First", "taken_at", "desc"),
    OLDEST("Oldest First", "taken_at", "asc"),
    LARGEST("Largest Size", "size_bytes", "desc"),
    SMALLEST("Smallest Size", "size_bytes", "asc"),
    NAME_AZ("Name (A to Z)", "filename", "asc"),
    NAME_ZA("Name (Z to A)", "filename", "desc")
}

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

    // Filters & Sorting
    var selectedTypeFilter by remember { mutableStateOf("all") } // "all", "photos", "videos", "favorites"
    var selectedDeviceFilter by remember { mutableStateOf(initialDeviceId ?: "") } // "" = all, "other_devices", or deviceId
    var currentSort by remember { mutableStateOf(SortOption.NEWEST) }
    var showSortSheet by remember { mutableStateOf(false) }

    // Multi-Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isBatchDownloading by remember { mutableStateOf(false) }
    var batchProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Intercept back button in selection mode to exit selection mode
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedFileIds = emptySet()
    }

    fun loadData() {
        isLoading = true
        scope.launch {
            // Load devices for filter chips
            val devResult = app.apiClient.fetchDevices()
            devResult.onSuccess { devices = it }

            // Load media with filters & sort
            val mimeFilter = when (selectedTypeFilter) {
                "photos" -> "image/"
                "videos" -> "video/"
                else -> ""
            }
            val favoriteOnly = selectedTypeFilter == "favorites"
            val devId = if (selectedDeviceFilter != "other_devices") selectedDeviceFilter else ""
            val exclDevId = if (selectedDeviceFilter == "other_devices") currentDeviceId else ""

            val result = app.apiClient.fetchMedia(
                mimeType = mimeFilter,
                favoriteOnly = favoriteOnly,
                deviceId = devId,
                excludeDeviceId = exclDevId,
                sort = currentSort.sortField,
                order = currentSort.sortOrder
            )
            isLoading = false
            result.onSuccess {
                mediaList = it
            }
        }
    }

    LaunchedEffect(selectedTypeFilter, selectedDeviceFilter, currentSort) {
        loadData()
    }

    fun toggleSelection(fileId: String) {
        HapticHelper.performClick(view)
        selectedFileIds = if (selectedFileIds.contains(fileId)) {
            selectedFileIds - fileId
        } else {
            selectedFileIds + fileId
        }
        if (selectedFileIds.isEmpty()) {
            isSelectionMode = false
        }
    }

    fun batchDownloadSelected() {
        val targets = mediaList.filter { selectedFileIds.contains(it.fileId) }
        if (targets.isEmpty() || isBatchDownloading) return

        isBatchDownloading = true
        scope.launch {
            val total = targets.size
            var completed = 0
            batchProgress = 0 to total
            for (item in targets) {
                app.apiClient.downloadMediaToGallery(
                    fileId = item.fileId,
                    filename = item.filename,
                    mimeType = item.mimeType
                )
                completed++
                batchProgress = completed to total
            }
            isBatchDownloading = false
            batchProgress = null
            isSelectionMode = false
            selectedFileIds = emptySet()
            HapticHelper.vibrateSuccess(context)
            Toast.makeText(context, "Downloaded $completed files to gallery!", Toast.LENGTH_LONG).show()
        }
    }

    fun batchDeleteSelected() {
        val ids = selectedFileIds.toList()
        if (ids.isEmpty()) return
        HapticHelper.vibrateWarning(context)
        scope.launch {
            for (id in ids) {
                app.apiClient.deleteMedia(id)
            }
            isSelectionMode = false
            selectedFileIds = emptySet()
            loadData()
            Toast.makeText(context, "Removed ${ids.size} files", Toast.LENGTH_SHORT).show()
        }
    }

    fun batchFavoriteSelected(fav: Boolean) {
        val ids = selectedFileIds.toList()
        if (ids.isEmpty()) return
        HapticHelper.performClick(view)
        scope.launch {
            for (id in ids) {
                app.apiClient.setFavorite(id, fav)
            }
            isSelectionMode = false
            selectedFileIds = emptySet()
            loadData()
            Toast.makeText(context, if (fav) "Added ${ids.size} to favorites" else "Removed ${ids.size} from favorites", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "${selectedFileIds.size} Selected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    } else {
                        Column {
                            Text(
                                text = "Library",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${mediaList.size} items • ${currentSort.label}" + when {
                                    selectedDeviceFilter == "other_devices" -> " • Other Devices Only"
                                    selectedDeviceFilter.isNotEmpty() -> {
                                        val devName = devices.find { it.id == selectedDeviceFilter }?.name ?: "Filtered"
                                        " • $devName"
                                    }
                                    else -> ""
                                },
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedFileIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // Select All / Deselect All
                        TextButton(onClick = {
                            HapticHelper.performSelection(view)
                            selectedFileIds = if (selectedFileIds.size == mediaList.size) {
                                emptySet()
                            } else {
                                mediaList.map { it.fileId }.toSet()
                            }
                        }) {
                            Text(
                                text = if (selectedFileIds.size == mediaList.size) "Deselect" else "Select All",
                                color = AccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        // Enter Selection Mode Button
                        IconButton(onClick = {
                            HapticHelper.performClick(view)
                            isSelectionMode = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Select Media",
                                tint = AccentGold
                            )
                        }

                        // Sort Sheet Trigger
                        IconButton(onClick = {
                            HapticHelper.performClick(view)
                            showSortSheet = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Options",
                                tint = TextSecondary
                            )
                        }

                        // Density switcher (2 -> 3 -> 4 -> 5 -> 2)
                        IconButton(onClick = {
                            HapticHelper.performSelection(view)
                            val nextCols = if (columns >= 5) 2 else columns + 1
                            app.preferenceStore.setGridColumns(nextCols)
                        }) {
                            Icon(
                                imageVector = Icons.Default.ViewModule,
                                contentDescription = "Grid Density",
                                tint = TextSecondary
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
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Row 1: Media Type Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "all" to "All Media",
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
                            label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
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

                // Row 2: Device Source Filter Chips (All, Other Devices Only, This Phone, Specific Nodes)
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

                    // Other Devices Only (Quick Filter for cross-device sync)
                    FilterChip(
                        selected = selectedDeviceFilter == "other_devices",
                        onClick = {
                            HapticHelper.performClick(view)
                            selectedDeviceFilter = if (selectedDeviceFilter == "other_devices") "" else "other_devices"
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text("Other Devices Only ⚡", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGold,
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black,
                            containerColor = DarkSurfaceVariant.copy(alpha = 0.6f),
                            labelColor = AccentGold,
                            iconColor = AccentGold
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
                            Text("No media found in vault", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (selectedDeviceFilter.isNotEmpty()) "No files match the selected filter"
                                else "Upload or backup photos to populate library",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(
                            start = 1.dp,
                            end = 1.dp,
                            top = 1.dp,
                            bottom = if (isSelectionMode) 100.dp else 1.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(mediaList, key = { it.fileId }) { item ->
                            val isSelected = selectedFileIds.contains(item.fileId)
                            GalleryTile(
                                media = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        toggleSelection(item.fileId)
                                    } else {
                                        HapticHelper.performClick(view)
                                        onMediaSelected(item.fileId)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        toggleSelection(item.fileId)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Floating Batch Actions Bar (Thumb Zone)
            AnimatedVisibility(
                visible = isSelectionMode && selectedFileIds.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceOverlay),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Download Batch Button
                        Button(
                            onClick = { batchDownloadSelected() },
                            enabled = !isBatchDownloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGold,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isBatchDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Black
                                    )
                                    batchProgress?.let { (done, total) ->
                                        Text("$done/$total", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Download (${selectedFileIds.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Favorite Batch
                        IconButton(onClick = { batchFavoriteSelected(true) }) {
                            Icon(Icons.Default.Favorite, contentDescription = "Favorite All", tint = AccentGold, modifier = Modifier.size(20.dp))
                        }

                        // Delete Batch
                        IconButton(onClick = { batchDeleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete All", tint = DangerRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Sort Options Modal Bottom Sheet
        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = DarkSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sort Media By",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(onClick = { showSortSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    SortOption.entries.forEach { option ->
                        val isSelected = currentSort == option
                        Card(
                            onClick = {
                                HapticHelper.performSelection(view)
                                currentSort = option
                                showSortSheet = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AccentGold.copy(alpha = 0.15f) else DarkBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (isSelected) AccentGold else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryTile(
    media: MediaItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isSelected) Modifier.border(2.dp, AccentGold) else Modifier
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(200)
                .build(),
            contentDescription = media.filename,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
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
            if (devName.isNotBlank() && !isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = devName,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // Selection Checkbox Ring
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(if (isSelected) AccentGold else Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Favorite Heart Indicator
        if (media.favorite && !isSelectionMode) {
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
                    tint = DangerRed,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
