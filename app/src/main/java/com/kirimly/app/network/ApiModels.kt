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
    val sharedCount: Int = 0
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
    val deviceName: String = ""
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
    val progress: Float? = null
)
