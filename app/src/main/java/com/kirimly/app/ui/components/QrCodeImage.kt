package com.kirimly.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kirimly.app.R
import com.kirimly.app.network.QrCodeGenerator
import com.kirimly.app.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** High-contrast QR (always black on white, whatever the theme) so phone cameras read it instantly. */
@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier, maxSize: Dp = 280.dp) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = content) {
        value = withContext(Dispatchers.Default) { QrCodeGenerator.generate(content) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(max = maxSize)
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color.White, RectangleShape)
            .padding(maxSize / 20)
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = tr(R.string.u_qr_description),
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        } else {
            CircularProgressIndicator(color = Color.Black)
        }
    }
}
