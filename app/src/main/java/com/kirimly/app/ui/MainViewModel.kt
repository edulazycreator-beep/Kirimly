package com.kirimly.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kirimly.app.KirimlyApplication
import com.kirimly.app.R
import com.kirimly.app.data.model.AppPalette
import com.kirimly.app.data.model.ConnectedBrowser
import com.kirimly.app.data.model.HistoryFilter
import com.kirimly.app.data.model.PeerDevice
import com.kirimly.app.data.model.PeerSendResult
import com.kirimly.app.data.model.PendingTransferRequest
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
import com.kirimly.app.network.ServerEvent
import com.kirimly.app.network.SharedFileCache
import com.kirimly.app.service.TransferForegroundService
import com.kirimly.app.util.CrashLogger
import com.kirimly.app.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Single source of truth for the UI. One method: devices on the same Wi-Fi network. */
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
    val unreachablePeers: StateFlow<List<String>> = container.nsdManager.unreachablePeers
    val browsers: StateFlow<List<ConnectedBrowser>> = serverManager.browsers

    val queueTotalBytes: StateFlow<Long> = sharedItems
        .map { items -> items.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** The one QR code: address plus PIN in the fragment, so scanning needs no typing. */
    val qrUrl: StateFlow<String?> = combine(serverState, pin, preferences) { state, currentPin, prefs ->
        val running = state as? ServerState.Running
        when {
            running == null || !running.hasNetwork -> null
            prefs.pinRequired -> running.url + "/#pin=" + currentPin
            else -> running.url
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private val _crashLogCount = MutableStateFlow(0)
    val crashLogCount: StateFlow<Int> = _crashLogCount.asStateFlow()

    // Channels buffer one-shot events until the UI collects them (e.g. share intent on cold start).
    private val _messages = Channel<String>(capacity = Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _navigation = Channel<String>(capacity = Channel.CONFLATED)
    val navigation: Flow<String> = _navigation.receiveAsFlow()

    init {
        viewModelScope.launch {
            serverManager.events.collect { event ->
                when (event) {
                    is ServerEvent.FileReceived -> _messages.trySend(tr(R.string.u_msg_received, event.fileName))
                    is ServerEvent.FileFailed -> _messages.trySend(tr(R.string.u_msg_receive_failed, event.fileName, event.reason))
                    is ServerEvent.TextReceived -> _messages.trySend(tr(R.string.u_msg_text_received, event.from))
                    is ServerEvent.FileDownloaded -> _messages.trySend(tr(R.string.u_msg_downloaded, event.by, event.fileName))
                    is ServerEvent.IncomingRequest -> Unit
                    is ServerEvent.TrustedAutoAccepted -> _messages.trySend(tr(R.string.u_msg_auto_accepted, event.request.deviceName))
                    is ServerEvent.BrowserOfferResult -> _messages.trySend(
                        if (event.accepted) tr(R.string.u_msg_browser_downloading, event.browserName)
                        else tr(R.string.u_msg_browser_declined, event.browserName)
                    )
                    is ServerEvent.AddressChanged -> _messages.trySend(tr(R.string.u_msg_address_changed))
                }
            }
        }
    }

    // ------------------------------------------------------------------ server (always on while the app is open)

    fun ensureServerRunning() {
        if (serverState.value is ServerState.Stopped || serverState.value is ServerState.Error) {
            TransferForegroundService.start(getApplication())
        }
    }

    fun stopServer() {
        TransferForegroundService.stop(getApplication())
    }

    fun refreshAddress() {
        serverManager.refreshAddress()
    }

    fun refreshPin() {
        serverManager.refreshPin()
        _messages.trySend(tr(R.string.u_msg_new_code))
    }

    fun respondToRequest(requestId: String, accept: Boolean, trust: Boolean = false) {
        serverManager.respondToRequest(requestId, accept, trust)
    }

    // ------------------------------------------------------------------ discovery

    fun startDiscovery() = container.nsdManager.startDiscovery()

    fun stopDiscovery() = container.nsdManager.stopDiscovery()

    fun rescan() = container.nsdManager.restartDiscovery()

    // ------------------------------------------------------------------ queue

    /**
     * Adds files to the queue. Picker Uris get a persistable grant; share-sheet Uris (or any Uri
     * whose grant cannot be persisted) are copied into the app cache so they stay readable.
     */
    fun addUris(uris: List<Uri>, fromShareSheet: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val items = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val persisted = !fromShareSheet && try {
                        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        true
                    } catch (_: Exception) {
                        false
                    }
                    val meta = NetworkUtils.querySharedFile(app, uri) ?: return@mapNotNull null
                    if (persisted || SharedFileCache.isCached(app, uri)) meta
                    else SharedFileCache.copyIntoCache(app, meta) ?: meta
                }
            }
            if (items.isEmpty()) {
                _messages.trySend(tr(R.string.u_msg_unreadable))
            } else {
                serverManager.addSharedItems(items)
            }
        }
    }

    fun addText(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        serverManager.addSharedItems(listOf(SharedText(UUID.randomUUID().toString(), clean)))
        return true
    }

    fun readClipboardText(): String? {
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(getApplication()).toString().takeIf { it.isNotBlank() }
    }

    fun removeItem(id: String) = serverManager.removeSharedItem(id)

    fun clearQueue() = serverManager.clearSharedItems()

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
        intent.getStringExtra(EXTRA_OPEN_TAB)?.let { _navigation.trySend(it) }
    }

    // ------------------------------------------------------------------ sending

    fun sendToPeer(peer: PeerDevice, pin: String?) {
        val items = sharedItems.value
        if (items.isEmpty()) {
            _messages.trySend(tr(R.string.u_msg_queue_empty))
            return
        }
        if (_sendingPeerId.value != null) return
        _sendingPeerId.value = peer.id
        viewModelScope.launch {
            val result = container.peerClient.sendItems(peer, pin, items, preferences.value.deviceNickname)
            _sendingPeerId.value = null
            val message = when (result) {
                is PeerSendResult.Success -> {
                    clearQueue()
                    tr(R.string.u_msg_sent, peer.name)
                }
                PeerSendResult.PinRejected -> tr(R.string.u_msg_wrong_code, peer.name)
                PeerSendResult.Declined -> tr(R.string.u_msg_declined, peer.name)
                PeerSendResult.Timeout -> tr(R.string.u_msg_no_answer, peer.name)
                is PeerSendResult.Failed -> tr(R.string.u_msg_send_failed, result.message)
            }
            _messages.trySend(message)
        }
    }

    fun offerToBrowser(browser: ConnectedBrowser) {
        if (sharedItems.value.isEmpty()) {
            _messages.trySend(tr(R.string.u_msg_queue_empty))
            return
        }
        _messages.trySend(
            if (serverManager.offerToBrowser(browser.id)) tr(R.string.u_msg_offer_sent, browser.name)
            else tr(R.string.u_msg_browser_gone, browser.name)
        )
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
    }

    fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Kirimly", text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) _messages.trySend(tr(R.string.u_msg_copied))
    }

    fun showMessage(message: String) {
        _messages.trySend(message)
    }

    // ------------------------------------------------------------------ settings

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.preferences.setThemeMode(mode) }
    }

    fun setPalette(palette: AppPalette) {
        viewModelScope.launch { container.preferences.setPalette(palette) }
    }

    fun setNickname(nickname: String) {
        viewModelScope.launch { container.preferences.setDeviceNickname(nickname) }
    }

    fun removeTrustedDevice(id: String) {
        viewModelScope.launch { container.preferences.removeTrustedDevice(id) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { container.preferences.setOnboardingCompleted(true) }
    }

    fun replayOnboarding() {
        viewModelScope.launch { container.preferences.setOnboardingCompleted(false) }
    }

    fun refreshCrashLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _crashLogCount.value = CrashLogger.logs(getApplication()).count { it.name.startsWith("crash-") }
        }
    }

    /** Returns a shareable content Uri bundling all crash logs, or null when there are none. */
    suspend fun crashReportUri(): Uri? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        CrashLogger.bundle(app)?.let { FileProvider.getUriForFile(app, app.packageName + ".fileprovider", it) }
    }

    fun dismissTransfer(id: String) = container.transferTracker.dismiss(id)

    fun clearFinishedTransfers() = container.transferTracker.clearFinished()

    companion object {
        const val ROUTE_SEND = "send"
        const val ROUTE_RECEIVE = "receive"
        const val ROUTE_HISTORY = "history"
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
