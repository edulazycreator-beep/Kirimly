package com.kirimly.app.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.kirimly.app.util.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Square preview for images/videos; falls back to the file-type icon. */
@Composable
fun FileThumbnail(
    uri: Uri?,
    mimeType: String,
    fileName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, mimeType) {
        value = if (uri != null && ThumbnailLoader.supports(mimeType)) {
            withContext(Dispatchers.IO) { ThumbnailLoader.load(context, uri, mimeType, 160)?.asImageBitmap() }
        } else null
    }
    Surface(shape = RectangleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = fileIconFor(mimeType, fileName),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
