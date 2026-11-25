package com.kirimly.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/** Maps a MIME type / file name to a representative Material icon. */
@Suppress("DEPRECATION")
fun fileIconFor(mimeType: String, fileName: String = ""): ImageVector {
    val mime = mimeType.lowercase(Locale.US)
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
    return when {
        mime.startsWith("image/") -> Icons.Rounded.Image
        mime.startsWith("video/") -> Icons.Rounded.VideoFile
        mime.startsWith("audio/") -> Icons.Rounded.AudioFile
        mime == "application/pdf" || ext == "pdf" -> Icons.Rounded.PictureAsPdf
        mime == "text/plain" -> Icons.Rounded.TextSnippet
        mime == "application/vnd.android.package-archive" || ext == "apk" -> Icons.Rounded.Android
        mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar") ||
            mime.contains("gzip") || ext in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Rounded.FolderZip
        mime.contains("spreadsheet") || mime.contains("excel") || ext in setOf("xls", "xlsx", "csv", "numbers") -> Icons.Rounded.TableChart
        mime.contains("presentation") || mime.contains("powerpoint") || ext in setOf("ppt", "pptx", "key") -> Icons.Rounded.Slideshow
        mime.contains("word") || mime.contains("document") || ext in setOf("doc", "docx", "pages", "rtf", "odt") -> Icons.Rounded.Description
        mime.startsWith("text/") -> Icons.Rounded.Article
        else -> Icons.Rounded.InsertDriveFile
    }
}
