package com.photovault.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.MediaItem
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.DarkSurfaceOverlay

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
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
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
        // Stage 1: Fast low-latency thumbnail backdrop (loads immediately)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(200)
                .build(),
            contentDescription = media.filename,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        // Stage 2: High-resolution full original loaded in background and cross-faded smoothly
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(originalUrl)
                .crossfade(600)
                .build(),
            contentDescription = media.filename,
            contentScale = contentScale,
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
