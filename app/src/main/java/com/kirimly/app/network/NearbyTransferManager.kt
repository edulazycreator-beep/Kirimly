package com.kirimly.app.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.kirimly.app.data.db.TransferDao
import com.kirimly.app.data.db.TransferRecordEntity
import com.kirimly.app.data.model.IncomingFileMeta
import com.kirimly.app.data.model.PeerDevice
import com.kirimly.app.data.model.PeerSendResult
import com.kirimly.app.data.model.PendingTransferRequest
import com.kirimly.app.data.model.SharedFile
import com.kirimly.app.data.model.SharedItem
import com.kirimly.app.data.model.SharedText
import com.kirimly.app.data.model.TransferDirection
import com.kirimly.app.data.model.TransferStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Android ↔ Android transfers with Google Nearby Connections: devices find each other over
 * Bluetooth LE without sharing a Wi-Fi network, then Nearby upgrades the link to Wi-Fi Direct /
 * a private hotspot for speed. The link is encrypted by Nearby (UKEY2), and both screens show
 * the same authentication digits. Needs Google Play Services; failures are reported, not fatal.
 */
class NearbyTransferManager(
    context: Context,
    private val scope: CoroutineScope,
    private val dao: TransferDao,
    private val tracker: TransferTracker,
    private val server: KirimlyServerManager
) {

    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    var localName: String = "Android"

    @Volatile
    var localDeviceId: String = ""

    private var advertising = false
    private var discovering = false

    /** Per-connection state, keyed by endpoint id. */
    private class Link(val endpointId: String, val incoming: Boolean) {
        var authDigits: String = ""
        var remoteName: String = "Android"
        var remoteDeviceId: String? = null
        val connected = CompletableDeferred<Boolean>()
        val decision = CompletableDeferred<Boolean>()
        // Receiver side: payload id → file meta and the received payload
        val expected = ConcurrentHashMap<Long, ManifestFile>()
        val received = ConcurrentHashMap<Long, Payload>()
        val trackIds = ConcurrentHashMap<Long, String>()
        // Sender side: payload id → completion
        val outgoing = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()
        val outgoingTrack = ConcurrentHashMap<Long, String>()
    }

    private val links = ConcurrentHashMap<String, Link>()

    @Serializable
    private data class EndpointTag(val n: String, val d: String? = null)

    @Serializable
    data class ManifestFile(val payloadId: Long, val name: String, val size: Long, val mimeType: String)

    @Serializable
    private data class Message(
        val type: String,
        val senderName: String? = null,
        val deviceId: String? = null,
        val files: List<ManifestFile> = emptyList(),
        val texts: List<String> = emptyList()
    )

    // ------------------------------------------------------------------ lifecycle

    fun start() {
        if (advertising && discovering) return
        val tag = json.encodeToString(EndpointTag.serializer(), EndpointTag(localName.take(40), localDeviceId.ifBlank { null }))
            .toByteArray(Charsets.UTF_8)
        if (!advertising) {
            client.startAdvertising(
                tag,
                SERVICE_ID,
                lifecycleCallback,
                AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
            ).addOnSuccessListener {
                advertising = true
                _active.value = true
                _lastError.value = null
            }.addOnFailureListener { error ->
                advertising = false
                _lastError.value = describe(error)
            }
        }
        if (!discovering) {
            client.startDiscovery(
                SERVICE_ID,
                discoveryCallback,
                DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            ).addOnSuccessListener {
                discovering = true
                _active.value = true
            }.addOnFailureListener { error ->
                discovering = false
                _lastError.value = describe(error)
            }
        }
    }

    fun stop() {
        try {
            client.stopAdvertising()
            client.stopDiscovery()
        } catch (_: Exception) {
        }
        advertising = false
        discovering = false
        _active.value = false
        _peers.value = emptyList()
    }

    /** Re-advertises with a new nickname. */
    fun restart() {
        stop()
        start()
    }

    private fun describe(error: Exception): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("MISSING_PERMISSION", true) || text.contains("8034") || text.contains("8032") ->
                "Izin Bluetooth / Perangkat Terdekat diperlukan untuk Nearby"
            text.contains("API_UNAVAILABLE", true) || text.contains("SERVICE_MISSING", true) ->
                "Nearby butuh Google Play Services"
            text.contains("BLUETOOTH", true) -> "Nyalakan Bluetooth untuk menemukan perangkat tanpa Wi-Fi"
            else -> "Nearby tidak tersedia: ${text.take(80)}"
        }
    }

    // ------------------------------------------------------------------ discovery

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val tag = decodeTag(info.endpointInfo, info.endpointName)
            if (tag.d != null && tag.d == localDeviceId) return
            val peer = PeerDevice(
                id = "nearby:$endpointId",
                name = tag.n,
                host = endpointId,
                port = 0,
                pinRequired = false,
                channel = PeerDevice.CHANNEL_NEARBY,
                deviceId = tag.d
            )
            _peers.update { list -> list.filterNot { it.id == peer.id || (peer.deviceId != null && it.deviceId == peer.deviceId) } + peer }
        }

        override fun onEndpointLost(endpointId: String) {
            _peers.update { list -> list.filterNot { it.id == "nearby:$endpointId" } }
        }
    }

    private fun decodeTag(bytes: ByteArray?, fallbackName: String?): EndpointTag = try {
        json.decodeFromString(EndpointTag.serializer(), String(bytes ?: ByteArray(0), Charsets.UTF_8))
    } catch (e: Exception) {
        EndpointTag(fallbackName?.ifBlank { null } ?: "Android", null)
    }

    // ------------------------------------------------------------------ connections

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val link = links.getOrPut(endpointId) { Link(endpointId, info.isIncomingConnection) }
            link.authDigits = info.authenticationDigits
            val tag = decodeTag(info.endpointInfo, info.endpointName)
            link.remoteName = tag.n
            link.remoteDeviceId = tag.d
            // Accept at the transport level; the user approves once the manifest arrives.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val ok = resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK
            links[endpointId]?.connected?.complete(ok)
            if (!ok) links.remove(endpointId)
        }

        override fun onDisconnected(endpointId: String) {
            val link = links.remove(endpointId) ?: return
            link.connected.complete(false)
            link.decision.complete(false)
            link.outgoing.values.forEach { it.complete(false) }
            link.trackIds.values.forEach { tracker.finish(it, TransferStatus.FAILED, errorMessage = "Koneksi Nearby terputus") }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val link = links[endpointId] ?: return
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val message = try {
                        json.decodeFromString(Message.serializer(), String(bytes, Charsets.UTF_8))
                    } catch (e: Exception) {
                        null
                    } ?: return
                    handleMessage(link, message)
                }
                Payload.Type.FILE -> {
                    link.received[payload.id] = payload
                    val meta = link.expected[payload.id]
                    if (meta != null && !link.trackIds.containsKey(payload.id)) {
                        link.trackIds[payload.id] = tracker.start(meta.name, TransferDirection.RECEIVED, link.remoteName, meta.size, true)
                    }
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val link = links[endpointId] ?: return
            val id = update.payloadId
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    link.trackIds[id]?.let { tracker.update(it, update.bytesTransferred) }
                    link.outgoingTrack[id]?.let { tracker.update(it, update.bytesTransferred) }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    link.outgoing[id]?.let { done ->
                        link.outgoingTrack[id]?.let { tracker.finish(it, TransferStatus.COMPLETED, update.totalBytes) }
                        done.complete(true)
                    }
                    if (link.expected.containsKey(id)) scope.launch(Dispatchers.IO) { storeReceived(link, id) }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    link.outgoing[id]?.let { done ->
                        link.outgoingTrack[id]?.let { tracker.finish(it, TransferStatus.FAILED, update.bytesTransferred, "Nearby gagal") }
                        done.complete(false)
                    }
                    link.trackIds[id]?.let { tracker.finish(it, TransferStatus.FAILED, update.bytesTransferred, "Nearby gagal") }
                }
                else -> Unit
            }
        }
    }

    private fun handleMessage(link: Link, message: Message) {
        when (message.type) {
            "manifest" -> scope.launch { onManifest(link, message) }
            "accept" -> link.decision.complete(true)
            "decline" -> link.decision.complete(false)
        }
    }

    // ------------------------------------------------------------------ receiver

    private suspend fun onManifest(link: Link, message: Message) {
        val name = message.senderName?.take(40)?.ifBlank { null } ?: link.remoteName
        val now = System.currentTimeMillis()
        val request = PendingTransferRequest(
            id = UUID.randomUUID().toString(),
            deviceId = message.deviceId ?: link.remoteDeviceId,
            deviceName = name,
            userAgent = "Nearby Connections",
            remoteAddress = "Nearby",
            files = message.files.map { IncomingFileMeta(it.name, it.size, it.mimeType) } +
                message.texts.map { IncomingFileMeta("Teks: " + it.take(40), it.length.toLong(), "text/plain") },
            createdAt = now,
            expiresAt = now + KirimlyServerManager.APPROVAL_TIMEOUT_MS,
            verificationCode = link.authDigits,
            channel = PeerDevice.CHANNEL_NEARBY
        )
        val approved = server.requestApproval(request) == true
        if (!approved) {
            send(link.endpointId, Message(type = "decline"))
            delay(300)
            client.disconnectFromEndpoint(link.endpointId)
            links.remove(link.endpointId)
            return
        }
        message.files.forEach { link.expected[it.payloadId] = it }
        // Files that already started arriving before approval get their progress entry now.
        link.received.keys.forEach { id ->
            val meta = link.expected[id] ?: return@forEach
            if (!link.trackIds.containsKey(id)) {
                link.trackIds[id] = tracker.start(meta.name, TransferDirection.RECEIVED, name, meta.size, true)
            }
        }
        message.texts.forEach { text -> receiveText(text, name) }
        send(link.endpointId, Message(type = "accept"))
        if (message.files.isEmpty()) {
            delay(500)
            client.disconnectFromEndpoint(link.endpointId)
            links.remove(link.endpointId)
        }
    }

    private suspend fun receiveText(text: String, from: String) {
        withContext(Dispatchers.Main) {
            try {
                appContext.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Kirimly", text))
            } catch (_: Exception) {
            }
        }
        withContext(Dispatchers.IO) { dao.insert(TransferRecordEntity.forText(text, TransferDirection.RECEIVED, from)) }
        server.emitEvent(ServerEvent.TextReceived(text, from))
    }

    private suspend fun storeReceived(link: Link, payloadId: Long) {
        val meta = link.expected.remove(payloadId) ?: return
        val payload = link.received.remove(payloadId) ?: return
        val trackId = link.trackIds.remove(payloadId)
            ?: tracker.start(meta.name, TransferDirection.RECEIVED, link.remoteName, meta.size, true)
        val file = payload.asFile()
        try {
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = file?.asUri() ?: throw IllegalStateException("File Nearby tidak ditemukan")
                val result = appContext.contentResolver.openInputStream(uri)?.use { input ->
                    MediaStoreWriter.saveToDownloads(appContext, meta.name, meta.mimeType, input) {}
                } ?: throw IllegalStateException("File Nearby tidak bisa dibaca")
                try {
                    appContext.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                result
            } else {
                @Suppress("DEPRECATION")
                val javaFile = file?.asJavaFile() ?: throw IllegalStateException("File Nearby tidak ditemukan")
                val result = FileInputStream(javaFile).use { input ->
                    MediaStoreWriter.saveToDownloads(appContext, meta.name, meta.mimeType, input) {}
                }
                javaFile.delete()
                result
            }
            dao.insert(
                TransferRecordEntity.forFile(
                    fileName = saved.displayName,
                    sizeBytes = saved.bytesWritten,
                    mimeType = meta.mimeType,
                    direction = TransferDirection.RECEIVED,
                    peerName = link.remoteName,
                    uri = saved.uri.toString()
                )
            )
            tracker.finish(trackId, TransferStatus.COMPLETED, saved.bytesWritten)
            server.emitEvent(ServerEvent.FileReceived(saved.displayName, link.remoteName))
        } catch (e: Exception) {
            tracker.finish(trackId, TransferStatus.FAILED, errorMessage = e.message ?: "Gagal menyimpan")
            server.emitEvent(ServerEvent.FileFailed(meta.name, e.message ?: "Gagal menyimpan"))
        } finally {
            try {
                file?.asParcelFileDescriptor()?.close()
            } catch (_: Exception) {
            }
        }
        if (link.expected.isEmpty()) {
            delay(500)
            client.disconnectFromEndpoint(link.endpointId)
            links.remove(link.endpointId)
        }
    }

    // ------------------------------------------------------------------ sender

    suspend fun send(
        peer: PeerDevice,
        items: List<SharedItem>,
        senderName: String,
        onVerificationCode: (String?) -> Unit
    ): PeerSendResult {
        val endpointId = peer.host
        val files = items.filterIsInstance<SharedFile>()
        val texts = items.filterIsInstance<SharedText>()
        val link = Link(endpointId, incoming = false).also { links[endpointId] = it }
        val tag = json.encodeToString(EndpointTag.serializer(), EndpointTag(senderName.take(40), localDeviceId.ifBlank { null }))
            .toByteArray(Charsets.UTF_8)
        try {
            val requested = CompletableDeferred<Boolean>()
            client.requestConnection(tag, endpointId, lifecycleCallback)
                .addOnSuccessListener { requested.complete(true) }
                .addOnFailureListener { error ->
                    _lastError.value = describe(error)
                    requested.complete(false)
                }
            if (!requested.await()) return PeerSendResult.Failed(_lastError.value ?: "Tidak dapat terhubung lewat Nearby")
            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { link.connected.await() } == true
            if (!connected) return PeerSendResult.Failed("${peer.name} tidak merespons lewat Nearby")
            onVerificationCode(link.authDigits.ifBlank { null })

            // Build file payloads first so their ids can be listed in the manifest.
            val payloads = files.mapNotNull { file ->
                val pfd: ParcelFileDescriptor = try {
                    appContext.contentResolver.openFileDescriptor(file.uri, "r")
                } catch (e: Exception) {
                    null
                } ?: return@mapNotNull null
                file to Payload.fromFile(pfd)
            }
            val manifest = Message(
                type = "manifest",
                senderName = senderName,
                deviceId = localDeviceId.ifBlank { null },
                files = payloads.map { (file, payload) -> ManifestFile(payload.id, file.displayName, file.sizeBytes, file.mimeType) },
                texts = texts.map { it.text }
            )
            send(endpointId, manifest)
            val accepted = withTimeoutOrNull(KirimlyServerManager.APPROVAL_TIMEOUT_MS + 10_000L) { link.decision.await() }
            onVerificationCode(null)
            when (accepted) {
                null -> return PeerSendResult.Timeout
                false -> return PeerSendResult.Declined
                true -> Unit
            }
            texts.forEach { dao.insert(TransferRecordEntity.forText(it.text, TransferDirection.SENT, peer.name)) }
            var sent = 0
            for ((file, payload) in payloads) {
                val done = CompletableDeferred<Boolean>()
                link.outgoing[payload.id] = done
                link.outgoingTrack[payload.id] = tracker.start(file.displayName, TransferDirection.SENT, peer.name, file.sizeBytes, true)
                client.sendPayload(endpointId, payload)
            }
            for ((file, payload) in payloads) {
                val ok = link.outgoing[payload.id]?.await() == true
                if (ok) {
                    sent++
                    dao.insert(
                        TransferRecordEntity.forFile(
                            fileName = file.displayName,
                            sizeBytes = file.sizeBytes,
                            mimeType = file.mimeType,
                            direction = TransferDirection.SENT,
                            peerName = peer.name,
                            uri = file.uri.toString()
                        )
                    )
                }
            }
            return if (sent == files.size) PeerSendResult.Success(sent, texts.size, encrypted = true)
            else PeerSendResult.Failed("$sent dari ${files.size} file terkirim lewat Nearby")
        } catch (e: Exception) {
            return PeerSendResult.Failed(e.message ?: "Nearby gagal")
        } finally {
            onVerificationCode(null)
            delay(800)
            try {
                client.disconnectFromEndpoint(endpointId)
            } catch (_: Exception) {
            }
            links.remove(endpointId)
        }
    }

    private fun send(endpointId: String, message: Message) {
        val bytes = json.encodeToString(Message.serializer(), message).toByteArray(Charsets.UTF_8)
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    companion object {
        private const val SERVICE_ID = "com.kirimly.app.nearby"
        private val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
        private const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
