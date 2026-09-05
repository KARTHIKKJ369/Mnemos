package com.photovault.ui.screens.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.MediaItem
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.LiquidGlassPill
import com.photovault.ui.components.liquidGlass
import com.photovault.ui.screens.gallery.GalleryTile
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextPrimary
import com.photovault.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onMediaSelected: (fileId: String) -> Unit
) {
    val app = PhotoVaultApplication.instance
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val columns by app.preferenceStore.gridColumns.collectAsState()

    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortDescending by remember { mutableStateOf(true) }

    fun loadMedia() {
        isLoading = true
        scope.launch {
            val result = app.apiClient.fetchMedia(
                sort = "taken_at",
                order = if (sortDescending) "desc" else "asc"
            )
            isLoading = false
            result.onSuccess {
                mediaList = it
            }
        }
    }

    LaunchedEffect(sortDescending) {
        loadMedia()
    }

    // Group media by Date string (e.g. "YYYY-MM-DD")
    val groupedMedia = remember(mediaList) {
        mediaList.groupBy { item ->
            val rawDate = item.displayDate
            if (rawDate.length >= 10) rawDate.substring(0, 10) else "Unknown Date"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Liquid Glass Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Timeline",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (sortDescending) "Chronological (Newest)" else "Chronological (Oldest)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Sort Toggle
                        IconButton(onClick = {
                            HapticHelper.performSelection(view)
                            sortDescending = !sortDescending
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Toggle Sort Order",
                                tint = AccentGold,
                                modifier = Modifier.size(20.dp)
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
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Refresh
                        IconButton(onClick = {
                            HapticHelper.performClick(view)
                            loadMedia()
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
                    Text("No timeline items found in vault", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = 1.dp,
                        end = 1.dp,
                        top = 4.dp,
                        bottom = 12.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    groupedMedia.forEach { (dateHeader, itemsInDay) ->
                        // Section Header with Liquid Glass capsule
                        item(span = { GridItemSpan(columns) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .liquidGlass(
                                            shape = RoundedCornerShape(14.dp),
                                            backgroundColor = Color(0xCC111522),
                                            borderAlphaTop = 0.20f
                                        )
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = formatDateHeader(dateHeader),
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Media Tiles
                        items(itemsInDay, key = { it.fileId }) { item ->
                            GalleryTile(
                                media = item,
                                onClick = {
                                    HapticHelper.performClick(view)
                                    onMediaSelected(item.fileId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDateHeader(isoDate: String): String {
    return try {
        val parsed = java.time.LocalDate.parse(isoDate)
        val today = java.time.LocalDate.now()
        when {
            parsed == today -> "Today"
            parsed == today.minusDays(1) -> "Yesterday"
            parsed.year == today.year -> parsed.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
            else -> parsed.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        }
    } catch (_: Exception) {
        isoDate
    }
}
