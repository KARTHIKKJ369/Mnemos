package com.photovault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.MediaItem
import kotlinx.coroutines.launch

@Composable
fun ProgressiveMediaImage(
    media: MediaItem,
    modifier: Modifier = Modifier,
    isZoomable: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val client = PhotoVaultApplication.instance.apiClient
    val scope = rememberCoroutineScope()

    var isOriginalLoaded by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val thumbnailUrl = remember(media.fileId) {
        if (media.thumbnailAvailable) client.getThumbnailUrl(media.fileId) else client.getOriginalUrl(media.fileId)
    }
    val originalUrl = remember(media.fileId) {
        client.getOriginalUrl(media.fileId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isZoomable) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    scope.launch {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                            offset = Offset.Zero
                                        }
                                    }
                                },
                                onTap = { onTap() }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 6f)
                                scale = newScale
                                if (newScale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Stage 1: Fast low-latency thumbnail backdrop (loads instantly)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(150)
                .precision(Precision.INEXACT)
                .build(),
            contentDescription = media.filename,
            contentScale = contentScale,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        // Stage 2: High-resolution full original loaded with crystal clarity
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(originalUrl)
                .crossfade(400)
                .precision(Precision.EXACT)
                .allowHardware(true)
                .build(),
            contentDescription = media.filename,
            contentScale = contentScale,
            filterQuality = FilterQuality.High,
            onSuccess = {
                isOriginalLoaded = true
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
