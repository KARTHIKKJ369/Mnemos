package com.photovault.ui.screens.trash

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.MediaItem
import com.photovault.ui.components.ButtonVariant
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.MnemosButton
import com.photovault.ui.components.RedDotIndicator
import com.photovault.ui.screens.gallery.GalleryTile
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameGray300
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameSurface
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.SignalRed
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    onMediaSelected: (fileId: String, currentMediaList: List<MediaItem>) -> Unit
) {
    BackHandler(enabled = true) {
        onNavigateBack()
    }

    val app = PhotoVaultApplication.instance
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val columns by app.preferenceStore.gridColumns.collectAsState()

    var trashList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    fun loadTrash() {
        isLoading = true
        scope.launch {
            val result = app.apiClient.fetchMedia(deletedOnly = true)
            isLoading = false
            result.onSuccess {
                trashList = it
            }
        }
    }

    LaunchedEffect(Unit) {
        loadTrash()
    }

    fun restoreSelected() {
        val ids = selectedFileIds.toList()
        if (ids.isEmpty()) return
        HapticHelper.performClick(view)
        scope.launch {
            for (id in ids) {
                app.apiClient.restoreMedia(id)
            }
            HapticHelper.vibrateSuccess(context)
            Toast.makeText(context, "Restored ${ids.size} files to vault", Toast.LENGTH_SHORT).show()
            selectedFileIds = emptySet()
            isSelectionMode = false
            loadTrash()
        }
    }

    fun permanentDeleteSelected() {
        val ids = selectedFileIds.toList()
        if (ids.isEmpty()) return
        HapticHelper.vibrateWarning(context)
        scope.launch {
            for (id in ids) {
                app.apiClient.permanentDeleteMedia(id)
            }
            Toast.makeText(context, "Permanently deleted ${ids.size} files", Toast.LENGTH_SHORT).show()
            selectedFileIds = emptySet()
            isSelectionMode = false
            loadTrash()
        }
    }

    fun emptyEntireTrash() {
        HapticHelper.vibrateWarning(context)
        scope.launch {
            for (item in trashList) {
                app.apiClient.permanentDeleteMedia(item.fileId)
            }
            Toast.makeText(context, "Trash cleared permanently", Toast.LENGTH_SHORT).show()
            trashList = emptyList()
            showEmptyTrashDialog = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrameBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FrameWhite)
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RedDotIndicator(size = 6.dp)
                                Text(
                                    text = "TRASH // REPOSITORY",
                                    style = MnemosType.Headline28.copy(fontSize = 18.sp),
                                    color = FrameWhite
                                )
                            }
                            Text(
                                text = "${trashList.size} DELETED BLOBS",
                                style = MnemosType.Mono11,
                                color = FrameGray500
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (trashList.isNotEmpty()) {
                            // Empty Trash Button
                            IconButton(onClick = { showEmptyTrashDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Empty Trash",
                                    tint = SignalRed
                                )
                            }

                            // Selection Mode Toggle
                            IconButton(onClick = {
                                HapticHelper.performClick(view)
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedFileIds = emptySet()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Select",
                                    tint = if (isSelectionMode) SignalRed else FrameGray300
                                )
                            }
                        }

                        // Refresh
                        IconButton(onClick = {
                            HapticHelper.performClick(view)
                            loadTrash()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = FrameGray300)
                        }
                    }
                }
            }

            if (isLoading && trashList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SignalRed, strokeWidth = 2.dp)
                }
            } else if (trashList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoDelete,
                            contentDescription = null,
                            tint = FrameGray500,
                            modifier = Modifier.size(44.dp)
                        )
                        Text("TRASH EMPTY", color = FrameWhite, style = MnemosType.CardTitle15)
                        Text("DELETED ITEMS APPEAR HERE BEFORE PURGE", color = FrameGray500, style = MnemosType.Mono11)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trashList, key = { it.fileId }) { item ->
                        val isSelected = selectedFileIds.contains(item.fileId)
                        GalleryTile(
                            media = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    HapticHelper.performClick(view)
                                    selectedFileIds = if (isSelected) selectedFileIds - item.fileId else selectedFileIds + item.fileId
                                } else {
                                    HapticHelper.performClick(view)
                                    onMediaSelected(item.fileId, trashList)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Bottom Batch Restore / Permanent Delete Bar
        AnimatedVisibility(
            visible = isSelectionMode && selectedFileIds.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 14.dp, end = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FrameSurface)
                    .border(1.dp, FrameBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Restore Button (White)
                    MnemosButton(
                        text = "RESTORE (${selectedFileIds.size})",
                        onClick = { restoreSelected() },
                        variant = ButtonVariant.PRIMARY,
                        icon = Icons.Default.RestoreFromTrash
                    )

                    // Delete Forever Button (Signal Red)
                    MnemosButton(
                        text = "PURGE FOREVER",
                        onClick = { permanentDeleteSelected() },
                        variant = ButtonVariant.DESTRUCTIVE,
                        icon = Icons.Default.DeleteForever
                    )
                }
            }
        }

        // Empty Trash Confirmation Dialog
        if (showEmptyTrashDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RedDotIndicator(size = 6.dp)
                        Text("PURGE ENTIRE TRASH?", color = FrameWhite, style = MnemosType.CardTitle15)
                    }
                },
                text = {
                    Text(
                        "All ${trashList.size} items will be permanently erased from disk. This operation is non-reversible.",
                        color = FrameGray300,
                        style = MnemosType.BodySecondary13
                    )
                },
                confirmButton = {
                    MnemosButton(
                        text = "ERASE PERMANENTLY",
                        onClick = { emptyEntireTrash() },
                        variant = ButtonVariant.DESTRUCTIVE
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashDialog = false }) {
                        Text("CANCEL", style = MnemosType.Mono11, color = FrameGray500)
                    }
                },
                containerColor = FrameSurface
            )
        }
    }
}
