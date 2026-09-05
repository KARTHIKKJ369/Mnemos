package com.photovault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.photovault.ui.screens.MainNavHost
import com.photovault.ui.screens.auth.ConnectScreen
import com.photovault.ui.theme.DarkBackground
import com.photovault.ui.theme.PhotoVaultTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    PhotoVaultRoot()
                }
            }
        }
    }
}

@Composable
fun PhotoVaultRoot() {
    val app = PhotoVaultApplication.instance
    val authToken by app.preferenceStore.authToken.collectAsState()
    val serverUrl by app.preferenceStore.serverUrl.collectAsState()

    val isConfigured = authToken.isNotEmpty() && serverUrl.isNotEmpty()

    if (isConfigured) {
        MainNavHost(
            onLogout = {
                app.preferenceStore.clear()
            }
        )
    } else {
        ConnectScreen(
            onConnected = {}
        )
    }
}
