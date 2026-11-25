package com.kirimly.app.network

import kotlinx.serialization.Serializable

/** Wire models shared by the embedded server and the peer client. */

@Serializable
data class ServerInfoResponse(
    val app: String = "Kirimly",
    val version: String = "1.0.0",
    val deviceName: String,
    val platform: String = "android",
    val pinRequired: Boolean,
    val sharedCount: Int = 0,
    val features: List<String> = emptyList(),
    val trusted: Boolean = false,
    val publicKey: String? = null,
    val deviceId: String? = null
)

@Serializable
data class FileMetaDto(
    val name: String,
    val size: Long = -1L,
    val mimeType: String = "application/octet-stream"
)

@Serializable
data class UploadRequestBody(
    val deviceName: String = "",
    val deviceId: String? = null,
    val publicKey: String? = null,
    val files: List<FileMetaDto> = emptyList()
)

@Serializable
data class UploadRequestResponse(
    val approved: Boolean,
    val token: String? = null,
    val requestId: String? = null,
    val message: String? = null
)

@Serializable
data class UploadResponse(
    val ok: Boolean,
    val saved: List<String> = emptyList(),
    val message: String? = null
)

@Serializable
data class TextBody(
    val text: String = "",
    val deviceName: String = "",
    val deviceId: String? = null,
    /** True when sent by clipboard sync: copied on arrival, kept out of history. */
    val clipboard: Boolean = false
)

@Serializable
data class SimpleResponse(
    val ok: Boolean,
    val message: String? = null
)

@Serializable
data class SharedItemDto(
    val id: String,
    val type: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val text: String? = null
)

@Serializable
data class SharedListResponse(
    val deviceName: String,
    val items: List<SharedItemDto>
)

@Serializable
data class WsMessage(
    val type: String,
    val message: String? = null,
    val fileName: String? = null,
    val progress: Float? = null,
    val items: List<SharedItemDto>? = null,
    val sender: String? = null
)

/** Persisted form of an approved request (see ApprovedTokenEntity). */
@Serializable
data class ApprovedTokenDto(
    val requestId: String,
    val deviceId: String? = null,
    val deviceName: String,
    val clientPublicKey: String? = null,
    val files: List<FileMetaDto>
)

@Serializable
data class UploadInitBody(
    val token: String,
    val index: Int,
    val name: String,
    val size: Long,
    val mimeType: String = "application/octet-stream",
    val relativePath: String? = null,
    val sha256: String? = null,
    val publicKey: String? = null
)

@Serializable
data class UploadInitResponse(
    val uploadId: String,
    val offset: Long,
    val chunkSize: Int,
    val encrypted: Boolean,
    val serverPublicKey: String? = null
)

@Serializable
data class UploadChunkResponse(
    val offset: Long,
    val message: String? = null
)

@Serializable
data class UploadCompleteBody(
    val sha256: String? = null
)

@Serializable
data class UploadCompleteResponse(
    val ok: Boolean,
    val savedName: String? = null,
    val sha256: String? = null,
    val message: String? = null
)
