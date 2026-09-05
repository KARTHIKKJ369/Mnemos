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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    // Intercept hardware/gesture back press to close the viewer
    BackHandler(enabled = true) {
        onClose()
    }

    val app = PhotoVaultApplication.instance
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

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
                Toast.makeText(context, "Saved ${currentMedia.filename} to device gallery!", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Download failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
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

        // Top Floating Action Bar (safe padding with statusBarsPadding)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Filename & Device pill
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentMedia.filename,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        currentMedia.uploadedByDeviceName?.let { devName ->
                            if (devName.isNotBlank()) {
                                Text(
                                    text = "From $devName",
                                    color = AccentGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Actions Pill (Download, Favorite, Info, Delete)
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download Button
                    IconButton(
                        onClick = { downloadCurrent() },
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AccentGold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download to Device",
                                tint = AccentGold,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Favorite Button
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
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Info Sheet Button
                    IconButton(onClick = {
                        HapticHelper.performClick(view)
                        showInfoSheet = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Delete Button
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
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        // Bottom Filmstrip Carousel: Only displayed for photos to never block video playback controls!
        if (!currentMedia.isVideo && mediaList.size > 1) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                    .then(
                                        if (isSelected) Modifier
                                            .background(AccentGold, RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                        else Modifier
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

        // Metadata Info Sheet
        if (showInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = { showInfoSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = DarkSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Media Details",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(onClick = { showInfoSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    // Download to device button inside sheet
                    Button(
                        onClick = {
                            showInfoSheet = false
                            downloadCurrent()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text("Download Original to Gallery", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Metadata rows
                    MetadataRow(
                        icon = Icons.Default.Devices,
                        label = "Uploaded From",
                        value = "${currentMedia.uploadedByDeviceName ?: "Server Admin"} (${currentMedia.uploadedByDeviceType?.uppercase() ?: "NODE"})"
                    )

                    MetadataRow(
                        icon = Icons.Default.Schedule,
                        label = "Date",
                        value = currentMedia.takenAt ?: currentMedia.uploadedAt
                    )

                    MetadataRow(
                        icon = Icons.Default.Camera,
                        label = "File Specs",
                        value = "${currentMedia.filename} (${(currentMedia.sizeBytes / 1024.0 / 1024.0).format(2)} MB)"
                    )

                    if (currentMedia.width != null && currentMedia.height != null) {
                        MetadataRow(
                            icon = Icons.Default.Camera,
                            label = "Resolution",
                            value = "${currentMedia.width} × ${currentMedia.height}"
                        )
                    }

                    if (currentMedia.cameraMake != null || currentMedia.cameraModel != null) {
                        MetadataRow(
                            icon = Icons.Default.Camera,
                            label = "Camera",
                            value = "${currentMedia.cameraMake ?: ""} ${currentMedia.cameraModel ?: ""}".trim()
                        )
                    }

                    if (currentMedia.gpsLat != null && currentMedia.gpsLon != null) {
                        MetadataRow(
                            icon = Icons.Default.Map,
                            label = "GPS Coordinates",
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
        Icon(icon, contentDescription = null, tint = AccentGold, modifier = Modifier.size(20.dp))
        Column {
            Text(text = label, color = TextMuted, fontSize = 11.sp)
            Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun Double.format(digits: Int) = String.format("%.${digits}f", this)
