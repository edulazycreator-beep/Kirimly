package com.kirimly.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** A resumable upload that must survive the app process being killed. */
@Entity(tableName = "upload_sessions")
data class UploadSessionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "token") val token: String,
    @ColumnInfo(name = "file_index") val fileIndex: Int,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "relative_dir") val relativeDir: String,
    @ColumnInfo(name = "peer_name") val peerName: String,
    @ColumnInfo(name = "expected_sha256") val expectedSha256: String?,
    @ColumnInfo(name = "key_b64") val keyB64: String?,
    @ColumnInfo(name = "target_uri") val targetUri: String,
    @ColumnInfo(name = "legacy_path") val legacyPath: String?,
    @ColumnInfo(name = "initial_name") val initialName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** An approved upload request (token) so files not yet started can still be sent after a restart. */
@Entity(tableName = "approved_tokens")
data class ApprovedTokenEntity(
    @PrimaryKey @ColumnInfo(name = "token") val token: String,
    @ColumnInfo(name = "request_json") val requestJson: String,
    @ColumnInfo(name = "expires_at") val expiresAt: Long
)

@Dao
interface UploadSessionDao {
    @Query("SELECT * FROM upload_sessions")
    suspend fun allSessions(): List<UploadSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: UploadSessionEntity)

    @Query("DELETE FROM upload_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM approved_tokens WHERE expires_at > :now")
    suspend fun liveTokens(now: Long): List<ApprovedTokenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToken(entity: ApprovedTokenEntity)

    @Query("DELETE FROM approved_tokens WHERE token = :token")
    suspend fun deleteToken(token: String)

    @Query("DELETE FROM approved_tokens WHERE expires_at <= :now")
    suspend fun deleteExpiredTokens(now: Long)
}
