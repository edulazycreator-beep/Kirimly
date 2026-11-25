package com.kirimly.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kirimly.app.data.model.TransferDirection
import com.kirimly.app.data.model.TransferItem

@Entity(
    tableName = "transfer_history",
    indices = [Index(value = ["timestamp"]), Index(value = ["direction"])]
)
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "direction")
    val direction: String,
    @ColumnInfo(name = "peer_name")
    val peerName: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "uri")
    val uri: String?,
    @ColumnInfo(name = "text_content")
    val textContent: String?
) {
    fun toDomain(): TransferItem = TransferItem(
        id = id,
        fileName = fileName,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        direction = runCatching { TransferDirection.valueOf(direction) }.getOrDefault(TransferDirection.RECEIVED),
        peerName = peerName,
        timestamp = timestamp,
        uri = uri,
        textContent = textContent
    )

    companion object {
        fun forFile(
            fileName: String,
            sizeBytes: Long,
            mimeType: String,
            direction: TransferDirection,
            peerName: String,
            uri: String?
        ): TransferRecordEntity = TransferRecordEntity(
            fileName = fileName,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            direction = direction.name,
            peerName = peerName,
            timestamp = System.currentTimeMillis(),
            uri = uri,
            textContent = null
        )

        fun forText(
            text: String,
            direction: TransferDirection,
            peerName: String
        ): TransferRecordEntity = TransferRecordEntity(
            fileName = text.lineSequence().firstOrNull().orEmpty().take(80).ifBlank { "Teks" },
            sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
            mimeType = "text/plain",
            direction = direction.name,
            peerName = peerName,
            timestamp = System.currentTimeMillis(),
            uri = null,
            textContent = text
        )
    }
}
