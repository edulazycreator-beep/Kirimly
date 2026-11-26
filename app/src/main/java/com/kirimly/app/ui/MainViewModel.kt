package com.kirimly.app.ui

import android.app.Activity
import android.app.Application
import android.provider.DocumentsContract
import com.kirimly.app.billing.ProManager
import com.kirimly.app.data.model.SharedFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kirimly.app.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
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

    /** Kirimly Pro (one-time Google Play purchase). */
    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }
    private val proState = combine(preferences, minuteTicker) { prefs, _ -> prefs }

    /** Paid Pro or an active 3-day trial. Re-checked every minute so a trial ends on time. */
    val isPro: StateFlow<Boolean> = proState
        .map { it.isPro }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Remaining trial time in ms while the free trial runs, otherwise null. */
    val trialRemainingMs: StateFlow<Long?> = proState
        .map { prefs -> if (prefs.isTrialActive) prefs.trialEndsAt - System.currentTimeMillis() else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    enum class Prompt { TRIAL_OFFER, REVIEW }

    /** One-off prompts: the trial offer after the 5th transfer, the Play review after the 3rd. */
    private val _prompts = Channel<Prompt>(capacity = Channel.CONFLATED)
    val prompts: Flow<Prompt> = _prompts.receiveAsFlow()

    /** Direct Share asks the Send screen to collect a code for a device that needs one. */
    private val _pinRequests = Channel<PeerDevice>(capacity = Channel.CONFLATED)
    val pinRequests: Flow<PeerDevice> = _pinRequests.receiveAsFlow()
    val proPrice: StateFlow<String?> = container.proManager.price
    val proStatus: StateFlow<ProManager.Status> = container.proManager.status

    val serverState: StateFlow<ServerState> = serverManager.serverState
    val pin: StateFlow<String> = serverManager.pin
    val sharedItems: StateFlow<List<SharedItem>> = serverManager.sharedItems
    val pendingRequests: StateFlow<List<PendingTransferRequest>> = serverManager.pendingRequests
    val activeTransfers: StateFlow<List<TransferProgress>> = container.transferTracker.active

    /** Outgoing transfers (to Android or downloads by Safari) belong on the Send tab. */
    val sendingTransfers: StateFlow<List<TransferProgress>> = activeTransfers
        .map { list -> list.filter { it.direction == TransferDirection.SENT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Incoming transfers belong on the Receive tab. */
    val receivingTransfers: StateFlow<List<TransferProgress>> = activeTransfers
        .map { list -> list.filter { it.direction == TransferDirection.RECEIVED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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

    private data class HistoryView(val items: List<TransferItem>, val hidden: Int)

    private val historyView: StateFlow<HistoryView> = combine(
        dao.observeAll(),
        _historyQuery,
        _historyFilter,
        isPro
    ) { records, query, filter, pro ->
        // Free: the most recent entries, no search. Pro: everything, searchable. Nothing is deleted.
        val needle = if (pro) query.trim() else ""
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
            .let { all ->
                if (pro || all.size <= FREE_HISTORY_LIMIT) HistoryView(all, 0)
                else HistoryView(all.take(FREE_HISTORY_LIMIT), all.size - FREE_HISTORY_LIMIT)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryView(emptyList(), 0))

    val history: StateFlow<List<TransferItem>> = historyView
        .map { it.items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Older entries kept in the database but only shown with Pro. */
    val historyHiddenCount: StateFlow<Int> = historyView
        .map { it.hidden }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _sendingPeerIds = MutableStateFlow<Set<String>>(emptySet())
    val sendingPeerIds: StateFlow<Set<String>> = _sendingPeerIds.asStateFlow()

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
                    is ServerEvent.ClipboardReceived -> _messages.trySend(tr(R.string.p_msg_clip_received, event.from))
                }
            }
        }
        viewModelScope.launch {
            container.proManager.messages.collect { _messages.trySend(it) }
        }
        // Prompts use the real DataStore flow (not the placeholder defaults) so they fire only once.
        viewModelScope.launch {
            combine(dao.observeAll().map { it.size }, container.preferences.preferencesFlow) { count, prefs -> count to prefs }
                .collect { (count, prefs) ->
                    when {
                        count >= TRIAL_AFTER_TRANSFERS && !prefs.isPaidPro && !prefs.trialOffered && prefs.trialEndsAt == 0L -> {
                            container.preferences.setTrialOffered()
                            _prompts.trySend(Prompt.TRIAL_OFFER)
                        }
                        count >= REVIEW_AFTER_TRANSFERS && !prefs.reviewAsked -> {
                            container.preferences.setReviewAsked()
                            _prompts.trySend(Prompt.REVIEW)
                        }
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
        // Direct Share: the person picked one of Kirimly's device shortcuts in the Android share sheet.
        val shortcutId = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
        if (shortcutId != null && (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE)) {
            val name = ShortcutManagerCompat.getDynamicShortcuts(getApplication())
                .firstOrNull { it.id == shortcutId }?.shortLabel?.toString()
            if (name != null) sendWhenVisible(name, items = null)
        }
    }

    /**
     * Waits (up to [FIND_PEER_TIMEOUT_MS]) for a device called [name] to show up on the network and
     * sends to it: [items] when given (Send again), otherwise the queue (Direct Share).
     */
    private fun sendWhenVisible(name: String, items: List<SharedItem>?) {
        container.nsdManager.startDiscovery()
        _messages.trySend(tr(R.string.t_msg_looking_for, name))
        viewModelScope.launch {
            if (items == null) withTimeoutOrNull(5_000L) { sharedItems.first { it.isNotEmpty() } }
            val peer = withTimeoutOrNull(FIND_PEER_TIMEOUT_MS) {
                container.nsdManager.peers.first { list -> list.any { it.name == name } }
            }?.firstOrNull { it.name == name }
            if (peer == null) {
                items?.let { serverManager.addSharedItems(it) }
                _navigation.trySend(ROUTE_SEND)
                _messages.trySend(tr(R.string.t_msg_not_found, name))
                return@launch
            }
            if (items == null) {
                if (peer.pinRequired) _pinRequests.trySend(peer) else sendToPeer(peer, null)
                return@launch
            }
            if (_sendingPeerIds.value.isNotEmpty()) {
                serverManager.addSharedItems(items)
                _navigation.trySend(ROUTE_SEND)
                return@launch
            }
            _sendingPeerIds.value = setOf(peer.id)
            val result = container.peerClient.sendItems(peer, null, items, preferences.value.deviceNickname)
            _sendingPeerIds.value = emptySet()
            when (result) {
                is PeerSendResult.Success -> {
                    pushShareShortcut(peer)
                    _messages.trySend(tr(R.string.u_msg_sent, peer.name))
                }
                PeerSendResult.PinRejected -> {
                    // Needs the code: put it in the queue and let the Send screen ask for it.
                    serverManager.addSharedItems(items)
                    _navigation.trySend(ROUTE_SEND)
                    _pinRequests.trySend(peer)
                }
                PeerSendResult.Declined -> _messages.trySend(tr(R.string.u_msg_declined, peer.name))
                PeerSendResult.Timeout -> _messages.trySend(tr(R.string.u_msg_no_answer, peer.name))
                is PeerSendResult.Failed -> _messages.trySend(tr(R.string.u_msg_send_failed, result.message))
            }
        }
    }

    /** "Send again" from history: the same file or text, to the same device. */
    fun resendFromHistory(item: TransferItem) {
        viewModelScope.launch {
            val shared: SharedItem? = if (item.textContent != null) {
                SharedText(UUID.randomUUID().toString(), item.textContent)
            } else {
                val uri = item.uri?.let(Uri::parse)
                uri?.let { withContext(Dispatchers.IO) { NetworkUtils.querySharedFile(getApplication(), it) } }
            }
            if (shared == null) {
                _messages.trySend(tr(R.string.t_msg_file_gone))
                return@launch
            }
            sendWhenVisible(item.peerName, listOf(shared))
        }
    }

    /**
     * Publishes a long-lived sharing shortcut so this device appears at the top of the Android
     * share sheet (Direct Share). Android ranks the most used ones first.
     */
    private fun pushShareShortcut(peer: PeerDevice) {
        try {
            val app = getApplication<Application>()
            val id = SHORTCUT_PREFIX + peer.name.lowercase().filter { it.isLetterOrDigit() }.take(40).ifBlank { "device" }
            val shortcut = ShortcutInfoCompat.Builder(app, id)
                .setShortLabel(peer.name)
                .setLongLived(true)
                .setPerson(Person.Builder().setName(peer.name).setKey(id).build())
                .setIcon(IconCompat.createWithResource(app, R.mipmap.ic_launcher))
                .setCategories(setOf(SHARE_CATEGORY))
                .setIntent(
                    Intent(app, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_OPEN_TAB, ROUTE_SEND)
                )
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(app, shortcut)
        } catch (_: Exception) {
            // Shortcuts are a convenience; never fail a transfer because of them.
        }
    }

    // ------------------------------------------------------------------ sending

    fun sendToPeer(peer: PeerDevice, pin: String?) {
        val items = sharedItems.value
        if (items.isEmpty()) {
            _messages.trySend(tr(R.string.u_msg_queue_empty))
            return
        }
        if (_sendingPeerIds.value.isNotEmpty()) return
        _sendingPeerIds.value = setOf(peer.id)
        viewModelScope.launch {
            val result = container.peerClient.sendItems(peer, pin, items, preferences.value.deviceNickname)
            _sendingPeerIds.value = emptySet()
            val message = when (result) {
                is PeerSendResult.Success -> {
                    clearQueue()
                    pushShareShortcut(peer)
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

    /**
     * Pro: sends the same queue to several devices at once. Android peers receive in parallel;
     * Safari tabs get the usual accept/download prompt. Peers that still ask for a code are listed.
     */
    fun sendToMany(peers: List<PeerDevice>, browsers: List<ConnectedBrowser>) {
        if (!isPro.value) return
        val items = sharedItems.value
        if (items.isEmpty()) {
            _messages.trySend(tr(R.string.u_msg_queue_empty))
            return
        }
        if (_sendingPeerIds.value.isNotEmpty() || (peers.isEmpty() && browsers.isEmpty())) return
        val offered = browsers.count { serverManager.offerToBrowser(it.id) }
        if (peers.isEmpty()) {
            _messages.trySend(tr(R.string.p_msg_multi_result, offered, browsers.size))
            return
        }
        _sendingPeerIds.value = peers.map { it.id }.toSet()
        val nickname = preferences.value.deviceNickname
        viewModelScope.launch {
            val results = peers.map { peer ->
                async {
                    val result = container.peerClient.sendItems(peer, null, items, nickname)
                    _sendingPeerIds.update { it - peer.id }
                    peer to result
                }
            }.awaitAll()
            results.filter { it.second is PeerSendResult.Success }.forEach { pushShareShortcut(it.first) }
            val ok = results.count { it.second is PeerSendResult.Success } + offered
            val needCode = results.filter { it.second == PeerSendResult.PinRejected }.map { it.first.name }
            // Safari tabs download from the queue, so it stays while they are offered it.
            if (ok == peers.size + browsers.size && browsers.isEmpty()) clearQueue()
            val summary = tr(R.string.p_msg_multi_result, ok, peers.size + browsers.size)
            _messages.trySend(
                if (needCode.isEmpty()) summary
                else summary + " " + tr(R.string.p_msg_multi_need_code, needCode.joinToString(", "))
            )
        }
    }

    /**
     * Pro: adds every file inside a picked folder, keeping "Folder/sub/file" so the receiver
     * recreates the same structure in Downloads/Kirimly.
     */
    fun addFolder(treeUri: Uri) {
        if (!isPro.value) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val (files, skipped) = withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
                val out = mutableListOf<SharedFile>()
                var skippedCount = 0
                val columns = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                )
                fun walk(docId: String, path: String, depth: Int) {
                    if (depth > MAX_FOLDER_DEPTH) return
                    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                    try {
                        app.contentResolver.query(children, columns, null, null, null)?.use { c ->
                            while (c.moveToNext()) {
                                if (out.size >= MAX_FOLDER_FILES) {
                                    skippedCount++
                                    continue
                                }
                                val id = c.getString(0) ?: continue
                                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                                val mime = c.getString(2).orEmpty()
                                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                    walk(id, "$path/$name", depth + 1)
                                } else {
                                    val size = if (c.isNull(3)) -1L else c.getLong(3)
                                    if (size < 0L) {
                                        skippedCount++
                                    } else {
                                        out += SharedFile(
                                            id = UUID.randomUUID().toString(),
                                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                                            displayName = name,
                                            sizeBytes = size,
                                            mimeType = mime.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                                                ?: NetworkUtils.resolveMimeType(name),
                                            relativePath = "$path/$name"
                                        )
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        skippedCount++
                    }
                }
                val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootName = try {
                    app.contentResolver.query(
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                } catch (_: Exception) {
                    null
                }?.takeIf { it.isNotBlank() } ?: "Kirimly"
                walk(rootId, rootName, 0)
                out to skippedCount
            }
            if (files.isEmpty()) {
                _messages.trySend(tr(R.string.p_msg_folder_empty))
            } else {
                serverManager.addSharedItems(files)
                _messages.trySend(
                    if (skipped == 0) tr(R.string.p_msg_folder_added, files.size)
                    else tr(R.string.p_msg_folder_added_skipped, files.size, skipped)
                )
            }
        }
    }

    /** Pro clipboard sync: Android only allows reading the clipboard while Kirimly has focus. */
    fun onAppFocused() {
        val prefs = preferences.value
        if (!prefs.isPro || !prefs.clipboardSync) return
        val text = try {
            readClipboardText()
        } catch (_: Exception) {
            null
        } ?: return
        if (text.length > MAX_CLIP_LENGTH || !container.clipboardSync.isNew(text)) return
        viewModelScope.launch {
            val count = container.clipboardSync.push(text, prefs.deviceNickname)
            if (count > 0) _messages.trySend(tr(R.string.p_msg_clip_sent, count))
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
        if (palette != AppPalette.MIDNIGHT_MONO && !isPro.value) return
        viewModelScope.launch { container.preferences.setPalette(palette) }
    }

    fun buyPro(activity: Activity) = container.proManager.purchase(activity)

    fun startTrial() {
        viewModelScope.launch {
            container.preferences.startTrial(TRIAL_DURATION_MS)
            _messages.trySend(tr(R.string.t_msg_trial_started))
        }
    }

    fun restorePro() {
        viewModelScope.launch { container.proManager.restore() }
    }

    fun setDebugPro(enabled: Boolean) = container.proManager.setDebugPro(enabled)

    fun setClipboardSync(enabled: Boolean) {
        if (enabled && !isPro.value) return
        viewModelScope.launch { container.preferences.setClipboardSync(enabled) }
    }

    fun setPortalName(name: String) {
        if (!isPro.value) return
        viewModelScope.launch { container.preferences.setPortalName(name) }
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
        const val FREE_HISTORY_LIMIT = 20
        const val TRIAL_AFTER_TRANSFERS = 5
        const val REVIEW_AFTER_TRANSFERS = 3
        const val TRIAL_DURATION_MS = 3L * 24 * 60 * 60 * 1000
        private const val FIND_PEER_TIMEOUT_MS = 12_000L
        private const val SHORTCUT_PREFIX = "peer_"
        const val SHARE_CATEGORY = "com.kirimly.app.category.SEND_TO_DEVICE"
        private const val MAX_FOLDER_FILES = 2_000
        private const val MAX_FOLDER_DEPTH = 12
        private const val MAX_CLIP_LENGTH = 100_000
    }
}
