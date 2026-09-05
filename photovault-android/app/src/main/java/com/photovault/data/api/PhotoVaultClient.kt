package com.photovault.data.api

import android.content.Context
import android.net.Uri
import com.photovault.data.local.PreferenceStore
import com.photovault.data.model.DeviceRegistrationRequest
import com.photovault.data.model.DeviceRegistrationResponse
import com.photovault.data.model.HealthResponse
import com.photovault.data.model.MediaItem
import com.photovault.data.model.SearchResponse
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
                val payload = json.encodeToString(
                    DeviceRegistrationRequest.serializer(),
                    DeviceRegistrationRequest(name = deviceName, deviceType = "android")
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
