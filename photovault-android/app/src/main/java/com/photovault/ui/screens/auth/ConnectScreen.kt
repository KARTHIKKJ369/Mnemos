package com.photovault.ui.screens.auth

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.PhotoVaultApplication
import com.photovault.ui.components.ButtonVariant
import com.photovault.ui.components.MnemosButton
import com.photovault.ui.theme.IrisLight
import com.photovault.ui.theme.IrisPrimary
import com.photovault.ui.theme.IrisSubtle
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.Slate200
import com.photovault.ui.theme.Slate400
import com.photovault.ui.theme.Slate50
import com.photovault.ui.theme.Slate700
import com.photovault.ui.theme.Slate800
import com.photovault.ui.theme.Slate900
import com.photovault.ui.theme.Slate950
import com.photovault.ui.theme.TomatoRed
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(
    onConnected: () -> Unit
) {
    val app = PhotoVaultApplication.instance
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf("http://192.168.1.") }
    var deviceName by remember {
        mutableStateOf("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(IrisSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = IrisLight,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "MNEMOS",
            style = MnemosType.Headline28.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
            color = Slate50
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Private Vault & Cluster Backup",
            style = MnemosType.BodySecondary13,
            color = Slate400
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = {
                serverUrl = it
                errorMessage = null
            },
            label = { Text("Server URL (LAN or Tailscale IP)") },
            placeholder = { Text("http://100.x.y.z:8080") },
            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = IrisLight) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slate900,
                unfocusedContainerColor = Slate900,
                focusedBorderColor = IrisPrimary,
                unfocusedBorderColor = Slate800,
                focusedLabelColor = IrisLight,
                unfocusedLabelColor = Slate400,
                focusedTextColor = Slate50,
                unfocusedTextColor = Slate50
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Node Name") },
            leadingIcon = { Icon(Icons.Default.Smartphone, contentDescription = null, tint = Slate400) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slate900,
                unfocusedContainerColor = Slate900,
                focusedBorderColor = IrisPrimary,
                unfocusedBorderColor = Slate800,
                focusedLabelColor = IrisLight,
                unfocusedLabelColor = Slate400,
                focusedTextColor = Slate50,
                unfocusedTextColor = Slate50
            ),
            shape = RoundedCornerShape(10.dp)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage ?: "",
                color = TomatoRed,
                style = MnemosType.BodySecondary13,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        MnemosButton(
            text = "Connect to Vault",
            onClick = {
                if (serverUrl.isBlank()) {
                    errorMessage = "Please enter your server URL"
                    return@MnemosButton
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = app.apiClient.registerDevice(serverUrl, deviceName)
                    isLoading = false
                    result.onSuccess { response ->
                        app.preferenceStore.saveServerConfig(
                            url = serverUrl,
                            token = response.authToken,
                            deviceId = response.deviceId
                        )
                        onConnected()
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Failed to connect to Mnemos server"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.PRIMARY,
            isLoading = isLoading
        )
    }
}
