package com.kirimly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kirimly.app.data.model.QrMode
import com.kirimly.app.network.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-contrast QR card with a switcher between the Safari server URL and the Wi-Fi hotspot
 * join code. [hotspotContent] renders hotspot controls when there is no Wi-Fi payload yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrDisplayCard(
    mode: QrMode,
    onModeChange: (QrMode) -> Unit,
    serverUrl: String?,
    wifiPayload: String?,
    serverCaption: String,
    wifiCaption: String,
    modifier: Modifier = Modifier,
    hotspotContent: @Composable ColumnScope.() -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == QrMode.SERVER_URL,
                    onClick = { onModeChange(QrMode.SERVER_URL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) { Text("URL Safari") }
                SegmentedButton(
                    selected = mode == QrMode.WIFI_HOTSPOT,
                    onClick = { onModeChange(QrMode.WIFI_HOTSPOT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) { Text("Wi-Fi Hotspot") }
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "qrMode"
            ) { target ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val payload = if (target == QrMode.SERVER_URL) serverUrl else wifiPayload
                    if (payload != null) {
                        QrImage(content = payload)
                        Text(
                            text = if (target == QrMode.SERVER_URL) serverCaption else wifiCaption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else if (target == QrMode.SERVER_URL) {
                        QrPlaceholder(text = "Aktifkan server untuk menampilkan QR")
                    }
                    if (target == QrMode.WIFI_HOTSPOT) hotspotContent()
                }
            }
        }
    }
}

@Composable
private fun QrImage(content: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = content) {
        value = withContext(Dispatchers.Default) { QrCodeGenerator.generate(content) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.White, RectangleShape)
            .padding(14.dp)
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Kode QR",
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        } else {
            CircularProgressIndicator(color = Color(0xFF1F5FD6))
        }
    }
}

@Composable
private fun QrPlaceholder(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RectangleShape)
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.QrCode2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
