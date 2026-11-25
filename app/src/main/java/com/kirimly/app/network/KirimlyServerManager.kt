package com.kirimly.app.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.kirimly.app.data.db.TransferDao
import com.kirimly.app.data.db.TransferRecordEntity
import com.kirimly.app.data.model.IncomingFileMeta
import com.kirimly.app.data.model.PendingTransferRequest
import com.kirimly.app.data.model.ServerState
import com.kirimly.app.data.model.SharedFile
import com.kirimly.app.data.model.SharedItem
import com.kirimly.app.data.model.SharedText
import com.kirimly.app.data.model.TransferDirection
import com.kirimly.app.data.model.TransferStatus
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.streams.asStream
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Events surfaced to the UI layer (snackbars, navigation). */
sealed interface ServerEvent {
    data class IncomingRequest(val request: PendingTransferRequest) : ServerEvent
    data class FileReceived(val fileName: String, val from: String) : ServerEvent
    data class FileFailed(val fileName: String, val reason: String) : ServerEvent
    data class TextReceived(val text: String, val from: String) : ServerEvent
    data class FileDownloaded(val fileName: String, val by: String) : ServerEvent
}

/**
 * Owns the embedded Ktor CIO server: routes for the iOS web portal, peer uploads, PIN checks,
 * approval suspension and the shared-file catalogue exposed to remote clients.
 */
class KirimlyServerManager(
    private val appContext: Context,
    private val dao: TransferDao,
    private val tracker: TransferTracker,
    private val scope: CoroutineScope
) {

    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    private val secureRandom = SecureRandom()
    private val engineMutex = Mutex()
    private var engine: ApplicationEngine? = null

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _pin = MutableStateFlow(generatePin())
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _pinRequired = MutableStateFlow(true)
    val pinRequired: StateFlow<Boolean> = _pinRequired.asStateFlow()

    @Volatile
    private var deviceName: String = "Android"

    private val _pendingRequests = MutableStateFlow<List<PendingTransferRequest>>(emptyList())
    val pendingRequests: StateFlow<List<PendingTransferRequest>> = _pendingRequests.asStateFlow()

    private val _sharedItems = MutableStateFlow<List<SharedItem>>(emptyList())
    val sharedItems: StateFlow<List<SharedItem>> = _sharedItems.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private val wsBroadcast = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private val decisions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val approvedSessions = ConcurrentHashMap<String, ApprovedSession>()
    private val failedPinAttempts = ConcurrentHashMap<String, PinAttempt>()

    private data class ApprovedSession(
        val request: PendingTransferRequest,
        val expiresAt: Long,
        val received: AtomicInteger = AtomicInteger(0)
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    private data class PinAttempt(val failures: Int, val lockedUntil: Long)

    private enum class PinCheck { OK, INVALID, LOCKED }

    // ------------------------------------------------------------------ public API

    fun updatePreferences(name: String, pinRequired: Boolean) {
        deviceName = name.ifBlank { "Android" }
        _pinRequired.value = pinRequired
    }

    fun refreshPin() {
        _pin.value = generatePin()
        failedPinAttempts.clear()
        broadcast(WsMessage(type = "pin-changed"))
    }

    fun addSharedItems(items: List<SharedItem>) {
        if (items.isEmpty()) return
        _sharedItems.update { current ->
            val existingUris = current.filterIsInstance<SharedFile>().map { it.uri }.toSet()
            current + items.filterNot { it is SharedFile && it.uri in existingUris }
        }
        broadcast(WsMessage(type = "shared-updated"))
    }

    fun removeSharedItem(id: String) {
        _sharedItems.update { list -> list.filterNot { it.id == id } }
        broadcast(WsMessage(type = "shared-updated"))
    }

    fun clearSharedItems() {
        _sharedItems.value = emptyList()
        broadcast(WsMessage(type = "shared-updated"))
    }

    fun respondToRequest(requestId: String, accept: Boolean) {
        decisions[requestId]?.complete(accept)
    }

    /** Re-reads the LAN address (e.g. after the hotspot comes up) without restarting. */
    fun refreshAddress() {
        val current = _serverState.value
        if (current is ServerState.Running) {
            val ip = NetworkUtils.getLocalIpv4Address() ?: NO_NETWORK_IP
            if (ip != current.ip) _serverState.value = current.copy(ip = ip)
        }
    }

    suspend fun start(): ServerState = engineMutex.withLock {
        val current = _serverState.value
        if (engine != null && current is ServerState.Running) return@withLock current
        _serverState.value = ServerState.Starting
        val port = withContext(Dispatchers.IO) { findAvailablePort() }
        if (port == null) {
            val error = ServerState.Error("Tidak ada port kosong antara $DEFAULT_PORT–${DEFAULT_PORT + PORT_RANGE - 1}")
            _serverState.value = error
            return@withLock error
        }
        try {
            val server = embeddedServer(
                factory = CIO,
                port = port,
                host = "0.0.0.0",
                watchPaths = emptyList(),
                configure = { connectionIdleTimeoutSeconds = CONNECTION_IDLE_TIMEOUT_SECONDS }
            ) {
                kirimlyModule()
            }
            withContext(Dispatchers.IO) { server.start(wait = false) }
            engine = server
            val ip = NetworkUtils.getLocalIpv4Address() ?: NO_NETWORK_IP
            val running = ServerState.Running(ip, port)
            _serverState.value = running
            running
        } catch (e: Exception) {
            engine = null
            val error = ServerState.Error(e.message ?: "Server gagal dijalankan")
            _serverState.value = error
            error
        }
    }

    suspend fun stop() = engineMutex.withLock {
        broadcast(WsMessage(type = "server-stopping"))
        decisions.values.forEach { it.complete(false) }
        decisions.clear()
        approvedSessions.clear()
        _pendingRequests.value = emptyList()
        val server = engine
        engine = null
        if (server != null) {
            withContext(Dispatchers.IO) {
                try {
                    server.stop(STOP_GRACE_MS, STOP_TIMEOUT_MS)
                } catch (_: Exception) {
                }
            }
        }
        _serverState.value = ServerState.Stopped
    }

    // ------------------------------------------------------------------ Ktor module

    private fun Application.kirimlyModule() {
        install(ContentNegotiation) {
            json(jsonCodec)
        }
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(PIN_HEADER)
            allowHeader(TOKEN_HEADER)
        }
        install(WebSockets) {
            pingPeriodMillis = 15_000L
            timeoutMillis = 30_000L
            maxFrameSize = 64L * 1024L
            masking = false
        }

        routing {
            get("/") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondText(IosWebPortalHtml.PAGE, ContentType.Text.Html.withCharset(Charsets.UTF_8))
            }

            get("/api/info") {
                call.respond(
                    ServerInfoResponse(
                        deviceName = deviceName,
                        pinRequired = _pinRequired.value,
                        sharedCount = _sharedItems.value.size
                    )
                )
            }

            post("/api/verify-pin") {
                if (!call.requirePin()) return@post
                call.respond(SimpleResponse(ok = true))
            }

            post("/api/request-upload") {
                if (!call.requirePin()) return@post
                val body = try {
                    call.receive<UploadRequestBody>()
                } catch (e: Exception) {
                    null
                }
                if (body == null || body.files.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, UploadRequestResponse(approved = false, message = "Daftar file kosong"))
                    return@post
                }
                if (body.files.size > MAX_FILES_PER_REQUEST) {
                    call.respond(HttpStatusCode.BadRequest, UploadRequestResponse(approved = false, message = "Maksimal $MAX_FILES_PER_REQUEST file per kiriman"))
                    return@post
                }
                val userAgent = call.request.headers[HttpHeaders.UserAgent].orEmpty()
                val now = System.currentTimeMillis()
                val request = PendingTransferRequest(
                    id = UUID.randomUUID().toString(),
                    deviceName = body.deviceName.trim().take(40).ifBlank { describeUserAgent(userAgent) },
                    userAgent = userAgent,
                    remoteAddress = call.request.origin.remoteHost,
                    files = body.files.map { meta ->
                        val safeName = NetworkUtils.sanitizeFileName(meta.name)
                        IncomingFileMeta(
                            name = safeName,
                            sizeBytes = meta.size,
                            mimeType = meta.mimeType.ifBlank { NetworkUtils.resolveMimeType(safeName) }
                        )
                    },
                    createdAt = now,
                    expiresAt = now + APPROVAL_TIMEOUT_MS
                )
                val decision = CompletableDeferred<Boolean>()
                decisions[request.id] = decision
                _pendingRequests.update { it + request }
                _events.tryEmit(ServerEvent.IncomingRequest(request))

                val approved = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { decision.await() }

                decisions.remove(request.id)
                _pendingRequests.update { list -> list.filterNot { it.id == request.id } }

                when (approved) {
                    true -> {
                        val token = UUID.randomUUID().toString()
                        approvedSessions[token] = ApprovedSession(
                            request = request,
                            expiresAt = System.currentTimeMillis() + SESSION_TTL_MS
                        )
                        call.respond(UploadRequestResponse(approved = true, token = token, requestId = request.id))
                    }
                    false -> call.respond(
                        HttpStatusCode.Forbidden,
                        UploadRequestResponse(approved = false, requestId = request.id, message = "Permintaan ditolak")
                    )
                    null -> call.respond(
                        HttpStatusCode.RequestTimeout,
                        UploadRequestResponse(approved = false, requestId = request.id, message = "Waktu persetujuan habis")
                    )
                }
            }

            post("/api/upload") {
                val token = call.request.headers[TOKEN_HEADER] ?: call.request.queryParameters["token"]
                val session = token?.let { approvedSessions[it] }
                if (token == null || session == null || session.isExpired()) {
                    token?.let { approvedSessions.remove(it) }
                    call.respond(HttpStatusCode.Unauthorized, UploadResponse(ok = false, message = "Sesi upload tidak valid"))
                    return@post
                }
                val index = call.request.queryParameters["index"]?.toIntOrNull()
                val queryName = call.request.queryParameters["name"]
                val expected = index?.let { session.request.files.getOrNull(it) }
                val peerName = session.request.deviceName
                val saved = mutableListOf<String>()
                var failure: String? = null

                try {
                    val multipart = call.receiveMultipart()
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                val rawName = queryName?.takeIf { it.isNotBlank() }
                                    ?: part.originalFileName?.takeIf { it.isNotBlank() }
                                    ?: expected?.name
                                    ?: "kirimly_${System.currentTimeMillis()}"
                                val name = NetworkUtils.sanitizeFileName(rawName)
                                val partType = part.contentType?.withoutParameters()?.toString()
                                val mime = partType
                                    ?.takeIf { it != "application/octet-stream" && it.isNotBlank() }
                                    ?: expected?.mimeType?.takeIf { it != "application/octet-stream" }
                                    ?: NetworkUtils.resolveMimeType(name)
                                val result = receiveFile(
                                    fileName = name,
                                    mimeType = mime,
                                    expectedSize = expected?.sizeBytes ?: -1L,
                                    peerName = peerName,
                                    streamProvider = { part.provider().asStream() }
                                )
                                if (result == null) saved += name else failure = result
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }
                } catch (e: Exception) {
                    failure = e.message ?: "Upload terputus"
                }

                val receivedCount = session.received.incrementAndGet()
                if (receivedCount >= session.request.files.size) approvedSessions.remove(token)

                val failMessage = failure
                if (failMessage == null && saved.isNotEmpty()) {
                    call.respond(UploadResponse(ok = true, saved = saved))
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        UploadResponse(ok = false, saved = saved, message = failMessage ?: "Tidak ada file diterima")
                    )
                }
            }

            post("/api/text") {
                if (!call.requirePin()) return@post
                val body = try {
                    call.receive<TextBody>()
                } catch (e: Exception) {
                    null
                }
                val text = body?.text.orEmpty()
                if (text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(ok = false, message = "Teks kosong"))
                    return@post
                }
                if (text.length > MAX_TEXT_LENGTH) {
                    call.respond(HttpStatusCode.PayloadTooLarge, SimpleResponse(ok = false, message = "Teks terlalu panjang"))
                    return@post
                }
                val userAgent = call.request.headers[HttpHeaders.UserAgent].orEmpty()
                val from = body?.deviceName?.trim()?.take(40)?.ifBlank { null } ?: describeUserAgent(userAgent)
                withContext(Dispatchers.Main) {
                    try {
                        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Kirimly", text))
                    } catch (_: Exception) {
                    }
                }
                withContext(Dispatchers.IO) {
                    dao.insert(TransferRecordEntity.forText(text, TransferDirection.RECEIVED, from))
                }
                _events.tryEmit(ServerEvent.TextReceived(text, from))
                call.respond(SimpleResponse(ok = true))
            }

            get("/api/shared-files") {
                if (!call.requirePin()) return@get
                val items = _sharedItems.value.map { item ->
                    when (item) {
                        is SharedFile -> SharedItemDto(
                            id = item.id,
                            type = "file",
                            name = item.displayName,
                            size = item.sizeBytes,
                            mimeType = item.mimeType
                        )
                        is SharedText -> SharedItemDto(
                            id = item.id,
                            type = "text",
                            name = item.displayName,
                            size = item.sizeBytes,
                            mimeType = item.mimeType,
                            text = item.text
                        )
                    }
                }
                call.respond(SharedListResponse(deviceName = deviceName, items = items))
            }

            get("/api/download/{id}") {
                if (!call.requirePin()) return@get
                val id = call.parameters["id"]
                val item = _sharedItems.value.firstOrNull { it.id == id }
                if (item == null) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(ok = false, message = "File tidak ditemukan"))
                    return@get
                }
                val userAgent = call.request.headers[HttpHeaders.UserAgent].orEmpty()
                val downloader = describeUserAgent(userAgent)
                when (item) {
                    is SharedText -> {
                        call.response.header(HttpHeaders.ContentDisposition, contentDisposition("kirimly-teks.txt"))
                        call.respondText(item.text, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                        withContext(Dispatchers.IO) {
                            dao.insert(TransferRecordEntity.forText(item.text, TransferDirection.SENT, downloader))
                        }
                    }
                    is SharedFile -> {
                        val contentType = try {
                            ContentType.parse(item.mimeType)
                        } catch (e: Exception) {
                            ContentType.Application.OctetStream
                        }
                        val trackId = tracker.start(item.displayName, TransferDirection.SENT, downloader, item.sizeBytes)
                        call.response.header(HttpHeaders.ContentDisposition, contentDisposition(item.displayName))
                        call.response.header(HttpHeaders.CacheControl, "no-store")
                        call.respond(
                            UriStreamContent(
                                context = appContext,
                                uri = item.uri,
                                contentType = contentType,
                                contentLength = item.sizeBytes.takeIf { it >= 0L },
                                onProgress = { bytes -> tracker.update(trackId, bytes) },
                                onComplete = { success, bytes ->
                                    if (success) {
                                        tracker.finish(trackId, TransferStatus.COMPLETED, bytes)
                                        scope.launch(Dispatchers.IO) {
                                            dao.insert(
                                                TransferRecordEntity.forFile(
                                                    fileName = item.displayName,
                                                    sizeBytes = bytes,
                                                    mimeType = item.mimeType,
                                                    direction = TransferDirection.SENT,
                                                    peerName = downloader,
                                                    uri = item.uri.toString()
                                                )
                                            )
                                        }
                                        _events.tryEmit(ServerEvent.FileDownloaded(item.displayName, downloader))
                                    } else {
                                        tracker.finish(trackId, TransferStatus.FAILED, bytes, "Unduhan terputus")
                                    }
                                }
                            )
                        )
                    }
                }
            }

            webSocket("/ws") {
                val provided = call.request.queryParameters["pin"]
                if (_pinRequired.value && provided != _pin.value) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "PIN tidak valid"))
                    return@webSocket
                }
                send(Frame.Text(jsonCodec.encodeToString(WsMessage.serializer(), WsMessage(type = "hello", message = deviceName))))
                val forwarder = launch {
                    wsBroadcast.collect { message -> send(Frame.Text(message)) }
                }
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text && frame.readText() == "ping") {
                            send(Frame.Text(jsonCodec.encodeToString(WsMessage.serializer(), WsMessage(type = "pong"))))
                        }
                    }
                } finally {
                    forwarder.cancel()
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Saves one streamed file. Returns null on success or an error message. */
    private suspend fun receiveFile(
        fileName: String,
        mimeType: String,
        expectedSize: Long,
        peerName: String,
        streamProvider: () -> InputStream
    ): String? {
        val trackId = tracker.start(fileName, TransferDirection.RECEIVED, peerName, expectedSize)
        broadcast(WsMessage(type = "upload-started", fileName = fileName))
        return try {
            val savedFile = withContext(Dispatchers.IO) {
                streamProvider().use { input ->
                    MediaStoreWriter.saveToDownloads(appContext, fileName, mimeType, input) { bytes ->
                        tracker.update(trackId, bytes)
                    }
                }
            }
            if (expectedSize > 0L && savedFile.bytesWritten < expectedSize) {
                throw IOException("File tidak lengkap (${savedFile.bytesWritten}/$expectedSize byte)")
            }
            withContext(Dispatchers.IO) {
                dao.insert(
                    TransferRecordEntity.forFile(
                        fileName = savedFile.displayName,
                        sizeBytes = savedFile.bytesWritten,
                        mimeType = mimeType,
                        direction = TransferDirection.RECEIVED,
                        peerName = peerName,
                        uri = savedFile.uri.toString()
                    )
                )
            }
            tracker.finish(trackId, TransferStatus.COMPLETED, savedFile.bytesWritten)
            _events.tryEmit(ServerEvent.FileReceived(savedFile.displayName, peerName))
            broadcast(WsMessage(type = "upload-finished", fileName = savedFile.displayName, progress = 1f))
            null
        } catch (e: Exception) {
            val message = e.message ?: "Gagal menyimpan file"
            tracker.finish(trackId, TransferStatus.FAILED, errorMessage = message)
            _events.tryEmit(ServerEvent.FileFailed(fileName, message))
            message
        }
    }

    /** Validates the PIN for this call and responds with 401/429 when invalid. */
    private suspend fun ApplicationCall.requirePin(): Boolean {
        val remote = request.origin.remoteHost
        return when (checkPin(remote, request.headers[PIN_HEADER] ?: request.queryParameters["pin"])) {
            PinCheck.OK -> true
            PinCheck.INVALID -> {
                respond(HttpStatusCode.Unauthorized, SimpleResponse(ok = false, message = "PIN salah atau belum diisi"))
                false
            }
            PinCheck.LOCKED -> {
                respond(HttpStatusCode.TooManyRequests, SimpleResponse(ok = false, message = "Terlalu banyak percobaan. Tunggu 30 detik."))
                false
            }
        }
    }

    private fun checkPin(remote: String, provided: String?): PinCheck {
        if (!_pinRequired.value) return PinCheck.OK
        val now = System.currentTimeMillis()
        val attempt = failedPinAttempts[remote]
        if (attempt != null && attempt.lockedUntil > now) return PinCheck.LOCKED
        if (provided != null && provided == _pin.value) {
            failedPinAttempts.remove(remote)
            return PinCheck.OK
        }
        if (provided.isNullOrBlank()) return PinCheck.INVALID
        val failures = (attempt?.failures ?: 0) + 1
        failedPinAttempts[remote] = if (failures >= MAX_PIN_FAILURES) {
            PinAttempt(0, now + PIN_LOCK_MS)
        } else {
            PinAttempt(failures, 0L)
        }
        return PinCheck.INVALID
    }

    private fun broadcast(message: WsMessage) {
        wsBroadcast.tryEmit(jsonCodec.encodeToString(WsMessage.serializer(), message))
    }

    private fun generatePin(): String = String.format(java.util.Locale.US, "%04d", secureRandom.nextInt(10_000))

    private fun findAvailablePort(): Int? {
        for (candidate in DEFAULT_PORT until DEFAULT_PORT + PORT_RANGE) {
            val free = try {
                ServerSocket().use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(candidate))
                }
                true
            } catch (e: IOException) {
                false
            }
            if (free) return candidate
        }
        return null
    }

    private fun contentDisposition(fileName: String): String {
        val ascii = fileName.map { if (it.code in 0x20..0x7E && it != '"' && it != '\\') it else '_' }.joinToString("")
        val encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        return "attachment; filename=\"$ascii\"; filename*=UTF-8''$encoded"
    }

    /** Streams a content Uri to the client with an 8 KB buffer. */
    private class UriStreamContent(
        private val context: Context,
        private val uri: Uri,
        override val contentType: ContentType,
        override val contentLength: Long?,
        private val onProgress: (Long) -> Unit,
        private val onComplete: (Boolean, Long) -> Unit
    ) : OutgoingContent.WriteChannelContent() {

        override suspend fun writeTo(channel: ByteWriteChannel) {
            var total = 0L
            var success = false
            try {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("File sumber tidak dapat dibuka")
                    input.use { stream ->
                        val buffer = ByteArray(NetworkUtils.BUFFER_SIZE)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            if (read == 0) continue
                            channel.writeFully(buffer, 0, read)
                            total += read
                            onProgress(total)
                        }
                    }
                    channel.flush()
                }
                success = true
            } finally {
                onComplete(success, total)
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 8080
        const val PORT_RANGE = 20
        const val PIN_HEADER = "X-Kirimly-Pin"
        const val TOKEN_HEADER = "X-Kirimly-Token"
        const val NO_NETWORK_IP = "0.0.0.0"
        const val APPROVAL_TIMEOUT_MS = 55_000L
        private const val CONNECTION_IDLE_TIMEOUT_SECONDS = 180
        private const val SESSION_TTL_MS = 6L * 60L * 60L * 1000L
        private const val MAX_FILES_PER_REQUEST = 500
        private const val MAX_TEXT_LENGTH = 1_000_000
        private const val MAX_PIN_FAILURES = 5
        private const val PIN_LOCK_MS = 30_000L
        private const val STOP_GRACE_MS = 300L
        private const val STOP_TIMEOUT_MS = 1_500L

        fun describeUserAgent(userAgent: String): String = when {
            userAgent.contains("Kirimly", ignoreCase = true) -> "Kirimly Android"
            userAgent.contains("iPhone") -> "iPhone"
            userAgent.contains("iPad") -> "iPad"
            userAgent.contains("Macintosh") -> "Mac"
            userAgent.contains("Android") -> "Android"
            userAgent.contains("Windows") -> "Windows PC"
            userAgent.contains("Linux") -> "Linux PC"
            else -> "Perangkat Web"
        }
    }
}
