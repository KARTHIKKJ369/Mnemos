package com.photovault.ui.screens.auth

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.photovault.ui.components.RedDotIndicator
import com.photovault.ui.theme.FrameBlack
import com.photovault.ui.theme.FrameBorder
import com.photovault.ui.theme.FrameGray300
import com.photovault.ui.theme.FrameGray500
import com.photovault.ui.theme.FrameSurface
import com.photovault.ui.theme.FrameWhite
import com.photovault.ui.theme.MnemosType
import com.photovault.ui.theme.SignalRed
import com.photovault.ui.theme.SignalRedSubtle
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
            .background(FrameBlack)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(SignalRedSubtle)
                .border(1.dp, SignalRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = SignalRed,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RedDotIndicator(size = 7.dp)
            Text(
                text = "FRAME // MNEMOS",
                style = MnemosType.Headline28,
                color = FrameWhite
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "PRIVATE VAULT // HARDWARE NODE AUTH",
            style = MnemosType.Mono11,
            color = FrameGray500
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = {
                serverUrl = it
                errorMessage = null
            },
            label = { Text("SERVER URL // IP", style = MnemosType.Mono11) },
            placeholder = { Text("http://100.x.y.z:8080", style = MnemosType.Mono12) },
            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = SignalRed, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FrameSurface,
                unfocusedContainerColor = FrameSurface,
                focusedBorderColor = SignalRed,
                unfocusedBorderColor = FrameBorder,
                focusedLabelColor = SignalRed,
                unfocusedLabelColor = FrameGray500,
                focusedTextColor = FrameWhite,
                unfocusedTextColor = FrameWhite
            ),
            shape = RoundedCornerShape(6.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("NODE IDENTIFIER", style = MnemosType.Mono11) },
            leadingIcon = { Icon(Icons.Default.Smartphone, contentDescription = null, tint = FrameGray500, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FrameSurface,
                unfocusedContainerColor = FrameSurface,
                focusedBorderColor = SignalRed,
                unfocusedBorderColor = FrameBorder,
                focusedLabelColor = SignalRed,
                unfocusedLabelColor = FrameGray500,
                focusedTextColor = FrameWhite,
                unfocusedTextColor = FrameWhite
            ),
            shape = RoundedCornerShape(6.dp)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage ?: "",
                color = SignalRed,
                style = MnemosType.Mono11,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        MnemosButton(
            text = "AUTHENTICATE NODE",
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
