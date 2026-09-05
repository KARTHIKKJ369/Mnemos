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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewModule
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
import androidx.compose.ui.draw.clip
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
import com.photovault.ui.components.ButtonVariant
import com.photovault.ui.components.FrameHeroHeader
import com.photovault.ui.components.FrameSearchBar
import com.photovault.ui.components.FrameSegmentedControl
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.MnemosButton
import com.photovault.ui.components.NodeBadge
import com.photovault.ui.components.RedDotIndicator
import com.photovault.ui.components.TimelineSectionHeader
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameBorderLight
import com.photovault.ui.theme.FrameGray100
import com.photovault.ui.theme.FrameGray300
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameGray700
import com.photovault.ui.theme.FrameSurface
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.SignalRed
import com.photovault.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.launch

enum class SortOption(val label: String, val sortField: String, val sortOrder: String) {
    NEWEST("Newest First", "taken_at", "desc"),
    OLDEST("Oldest First", "taken_at", "asc"),
    LARGEST("Largest Size", "size_bytes", "desc"),
    SMALLEST("Smallest Size", "size_bytes", "asc"),
    NAME_AZ("Name (A to Z)", "filename", "asc"),
    NAME_ZA("Name (Z to A)", "filename", "desc")
}

data class TimelineGroup(
    val monthYear: String,
    val items: List<MediaItem>,
    val startIndex: Int
)

fun parseMonthYear(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "Recent"
    return try {
        val parts = dateStr.substringBefore("T").split("-")
        if (parts.size >= 2) {
            val year = parts[0]
            val monthNum = parts[1].toIntOrNull() ?: 1
            val monthNames = arrayOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            val month = monthNames.getOrElse(monthNum - 1) { "Month" }
            "$month $year"
        } else {
            "Recent"
        }
    } catch (e: Exception) {
        "Recent"
    }
}

private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

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
    val gridState = rememberLazyGridState()

    val columns by app.preferenceStore.gridColumns.collectAsState()
    val currentDeviceId by app.preferenceStore.deviceId.collectAsState()

    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Search & Filter
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("all") }
    val selectedDeviceFilter by remember { mutableStateOf(initialDeviceId ?: "") }
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

            val result = app.apiClient.fetchMedia(
                query = searchQuery.trim(),
                mimeType = mimeFilter,
                favoriteOnly = favoriteOnly,
                deviceId = selectedDeviceFilter,
                sort = currentSort.sortField,
                order = currentSort.sortOrder
            )
            isLoading = false
            result.onSuccess {
                mediaList = it
            }
        }
    }

    LaunchedEffect(selectedTypeFilter, selectedDeviceFilter, currentSort, searchQuery) {
        loadData()
    }

    // Calculate total vault storage used in GB
    val totalBytes = remember(mediaList) { mediaList.sumOf { it.sizeBytes } }
    val totalGB = remember(totalBytes) { (totalBytes / (1024.0 * 1024.0 * 1024.0)).format(1) }

    // Timeline grouping by Month & Year
    val timelineGroups = remember(mediaList, currentSort) {
        if (currentSort == SortOption.NEWEST || currentSort == SortOption.OLDEST) {
            val groups = mutableListOf<TimelineGroup>()
            var currentIndex = 0
            val groupedMap = mediaList.groupBy { parseMonthYear(it.displayDate) }
            groupedMap.forEach { (monthYear, items) ->
                groups.add(TimelineGroup(monthYear, items, currentIndex))
                currentIndex += 1 + items.size
            }
            groups
        } else {
            listOf(TimelineGroup("All Media", mediaList, 0))
        }
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
            .background(FrameBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: FRAME // OS Header (🔴 135H 56M // 05 TITLES style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedFileIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = FrameWhite)
                            }
                            Text(
                                text = "${selectedFileIds.size} SELECTED",
                                style = MnemosType.Headline28.copy(fontSize = 18.sp),
                                color = FrameWhite
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
                                text = if (selectedFileIds.size == mediaList.size) "DESELECT ALL" else "SELECT ALL",
                                style = MnemosType.Mono12.copy(fontWeight = FontWeight.Bold),
                                color = SignalRed
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header Title + Subtitle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                        text = "${mediaList.size} ITEMS // $totalGB GB",
                                        style = MnemosType.Headline28,
                                        color = FrameWhite
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = String.format("%02d NODES // %02d SYNCED // %02d OFFLINE", devices.size, devices.size, 0),
                                    style = MnemosType.Mono11,
                                    color = FrameGray500
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
                                    Text("SELECT", style = MnemosType.Mono11.copy(fontWeight = FontWeight.Bold), color = FrameWhite)
                                }

                                IconButton(onClick = {
                                    HapticHelper.performClick(view)
                                    showSortSheet = true
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort Options",
                                        tint = FrameGray300,
                                        modifier = Modifier.size(18.dp)
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
                                        tint = FrameGray300,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(onClick = {
                                    HapticHelper.performClick(view)
                                    loadData()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = FrameGray300,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Capsule Segmented Control: [ ALL | PHOTOS | VIDEOS | FAVORITES ]
                        FrameSegmentedControl(
                            items = listOf(
                                "all" to "ALL",
                                "photos" to "PHOTOS",
                                "videos" to "VIDEOS",
                                "favorites" to "FAVORITES"
                            ),
                            selectedKey = selectedTypeFilter,
                            onItemSelected = {
                                HapticHelper.performClick(view)
                                selectedTypeFilter = it
                            }
                        )

                        // Minimal Frame Search Bar
                        FrameSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { loadData() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // The Timeline Grid
            if (isLoading && mediaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SignalRed, strokeWidth = 2.dp)
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
                        Text("VAULT EMPTY", style = MnemosType.Headline28.copy(fontSize = 18.sp), color = FrameGray500)
                        Text(
                            text = if (searchQuery.isNotEmpty()) "NO MATCHES FOR \"$searchQuery\"" else "CONNECT NODES OR SYNC CAMERA ROLL",
                            style = MnemosType.Mono11,
                            color = FrameGray500
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(0.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
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
                        timelineGroups.forEach { group ->
                            if (group.monthYear != "All Media") {
                                item(
                                    key = "header_${group.monthYear}",
                                    span = { GridItemSpan(columns) }
                                ) {
                                    TimelineSectionHeader(
                                        title = group.monthYear,
                                        itemCount = group.items.size
                                    )
                                }
                            }

                            items(group.items, key = { it.fileId }) { item ->
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

                    // Fast Date Scrubber on the Right Edge
                    if (timelineGroups.size > 1 && !isSelectionMode) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(FrameBlack.copy(alpha = 0.88f))
                                .border(0.5.dp, FrameBorder, RoundedCornerShape(8.dp))
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            timelineGroups.take(8).forEach { group ->
                                val label = group.monthYear.take(3).uppercase()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .clickable {
                                            HapticHelper.performClick(view)
                                            scope.launch {
                                                gridState.animateScrollToItem(group.startIndex)
                                            }
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MnemosType.Mono11.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = FrameGray300
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Selection Action Bar
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(FrameSurface)
                    .border(1.dp, FrameBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedFileIds.size} SELECTED",
                        style = MnemosType.Mono12.copy(fontWeight = FontWeight.Bold),
                        color = FrameWhite
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Download Primary CTA (Pure White)
                        MnemosButton(
                            text = if (isBatchDownloading) {
                                batchProgress?.let { "${it.first}/${it.second}" } ?: "SAVING…"
                            } else "DOWNLOAD",
                            onClick = { batchDownloadSelected() },
                            variant = ButtonVariant.PRIMARY,
                            icon = if (!isBatchDownloading) Icons.Default.CloudDownload else null,
                            isLoading = isBatchDownloading
                        )

                        // Favorite Action
                        IconButton(
                            onClick = { batchFavoriteSelected(true) },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = FrameWhite, modifier = Modifier.size(18.dp))
                        }

                        // Trash Action (Signal Red)
                        IconButton(
                            onClick = { batchDeleteSelected() },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SignalRed, modifier = Modifier.size(18.dp))
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
                containerColor = FrameSurface,
                scrimColor = FrameBlack.copy(alpha = 0.75f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SORT BY // ORDER",
                        style = MnemosType.Mono11,
                        color = FrameGray500
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
                                text = option.label.uppercase(),
                                style = MnemosType.CardTitle15,
                                color = if (isSelected) FrameWhite else FrameGray300
                            )
                            if (isSelected) {
                                RedDotIndicator(size = 6.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * FRAME Photo Tile:
 * - 4dp Signal Red selection border
 * - Top-left badge with node source (e.g. `NODE: MAC`) in clean monospace box
 * - Video duration badge
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
        targetValue = if (isSelected) 0.95f else 1f,
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
                if (isSelected) Modifier.border(4.dp, SignalRed) else Modifier
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(150)
                .build(),
            contentDescription = media.filename,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier.fillMaxSize()
        )

        // Top-Left: Origin Device Badge in crisp black box
        media.uploadedByDeviceName?.let { devName ->
            if (devName.isNotBlank() && !isSelectionMode) {
                NodeBadge(
                    deviceName = devName,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )
            }
        }

        // Top-Right: Favorite Indicator
        if (media.favorite && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(FrameBlack.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = SignalRed,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        // Bottom-End: Video duration badge in monospace
        if (media.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FrameBlack.copy(alpha = 0.92f))
                    .border(0.5.dp, FrameBorderLight, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = FrameWhite,
                        modifier = Modifier.size(10.dp)
                    )
                    media.durationMs?.let { ms ->
                        val seconds = (ms / 1000) % 60
                        val minutes = (ms / (1000 * 60))
                        Text(
                            text = String.format("%d:%02d", minutes, seconds),
                            color = FrameWhite,
                            style = MnemosType.Mono11.copy(fontSize = 9.sp)
                        )
                    }
                }
            }
        }
    }
}
