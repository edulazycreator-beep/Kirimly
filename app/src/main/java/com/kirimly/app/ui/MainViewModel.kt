package com.kirimly.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kirimly.app.KirimlyApplication
import com.kirimly.app.data.model.AppPalette
import com.kirimly.app.data.model.HistoryFilter
import com.kirimly.app.data.model.HotspotState
import com.kirimly.app.data.model.PeerDevice
import com.kirimly.app.data.model.PeerSendResult
import com.kirimly.app.data.model.PendingTransferRequest
import com.kirimly.app.data.model.QrMode
import com.kirimly.app.data.model.ServerState
import com.kirimly.app.data.model.SharedItem
import com.kirimly.app.data.model.SharedText
import com.kirimly.app.data.model.ThemeMode
import com.kirimly.app.data.model.TransferDirection
import com.kirimly.app.data.model.TransferItem
import com.kirimly.app.data.model.TransferProgress
import com.kirimly.app.data.model.UserPreferences
import com.kirimly.app.data.prefs.UserPreferencesRepository
import com.kirimly.app.network.NetworkUtils
import com.kirimly.app.network.QrCodeGenerator
import com.kirimly.app.network.SharedFileCache
import com.kirimly.app.network.ServerEvent
import com.kirimly.app.service.TransferForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Single source of truth for the UI: service state, queue, peers, approvals, history and settings. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as KirimlyApplication).container
    private val serverManager = container.serverManager
    private val dao = container.database.transferDao()

    /** Null until DataStore has delivered its first value; the splash screen waits on this. */
    val loadedPreferences: StateFlow<UserPreferences?> = container.preferences.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val preferences: StateFlow<UserPreferences> = loadedPreferences
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferencesRepository.DEFAULT)

    val serverState: StateFlow<ServerState> = serverManager.serverState
    val pin: StateFlow<String> = serverManager.pin
    val sharedItems: StateFlow<List<SharedItem>> = serverManager.sharedItems
    val pendingRequests: StateFlow<List<PendingTransferRequest>> = serverManager.pendingRequests
    val activeTransfers: StateFlow<List<TransferProgress>> = container.transferTracker.active
    val peers: StateFlow<List<PeerDevice>> = container.nsdManager.peers
    val isDiscovering: StateFlow<Boolean> = container.nsdManager.isDiscovering
    val hotspotState: StateFlow<HotspotState> = container.hotspotManager.state
    val unreachablePeers: StateFlow<List<String>> = container.nsdManager.unreachablePeers

    /** URL encoded in the Safari QR; optionally carries the PIN in the fragment (never sent to the server). */
    val qrServerUrl: StateFlow<String?> = combine(serverState, pin, preferences) { state, currentPin, prefs ->
        val running = state as? ServerState.Running
        when {
            running == null || !running.hasNetwork -> null
            prefs.pinInQr && prefs.pinRequired -> running.url + "/#pin=" + currentPin
            else -> running.url
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Bonjour name Apple devices can open instead of the IP. */
    val localHostUrl: StateFlow<String?> = serverState
        .map { state -> (state as? ServerState.Running)?.let { "http://kirimly.local:${it.port}" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val queueTotalBytes: StateFlow<Long> = sharedItems
        .map { items -> items.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _qrMode = MutableStateFlow(QrMode.SERVER_URL)
    val qrMode: StateFlow<QrMode> = _qrMode.asStateFlow()

    val wifiPayload: StateFlow<String?> = hotspotState
        .map { state ->
            when (state) {
                is HotspotState.Active -> QrCodeGenerator.buildWifiPayload(state.ssid, state.password)
                is HotspotState.Manual -> QrCodeGenerator.buildWifiPayload(state.ssid, state.password)
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _historyQuery = MutableStateFlow("")
    val historyQuery: StateFlow<String> = _historyQuery.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    val history: StateFlow<List<TransferItem>> = combine(
        dao.observeAll(),
        _historyQuery,
        _historyFilter
    ) { records, query, filter ->
        val needle = query.trim()
        records.asSequence()
            .map { it.toDomain() }
            .filter { item ->
                when (filter) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.RECEIVED -> item.direction == TransferDirection.RECEIVED
                    HistoryFilter.SENT -> item.direction == TransferDirection.SENT
                }
            }
            .filter { item ->
                needle.isEmpty() ||
                    item.fileName.contains(needle, ignoreCase = true) ||
                    item.peerName.contains(needle, ignoreCase = true) ||
                    (item.textContent?.contains(needle, ignoreCase = true) == true)
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sendingPeerId = MutableStateFlow<String?>(null)
    val sendingPeerId: StateFlow<String?> = _sendingPeerId.asStateFlow()

    // Channels buffer one-shot events until the UI collects them (e.g. share intent on cold start).
    private val _messages = Channel<String>(capacity = Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _navigation = Channel<String>(capacity = Channel.CONFLATED)
    val navigation: Flow<String> = _navigation.receiveAsFlow()

    init {
        viewModelScope.launch {
            serverManager.events.collect { event ->
                when (event) {
                    is ServerEvent.FileReceived -> _messages.trySend("Diterima: ${event.fileName}")
                    is ServerEvent.FileFailed -> _messages.trySend("Gagal menerima ${event.fileName}: ${event.reason}")
                    is ServerEvent.TextReceived -> _messages.trySend("Teks dari ${event.from} disalin ke clipboard")
                    is ServerEvent.FileDownloaded -> _messages.trySend("${event.by} mengunduh ${event.fileName}")
                    is ServerEvent.IncomingRequest -> Unit
                    is ServerEvent.TrustedAutoAccepted ->
                        _messages.trySend("Kiriman dari ${event.request.deviceName} diterima otomatis (perangkat tepercaya)")
                    is ServerEvent.AddressChanged ->
                        _messages.trySend("Alamat berubah ke ${event.ip} — pindai ulang QR di perangkat lain")
                }
            }
        }
        viewModelScope.launch {
            hotspotState.collect { state ->
                if (state is HotspotState.Active) {
                    delay(1_500L)
                    serverManager.refreshAddress()
                    delay(3_000L)
                    serverManager.refreshAddress()
                }
            }
        }
    }

    // ------------------------------------------------------------------ server

    fun startServer() {
        TransferForegroundService.start(getApplication())
    }

    fun stopServer() {
        TransferForegroundService.stop(getApplication())
    }

    fun toggleServer() {
        when (serverState.value) {
            is ServerState.Running, ServerState.Starting -> stopServer()
            else -> startServer()
        }
    }

    fun refreshAddress() {
        serverManager.refreshAddress()
    }

    fun refreshPin() {
        serverManager.refreshPin()
        _messages.trySend("PIN baru dibuat")
    }

    fun setPinRequired(required: Boolean) {
        viewModelScope.launch { container.preferences.setPinRequired(required) }
    }

    fun respondToRequest(requestId: String, accept: Boolean, trust: Boolean = false) {
        serverManager.respondToRequest(requestId, accept, trust)
    }

    /** Starts the server once per app session when the user enabled automatic start. */
    private var autoStartAttempted = false

    fun maybeAutoStartServer() {
        if (autoStartAttempted) return
        autoStartAttempted = true
        val prefs = loadedPreferences.value ?: return
        if (prefs.autoStartServer && serverState.value is ServerState.Stopped) startServer()
    }

    fun setAutoStartServer(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setAutoStartServer(enabled) }
    }

    fun setPinInQr(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setPinInQr(enabled) }
    }

    fun setConvertHeic(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setConvertHeic(enabled) }
    }

    fun removeTrustedDevice(id: String) {
        viewModelScope.launch { container.preferences.removeTrustedDevice(id) }
        _messages.trySend("Perangkat dihapus dari daftar tepercaya")
    }

    fun setQrMode(mode: QrMode) {
        _qrMode.value = mode
    }

    // ------------------------------------------------------------------ hotspot

    fun hotspotPermission(): String = container.hotspotManager.requiredPermission()

    fun startHotspot() {
        container.hotspotManager.start()
    }

    fun stopHotspot() {
        container.hotspotManager.stop()
        serverManager.refreshAddress()
    }

    fun setManualHotspot(ssid: String, password: String) {
        if (ssid.isBlank()) {
            _messages.trySend("SSID tidak boleh kosong")
            return
        }
        if (password.isNotEmpty() && password.length < 8) {
            _messages.trySend("Kata sandi WPA minimal 8 karakter")
            return
        }
        container.hotspotManager.setManualCredentials(ssid, password)
        serverManager.refreshAddress()
    }

    fun resetHotspot() {
        container.hotspotManager.reset()
    }

    // ------------------------------------------------------------------ discovery

    fun startDiscovery() {
        container.nsdManager.startDiscovery()
    }

    fun stopDiscovery() {
        container.nsdManager.stopDiscovery()
    }

    fun rescan() {
        container.nsdManager.restartDiscovery()
    }

    // ------------------------------------------------------------------ queue

    /**
     * Adds files to the queue. Picker Uris get a persistable grant; share-sheet Uris (or any Uri
     * whose grant cannot be persisted) are copied into the app cache so they stay readable.
     */
    fun addUris(uris: List<Uri>, fromShareSheet: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            var copied = 0
            val items = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val persisted = !fromShareSheet && try {
                        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        true
                    } catch (_: Exception) {
                        false
                    }
                    val meta = NetworkUtils.querySharedFile(app, uri) ?: return@mapNotNull null
                    if (persisted || SharedFileCache.isCached(app, uri)) {
                        meta
                    } else {
                        SharedFileCache.copyIntoCache(app, meta)?.also { copied++ } ?: meta
                    }
                }
            }
            if (items.isEmpty()) {
                _messages.trySend("File tidak dapat dibaca")
            } else {
                serverManager.addSharedItems(items)
                val note = if (copied > 0) " ($copied disalin agar tetap bisa diakses)" else ""
                _messages.trySend("${items.size} file ditambahkan ke antrean$note")
            }
        }
    }

    fun addText(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) {
            _messages.trySend("Teks masih kosong")
            return false
        }
        serverManager.addSharedItems(listOf(SharedText(UUID.randomUUID().toString(), clean)))
        _messages.trySend("Teks ditambahkan ke antrean")
        return true
    }

    fun readClipboardText(): String? {
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(getApplication()).toString().takeIf { it.isNotBlank() }
    }

    fun removeItem(id: String) {
        serverManager.removeSharedItem(id)
    }

    fun clearQueue() {
        serverManager.clearSharedItems()
    }

    /** Ingests ACTION_SEND / ACTION_SEND_MULTIPLE share-sheet intents. */
    fun handleShareIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (stream != null) {
                    addUris(listOf(stream), fromShareSheet = true)
                } else {
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    val combined = listOfNotNull(subject?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
                        .joinToString("\n")
                    if (combined.isNotBlank()) addText(combined)
                }
                _navigation.trySend(ROUTE_SEND)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (!streams.isNullOrEmpty()) addUris(streams.toList(), fromShareSheet = true)
                _navigation.trySend(ROUTE_SEND)
            }
        }
        val tab = intent.getStringExtra(EXTRA_OPEN_TAB)
        if (tab != null) _navigation.trySend(tab)
    }

    // ------------------------------------------------------------------ peer sending

    fun sendToPeer(peer: PeerDevice, pin: String?) {
        val items = sharedItems.value
        if (items.isEmpty()) {
            _messages.trySend("Antrean kosong. Tambahkan file atau teks dulu.")
            return
        }
        if (_sendingPeerId.value != null) {
            _messages.trySend("Masih mengirim ke perangkat lain…")
            return
        }
        _sendingPeerId.value = peer.id
        _messages.trySend("Menunggu persetujuan dari ${peer.name}…")
        viewModelScope.launch {
            val result = container.peerClient.sendItems(peer, pin, items, preferences.value.deviceNickname)
            _sendingPeerId.value = null
            val message = when (result) {
                is PeerSendResult.Success -> {
                    val parts = buildList {
                        if (result.fileCount > 0) add("${result.fileCount} file")
                        if (result.textCount > 0) add("${result.textCount} teks")
                    }.joinToString(" & ")
                    "Terkirim ke ${peer.name}: $parts" + if (result.encrypted) " • terenkripsi" else ""
                }
                PeerSendResult.PinRejected -> "PIN salah untuk ${peer.name}"
                PeerSendResult.Declined -> "${peer.name} menolak kiriman"
                PeerSendResult.Timeout -> "${peer.name} tidak merespons"
                is PeerSendResult.Failed -> "Gagal: ${result.message}"
            }
            _messages.trySend(message)
        }
    }

    // ------------------------------------------------------------------ history

    fun setHistoryQuery(query: String) {
        _historyQuery.value = query
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { dao.clearAll() }
        _messages.trySend("Riwayat dihapus")
    }

    fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Kirimly", text))
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) _messages.trySend("Disalin ke clipboard")
    }

    fun showMessage(message: String) {
        _messages.trySend(message)
    }

    // ------------------------------------------------------------------ preferences

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.preferences.setThemeMode(mode) }
    }

    fun setPalette(palette: AppPalette) {
        viewModelScope.launch { container.preferences.setPalette(palette) }
    }

    fun setNickname(nickname: String) {
        viewModelScope.launch { container.preferences.setDeviceNickname(nickname) }
        _messages.trySend("Nama perangkat disimpan")
    }

    fun completeOnboarding() {
        viewModelScope.launch { container.preferences.setOnboardingCompleted(true) }
    }

    fun replayOnboarding() {
        viewModelScope.launch { container.preferences.setOnboardingCompleted(false) }
    }

    fun dismissTransfer(id: String) {
        container.transferTracker.dismiss(id)
    }

    fun clearFinishedTransfers() {
        container.transferTracker.clearFinished()
    }

    companion object {
        const val ROUTE_SEND = "send"
        const val ROUTE_RECEIVE = "receive"
        const val ROUTE_HISTORY = "history"
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
