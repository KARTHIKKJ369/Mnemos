package com.photovault

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.photovault.data.api.PhotoVaultClient
import com.photovault.data.local.PreferenceStore
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class PhotoVaultApplication : Application(), ImageLoaderFactory {

    lateinit var preferenceStore: PreferenceStore
        private set

    lateinit var apiClient: PhotoVaultClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferenceStore = PreferenceStore(this)
        apiClient = PhotoVaultClient(this, preferenceStore)
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val token = preferenceStore.authToken.value
                val newRequest = if (token.isNotEmpty()) {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    request
                }
                chain.proceed(newRequest)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "photovault_media_cache"))
                    .maxSizeBytes(2L * 1024 * 1024 * 1024) // 2 GB disk cache
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: PhotoVaultApplication
            private set
    }
}
