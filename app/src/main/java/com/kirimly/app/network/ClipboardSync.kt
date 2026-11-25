package com.kirimly.app.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Kirimly Pro clipboard sync. Android only lets an app read the clipboard while it is in the
 * foreground, so copied text is pushed when Kirimly is opened; incoming clips are copied at once.
 */
class ClipboardSync(
    private val peerClient: PeerTransferClient,
    private val nsdManager: NsdDiscoveryManager,
    private val serverManager: KirimlyServerManager
) {
    /** Last text sent or received; stops the same clip bouncing back and forth. */
    @Volatile
    private var lastText: String? = null

    fun markSeen(text: String) {
        lastText = text
    }

    fun isNew(text: String): Boolean = text.isNotBlank() && text != lastText

    /**
     * Sends [text] to every visible Android peer that remembers this device, plus open Safari tabs.
     * Returns how many destinations got it.
     */
    suspend fun push(text: String, senderName: String): Int {
        lastText = text
        val peers = nsdManager.peers.value
        val toPeers = coroutineScope {
            peers.map { peer ->
                async {
                    val info = peerClient.fetchInfo(peer.host, peer.port)
                    info?.trusted == true && peerClient.sendClipboard(peer, text, senderName)
                }
            }.awaitAll().count { it }
        }
        return toPeers + serverManager.broadcastClipboard(text, senderName)
    }
}
