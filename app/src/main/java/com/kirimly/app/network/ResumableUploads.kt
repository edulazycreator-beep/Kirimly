package com.kirimly.app.network

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/**
 * Where an in-progress upload is being written. Scoped storage (Android 10+) uses a pending
 * MediaStore row that is only published when the upload completes; older versions write a
 * file in Downloads/Kirimly directly.
 */
class UploadTarget(
    val uri: Uri,
    val legacyFile: File?,
    val initialDisplayName: String
)

/** One resumable upload: bytes land at [written]; the digest covers exactly those bytes. */
class UploadSession(
    val id: String,
    val token: String,
    val index: Int,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val relativeDir: String,
    val peerName: String,
    val expectedSha256: String?,
    val key: SecretKeySpec?,
    val target: UploadTarget,
    val trackId: String
) {
    val mutex = Mutex()
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    @Volatile
    var written: Long = 0L

    @Volatile
    var lastActivity: Long = System.currentTimeMillis()

    val encrypted: Boolean get() = key != null
}

object ResumableUploadStore {

    private const val ROOT = "Kirimly"

    /** Cleans a client-supplied relative folder ("Trip/Day 1") into safe path segments. */
    fun sanitizeRelativeDir(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .take(8)
            .map { NetworkUtils.sanitizeFileName(it).take(80) }
            .joinToString("/")
    }

    @Throws(IOException::class)
    fun createTarget(context: Context, fileName: String, mimeType: String, relativeDir: String): UploadTarget {
        val safeName = NetworkUtils.sanitizeFileName(fileName)
        val folder = if (relativeDir.isBlank()) ROOT else "$ROOT/$relativeDir"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder + "/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IOException("Tidak dapat membuat file di Downloads")
            UploadTarget(uri, null, safeName)
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, folder)
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Tidak dapat membuat folder $folder")
            val file = uniqueFile(dir, safeName)
            if (!file.createNewFile() && !file.exists()) throw IOException("Tidak dapat membuat ${file.name}")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            UploadTarget(uri, file, file.name)
        }
    }

    /** Writes [length] bytes of [data] at [offset], truncating anything a failed attempt left beyond it. */
    @Throws(IOException::class)
    fun writeAt(context: Context, target: UploadTarget, offset: Long, data: ByteArray, length: Int) {
        val legacy = target.legacyFile
        if (legacy != null) {
            RandomAccessFile(legacy, "rw").use { file ->
                file.setLength(offset)
                file.seek(offset)
                file.write(data, 0, length)
            }
            return
        }
        val pfd = context.contentResolver.openFileDescriptor(target.uri, "rw")
            ?: throw IOException("File tujuan tidak dapat dibuka")
        pfd.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { stream ->
                val channel = stream.channel
                channel.truncate(offset)
                channel.position(offset)
                val buffer = ByteBuffer.wrap(data, 0, length)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(false)
            }
        }
    }

    /** Publishes the file and returns its final display name. */
    @Throws(IOException::class)
    fun publish(context: Context, target: UploadTarget, mimeType: String): String {
        val legacy = target.legacyFile
        if (legacy != null) {
            MediaScannerConnection.scanFile(context, arrayOf(legacy.absolutePath), arrayOf(mimeType), null)
            return legacy.name
        }
        val resolver = context.contentResolver
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(target.uri, done, null, null)
        var name = target.initialDisplayName
        resolver.query(target.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0)
        }
        return name
    }

    fun discard(context: Context, target: UploadTarget) {
        try {
            val legacy = target.legacyFile
            if (legacy != null) legacy.delete() else context.contentResolver.delete(target.uri, null, null)
        } catch (_: Exception) {
        }
    }

    fun isHeic(mimeType: String, fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return mimeType.equals("image/heic", true) || mimeType.equals("image/heif", true) ||
            ext == "heic" || ext == "heif"
    }

    /**
     * Converts a published HEIC/HEIF photo into a JPEG next to it (Android 9+), deleting the
     * original on success. Returns the new (uri, name, size) or null to keep the original.
     */
    fun convertHeicToJpeg(context: Context, source: Uri, fileName: String, relativeDir: String): Triple<Uri, String, Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val jpegName = fileName.substringBeforeLast('.', fileName) + ".jpg"
            val target = createTarget(context, jpegName, "image/jpeg", relativeDir)
            var size = 0L
            val stream = if (target.legacyFile != null) FileOutputStream(target.legacyFile)
            else context.contentResolver.openOutputStream(target.uri, "w") ?: throw IOException("JPEG tidak dapat ditulis")
            stream.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }
            bitmap.recycle()
            val name = publish(context, target, "image/jpeg")
            size = if (target.legacyFile != null) target.legacyFile.length() else querySize(context, target.uri)
            try {
                context.contentResolver.delete(source, null, null)
            } catch (_: Exception) {
            }
            Triple(target.uri, name, size)
        } catch (e: Exception) {
            null
        }
    }

    private fun querySize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
        }
        return -1L
    }

    private fun uniqueFile(folder: File, fileName: String): File {
        var candidate = File(folder, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            candidate = File(folder, if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext")
            index++
        }
        return candidate
    }
}
