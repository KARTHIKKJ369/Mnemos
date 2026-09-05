package com.photovault.ui.screens.gallery

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
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
import com.photovault.ui.theme.AccentAmber
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.NeutralCanvas
import com.photovault.ui.theme.NeutralElevated
import com.photovault.ui.theme.NeutralHairline
import com.photovault.ui.theme.NeutralSurface
import com.photovault.ui.theme.StatusError
import com.photovault.ui.theme.StatusSyncing
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
    var selectedTypeFilter by remember { mutableStateOf("all") }
    var selectedDeviceFilter by remember { mutableStateOf(initialDeviceId ?: "") }
    var currentSort by remember { mutableStateOf(SortOption.NEWEST) }
    var showSortSheet by remember { mutableStateOf(false) }

    // Multi-Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isBatchDownloading by remember { mutableStateOf(false) }
    var batchProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Back button in selection mode exits selection
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedFileIds = emptySet()
    }

    fun loadData() {
        isLoading = true
        scope.launch {
            val devResult = app.apiClient.fetchDevices()
            devResult.onSuccess { devices = it }

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
    }

    fun batchDownloadSelected() {
        val toDownload = mediaList.filter { selectedFileIds.contains(it.fileId) }
        if (toDownload.isEmpty()) return

        isBatchDownloading = true
        batchProgress = Pair(0, toDownload.size)
        HapticHelper.performClick(view)

        scope.launch {
            var successCount = 0
            toDownload.forEachIndexed { index, item ->
                batchProgress = Pair(index + 1, toDownload.size)
                val res = app.apiClient.downloadMediaToGallery(item.fileId, item.filename, item.mimeType)
                if (res.isSuccess) successCount++
            }
            isBatchDownloading = false
            batchProgress = null
            HapticHelper.vibrateSuccess(context)
            Toast.makeText(context, "Saved $successCount files to device gallery", Toast.LENGTH_SHORT).show()
            isSelectionMode = false
            selectedFileIds = emptySet()
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
            Toast.makeText(context, "Updated ${ids.size} favorites", Toast.LENGTH_SHORT).show()
            isSelectionMode = false
            selectedFileIds = emptySet()
            loadData()
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
            Toast.makeText(context, "Moved ${ids.size} items to trash", Toast.LENGTH_SHORT).show()
            isSelectionMode = false
            selectedFileIds = emptySet()
            loadData()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralCanvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Quiet, persistent sync state affordance & controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedFileIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextPrimary)
                            }
                            Text(
                                text = "${selectedFileIds.size} SELECTED",
                                style = MnemosType.Label11,
                                color = AccentAmber
                            )
                        }

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
                                style = MnemosType.BodySmall13,
                                color = AccentAmber
                            )
                        }
                    } else {
                        // Title + Quiet Sync Affordance (Persistent top bar)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "MNEMOS",
                                    style = MnemosType.Label11,
                                    color = TextPrimary
                                )

                                // Quiet sync affordance (never loud, weight + type)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .background(NeutralSurface, RoundedCornerShape(4.dp))
                                        .border(1.dp, NeutralHairline, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(if (isLoading) StatusSyncing else AccentAmber, CircleShape)
                                    )
                                    Text(
                                        text = if (isLoading) "Syncing…" else "Up to date",
                                        style = MnemosType.Mono11,
                                        color = if (isLoading) StatusSyncing else TextSecondary
                                    )
                                }
                            }

                            Text(
                                text = "${mediaList.size} items" + when {
                                    selectedDeviceFilter == "other_devices" -> " • Other Nodes"
                                    selectedDeviceFilter.isNotEmpty() -> {
                                        val devName = devices.find { it.id == selectedDeviceFilter }?.name ?: "Filtered"
                                        " • $devName"
                                    }
                                    else -> ""
                                },
                                style = MnemosType.Mono11,
                                color = TextMuted
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TextButton(onClick = {
                                HapticHelper.performClick(view)
                                isSelectionMode = true
                            }) {
                                Text("Select", style = MnemosType.BodySmall13, color = AccentAmber)
                            }

                            IconButton(onClick = {
                                HapticHelper.performClick(view)
                                showSortSheet = true
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort Options",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = {
                                HapticHelper.performSelection(view)
                                val nextCols = if (columns >= 5) 2 else columns + 1
                                app.preferenceStore.setGridColumns(nextCols)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ViewModule,
                                    contentDescription = "Grid Density",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = {
                                HapticHelper.performClick(view)
                                loadData()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Filter Row 1: Media Types (Restrained hairline pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "all" to "ALL",
                    "photos" to "PHOTOS",
                    "videos" to "VIDEOS",
                    "favorites" to "FAVORITES"
                ).forEach { (key, label) ->
                    val isSelected = selectedTypeFilter == key
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) AccentAmber.copy(alpha = 0.15f) else NeutralSurface,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) AccentAmber else NeutralHairline,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                HapticHelper.performClick(view)
                                selectedTypeFilter = key
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            style = MnemosType.Label11,
                            color = if (isSelected) AccentAmber else TextSecondary
                        )
                    }
                }
            }

            // Filter Row 2: Node Sources
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAllSelected = selectedDeviceFilter.isEmpty()
                Box(
                    modifier = Modifier
                        .background(
                            if (isAllSelected) AccentAmber.copy(alpha = 0.15f) else NeutralSurface,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isAllSelected) AccentAmber else NeutralHairline,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            HapticHelper.performClick(view)
                            selectedDeviceFilter = ""
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ALL NODES",
                        style = MnemosType.Label11,
                        color = if (isAllSelected) AccentAmber else TextSecondary
                    )
                }

                val isOtherSelected = selectedDeviceFilter == "other_devices"
                Box(
                    modifier = Modifier
                        .background(
                            if (isOtherSelected) AccentAmber.copy(alpha = 0.15f) else NeutralSurface,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isOtherSelected) AccentAmber else NeutralHairline,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            HapticHelper.performClick(view)
                            selectedDeviceFilter = if (selectedDeviceFilter == "other_devices") "" else "other_devices"
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "OTHER NODES",
                        style = MnemosType.Label11,
                        color = if (isOtherSelected) AccentAmber else TextSecondary
                    )
                }

                devices.forEach { dev ->
                    val isSelected = selectedDeviceFilter == dev.id
                    val isThisPhone = dev.id == currentDeviceId
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) AccentAmber.copy(alpha = 0.15f) else NeutralSurface,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) AccentAmber else NeutralHairline,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                HapticHelper.performClick(view)
                                selectedDeviceFilter = if (isSelected) "" else dev.id
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = (if (isThisPhone) "THIS: " else "") + dev.name.uppercase(),
                            style = MnemosType.Label11,
                            color = if (isSelected) AccentAmber else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // The Hero Grid (Full-bleed, 1dp hairline gutter, pinch density)
            if (isLoading && mediaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentAmber, strokeWidth = 2.dp)
                }
            } else if (mediaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("VAULT EMPTY", style = MnemosType.Label11, color = TextMuted)
                        Text(
                            text = if (selectedDeviceFilter.isNotEmpty()) "No files match filter" else "Connect or sync camera roll",
                            style = MnemosType.BodySmall13,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(0.dp), // Full-bleed
                    horizontalArrangement = Arrangement.spacedBy(1.dp), // 1dp hairline gutter
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom > 1.30f && columns > 2) {
                                    HapticHelper.performSelection(view)
                                    app.preferenceStore.setGridColumns(columns - 1)
                                } else if (zoom < 0.70f && columns < 5) {
                                    HapticHelper.performSelection(view)
                                    app.preferenceStore.setGridColumns(columns + 1)
                                }
                            }
                        }
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

        // Bottom Selection Action Bar (Never collides with photo content, min 48dp touch targets)
        AnimatedVisibility(
            visible = isSelectionMode && selectedFileIds.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeutralSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, NeutralHairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedFileIds.size} SELECTED",
                        style = MnemosType.Mono11,
                        color = AccentAmber
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Download Primary CTA
                        Button(
                            onClick = { batchDownloadSelected() },
                            enabled = !isBatchDownloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentAmber,
                                contentColor = NeutralCanvas
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            if (isBatchDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = NeutralCanvas
                                )
                                batchProgress?.let { (done, total) ->
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("$done/$total", style = MnemosType.Mono11)
                                }
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download", style = MnemosType.BodySmall13.copy(fontWeight = FontWeight.Medium))
                            }
                        }

                        // Favorite Toggle Action
                        IconButton(
                            onClick = { batchFavoriteSelected(true) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = AccentAmber, modifier = Modifier.size(20.dp))
                        }

                        // Trash Action
                        IconButton(
                            onClick = { batchDeleteSelected() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Sort Options Modal Bottom Sheet
        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = NeutralSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SORT BY",
                        style = MnemosType.Label11,
                        color = TextSecondary
                    )

                    SortOption.entries.forEach { option ->
                        val isSelected = currentSort == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    HapticHelper.performClick(view)
                                    currentSort = option
                                    showSortSheet = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                style = MnemosType.Body15,
                                color = if (isSelected) AccentAmber else TextPrimary
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(AccentAmber, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mnemos Photo Tile:
 * - Zero shimmer / gray skeleton
 * - Instant blur-up to 720p HD
 * - Selected state = 4dp amber inset border + scale(0.96), zero checkmark icon clutter
 * - Video duration in monospace 11sp on subtle charcoal pill
 */
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

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "tileScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isSelected) Modifier.border(4.dp, AccentAmber) else Modifier
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

        // Video duration badge in monospace
        if (media.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color(0xCC121212), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    media.durationMs?.let { ms ->
                        val seconds = (ms / 1000) % 60
                        val minutes = (ms / (1000 * 60))
                        Text(
                            text = String.format("%d:%02d", minutes, seconds),
                            color = Color.White,
                            style = MnemosType.Mono11
                        )
                    }
                }
            }
        }

        // Uploaded node badge (compact monospace)
        media.uploadedByDeviceName?.let { devName ->
            if (devName.isNotBlank() && !isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .background(Color(0xB3121212), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = devName.uppercase(),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MnemosType.Mono11.copy(fontSize = 8.sp)
                    )
                }
            }
        }

        // Favorite Indicator
        if (media.favorite && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(Color(0x99121212), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = AccentAmber,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}
