package com.photovault.ui.screens.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
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
import com.photovault.ui.components.ExoVideoPlayer
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.ProgressiveMediaImage
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.DarkSurfaceVariant
import com.photovault.ui.theme.DangerRed
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextPrimary
import com.photovault.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    initialFileId: String,
    onClose: () -> Unit
) {
    val app = PhotoVaultApplication.instance
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showInfoSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = app.apiClient.fetchMedia()
        result.onSuccess {
            mediaList = it
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
                .background(DarkBackground)
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
        filmstripListState.animateScrollToItem(
            (pagerState.currentPage - 2).coerceAtLeast(0)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Main Media Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
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
                    isZoomable = true
                )
            }
        }

        // Top Floating Pill Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = {
                    HapticHelper.performClick(view)
                    onClose()
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Filename pill
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = currentMedia.filename,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            // Actions Pill (Favorite, Info, Delete)
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    HapticHelper.performClick(view)
                    val newFav = !currentMedia.favorite
                    scope.launch {
                        app.apiClient.setFavorite(currentMedia.fileId, newFav)
                        mediaList = mediaList.map {
                            if (it.fileId == currentMedia.fileId) it.copy(favorite = newFav) else it
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (currentMedia.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (currentMedia.favorite) DangerRed else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = {
                    HapticHelper.performClick(view)
                    showInfoSheet = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = {
                    HapticHelper.vibrateWarning(context)
                    scope.launch {
                        app.apiClient.deleteMedia(currentMedia.fileId)
                        onClose()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = DangerRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom Filmstrip Carousel
        if (mediaList.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                LazyRow(
                    state = filmstripListState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(mediaList) { index, item ->
                        val isSelected = index == pagerState.currentPage
                        val thumbUrl = if (item.thumbnailAvailable) {
                            app.apiClient.getThumbnailUrl(item.fileId)
                        } else {
                            app.apiClient.getOriginalUrl(item.fileId)
                        }

                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 44.dp else 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    HapticHelper.performSelection(view)
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbUrl)
                                    .crossfade(true)
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

        // Slide-up EXIF Metadata Bottom Sheet
        if (showInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = { showInfoSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = DarkSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Media Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MetadataRow(label = "Filename", value = currentMedia.filename)
                    MetadataRow(label = "MIME Type", value = currentMedia.mimeType)
                    MetadataRow(
                        label = "File Size",
                        value = "${(currentMedia.sizeBytes / (1024 * 1024.0)).format(2)} MB"
                    )

                    currentMedia.takenAt?.let {
                        MetadataRow(
                            icon = Icons.Default.Schedule,
                            label = "Date Taken",
                            value = it
                        )
                    }

                    if (currentMedia.cameraMake != null || currentMedia.cameraModel != null) {
                        MetadataRow(
                            icon = Icons.Default.Camera,
                            label = "Camera",
                            value = listOfNotNull(currentMedia.cameraMake, currentMedia.cameraModel).joinToString(" ")
                        )
                    }

                    if (currentMedia.width != null && currentMedia.height != null) {
                        MetadataRow(
                            label = "Resolution",
                            value = "${currentMedia.width} × ${currentMedia.height} px"
                        )
                    }

                    if (currentMedia.gpsLat != null && currentMedia.gpsLon != null) {
                        MetadataRow(
                            icon = Icons.Default.Map,
                            label = "Coordinates",
                            value = "${currentMedia.gpsLat.format(4)}, ${currentMedia.gpsLon.format(4)}"
                        )
                    }

                    MetadataRow(
                        label = "Source Device",
                        value = currentMedia.uploadedByDeviceName.ifEmpty { "Other Device" }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(text = label, color = TextMuted, fontSize = 13.sp)
        }
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun Double.format(digits: Int) = String.format("%.${digits}f", this)
