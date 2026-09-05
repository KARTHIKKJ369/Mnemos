package com.photovault.ui.screens.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.photovault.PhotoVaultApplication
import com.photovault.ui.components.ButtonVariant
import com.photovault.ui.components.FrameHeroHeader
import com.photovault.ui.components.HapticHelper
import com.photovault.ui.components.IconTintVariant
import com.photovault.ui.components.MnemosButton
import com.photovault.ui.components.MnemosCard
import com.photovault.ui.components.MnemosRowCard
import com.photovault.ui.components.MnemosSwitch
import com.photovault.ui.components.RedDotIndicator
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameGray300
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameGray900
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.SignalRed
import com.photovault.ui.theme.SignalRedSubtle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun BackupScreen() {
    val app = PhotoVaultApplication.instance
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val autoBackup by app.preferenceStore.autoBackup.collectAsState()

    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var currentUploadIndex by remember { mutableIntStateOf(0) }
    var totalUploadCount by remember { mutableIntStateOf(0) }
    var uploadStatusMessage by remember { mutableStateOf("READY // IDLE") }

    // Android System Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isUploading = true
            totalUploadCount = uris.size
            currentUploadIndex = 0

            scope.launch {
                for ((index, uri) in uris.withIndex()) {
                    currentUploadIndex = index + 1
                    uploadStatusMessage = "UPLOADING // $currentUploadIndex OF $totalUploadCount"
                    uploadProgress = 0f

                    val file = copyUriToTempFile(context, uri)
                    if (file != null) {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val result = app.apiClient.uploadFile(file, mimeType) { progress ->
                            uploadProgress = progress
                        }
                        file.delete()
                    }
                }
                isUploading = false
                uploadStatusMessage = "SUCCESS // $totalUploadCount ITEMS SYNCED"
                HapticHelper.vibrateSuccess(context)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FrameBlack)
            .statusBarsPadding()
    ) {
        // FRAME Hero Header
        FrameHeroHeader(
            title = "SYNC",
            subtitle = "BACKGROUND CAMERA ROLL BACKUP"
        )

        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Auto-Backup Row Card
            MnemosRowCard(
                title = "AUTO-SYNC CAMERA ROLL",
                subtitle = "Automatically stream new media in background when server is reachable",
                icon = Icons.Default.Sync,
                iconTintVariant = IconTintVariant.IRIS,
                trailingContent = {
                    MnemosSwitch(
                        checked = autoBackup,
                        onCheckedChange = {
                            HapticHelper.performClick(view)
                            app.preferenceStore.setAutoBackup(it)
                        }
                    )
                }
            )

            // Manual Upload Container Card
            MnemosCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SignalRedSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = SignalRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.size(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MANUAL UPLOAD",
                                style = MnemosType.CardTitle15,
                                color = FrameWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Select photos or videos from local storage to sync immediately",
                                style = MnemosType.BodySecondary13,
                                color = FrameGray300
                            )
                        }
                    }

                    if (isUploading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { uploadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = SignalRed,
                                trackColor = FrameGray900
                            )
                            Text(
                                text = uploadStatusMessage,
                                style = MnemosType.Mono11,
                                color = FrameWhite
                            )
                        }
                    } else {
                        MnemosButton(
                            text = "SELECT PHOTOS & VIDEOS",
                            onClick = {
                                HapticHelper.performClick(view)
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.PRIMARY,
                            icon = Icons.Default.CloudUpload
                        )

                        Text(
                            text = "STATUS // $uploadStatusMessage",
                            style = MnemosType.Mono11,
                            color = FrameGray500
                        )
                    }
                }
            }
        }
    }
}

private suspend fun copyUriToTempFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        val fileName = getFileName(context, uri) ?: "upload_${System.currentTimeMillis()}.jpg"
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (_: Exception) {
        null
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
