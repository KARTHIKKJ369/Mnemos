package com.photovault.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    @SerialName("FileID") val fileId: String,
    @SerialName("Filename") val filename: String = "",
    @SerialName("Extension") val extension: String = "",
    @SerialName("MIMEType") val mimeType: String = "",
    @SerialName("SizeBytes") val sizeBytes: Long = 0,
    @SerialName("UploadedAt") val uploadedAt: String = "",
    @SerialName("TakenAt") val takenAt: String? = null,
    @SerialName("Favorite") val favorite: Boolean = false,
    @SerialName("Deleted") val deleted: Boolean = false,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("DurationMS") val durationMs: Long? = null,
    @SerialName("CameraMake") val cameraMake: String? = null,
    @SerialName("CameraModel") val cameraModel: String? = null,
    @SerialName("GPSLat") val gpsLat: Double? = null,
    @SerialName("GPSLon") val gpsLon: Double? = null,
    @SerialName("ThumbnailAvailable") val thumbnailAvailable: Boolean = false,
    @SerialName("PreviewAvailable") val previewAvailable: Boolean = false,
    @SerialName("UploadedByDeviceID") val uploadedByDeviceId: String? = null,
    @SerialName("UploadedByDeviceName") val uploadedByDeviceName: String? = null,
    @SerialName("UploadedByDeviceType") val uploadedByDeviceType: String? = null
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    val displayDate: String
        get() = takenAt ?: uploadedAt
}

@Serializable
data class SearchResponse(
    @SerialName("media") val media: List<MediaItem> = emptyList(),
    @SerialName("limit") val limit: Int = 0,
    @SerialName("offset") val offset: Int = 0
)

@Serializable
data class DeviceItem(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("device_type") val deviceType: String = "android",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null
)

@Serializable
data class DeviceListResponse(
    @SerialName("devices") val devices: List<DeviceItem> = emptyList()
)

@Serializable
data class SyncFileItem(
    @SerialName("file_id") val fileId: String,
    @SerialName("hash") val hash: String,
    @SerialName("filename") val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("thumbnail_available") val thumbnailAvailable: Boolean = false,
    @SerialName("preview_available") val previewAvailable: Boolean = false,
    @SerialName("uploaded_at") val uploadedAt: Long = 0
)

@Serializable
data class SyncDiffResponse(
    @SerialName("files") val files: List<SyncFileItem> = emptyList(),
    @SerialName("deleted_file_ids") val deletedFileIds: List<String> = emptyList(),
    @SerialName("cursor") val cursor: Long = 0
)

@Serializable
data class DeviceRegistrationRequest(
    @SerialName("name") val name: String,
    @SerialName("device_type") val deviceType: String = "android"
)

@Serializable
data class DeviceRegistrationResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("auth_token") val authToken: String
)

@Serializable
data class HealthResponse(
    @SerialName("version") val version: String = "",
    @SerialName("database") val database: String = "",
    @SerialName("blob_storage") val blobStorage: String = "",
    @SerialName("storage_path") val storagePath: String = "",
    @SerialName("total_media") val totalMedia: Int = 0,
    @SerialName("total_photos") val totalPhotos: Int = 0,
    @SerialName("total_videos") val totalVideos: Int = 0,
    @SerialName("total_devices") val totalDevices: Int = 0,
    @SerialName("vault_bytes") val vaultBytes: Long = 0,
    @SerialName("disk_free_bytes") val diskFreeBytes: Long = 0,
    @SerialName("disk_total_bytes") val diskTotalBytes: Long = 0
)

@Serializable
data class ApiError(
    @SerialName("error") val error: ErrorDetail? = null
) {
    @Serializable
    data class ErrorDetail(
        @SerialName("code") val code: String = "",
        @SerialName("message") val message: String = ""
    )
}
