package com.kirimly.app.network

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.kirimly.app.data.model.SharedFile
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Share-sheet Uris carry a temporary read grant that can lapse long before an iPhone gets round
 * to downloading the file. Such files are copied into the app cache and re-exposed through our
 * own FileProvider, so the queue stays readable for as long as the app keeps it.
 */
object SharedFileCache {

    private const val DIR = "shared"
    private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

    private fun root(context: Context): File = File(context.cacheDir, DIR)

    /** Blocking: copies [file] into the cache. Returns a SharedFile pointing at the copy. */
    fun copyIntoCache(context: Context, file: SharedFile, onProgress: (Long) -> Unit = {}): SharedFile? {
        val folder = File(root(context), UUID.randomUUID().toString())
        if (!folder.mkdirs()) return null
        val target = File(folder, file.displayName)
        return try {
            val input = context.contentResolver.openInputStream(file.uri) ?: return null
            var total = 0L
            input.use { stream ->
                FileOutputStream(target).use { out ->
                    val buffer = ByteArray(NetworkUtils.BUFFER_SIZE * 8)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        total += read
                        onProgress(total)
                    }
                }
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
            file.copy(uri = uri, sizeBytes = total)
        } catch (e: Exception) {
            folder.deleteRecursively()
            null
        }
    }

    fun isCached(context: Context, uri: Uri): Boolean =
        uri.authority == context.packageName + ".fileprovider" && uri.pathSegments.firstOrNull() == "kirimly_cache"

    /** Deletes the cached copy behind [uri], if it is one of ours. */
    fun release(context: Context, uri: Uri) {
        if (!isCached(context, uri)) return
        // Path: /kirimly_cache/shared/<uuid>/<name>
        val segments = uri.pathSegments
        if (segments.size >= 3 && segments[1] == DIR) {
            File(root(context), segments[2]).deleteRecursively()
        }
    }

    /** Removes copies older than a day (run at start-up). */
    fun pruneStale(context: Context) {
        val now = System.currentTimeMillis()
        root(context).listFiles()?.forEach { dir ->
            if (now - dir.lastModified() > MAX_AGE_MS) dir.deleteRecursively()
        }
    }
}
