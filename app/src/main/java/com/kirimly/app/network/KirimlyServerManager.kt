package com.kirimly.app.network

import com.kirimly.app.util.tr
import com.kirimly.app.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.kirimly.app.data.db.ApprovedTokenEntity
import com.kirimly.app.data.db.UploadSessionDao
import com.kirimly.app.data.db.UploadSessionEntity
import com.kirimly.app.data.model.ConnectedBrowser
import com.kirimly.app.util.ThumbnailLoader
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.websocket.DefaultWebSocketServerSession
import javax.crypto.spec.SecretKeySpec
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkRequest
import com.kirimly.app.data.model.UserPreferences
import com.kirimly.app.data.prefs.UserPreferencesRepository
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.security.KeyPair
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.readAvailable
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
    data class TrustedAutoAccepted(val request: PendingTransferRequest) : ServerEvent
    data class AddressChanged(val ip: String) : ServerEvent
    data class BrowserOfferResult(val browserName: String, val accepted: Boolean) : ServerEvent
}

/**
 * Owns the embedded Ktor CIO server: routes for the iOS web portal, peer uploads, PIN checks,
 * approval suspension and the shared-file catalogue exposed to remote clients.
 */
class KirimlyServerManager(
    private val appContext: Context,
    private val dao: TransferDao,
    private val tracker: TransferTracker,
    private val scope: CoroutineScope,
    private val preferences: UserPreferencesRepository,
    private val sessionDao: UploadSessionDao,
    private val isAppInForeground: () -> Boolean
) {

    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    private val secureRandom = SecureRandom()

    /** Host Grotesk for the Safari portal, served locally so it works on an offline hotspot. */
    private val portalFont: ByteArray? by lazy {
        try {
            appContext.assets.open("host-grotesk.woff2").use { it.readBytes() }
        } catch (e: IOException) {
            null
        }
    }
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

    @Volatile
    private var trustedDevices: Map<String, String> = emptyMap()

    @Volatile
    private var convertHeic: Boolean = false

    @Volatile
    private var idleStopMinutes: Int = 15

    @Volatile
    private var ownDeviceId: String = ""

    @Volatile
    private var lastRequestAt: Long = System.currentTimeMillis()

    private val _idleTimeouts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the server has been idle long enough (app in background) to be stopped. */
    val idleTimeouts: SharedFlow<Unit> = _idleTimeouts.asSharedFlow()

    private val browserSessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
    private val _browsers = MutableStateFlow<List<ConnectedBrowser>>(emptyList())

    /** Safari / browser tabs with the portal open right now. */
    val browsers: StateFlow<List<ConnectedBrowser>> = _browsers.asStateFlow()

    /** Server half of the ECDH exchange; regenerated each time the server starts. */
    @Volatile
    private var serverKeyPair: KeyPair = TransferCrypto.generateKeyPair()

    private val uploads = ConcurrentHashMap<String, UploadSession>()
    private val uploadsByFile = ConcurrentHashMap<String, String>()
    private var janitorJob: Job? = null
    private val mdnsResponder = MdnsHostResponder(appContext, scope)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
        val clientPublicKey: String? = null,
        val received: AtomicInteger = AtomicInteger(0)
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    private data class PinAttempt(val failures: Int, val lockedUntil: Long)

    private enum class PinCheck { OK, INVALID, LOCKED }

    // ------------------------------------------------------------------ public API

    fun updatePreferences(prefs: UserPreferences) {
        deviceName = prefs.deviceNickname.ifBlank { "Android" }
        _pinRequired.value = prefs.pinRequired
        convertHeic = prefs.convertHeic
        idleStopMinutes = prefs.idleStopMinutes
        ownDeviceId = prefs.deviceId
        trustedDevices = prefs.trustedDevices.associate { it.id to it.name }
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
        _sharedItems.value.firstOrNull { it.id == id }?.let { releaseCached(it) }
        _sharedItems.update { list -> list.filterNot { it.id == id } }
        broadcast(WsMessage(type = "shared-updated"))
    }

    fun clearSharedItems() {
        _sharedItems.value.forEach { releaseCached(it) }
        _sharedItems.value = emptyList()
        broadcast(WsMessage(type = "shared-updated"))
    }

    private fun releaseCached(item: SharedItem) {
        if (item is SharedFile) scope.launch(Dispatchers.IO) { SharedFileCache.release(appContext, item.uri) }
    }

    fun respondToRequest(requestId: String, accept: Boolean, trust: Boolean = false) {
        if (accept && trust) {
            val request = _pendingRequests.value.firstOrNull { it.id == requestId }
            val id = request?.deviceId
            if (request != null && !id.isNullOrBlank()) {
                trustedDevices = trustedDevices + (id to request.deviceName)
                scope.launch { preferences.addTrustedDevice(id, request.deviceName) }
            }
        }
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
            serverKeyPair = TransferCrypto.generateKeyPair()
            withContext(Dispatchers.IO) { server.start(wait = false) }
            engine = server
            lastRequestAt = System.currentTimeMillis()
            withContext(Dispatchers.IO) { restorePersisted() }
            startJanitor()
            registerNetworkCallback()
            mdnsResponder.start()
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
        janitorJob?.cancel()
        janitorJob = null
        unregisterNetworkCallback()
        mdnsResponder.stop()
        // Uploads stay on disk and in the database; they resume when the server starts again.
        uploads.values.forEach { session ->
            tracker.finish(session.trackId, TransferStatus.FAILED, session.written, "Dijeda — lanjut saat server aktif")
        }
        browserSessions.clear()
        _browsers.value = emptyList()
        uploads.clear()
        uploadsByFile.clear()
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
            allowHeader(DEVICE_HEADER)
            allowHeader(CHUNK_SHA_HEADER)
        }
        intercept(ApplicationCallPipeline.Plugins) {
            lastRequestAt = System.currentTimeMillis()
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
                call.respondText(
                    IosWebPortalHtml.render(tr(R.string.u_lang_code)),
                    ContentType.Text.Html.withCharset(Charsets.UTF_8)
                )
            }

            get("/assets/host-grotesk.woff2") {
                val bytes = portalFont
                if (bytes == null) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(ok = false, message = "Font tidak tersedia"))
                    return@get
                }
                call.response.header(HttpHeaders.CacheControl, "public, max-age=604800, immutable")
                call.respondBytes(bytes, ContentType("font", "woff2"))
            }

            get("/api/info") {
                call.respond(
                    ServerInfoResponse(
                        deviceName = deviceName,
                        pinRequired = _pinRequired.value,
                        sharedCount = _sharedItems.value.size,
                        features = FEATURES,
                        publicKey = TransferCrypto.encodePublicKey(serverKeyPair.public),
                        deviceId = ownDeviceId.ifBlank { null },
                        trusted = isTrusted(call.request.headers[DEVICE_HEADER])
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
                val claimedDeviceId = (body.deviceId ?: call.request.headers[DEVICE_HEADER])?.trim()?.take(64)?.ifBlank { null }
                val request = PendingTransferRequest(
                    id = UUID.randomUUID().toString(),
                    deviceId = claimedDeviceId,
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
                    expiresAt = now + APPROVAL_TIMEOUT_MS,
                    verificationCode = body.publicKey?.let { clientKey ->
                        try {
                            TransferCrypto.decodePublicKey(clientKey)
                            TransferCrypto.verificationCode(TransferCrypto.encodePublicKey(serverKeyPair.public), clientKey)
                        } catch (e: Exception) {
                            null
                        }
                    }
                )
                val approved: Boolean? = if (isTrusted(claimedDeviceId)) {
                    _events.tryEmit(ServerEvent.TrustedAutoAccepted(request))
                    true
                } else {
                    val decision = CompletableDeferred<Boolean>()
                    decisions[request.id] = decision
                    _pendingRequests.update { it + request }
                    _events.tryEmit(ServerEvent.IncomingRequest(request))
                    val result = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { decision.await() }
                    decisions.remove(request.id)
                    _pendingRequests.update { list -> list.filterNot { it.id == request.id } }
                    result
                }

                when (approved) {
                    true -> {
                        val token = UUID.randomUUID().toString()
                        val approval = ApprovedSession(
                            request = request,
                            expiresAt = System.currentTimeMillis() + SESSION_TTL_MS,
                            clientPublicKey = body.publicKey
                        )
                        approvedSessions[token] = approval
                        persistToken(token, approval)
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

            // ---------------- Resumable upload v2 ----------------

            post("/api/upload/init") {
                val body = try {
                    call.receive<UploadInitBody>()
                } catch (e: Exception) {
                    null
                }
                val approval = body?.let { approvedSessions[it.token] }
                if (body == null || approval == null || approval.isExpired()) {
                    call.respond(HttpStatusCode.Unauthorized, SimpleResponse(ok = false, message = "Sesi upload tidak valid"))
                    return@post
                }
                val expected = approval.request.files.getOrNull(body.index)
                if (expected == null) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(ok = false, message = "Indeks file tidak dikenal"))
                    return@post
                }
                // Same file again (reconnect): hand back the existing session and its offset.
                val fileKey = body.token + "#" + body.index
                uploadsByFile[fileKey]?.let { existingId -> uploads[existingId] }?.let { existing ->
                    existing.lastActivity = System.currentTimeMillis()
                    call.respond(
                        UploadInitResponse(
                            uploadId = existing.id,
                            offset = existing.written,
                            chunkSize = CHUNK_SIZE,
                            encrypted = existing.encrypted,
                            serverPublicKey = if (existing.encrypted) TransferCrypto.encodePublicKey(serverKeyPair.public) else null
                        )
                    )
                    return@post
                }
                val name = NetworkUtils.sanitizeFileName(body.name.ifBlank { expected.name })
                val relativeDir = ResumableUploadStore.sanitizeRelativeDir(
                    body.relativePath?.substringBeforeLast('/', "")
                )
                val mime = body.mimeType.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                    ?: expected.mimeType.takeIf { it != "application/octet-stream" }
                    ?: NetworkUtils.resolveMimeType(name)
                val key = (body.publicKey ?: approval.clientPublicKey)?.let { encoded ->
                    try {
                        TransferCrypto.deriveKey(serverKeyPair.private, TransferCrypto.decodePublicKey(encoded), fileKey)
                    } catch (e: Exception) {
                        null
                    }
                }
                if ((body.publicKey ?: approval.clientPublicKey) != null && key == null) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(ok = false, message = "Kunci enkripsi tidak valid"))
                    return@post
                }
                val target = try {
                    withContext(Dispatchers.IO) { ResumableUploadStore.createTarget(appContext, name, mime, relativeDir) }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, SimpleResponse(ok = false, message = e.message ?: "Penyimpanan penuh"))
                    return@post
                }
                val displayPath = if (relativeDir.isBlank()) name else "$relativeDir/$name"
                val session = UploadSession(
                    id = UUID.randomUUID().toString(),
                    token = body.token,
                    index = body.index,
                    fileName = name,
                    mimeType = mime,
                    size = body.size,
                    relativeDir = relativeDir,
                    peerName = approval.request.deviceName,
                    expectedSha256 = body.sha256?.lowercase(),
                    key = key,
                    target = target,
                    trackId = tracker.start(displayPath, TransferDirection.RECEIVED, approval.request.deviceName, body.size, key != null)
                )
                uploads[session.id] = session
                persistSession(session)
                uploadsByFile[fileKey] = session.id
                broadcast(WsMessage(type = "upload-started", fileName = name))
                call.respond(
                    UploadInitResponse(
                        uploadId = session.id,
                        offset = 0L,
                        chunkSize = CHUNK_SIZE,
                        encrypted = key != null,
                        serverPublicKey = if (key != null) TransferCrypto.encodePublicKey(serverKeyPair.public) else null
                    )
                )
            }

            get("/api/upload/status") {
                val session = call.request.queryParameters["id"]?.let { uploads[it] }
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, UploadChunkResponse(offset = 0L, message = "Sesi tidak ditemukan"))
                    return@get
                }
                session.lastActivity = System.currentTimeMillis()
                call.respond(UploadChunkResponse(offset = session.written))
            }

            post("/api/upload/chunk") {
                val session = call.request.queryParameters["id"]?.let { uploads[it] }
                val offset = call.request.queryParameters["offset"]?.toLongOrNull()
                if (session == null || offset == null) {
                    call.respond(HttpStatusCode.NotFound, UploadChunkResponse(offset = 0L, message = "Sesi tidak ditemukan"))
                    return@post
                }
                val declared = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declared != null && declared > MAX_CHUNK_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge, UploadChunkResponse(offset = session.written, message = "Chunk terlalu besar"))
                    return@post
                }
                val raw = try {
                    call.receive<ByteArray>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, UploadChunkResponse(offset = session.written, message = "Chunk terputus"))
                    return@post
                }
                session.mutex.withLock {
                    session.lastActivity = System.currentTimeMillis()
                    if (offset != session.written) {
                        call.respond(HttpStatusCode.Conflict, UploadChunkResponse(offset = session.written, message = "Offset tidak cocok"))
                        return@withLock
                    }
                    val plain = try {
                        session.key?.let { TransferCrypto.decrypt(it, raw) } ?: raw
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.UnprocessableEntity, UploadChunkResponse(offset = session.written, message = "Dekripsi gagal"))
                        return@withLock
                    }
                    val claimed = call.request.headers[CHUNK_SHA_HEADER]?.lowercase()
                    if (claimed != null && claimed != TransferCrypto.sha256Hex(plain)) {
                        call.respond(HttpStatusCode.UnprocessableEntity, UploadChunkResponse(offset = session.written, message = "Checksum chunk tidak cocok"))
                        return@withLock
                    }
                    if (session.size >= 0 && session.written + plain.size > session.size) {
                        call.respond(HttpStatusCode.BadRequest, UploadChunkResponse(offset = session.written, message = "Melebihi ukuran file"))
                        return@withLock
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            ResumableUploadStore.writeAt(appContext, session.target, session.written, plain, plain.size)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, UploadChunkResponse(offset = session.written, message = e.message ?: "Gagal menulis"))
                        return@withLock
                    }
                    session.digest.update(plain)
                    session.written += plain.size
                    tracker.update(session.trackId, session.written)
                    call.respond(UploadChunkResponse(offset = session.written))
                }
            }

            post("/api/upload/complete") {
                val session = call.request.queryParameters["id"]?.let { uploads[it] }
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, UploadCompleteResponse(ok = false, message = "Sesi tidak ditemukan"))
                    return@post
                }
                val body = try {
                    call.receive<UploadCompleteBody>()
                } catch (e: Exception) {
                    UploadCompleteBody()
                }
                session.mutex.withLock {
                    if (session.size >= 0 && session.written != session.size) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            UploadCompleteResponse(ok = false, message = "Baru ${session.written} dari ${session.size} byte")
                        )
                        return@withLock
                    }
                    val actual = TransferCrypto.toHex((session.digest.clone() as java.security.MessageDigest).digest())
                    val expectedHash = (body.sha256 ?: session.expectedSha256)?.lowercase()
                    if (expectedHash != null && expectedHash != actual) {
                        forgetSession(session.id)
                        uploads.remove(session.id)
                        uploadsByFile.remove(session.token + "#" + session.index)
                        withContext(Dispatchers.IO) { ResumableUploadStore.discard(appContext, session.target) }
                        tracker.finish(session.trackId, TransferStatus.FAILED, session.written, "SHA-256 tidak cocok")
                        _events.tryEmit(ServerEvent.FileFailed(session.fileName, "SHA-256 tidak cocok"))
                        call.respond(HttpStatusCode.UnprocessableEntity, UploadCompleteResponse(ok = false, sha256 = actual, message = "SHA-256 tidak cocok, file dibuang"))
                        return@withLock
                    }
                    val result = try {
                        withContext(Dispatchers.IO) { finalizeUpload(session) }
                    } catch (e: Exception) {
                        tracker.finish(session.trackId, TransferStatus.FAILED, session.written, e.message ?: "Gagal menyimpan")
                        call.respond(HttpStatusCode.InternalServerError, UploadCompleteResponse(ok = false, message = e.message ?: "Gagal menyimpan"))
                        return@withLock
                    }
                    forgetSession(session.id)
                    uploads.remove(session.id)
                    uploadsByFile.remove(session.token + "#" + session.index)
                    approvedSessions[session.token]?.let { approval ->
                        if (approval.received.incrementAndGet() >= approval.request.files.size) {
                            approvedSessions.remove(session.token)
                            scope.launch(Dispatchers.IO) { sessionDao.deleteToken(session.token) }
                        }
                    }
                    tracker.finish(session.trackId, TransferStatus.COMPLETED, session.written)
                    _events.tryEmit(ServerEvent.FileReceived(result, session.peerName))
                    broadcast(WsMessage(type = "upload-finished", fileName = result, progress = 1f))
                    call.respond(UploadCompleteResponse(ok = true, savedName = result, sha256 = actual))
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
                                    streamProvider = { KtorInputStream(part.provider()) }
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

            get("/api/download-all") {
                if (!call.requirePin()) return@get
                val items = _sharedItems.value
                if (items.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(ok = false, message = "Tidak ada file dibagikan"))
                    return@get
                }
                val downloader = describeUserAgent(call.request.headers[HttpHeaders.UserAgent].orEmpty())
                val total = items.sumOf { it.sizeBytes.coerceAtLeast(0L) }
                val trackId = tracker.start("Kirimly-${items.size}-item.zip", TransferDirection.SENT, downloader, total)
                call.response.header(HttpHeaders.ContentDisposition, contentDisposition("Kirimly-${System.currentTimeMillis() / 1000}.zip"))
                call.response.header(HttpHeaders.CacheControl, "no-store")
                var sent = 0L
                var ok = false
                try {
                    call.respondOutputStream(contentType = ContentType.Application.Zip, status = HttpStatusCode.OK) {
                        withContext(Dispatchers.IO) {
                            ZipOutputStream(this@respondOutputStream).use { zip ->
                                zip.setLevel(Deflater.NO_COMPRESSION)
                                val used = HashSet<String>()
                                var textIndex = 1
                                for (item in items) {
                                    val entryName = uniqueEntryName(
                                        used,
                                        when (item) {
                                            is SharedFile -> item.displayName
                                            is SharedText -> "teks-${textIndex++}.txt"
                                        }
                                    )
                                    zip.putNextEntry(ZipEntry(entryName))
                                    when (item) {
                                        is SharedText -> {
                                            val bytes = item.text.toByteArray(Charsets.UTF_8)
                                            zip.write(bytes)
                                            sent += bytes.size
                                        }
                                        is SharedFile -> {
                                            appContext.contentResolver.openInputStream(item.uri)?.use { input ->
                                                val buffer = ByteArray(STREAM_BUFFER)
                                                while (true) {
                                                    val read = input.read(buffer)
                                                    if (read == -1) break
                                                    zip.write(buffer, 0, read)
                                                    sent += read
                                                    tracker.update(trackId, sent)
                                                }
                                            }
                                        }
                                    }
                                    zip.closeEntry()
                                }
                                zip.finish()
                            }
                        }
                    }
                    ok = true
                } finally {
                    if (ok) {
                        tracker.finish(trackId, TransferStatus.COMPLETED, sent)
                        _events.tryEmit(ServerEvent.FileDownloaded("${items.size} item (ZIP)", downloader))
                    } else {
                        tracker.finish(trackId, TransferStatus.FAILED, sent, "Unduhan ZIP terputus")
                    }
                }
            }

            webSocket("/ws") {
                val provided = call.request.queryParameters["pin"]
                if (_pinRequired.value && provided != _pin.value && !isTrusted(call.request.queryParameters["device"])) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "PIN tidak valid"))
                    return@webSocket
                }
                val browserId = UUID.randomUUID().toString()
                val browserName = call.request.queryParameters["name"]?.trim()?.take(40)?.ifBlank { null }
                    ?: describeUserAgent(call.request.headers[HttpHeaders.UserAgent].orEmpty())
                browserSessions[browserId] = this
                _browsers.update { it + ConnectedBrowser(browserId, browserName, call.request.origin.remoteHost, System.currentTimeMillis()) }
                send(Frame.Text(jsonCodec.encodeToString(WsMessage.serializer(), WsMessage(type = "hello", message = deviceName))))
                val forwarder = launch {
                    wsBroadcast.collect { message -> send(Frame.Text(message)) }
                }
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        if (text == "ping") {
                            send(Frame.Text(jsonCodec.encodeToString(WsMessage.serializer(), WsMessage(type = "pong"))))
                            continue
                        }
                        val message = try {
                            jsonCodec.decodeFromString(WsMessage.serializer(), text)
                        } catch (e: Exception) {
                            null
                        } ?: continue
                        when (message.type) {
                            "offer-accepted" -> _events.tryEmit(ServerEvent.BrowserOfferResult(browserName, true))
                            "offer-declined" -> _events.tryEmit(ServerEvent.BrowserOfferResult(browserName, false))
                        }
                    }
                } finally {
                    forwarder.cancel()
                    browserSessions.remove(browserId)
                    _browsers.update { list -> list.filterNot { it.id == browserId } }
                    lastRequestAt = System.currentTimeMillis()
                }
            }

            get("/api/thumbnail/{id}") {
                if (!call.requirePin()) return@get
                val item = _sharedItems.value.firstOrNull { it.id == call.parameters["id"] } as? SharedFile
                val bytes = item?.let { file ->
                    withContext(Dispatchers.IO) { ThumbnailLoader.loadJpeg(appContext, file.uri, file.mimeType, THUMBNAIL_PX) }
                }
                if (bytes == null) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(ok = false, message = "Tidak ada pratinjau"))
                    return@get
                }
                call.response.header(HttpHeaders.CacheControl, "private, max-age=3600")
                call.respondBytes(bytes, ContentType.Image.JPEG)
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
        if (isTrusted(request.headers[DEVICE_HEADER] ?: request.queryParameters["device"])) return true
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

    /** Sends the current queue to one connected browser tab, which shows an accept/decline prompt. */
    fun offerToBrowser(browserId: String): Boolean {
        val session = browserSessions[browserId] ?: return false
        val items = _sharedItems.value
        if (items.isEmpty()) return false
        val message = WsMessage(type = "offer", message = deviceName, items = items.map { it.toDto() })
        scope.launch {
            try {
                session.send(Frame.Text(jsonCodec.encodeToString(WsMessage.serializer(), message)))
            } catch (_: Exception) {
            }
        }
        return true
    }

    private fun SharedItem.toDto(): SharedItemDto = when (this) {
        is SharedFile -> SharedItemDto(id = id, type = "file", name = displayName, size = sizeBytes, mimeType = mimeType)
        is SharedText -> SharedItemDto(id = id, type = "text", name = displayName, size = sizeBytes, mimeType = mimeType, text = text)
    }

    private fun checkIdle() {
        val minutes = idleStopMinutes
        if (minutes <= 0 || engine == null) return
        val busy = uploads.isNotEmpty() || _pendingRequests.value.isNotEmpty() || browserSessions.isNotEmpty() ||
            tracker.active.value.any { !it.isFinished }
        if (busy || isAppInForeground()) {
            if (busy) lastRequestAt = System.currentTimeMillis()
            return
        }
        if (System.currentTimeMillis() - lastRequestAt >= minutes * 60_000L) {
            _idleTimeouts.tryEmit(Unit)
        }
    }

    // ---------------- persistence of resumable uploads ----------------

    private suspend fun persistSession(session: UploadSession) {
        try {
            sessionDao.upsertSession(
                UploadSessionEntity(
                    id = session.id,
                    token = session.token,
                    fileIndex = session.index,
                    fileName = session.fileName,
                    mimeType = session.mimeType,
                    size = session.size,
                    relativeDir = session.relativeDir,
                    peerName = session.peerName,
                    expectedSha256 = session.expectedSha256,
                    keyB64 = session.key?.let { android.util.Base64.encodeToString(it.encoded, android.util.Base64.NO_WRAP) },
                    targetUri = session.target.uri.toString(),
                    legacyPath = session.target.legacyFile?.absolutePath,
                    initialName = session.target.initialDisplayName,
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun forgetSession(id: String) {
        scope.launch(Dispatchers.IO) {
            try {
                sessionDao.deleteSession(id)
            } catch (_: Exception) {
            }
        }
    }

    private fun persistToken(token: String, approval: ApprovedSession) {
        val dto = ApprovedTokenDto(
            requestId = approval.request.id,
            deviceId = approval.request.deviceId,
            deviceName = approval.request.deviceName,
            clientPublicKey = approval.clientPublicKey,
            files = approval.request.files.map { FileMetaDto(it.name, it.sizeBytes, it.mimeType) }
        )
        scope.launch(Dispatchers.IO) {
            try {
                sessionDao.upsertToken(
                    ApprovedTokenEntity(token, jsonCodec.encodeToString(ApprovedTokenDto.serializer(), dto), approval.expiresAt)
                )
            } catch (_: Exception) {
            }
        }
    }

    /** Reloads approvals and half-finished uploads after a restart; the digest is rebuilt from disk. */
    private suspend fun restorePersisted() {
        val now = System.currentTimeMillis()
        try {
            sessionDao.liveTokens(now).forEach { entity ->
                if (approvedSessions.containsKey(entity.token)) return@forEach
                val dto = try {
                    jsonCodec.decodeFromString(ApprovedTokenDto.serializer(), entity.requestJson)
                } catch (e: Exception) {
                    null
                } ?: return@forEach
                approvedSessions[entity.token] = ApprovedSession(
                    request = PendingTransferRequest(
                        id = dto.requestId,
                        deviceId = dto.deviceId,
                        deviceName = dto.deviceName,
                        userAgent = "",
                        remoteAddress = "",
                        files = dto.files.map { IncomingFileMeta(it.name, it.size, it.mimeType) },
                        createdAt = now,
                        expiresAt = entity.expiresAt
                    ),
                    expiresAt = entity.expiresAt,
                    clientPublicKey = dto.clientPublicKey
                )
            }
            sessionDao.allSessions().forEach { entity ->
                if (uploads.containsKey(entity.id)) return@forEach
                val target = UploadTarget(
                    uri = Uri.parse(entity.targetUri),
                    legacyFile = entity.legacyPath?.let { java.io.File(it) },
                    initialDisplayName = entity.initialName
                )
                val key = entity.keyB64?.let { SecretKeySpec(android.util.Base64.decode(it, android.util.Base64.NO_WRAP), "AES") }
                val displayPath = if (entity.relativeDir.isBlank()) entity.fileName else entity.relativeDir + "/" + entity.fileName
                val session = UploadSession(
                    id = entity.id,
                    token = entity.token,
                    index = entity.fileIndex,
                    fileName = entity.fileName,
                    mimeType = entity.mimeType,
                    size = entity.size,
                    relativeDir = entity.relativeDir,
                    peerName = entity.peerName,
                    expectedSha256 = entity.expectedSha256,
                    key = key,
                    target = target,
                    trackId = tracker.start(displayPath, TransferDirection.RECEIVED, entity.peerName, entity.size, key != null)
                )
                val restored = try {
                    val input = target.legacyFile?.inputStream() ?: appContext.contentResolver.openInputStream(target.uri)
                    input?.use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            session.digest.update(buffer, 0, read)
                            total += read
                        }
                        total
                    }
                } catch (e: Exception) {
                    null
                }
                if (restored == null) {
                    sessionDao.deleteSession(entity.id)
                    tracker.finish(session.trackId, TransferStatus.FAILED, 0L, "File sementara hilang")
                    return@forEach
                }
                session.written = restored
                tracker.update(session.trackId, restored)
                uploads[session.id] = session
                uploadsByFile[session.token + "#" + session.index] = session.id
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Shows the approval sheet / notification for a request that arrived over another channel
     * (Nearby Connections). Trusted devices are accepted at once. Returns null on timeout.
     */
    suspend fun requestApproval(request: PendingTransferRequest): Boolean? {
        if (isTrusted(request.deviceId)) {
            _events.tryEmit(ServerEvent.TrustedAutoAccepted(request))
            return true
        }
        val decision = CompletableDeferred<Boolean>()
        decisions[request.id] = decision
        _pendingRequests.update { it + request }
        _events.tryEmit(ServerEvent.IncomingRequest(request))
        val result = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { decision.await() }
        decisions.remove(request.id)
        _pendingRequests.update { list -> list.filterNot { it.id == request.id } }
        return result
    }

    fun isTrustedDevice(deviceId: String?): Boolean = isTrusted(deviceId)

    /** Lets other channels raise the same snackbar events (file received, text received). */
    fun emitEvent(event: ServerEvent) {
        _events.tryEmit(event)
    }

    private fun isTrusted(deviceId: String?): Boolean =
        !deviceId.isNullOrBlank() && trustedDevices.containsKey(deviceId)

    /** Publishes a finished v2 upload, optionally converts HEIC, records history. Returns the saved name. */
    private suspend fun finalizeUpload(session: UploadSession): String {
        var name = ResumableUploadStore.publish(appContext, session.target, session.mimeType)
        var uri = session.target.uri
        var mime = session.mimeType
        var size = session.written
        if (convertHeic && ResumableUploadStore.isHeic(session.mimeType, name)) {
            ResumableUploadStore.convertHeicToJpeg(appContext, uri, name, session.relativeDir)?.let { (newUri, newName, newSize) ->
                uri = newUri
                name = newName
                mime = "image/jpeg"
                if (newSize > 0) size = newSize
            }
        }
        val shownName = if (session.relativeDir.isBlank()) name else session.relativeDir + "/" + name
        dao.insert(
            TransferRecordEntity.forFile(
                fileName = shownName,
                sizeBytes = size,
                mimeType = mime,
                direction = TransferDirection.RECEIVED,
                peerName = session.peerName,
                uri = uri.toString()
            )
        )
        return shownName
    }

    /** Drops uploads idle for too long (client vanished) so their pending files do not linger. */
    private fun startJanitor() {
        janitorJob?.cancel()
        janitorJob = scope.launch {
            while (true) {
                delay(JANITOR_INTERVAL_MS)
                val cutoff = System.currentTimeMillis() - UPLOAD_IDLE_TIMEOUT_MS
                uploads.values.filter { it.lastActivity < cutoff }.forEach { stale ->
                    forgetSession(stale.id)
                    uploads.remove(stale.id)
                    uploadsByFile.remove(stale.token + "#" + stale.index)
                    ResumableUploadStore.discard(appContext, stale.target)
                    tracker.finish(stale.trackId, TransferStatus.FAILED, stale.written, "Koneksi terputus terlalu lama")
                }
                approvedSessions.entries.removeAll { it.value.isExpired() }
                sessionDao.deleteExpiredTokens(System.currentTimeMillis())
                checkIdle()
            }
        }
    }

    /** Keeps the advertised IP current when Wi-Fi or the default network changes. */
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onNetworkChanged()
            override fun onLost(network: Network) = onNetworkChanged()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = onNetworkChanged()
        }
        try {
            manager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
            networkCallback = callback
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            appContext.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    private fun onNetworkChanged() {
        scope.launch {
            delay(800L)
            val before = (_serverState.value as? ServerState.Running)?.ip
            refreshAddress()
            val after = (_serverState.value as? ServerState.Running)?.ip
            if (before != null && after != null && before != after) {
                broadcast(WsMessage(type = "address-changed", message = after))
                _events.tryEmit(ServerEvent.AddressChanged(after))
            }
        }
    }

    private fun uniqueEntryName(used: MutableSet<String>, name: String): String {
        if (used.add(name)) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            if (used.add(candidate)) return candidate
            index++
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

    /** Adapts a Ktor [Input] (multipart file part) to a blocking [InputStream]. */
    private class KtorInputStream(private val input: Input) : InputStream() {
        override fun read(): Int {
            if (input.endOfInput) return -1
            return input.readByte().toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (input.endOfInput) return -1
            val read = input.readAvailable(b, off, len)
            return if (read <= 0) -1 else read
        }

        override fun close() {
            input.close()
        }
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
                        val buffer = ByteArray(STREAM_BUFFER)
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
        const val DEVICE_HEADER = "X-Kirimly-Device"
        const val CHUNK_SHA_HEADER = "X-Chunk-Sha256"
        const val CHUNK_SIZE = 8 * 1024 * 1024
        private const val MAX_CHUNK_BYTES = 17L * 1024L * 1024L
        private const val UPLOAD_IDLE_TIMEOUT_MS = 30L * 60L * 1000L
        private const val JANITOR_INTERVAL_MS = 60_000L
        private const val THUMBNAIL_PX = 240
        /** 512 KB reads keep Wi-Fi saturated when serving downloads; 8 KB reads cost many suspends. */
        private const val STREAM_BUFFER = 512 * 1024
        val FEATURES = listOf("chunked-v2", "e2e-ecdh-aesgcm", "zip", "folders", "trusted-devices")
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
