package com.kirimly.app.network

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.kirimly.app.data.model.SharedFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import java.util.UUID

object NetworkUtils {

    const val BUFFER_SIZE = 8192

    /**
     * Returns the best IPv4 address for LAN clients. Hotspot interfaces (ap*, swlan*) are
     * preferred because an iOS device joining our hotspot can only reach us there; next come
     * Wi-Fi station interfaces (wlan*), then USB/Ethernet, then anything else that is private.
     */
    fun getLocalIpv4Address(): String? = getAllLocalIpv4Addresses().firstOrNull()?.second

    fun getAllLocalIpv4Addresses(): List<Pair<String, String>> {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val candidates = mutableListOf<Triple<Int, String, String>>()
        for (networkInterface in interfaces) {
            val usable = try {
                networkInterface.isUp && !networkInterface.isLoopback && !networkInterface.isVirtual
            } catch (e: Exception) {
                false
            }
            if (!usable) continue
            val name = networkInterface.name.lowercase(Locale.US)
            val priority = when {
                name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 0
                name.startsWith("wlan") -> 1
                name.startsWith("eth") || name.startsWith("rndis") || name.startsWith("usb") -> 2
                name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") ||
                    name.startsWith("ppp") || name.startsWith("dummy") -> 9
                else -> 5
            }
            if (priority == 9) continue
            for (address in Collections.list(networkInterface.inetAddresses)) {
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    val host = address.hostAddress ?: continue
                    val adjusted = if (address.isSiteLocalAddress) priority else priority + 10
                    candidates += Triple(adjusted, networkInterface.name, host)
                }
            }
        }
        return candidates.sortedBy { it.first }.map { it.second to it.third }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 0L) return "—"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[index])
        else String.format(Locale.US, "%.1f %s", value, units[index])
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return "0 KB/s"
        val kb = bytesPerSecond / 1024.0
        return if (kb < 1024.0) String.format(Locale.US, "%.0f KB/s", kb)
        else String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
    }

    fun resolveMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (extension.isBlank()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                "mov" -> "video/quicktime"
                "m4v" -> "video/x-m4v"
                "key" -> "application/vnd.apple.keynote"
                "pages" -> "application/vnd.apple.pages"
                "numbers" -> "application/vnd.apple.numbers"
                else -> "application/octet-stream"
            }
    }

    fun sanitizeFileName(raw: String): String {
        val trimmed = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = buildString {
            for (char in trimmed) {
                append(
                    when {
                        char.code < 0x20 -> '_'
                        char in "\\/:*?\"<>|" -> '_'
                        else -> char
                    }
                )
            }
        }.trim('.', ' ')
        return cleaned.take(180).ifBlank { "kirimly_${System.currentTimeMillis()}" }
    }

    /** Reads display name, size and MIME type of a content Uri. */
    fun querySharedFile(context: Context, uri: Uri): SharedFile? {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        } catch (e: Exception) {
            return null
        }
        if (size < 0L) {
            size = try {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            } catch (e: Exception) {
                -1L
            }
        }
        val displayName = sanitizeFileName(name ?: uri.lastPathSegment ?: "file")
        val mime = resolver.getType(uri)?.takeIf { it.isNotBlank() } ?: resolveMimeType(displayName)
        return SharedFile(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = displayName,
            sizeBytes = size,
            mimeType = mime
        )
    }
}

/** Streams incoming bytes into the public Downloads/Kirimly folder. */
object MediaStoreWriter {

    const val SUB_FOLDER = "Kirimly"

    data class SavedFile(val uri: Uri, val displayName: String, val bytesWritten: Long)

    /**
     * Blocking: call from Dispatchers.IO. Copies [input] using an 8 KB buffer so arbitrarily
     * large files never sit in memory.
     */
    @Throws(IOException::class)
    fun saveToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        input: InputStream,
        onProgress: (Long) -> Unit
    ): SavedFile {
        val safeName = NetworkUtils.sanitizeFileName(fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, safeName, mimeType, input, onProgress)
        } else {
            saveLegacy(context, safeName, mimeType, input, onProgress)
        }
    }

    private fun saveScoped(
        context: Context,
        fileName: String,
        mimeType: String,
        input: InputStream,
        onProgress: (Long) -> Unit
    ): SavedFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + SUB_FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
            ?: throw IOException("Tidak dapat membuat file di Downloads")
        try {
            val written = resolver.openOutputStream(itemUri, "w")?.use { output ->
                copyStream(input, output, onProgress)
            } ?: throw IOException("Tidak dapat membuka file tujuan")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(itemUri, done, null, null)
            var finalName = fileName
            resolver.query(itemUri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) finalName = c.getString(0)
            }
            return SavedFile(itemUri, finalName, written)
        } catch (e: Exception) {
            try {
                resolver.delete(itemUri, null, null)
            } catch (_: Exception) {
            }
            throw if (e is IOException) e else IOException(e.message ?: "Gagal menyimpan file", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        fileName: String,
        mimeType: String,
        input: InputStream,
        onProgress: (Long) -> Unit
    ): SavedFile {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloads, SUB_FOLDER)
        if (!folder.exists() && !folder.mkdirs()) throw IOException("Tidak dapat membuat folder Downloads/Kirimly")
        val target = uniqueFile(folder, fileName)
        try {
            val written = FileOutputStream(target).use { output -> copyStream(input, output, onProgress) }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
            return SavedFile(uri, target.name, written)
        } catch (e: Exception) {
            target.delete()
            throw if (e is IOException) e else IOException(e.message ?: "Gagal menyimpan file", e)
        }
    }

    private fun uniqueFile(folder: File, fileName: String): File {
        var candidate = File(folder, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val name = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            candidate = File(folder, name)
            index++
        }
        return candidate
    }

    private fun copyStream(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit): Long {
        val buffer = ByteArray(NetworkUtils.BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            total += read
            onProgress(total)
        }
        output.flush()
        return total
    }
}
