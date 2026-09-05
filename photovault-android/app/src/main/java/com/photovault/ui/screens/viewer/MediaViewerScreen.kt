package com.photovault.ui.screens.viewer

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.photovault.data.model.MediaItem
import com.photovault.ui.components.ButtonVariant
import com.photovault.ui.components.ExoVideoPlayer
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.MnemosButton
import com.photovault.ui.components.ProgressiveMediaImage
import com.photovault.ui.components.RedDotIndicator
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameGray300
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameSurface
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.SignalRed
import com.photovault.ui.theme.SignalRedSubtle
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    initialFileId: String,
    initialMediaList: List<MediaItem> = emptyList(),
    onClose: () -> Unit
) {
    BackHandler(enabled = true) {
        onClose()
    }

    val app = PhotoVaultApplication.instance
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var mediaList by remember(initialMediaList) {
        mutableStateOf(if (initialMediaList.isNotEmpty()) initialMediaList else emptyList())
    }
    var showInfoSheet by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(initialMediaList) {
        if (initialMediaList.isEmpty()) {
            val result = app.apiClient.fetchMedia()
            result.onSuccess {
                mediaList = it
            }
        } else {
            mediaList = initialMediaList
        }
    }

    val initialIndex = remember(mediaList, initialFileId) {
        val idx = mediaList.indexOfFirst { it.fileId == initialFileId }
        if (idx >= 0) idx else 0
    }

    if (mediaList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FrameBlack)
        )
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaList.size }
    )

    val currentMedia = mediaList.getOrNull(pagerState.currentPage) ?: return
    val filmstripListState = rememberLazyListState()

    LaunchedEffect(pagerState.currentPage) {
        if (!currentMedia.isVideo) {
            filmstripListState.animateScrollToItem(
                (pagerState.currentPage - 2).coerceAtLeast(0)
            )
        }
    }

    fun downloadCurrent() {
        if (isDownloading) return
        isDownloading = true
        HapticHelper.performClick(view)
        scope.launch {
            val res = app.apiClient.downloadMediaToGallery(
                fileId = currentMedia.fileId,
                filename = currentMedia.filename,
                mimeType = currentMedia.mimeType
            )
            isDownloading = false
            res.onSuccess {
                HapticHelper.vibrateSuccess(context)
                Toast.makeText(context, "Saved ${currentMedia.filename} to device gallery", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Download failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrameBlack)
    ) {
        // Main Media Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                        }
                    )
                }
        ) { page ->
            val item = mediaList[page]
            if (item.isVideo) {
                val videoUrl = app.apiClient.getOriginalUrl(item.fileId)
                ExoVideoPlayer(
                    videoUrl = videoUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ProgressiveMediaImage(
                    media = item,
                    modifier = Modifier.fillMaxSize(),
                    isZoomable = true,
                    onTap = {
                        controlsVisible = !controlsVisible
                    }
                )
            }
        }

        // Top Gradient Scrim & Floating Header
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(FrameBlack.copy(alpha = 0.88f), FrameBlack.copy(alpha = 0.40f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(FrameSurface.copy(alpha = 0.85f))
                            .border(1.dp, FrameBorder, CircleShape)
                            .clickable {
                                HapticHelper.performClick(view)
                                onClose()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FrameWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Center: Counter & Node Capsule
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(FrameSurface.copy(alpha = 0.85f))
                            .border(1.dp, FrameBorder, RoundedCornerShape(20.dp))
                            .clickable {
                                HapticHelper.performClick(view)
                                showInfoSheet = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RedDotIndicator(size = 5.dp)
                            Text(
                                text = String.format("%02d / %02d", pagerState.currentPage + 1, mediaList.size),
                                style = MnemosType.Mono12.copy(fontWeight = FontWeight.Bold),
                                color = FrameWhite
                            )
                        }
                    }

                    // Actions Cluster Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(FrameSurface.copy(alpha = 0.85f))
                            .border(1.dp, FrameBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Download Button
                        IconButton(
                            onClick = { downloadCurrent() },
                            enabled = !isDownloading,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = SignalRed
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download to Device",
                                    tint = FrameWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Favorite Button
                        IconButton(
                            onClick = {
                                HapticHelper.performClick(view)
                                val newFav = !currentMedia.favorite
                                scope.launch {
                                    app.apiClient.setFavorite(currentMedia.fileId, newFav)
                                    mediaList = mediaList.map {
                                        if (it.fileId == currentMedia.fileId) it.copy(favorite = newFav) else it
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (currentMedia.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (currentMedia.favorite) SignalRed else FrameGray300,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Info Sheet Button
                        IconButton(
                            onClick = {
                                HapticHelper.performClick(view)
                                showInfoSheet = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = FrameGray300,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Delete Button
                        IconButton(
                            onClick = {
                                HapticHelper.vibrateWarning(context)
                                scope.launch {
                                    app.apiClient.deleteMedia(currentMedia.fileId)
                                    val remaining = mediaList.filter { it.fileId != currentMedia.fileId }
                                    if (remaining.isEmpty()) {
                                        onClose()
                                    } else {
                                        mediaList = remaining
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = SignalRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Gradient Scrim & Floating Filmstrip Carousel
        if (!currentMedia.isVideo && mediaList.size > 1) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, FrameBlack.copy(alpha = 0.50f), FrameBlack.copy(alpha = 0.90f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp, top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FrameSurface.copy(alpha = 0.92f))
                            .border(1.dp, FrameBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        LazyRow(
                            state = filmstripListState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(mediaList, key = { _, item -> item.fileId }) { index, item ->
                                val isSelected = pagerState.currentPage == index
                                val thumbUrl = remember(item.fileId) {
                                    if (item.thumbnailAvailable) app.apiClient.getThumbnailUrl(item.fileId)
                                    else app.apiClient.getOriginalUrl(item.fileId)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 48.dp else 38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            HapticHelper.performClick(view)
                                            scope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                        .then(
                                            if (isSelected) Modifier
                                                .border(2.5.dp, SignalRed, RoundedCornerShape(6.dp))
                                            else Modifier.border(0.5.dp, FrameBorder, RoundedCornerShape(6.dp))
                                        )
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbUrl)
                                            .crossfade(100)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Metadata Info Sheet
        if (showInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = { showInfoSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = FrameSurface,
                scrimColor = FrameBlack.copy(alpha = 0.75f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RedDotIndicator(size = 6.dp)
                            Text(
                                text = "METADATA // SPECS",
                                style = MnemosType.Headline28.copy(fontSize = 18.sp),
                                color = FrameWhite
                            )
                        }
                        IconButton(onClick = { showInfoSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = FrameGray500)
                        }
                    }

                    // Download to device button inside sheet
                    MnemosButton(
                        text = "DOWNLOAD RAW ORIGINAL",
                        onClick = {
                            showInfoSheet = false
                            downloadCurrent()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.PRIMARY,
                        icon = Icons.Default.Download
                    )

                    // Metadata rows
                    MetadataRow(
                        icon = Icons.Default.Devices,
                        label = "UPLOADED NODE",
                        value = "${currentMedia.uploadedByDeviceName ?: "SERVER ADMIN"} // ${currentMedia.uploadedByDeviceType?.uppercase() ?: "NODE"}"
                    )

                    MetadataRow(
                        icon = Icons.Default.Schedule,
                        label = "TIMESTAMP",
                        value = currentMedia.takenAt ?: currentMedia.uploadedAt
                    )

                    MetadataRow(
                        icon = Icons.Default.Camera,
                        label = "FILE SPEC",
                        value = "${currentMedia.filename} // ${(currentMedia.sizeBytes / 1024.0 / 1024.0).format(2)} MB"
                    )

                    if (currentMedia.width != null && currentMedia.height != null) {
                        MetadataRow(
                            icon = Icons.Default.Camera,
                            label = "RESOLUTION",
                            value = "${currentMedia.width} × ${currentMedia.height}"
                        )
                    }

                    if (currentMedia.cameraMake != null || currentMedia.cameraModel != null) {
                        MetadataRow(
                            icon = Icons.Default.Camera,
                            label = "CAMERA",
                            value = "${currentMedia.cameraMake ?: ""} ${currentMedia.cameraModel ?: ""}".trim().uppercase()
                        )
                    }

                    if (currentMedia.gpsLat != null && currentMedia.gpsLon != null) {
                        MetadataRow(
                            icon = Icons.Default.Map,
                            label = "GPS",
                            value = "${currentMedia.gpsLat.format(4)}, ${currentMedia.gpsLon.format(4)}"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(SignalRedSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SignalRed, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(text = label, style = MnemosType.Mono11.copy(fontSize = 10.sp), color = FrameGray500)
            Text(
                text = value,
                style = MnemosType.Mono12,
                color = FrameWhite
            )
        }
    }
}

private fun Double.format(digits: Int) = String.format("%.${digits}f", this)
