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
import io.ktor.client.request.HttpRequestBuilder
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/**
 * Ktor client for Android ↔ Android transfers and discovery pings. Against a v2 peer it sends
 * files in resumable 4 MB chunks, three files at a time, encrypted end to end (ECDH + AES-GCM)
 * and verified by SHA-256; older peers fall back to a single multipart upload.
 */
class PeerTransferClient(
    private val context: Context,
    private val dao: TransferDao,
    private val tracker: TransferTracker
) {

    /** This install's id, sent so a peer that trusts us can skip PIN and approval. */
    @Volatile
    var deviceId: String = ""

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

    private fun HttpRequestBuilder.identify(pin: String? = null) {
        header(HttpHeaders.UserAgent, USER_AGENT)
        if (deviceId.isNotBlank()) header(KirimlyServerManager.DEVICE_HEADER, deviceId)
        pin?.let { header(KirimlyServerManager.PIN_HEADER, it) }
    }

    /** Lightweight ping used by discovery to confirm a peer is a live Kirimly server. */
    suspend fun fetchInfo(host: String, port: Int): ServerInfoResponse? = withContext(Dispatchers.IO) {
        try {
            val response = client.get(baseUrl(host, port) + "/api/info") {
                identify()
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

    suspend fun sendItems(
        peer: PeerDevice,
        pin: String?,
        items: List<SharedItem>,
        senderName: String,
        onVerificationCode: (String?) -> Unit = {}
    ): PeerSendResult = withContext(Dispatchers.IO) {
        val files = items.filterIsInstance<SharedFile>()
        val texts = items.filterIsInstance<SharedText>()
        val base = baseUrl(peer.host, peer.port)
        var sentFiles = 0
        var sentTexts = 0
        val info = fetchInfo(peer.host, peer.port)
        val chunked = info?.features?.contains("chunked-v2") == true
        val serverKey = info?.publicKey
        val encrypted = chunked && info?.features?.contains("e2e-ecdh-aesgcm") == true && serverKey != null
        // One ephemeral key pair per send: both sides derive per-file keys and the same 6-digit code.
        val sessionKeys = if (encrypted) TransferCrypto.generateKeyPair() else null
        val clientKey = sessionKeys?.let { TransferCrypto.encodePublicKey(it.public) }
        if (clientKey != null && serverKey != null) onVerificationCode(TransferCrypto.verificationCode(serverKey, clientKey))
        try {
            if (files.isNotEmpty()) {
                val response = client.post("$base/api/request-upload") {
                    identify(pin)
                    contentType(ContentType.Application.Json)
                    setBody(
                        UploadRequestBody(
                            deviceName = senderName,
                            deviceId = deviceId.ifBlank { null },
                            publicKey = clientKey,
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
                onVerificationCode(null)
                if (!approval.approved || token.isNullOrBlank()) return@withContext PeerSendResult.Declined

                val gate = Semaphore(PARALLEL_FILES)
                sentFiles = coroutineScope {
                    files.mapIndexed { index, file ->
                        async {
                            gate.withPermit {
                                if (chunked) {
                                    val key = if (sessionKeys != null && serverKey != null) {
                                        TransferCrypto.deriveKey(sessionKeys.private, TransferCrypto.decodePublicKey(serverKey), "$token#$index")
                                    } else null
                                    uploadChunked(base, token, index, file, peer.name, key)
                                }
                                else uploadLegacy(base, token, index, file, peer.name)
                            }
                        }
                    }.awaitAll().count { it }
                }
            }

            texts.forEach { text ->
                val response = client.post("$base/api/text") {
                    identify(pin)
                    contentType(ContentType.Application.Json)
                    setBody(TextBody(text = text.text, deviceName = senderName, deviceId = deviceId.ifBlank { null }))
                }
                if (response.status == HttpStatusCode.Unauthorized) return@withContext PeerSendResult.PinRejected
                if (response.status.isSuccess()) {
                    sentTexts++
                    dao.insert(TransferRecordEntity.forText(text.text, TransferDirection.SENT, peer.name))
                }
            }

            if (sentFiles == files.size && sentTexts == texts.size) {
                PeerSendResult.Success(sentFiles, sentTexts, encrypted)
            } else {
                PeerSendResult.Failed("${sentFiles + sentTexts} dari ${files.size + texts.size} item terkirim")
            }
        } catch (e: Exception) {
            PeerSendResult.Failed(e.message ?: "Koneksi ke ${peer.name} gagal")
        } finally {
            onVerificationCode(null)
        }
    }

    // ------------------------------------------------------------------ v2: resumable chunks

    private class ChunkReader(private val context: Context, private val file: SharedFile) {
        var stream: InputStream? = null
        var position = 0L
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

        /** Reopens the source and re-hashes up to [offset] so the digest matches the server's. */
        fun seekTo(offset: Long) {
            close()
            digest.reset()
            position = 0L
            val input = context.contentResolver.openInputStream(file.uri)
                ?: throw IOException("Tidak dapat membaca ${file.displayName}")
            stream = input
            val buffer = ByteArray(64 * 1024)
            while (position < offset) {
                val want = minOf(buffer.size.toLong(), offset - position).toInt()
                val read = input.read(buffer, 0, want)
                if (read == -1) throw IOException("File sumber lebih pendek dari offset")
                digest.update(buffer, 0, read)
                position += read
            }
        }

        /** Fills [buffer] as far as possible; returns bytes read (0 at end of file). */
        fun read(buffer: ByteArray): Int {
            val input = stream ?: throw IOException("Stream belum dibuka")
            var filled = 0
            while (filled < buffer.size) {
                val read = input.read(buffer, filled, buffer.size - filled)
                if (read == -1) break
                filled += read
            }
            return filled
        }

        fun commit(buffer: ByteArray, length: Int) {
            digest.update(buffer, 0, length)
            position += length
        }

        fun close() {
            try {
                stream?.close()
            } catch (_: Exception) {
            }
            stream = null
        }
    }

    private suspend fun uploadChunked(
        base: String,
        token: String,
        index: Int,
        file: SharedFile,
        peerName: String,
        key: SecretKeySpec?
    ): Boolean {
        val trackId = tracker.start(file.displayName, TransferDirection.SENT, peerName, file.sizeBytes, key != null)
        val reader = ChunkReader(context, file)
        try {
            val init = withRetry {
                val response = client.post("$base/api/upload/init") {
                    identify()
                    contentType(ContentType.Application.Json)
                    setBody(
                        UploadInitBody(
                            token = token,
                            index = index,
                            name = file.displayName,
                            size = file.sizeBytes,
                            mimeType = file.mimeType
                        )
                    )
                }
                if (!response.status.isSuccess()) throw IOException("Init gagal (${response.status.value})")
                response.body<UploadInitResponse>()
            }
            if (key != null && !init.encrypted) throw IOException("Peer menolak enkripsi")

            val chunkSize = init.chunkSize.coerceIn(256 * 1024, 8 * 1024 * 1024)
            val buffer = ByteArray(chunkSize)
            reader.seekTo(init.offset)
            var failures = 0
            while (true) {
                val length = reader.read(buffer)
                if (length == 0) break
                val offset = reader.position
                val payload = if (key != null) TransferCrypto.encrypt(key, buffer, length) else buffer.copyOf(length)
                val chunkSha = TransferCrypto.sha256Hex(buffer, length)
                val outcome = try {
                    val response = client.post("$base/api/upload/chunk") {
                        identify()
                        parameter("id", init.uploadId)
                        parameter("offset", offset.toString())
                        header(KirimlyServerManager.CHUNK_SHA_HEADER, chunkSha)
                        contentType(ContentType.Application.OctetStream)
                        setBody(payload)
                        timeout {
                            requestTimeoutMillis = 120_000L
                            socketTimeoutMillis = 60_000L
                        }
                    }
                    if (response.status.isSuccess()) {
                        reader.commit(buffer, length)
                        tracker.update(trackId, reader.position)
                        failures = 0
                        null
                    } else {
                        response.body<UploadChunkResponse>().offset
                    }
                } catch (e: Exception) {
                    if (++failures > MAX_RETRIES) throw e
                    delay(backoff(failures))
                    serverOffset(base, init.uploadId) ?: throw IOException("Sesi upload hilang")
                }
                if (outcome != null) {
                    // Server is elsewhere (lost reply or rejected chunk): resume from its offset.
                    if (++failures > MAX_RETRIES) throw IOException("Chunk terus ditolak")
                    reader.seekTo(outcome)
                }
            }
            val fullSha = TransferCrypto.toHex(reader.digest.digest())
            val done = withRetry {
                val response = client.post("$base/api/upload/complete") {
                    identify()
                    parameter("id", init.uploadId)
                    contentType(ContentType.Application.Json)
                    setBody(UploadCompleteBody(sha256 = fullSha))
                    timeout { requestTimeoutMillis = 120_000L }
                }
                if (response.status == HttpStatusCode.UnprocessableEntity) {
                    return@withRetry response.body<UploadCompleteResponse>()
                }
                if (!response.status.isSuccess()) throw IOException("Selesai gagal (${response.status.value})")
                response.body<UploadCompleteResponse>()
            }
            if (!done.ok) {
                tracker.finish(trackId, TransferStatus.FAILED, reader.position, done.message ?: "Verifikasi gagal")
                return false
            }
            tracker.finish(trackId, TransferStatus.COMPLETED, reader.position)
            dao.insert(
                TransferRecordEntity.forFile(
                    fileName = file.displayName,
                    sizeBytes = reader.position,
                    mimeType = file.mimeType,
                    direction = TransferDirection.SENT,
                    peerName = peerName,
                    uri = file.uri.toString()
                )
            )
            return true
        } catch (e: Exception) {
            tracker.finish(trackId, TransferStatus.FAILED, reader.position, e.message ?: "Upload gagal")
            return false
        } finally {
            reader.close()
        }
    }

    private suspend fun serverOffset(base: String, uploadId: String): Long? = try {
        val response = client.get("$base/api/upload/status") {
            identify()
            parameter("id", uploadId)
        }
        if (response.status.isSuccess()) response.body<UploadChunkResponse>().offset else null
    } catch (e: Exception) {
        null
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (++attempt > MAX_RETRIES) throw e
                delay(backoff(attempt))
            }
        }
    }

    private fun backoff(attempt: Int): Long = (500L shl (attempt - 1).coerceAtMost(4)).coerceAtMost(8_000L)

    // ------------------------------------------------------------------ v1 fallback

    private suspend fun uploadLegacy(base: String, token: String, index: Int, file: SharedFile, peerName: String): Boolean {
        val trackId = tracker.start(file.displayName, TransferDirection.SENT, peerName, file.sizeBytes)
        return try {
            val asciiName = file.displayName.map { if (it.code in 0x20..0x7E && it != '"' && it != '\\') it else '_' }
                .joinToString("")
            val response = client.post("$base/api/upload") {
                identify()
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
        const val USER_AGENT = "Kirimly-Android/1.1"
        private const val PARALLEL_FILES = 3
        private const val MAX_RETRIES = 8
    }
}
