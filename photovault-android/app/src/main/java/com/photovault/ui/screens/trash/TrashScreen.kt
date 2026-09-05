package com.photovault.ui.screens.trash

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.PhotoVaultApplication
import com.photovault.data.model.MediaItem
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.LiquidGlassCard
import com.photovault.ui.components.liquidGlass
import com.photovault.ui.screens.gallery.GalleryTile
import com.photovault.ui.theme.AccentGold
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.DarkSurfaceVariant
import com.photovault.ui.theme.DangerRed
import com.photovault.ui.theme.EmeraldGreen
import com.photovault.ui.theme.TextMuted
import com.photovault.ui.theme.TextPrimary
import com.photovault.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    onMediaSelected: (fileId: String) -> Unit
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
            .background(DarkBackground)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Column {
                            Text(
                                text = "Trash",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${trashList.size} deleted items",
                                fontSize = 11.sp,
                                color = TextMuted
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
                                    tint = DangerRed
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
                                    tint = if (isSelectionMode) AccentGold else TextSecondary
                                )
                            }
                        }

                        // Refresh
                        IconButton(onClick = {
                            HapticHelper.performClick(view)
                            loadTrash()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                        }
                    }
                }
            }

            if (isLoading && trashList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentGold)
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
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("Trash is Empty", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Deleted photos and videos will appear here", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = 1.dp,
                        end = 1.dp,
                        top = 6.dp,
                        bottom = if (isSelectionMode) 100.dp else 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp),
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
                                    onMediaSelected(item.fileId)
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
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            LiquidGlassCard(
                glowAccent = EmeraldGreen,
                backgroundColor = Color(0xF2161A26),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Restore Button
                    Button(
                        onClick = { restoreSelected() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Restore (${selectedFileIds.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Delete Forever Button
                    Button(
                        onClick = { permanentDeleteSelected() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Delete Forever", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Empty Trash Confirmation Dialog
        if (showEmptyTrashDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashDialog = false },
                title = {
                    Text("Empty Entire Trash?", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "All ${trashList.size} items in the trash will be permanently deleted from the vault server. This action cannot be undone.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { emptyEntireTrash() },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Empty Trash Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = DarkSurfaceVariant
            )
        }
    }
}
