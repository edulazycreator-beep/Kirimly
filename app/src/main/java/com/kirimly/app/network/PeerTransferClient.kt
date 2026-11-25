package com.kirimly.app.network

import android.content.Context
import com.kirimly.app.data.db.TransferDao
import com.kirimly.app.data.db.TransferRecordEntity
import com.kirimly.app.data.model.PeerDevice
import com.kirimly.app.data.model.PeerSendResult
import com.kirimly.app.data.model.SharedFile
import com.kirimly.app.data.model.SharedItem
import com.kirimly.app.data.model.SharedText
import com.kirimly.app.data.model.TransferDirection
import com.kirimly.app.data.model.TransferStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/** Ktor client used for Android ↔ Android transfers and discovery verification pings. */
class PeerTransferClient(
    private val context: Context,
    private val dao: TransferDao,
    private val tracker: TransferTracker
) {

    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = true
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000L
            requestTimeoutMillis = 20_000L
            socketTimeoutMillis = 30_000L
        }
        engine {
            requestTimeout = 0L
        }
    }

    private fun baseUrl(host: String, port: Int): String {
        val formattedHost = if (host.contains(':')) "[$host]" else host
        return "http://$formattedHost:$port"
    }

    /** Lightweight ping used by discovery to confirm a peer is a live Kirimly server. */
    suspend fun fetchInfo(host: String, port: Int): ServerInfoResponse? = withContext(Dispatchers.IO) {
        try {
            val response = client.get(baseUrl(host, port) + "/api/info") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                timeout {
                    connectTimeoutMillis = 2_500L
                    requestTimeoutMillis = 4_000L
                }
            }
            if (response.status.isSuccess()) response.body<ServerInfoResponse>() else null
        } catch (e: Exception) {
            null
        }
    }

    /** Sends every queued item to [peer]: files through request-upload/upload, texts through /api/text. */
    suspend fun sendItems(
        peer: PeerDevice,
        pin: String?,
        items: List<SharedItem>,
        senderName: String
    ): PeerSendResult = withContext(Dispatchers.IO) {
        val files = items.filterIsInstance<SharedFile>()
        val texts = items.filterIsInstance<SharedText>()
        val base = baseUrl(peer.host, peer.port)
        var sentFiles = 0
        var sentTexts = 0
        try {
            if (files.isNotEmpty()) {
                val response = client.post("$base/api/request-upload") {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    pin?.let { header(KirimlyServerManager.PIN_HEADER, it) }
                    contentType(ContentType.Application.Json)
                    setBody(
                        UploadRequestBody(
                            deviceName = senderName,
                            files = files.map { FileMetaDto(it.displayName, it.sizeBytes, it.mimeType) }
                        )
                    )
                    timeout {
                        requestTimeoutMillis = KirimlyServerManager.APPROVAL_TIMEOUT_MS + 15_000L
                        socketTimeoutMillis = KirimlyServerManager.APPROVAL_TIMEOUT_MS + 15_000L
                    }
                }
                when (response.status) {
                    HttpStatusCode.Unauthorized -> return@withContext PeerSendResult.PinRejected
                    HttpStatusCode.TooManyRequests -> return@withContext PeerSendResult.Failed("Terlalu banyak percobaan PIN")
                    HttpStatusCode.Forbidden -> return@withContext PeerSendResult.Declined
                    HttpStatusCode.RequestTimeout -> return@withContext PeerSendResult.Timeout
                    else -> Unit
                }
                if (!response.status.isSuccess()) {
                    return@withContext PeerSendResult.Failed("Server membalas ${response.status.value}")
                }
                val approval = response.body<UploadRequestResponse>()
                val token = approval.token
                if (!approval.approved || token.isNullOrBlank()) return@withContext PeerSendResult.Declined

                files.forEachIndexed { index, file ->
                    if (uploadFile(base, token, index, file, peer.name)) sentFiles++
                }
            }

            texts.forEach { text ->
                val response = client.post("$base/api/text") {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    pin?.let { header(KirimlyServerManager.PIN_HEADER, it) }
                    contentType(ContentType.Application.Json)
                    setBody(TextBody(text = text.text, deviceName = senderName))
                }
                if (response.status == HttpStatusCode.Unauthorized) return@withContext PeerSendResult.PinRejected
                if (response.status.isSuccess()) {
                    sentTexts++
                    dao.insert(TransferRecordEntity.forText(text.text, TransferDirection.SENT, peer.name))
                }
            }

            if (sentFiles == files.size && sentTexts == texts.size) {
                PeerSendResult.Success(sentFiles, sentTexts)
            } else {
                PeerSendResult.Failed("${sentFiles + sentTexts} dari ${files.size + texts.size} item terkirim")
            }
        } catch (e: Exception) {
            PeerSendResult.Failed(e.message ?: "Koneksi ke ${peer.name} gagal")
        }
    }

    private suspend fun uploadFile(base: String, token: String, index: Int, file: SharedFile, peerName: String): Boolean {
        val trackId = tracker.start(file.displayName, TransferDirection.SENT, peerName, file.sizeBytes)
        return try {
            val asciiName = file.displayName.map { if (it.code in 0x20..0x7E && it != '"' && it != '\\') it else '_' }
                .joinToString("")
            val response = client.post("$base/api/upload") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(KirimlyServerManager.TOKEN_HEADER, token)
                parameter("token", token)
                parameter("index", index.toString())
                parameter("name", file.displayName)
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            appendInput(
                                key = "file",
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, file.mimeType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"$asciiName\"")
                                },
                                size = file.sizeBytes.takeIf { it >= 0L }
                            ) {
                                val stream = context.contentResolver.openInputStream(file.uri)
                                    ?: throw IOException("Tidak dapat membaca ${file.displayName}")
                                stream.asInput()
                            }
                        }
                    )
                )
                onUpload { bytesSent, _ -> tracker.update(trackId, bytesSent) }
                timeout {
                    requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = 120_000L
                }
            }
            if (response.status.isSuccess()) {
                tracker.finish(trackId, TransferStatus.COMPLETED, file.sizeBytes.takeIf { it >= 0L })
                dao.insert(
                    TransferRecordEntity.forFile(
                        fileName = file.displayName,
                        sizeBytes = file.sizeBytes,
                        mimeType = file.mimeType,
                        direction = TransferDirection.SENT,
                        peerName = peerName,
                        uri = file.uri.toString()
                    )
                )
                true
            } else {
                tracker.finish(trackId, TransferStatus.FAILED, errorMessage = "HTTP ${response.status.value}")
                false
            }
        } catch (e: Exception) {
            tracker.finish(trackId, TransferStatus.FAILED, errorMessage = e.message ?: "Upload gagal")
            false
        }
    }

    companion object {
        const val USER_AGENT = "Kirimly-Android/1.0"
    }
}
