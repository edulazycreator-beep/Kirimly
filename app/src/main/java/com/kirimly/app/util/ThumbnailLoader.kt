package com.kirimly.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import java.io.ByteArrayOutputStream

/** Small, cached previews for images and videos from any content Uri (MediaStore, SAF, FileProvider). */
object ThumbnailLoader {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun supports(mimeType: String): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("video/")

    /** Blocking: call off the main thread. Returns null when no preview can be made. */
    fun load(context: Context, uri: Uri, mimeType: String, sizePx: Int): Bitmap? {
        if (!supports(mimeType)) return null
        val key = "$uri@$sizePx"
        cache.get(key)?.let { return it }
        val bitmap = try {
            systemThumbnail(context, uri, sizePx)
                ?: if (mimeType.startsWith("video/")) videoFrame(context, uri, sizePx) else decodeImage(context, uri, sizePx)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    fun loadJpeg(context: Context, uri: Uri, mimeType: String, sizePx: Int): ByteArray? {
        val bitmap = load(context, uri, mimeType, sizePx) ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }

    private fun systemThumbnail(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeImage(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                val scale = sizePx.toFloat() / longest
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun videoFrame(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val longest = maxOf(frame.width, frame.height).coerceAtLeast(1)
            if (longest <= sizePx) frame
            else Bitmap.createScaledBitmap(frame, frame.width * sizePx / longest, frame.height * sizePx / longest, true)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
