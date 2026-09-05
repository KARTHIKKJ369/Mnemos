package com.photovault.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    @SerialName("file_id") val fileId: String,
    @SerialName("filename") val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("uploaded_at") val uploadedAt: String = "",
    @SerialName("taken_at") val takenAt: String? = null,
    @SerialName("favorite") val favorite: Boolean = false,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("camera_make") val cameraMake: String? = null,
    @SerialName("camera_model") val cameraModel: String? = null,
    @SerialName("gps_lat") val gpsLat: Double? = null,
    @SerialName("gps_lon") val gpsLon: Double? = null,
    @SerialName("thumbnail_available") val thumbnailAvailable: Boolean = false,
    @SerialName("preview_available") val previewAvailable: Boolean = false,
    @SerialName("uploaded_by_device_id") val uploadedByDeviceId: String = "",
    @SerialName("uploaded_by_device_name") val uploadedByDeviceName: String = "",
    @SerialName("uploaded_by_device_type") val uploadedByDeviceType: String = ""
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    val displayDate: String
        get() = takenAt ?: uploadedAt
}

@Serializable
data class SearchResponse(
    @SerialName("media") val media: List<MediaItem> = emptyList(),
    @SerialName("total") val total: Int = 0,
    @SerialName("limit") val limit: Int = 0,
    @SerialName("offset") val offset: Int = 0
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
