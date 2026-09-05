package com.photovault.data.api

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.photovault.data.local.PreferenceStore
import com.photovault.data.model.DeviceItem
import com.photovault.data.model.DeviceListResponse
import com.photovault.data.model.DeviceRegistrationRequest
import com.photovault.data.model.DeviceRegistrationResponse
import com.photovault.data.model.HealthResponse
import com.photovault.data.model.MediaItem
import com.photovault.data.model.SearchResponse
import com.photovault.data.model.SyncDiffResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class PhotoVaultClient(
    private val context: Context,
    private val prefs: PreferenceStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val token = prefs.authToken.value
            val requestBuilder = original.newBuilder()
            if (token.isNotEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    fun getThumbnailUrl(fileId: String): String {
        val base = prefs.serverUrl.value
        val token = prefs.authToken.value
        return "$base/media/$fileId/thumbnail?token=$token"
    }

    fun getOriginalUrl(fileId: String): String {
        val base = prefs.serverUrl.value
        val token = prefs.authToken.value
        return "$base/media/$fileId/original?token=$token"
    }

    fun getPreviewUrl(fileId: String): String {
        val base = prefs.serverUrl.value
        val token = prefs.authToken.value
        return "$base/media/$fileId/preview?token=$token"
    }

    suspend fun registerDevice(serverUrl: String, deviceName: String): Result<DeviceRegistrationResponse> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = serverUrl.trim().trimEnd('/')
                val sanitizedName = deviceName.trim().ifEmpty { "Android Device" }.take(100)
                val payload = json.encodeToString(
                    DeviceRegistrationRequest.serializer(),
                    DeviceRegistrationRequest(name = sanitizedName, deviceType = "android")
                )
                val body = payload.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$cleanUrl/devices/register")
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val parsed = json.decodeFromString(DeviceRegistrationResponse.serializer(), responseBody)
                        Result.success(parsed)
                    } else {
                        Result.failure(IOException("Registration failed: HTTP ${response.code} $responseBody"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchDevices(): Result<List<DeviceItem>> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val request = Request.Builder()
                .url("$base/devices")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString(DeviceListResponse.serializer(), body)
                    Result.success(parsed.devices)
                } else {
                    Result.failure(IOException("Fetch devices failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val request = Request.Builder()
                .url("$base/devices/$deviceId")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Delete device failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSyncDiff(cursor: Long = 0): Result<SyncDiffResponse> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val deviceId = prefs.deviceId.value
            val request = Request.Builder()
                .url("$base/sync/diff?device_id=$deviceId&cursor=$cursor")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString(SyncDiffResponse.serializer(), body)
                    Result.success(parsed)
                } else {
                    Result.failure(IOException("Sync diff failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val request = Request.Builder()
                .url("$base/health")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString(HealthResponse.serializer(), body)
                    Result.success(parsed)
                } else {
                    Result.failure(IOException("Health check failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMedia(
        query: String = "",
        mimeType: String = "",
        favoriteOnly: Boolean = false,
        deletedOnly: Boolean = false,
        deviceId: String = "",
        excludeDeviceId: String = "",
        sort: String = "taken_at",
        order: String = "desc",
        limit: Int = 1000,
        offset: Int = 0
    ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val urlBuilder = StringBuilder("$base/media?limit=$limit&offset=$offset&sort=$sort&order=$order")
            if (query.isNotEmpty()) urlBuilder.append("&query=").append(Uri.encode(query))
            if (mimeType.isNotEmpty()) urlBuilder.append("&mime_type=").append(Uri.encode(mimeType))
            if (favoriteOnly) urlBuilder.append("&favorite=true")
            if (deletedOnly) urlBuilder.append("&deleted=true")
            if (deviceId.isNotEmpty()) urlBuilder.append("&device_id=").append(Uri.encode(deviceId))
            if (excludeDeviceId.isNotEmpty()) urlBuilder.append("&exclude_device_id=").append(Uri.encode(excludeDeviceId))

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val searchResponse = json.decodeFromString(SearchResponse.serializer(), body)
                    Result.success(searchResponse.media)
                } else {
                    Result.failure(IOException("Fetch media failed: HTTP ${response.code} $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreMedia(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val request = Request.Builder()
                .url("$base/media/$fileId/restore")
                .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Restore failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun permanentDeleteMedia(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val request = Request.Builder()
                .url("$base/media/$fileId/permanent")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Permanent delete failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMediaToGallery(
        fileId: String,
        filename: String,
        mimeType: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val url = getOriginalUrl(fileId)
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Download failed: HTTP ${response.code}"))
                }
                val responseBody = response.body ?: return@withContext Result.failure(IOException("Empty response body"))
                val isVideo = mimeType.startsWith("video/")
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val safeFilename = if (filename.isBlank()) "photovault_${fileId.take(8)}" else filename
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeFilename)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (mimeType.isNotBlank()) mimeType else "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            if (isVideo) Environment.DIRECTORY_MOVIES + "/PhotoVault"
                            else Environment.DIRECTORY_PICTURES + "/PhotoVault"
                        )
                    }
                }

                val uri = context.contentResolver.insert(collection, values)
                    ?: return@withContext Result.failure(IOException("Failed to create MediaStore entry"))

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    responseBody.byteStream().copyTo(output)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }

                prefs.markFileDownloaded(fileId)
                Result.success(uri)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAllMedia(
        query: String = "",
        mimeType: String = "",
        favoriteOnly: Boolean = false,
        deletedOnly: Boolean = false,
        deviceId: String = "",
        excludeDeviceId: String = "",
        sort: String = "taken_at",
        order: String = "desc"
    ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val allItems = mutableListOf<MediaItem>()
            var offset = 0
            val pageSize = 1000
            while (true) {
                val result = fetchMedia(
                    query = query,
                    mimeType = mimeType,
                    favoriteOnly = favoriteOnly,
                    deletedOnly = deletedOnly,
                    deviceId = deviceId,
                    excludeDeviceId = excludeDeviceId,
                    sort = sort,
                    order = order,
                    limit = pageSize,
                    offset = offset
                )
                if (result.isFailure) {
                    return@withContext Result.failure(result.exceptionOrNull() ?: IOException("Failed to fetch media"))
                }
                val page = result.getOrNull().orEmpty()
                allItems.addAll(page)
                if (page.size < pageSize) break
                offset += page.size
            }
            Result.success(allItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAllMediaForDevice(deviceId: String): Result<List<MediaItem>> =
        fetchAllMedia(deviceId = deviceId)

    suspend fun setFavorite(fileId: String, favorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val request = Request.Builder()
                .url("$base/media/$fileId/favorite")
                .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Set favorite failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMedia(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val request = Request.Builder()
                .url("$base/media/$fileId")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Delete media failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        file: File,
        mimeType: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val base = prefs.serverUrl.value
            val fileRequestBody = CountingRequestBody(file.asRequestBody(mimeType.toMediaTypeOrNull())) { bytesWritten, contentLength ->
                if (contentLength > 0) {
                    onProgress(bytesWritten.toFloat() / contentLength.toFloat())
                }
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileRequestBody)
                .build()

            val request = Request.Builder()
                .url("$base/upload")
                .post(multipartBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Upload failed: HTTP ${response.code} ${response.body?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private class CountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, contentLength: Long) -> Unit
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink, contentLength(), onProgress)
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}

private class CountingSink(
    delegate: Sink,
    private val contentLength: Long,
    private val onProgress: (bytesWritten: Long, contentLength: Long) -> Unit
) : ForwardingSink(delegate) {
    private var bytesWritten = 0L

    override fun write(source: Buffer, byteCount: Long) {
        super.write(source, byteCount)
        bytesWritten += byteCount
        onProgress(bytesWritten, contentLength)
    }
}
