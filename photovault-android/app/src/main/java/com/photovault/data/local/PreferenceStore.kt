package com.photovault.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("photovault_preferences", Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, "") ?: "")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _authToken = MutableStateFlow(prefs.getString(KEY_AUTH_TOKEN, "") ?: "")
    val authToken: StateFlow<String> = _authToken.asStateFlow()

    private val _deviceId = MutableStateFlow(prefs.getString(KEY_DEVICE_ID, "") ?: "")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _gridColumns = MutableStateFlow(prefs.getInt(KEY_GRID_COLUMNS, 4))
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _autoBackup = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP, false))
    val autoBackup: StateFlow<Boolean> = _autoBackup.asStateFlow()

    private val _downloadedFileIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet(KEY_DOWNLOADED_FILE_IDS, emptySet()) ?: emptySet()
    )
    val downloadedFileIds: StateFlow<Set<String>> = _downloadedFileIds.asStateFlow()

    fun saveServerConfig(url: String, token: String, deviceId: String) {
        val cleanUrl = url.trim().trimEnd('/')
        prefs.edit()
            .putString(KEY_SERVER_URL, cleanUrl)
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
        _serverUrl.value = cleanUrl
        _authToken.value = token
        _deviceId.value = deviceId
    }

    fun setGridColumns(cols: Int) {
        val bounded = cols.coerceIn(2, 6)
        prefs.edit().putInt(KEY_GRID_COLUMNS, bounded).apply()
        _gridColumns.value = bounded
    }

    fun setAutoBackup(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
        _autoBackup.value = enabled
    }

    fun markFileDownloaded(fileId: String) {
        val updated = _downloadedFileIds.value + fileId
        prefs.edit().putStringSet(KEY_DOWNLOADED_FILE_IDS, updated).apply()
        _downloadedFileIds.value = updated
    }

    fun markFilesDownloaded(fileIds: Collection<String>) {
        val updated = _downloadedFileIds.value + fileIds
        prefs.edit().putStringSet(KEY_DOWNLOADED_FILE_IDS, updated).apply()
        _downloadedFileIds.value = updated
    }

    fun isFileDownloaded(fileId: String): Boolean {
        return _downloadedFileIds.value.contains(fileId)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _serverUrl.value = ""
        _authToken.value = ""
        _deviceId.value = ""
        _downloadedFileIds.value = emptySet()
    }

    val isConfigured: Boolean
        get() = _serverUrl.value.isNotEmpty() && _authToken.value.isNotEmpty()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_DOWNLOADED_FILE_IDS = "downloaded_file_ids"
    }
}
