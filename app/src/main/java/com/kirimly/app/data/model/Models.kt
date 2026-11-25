package com.kirimly.app.data.model

import com.kirimly.app.util.tr
import com.kirimly.app.R
import androidx.annotation.StringRes
import android.net.Uri

/** Direction of a transfer relative to this device. */
enum class TransferDirection { SENT, RECEIVED }

/** Lifecycle status of a live transfer. */
enum class TransferStatus { QUEUED, AWAITING_APPROVAL, IN_PROGRESS, COMPLETED, FAILED, DECLINED }

/** App theme mode preference. */
enum class ThemeMode(@StringRes private val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark);

    val displayName: String get() = tr(labelRes)

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: DARK
    }
}

/** Curated Material 3 colour palettes. */
enum class AppPalette(val displayName: String, val previewArgb: Long) {
    MIDNIGHT_MONO("Midnight Mono", 0xFF000000),
    OCEAN_COBALT("Ocean Cobalt", 0xFF1F5FD6),
    EMERALD_MINT("Emerald Mint", 0xFF0F7B5C),
    SUNSET_CORAL("Sunset Coral", 0xFFD9534A),
    ROYAL_VIOLET("Royal Violet", 0xFF6A3FD0),
    GRAPHITE_SLATE("Graphite Slate", 0xFF3D4F63);

    companion object {
        fun fromName(name: String?): AppPalette = entries.firstOrNull { it.name == name } ?: MIDNIGHT_MONO
    }
}

/** History filter for the Riwayat screen. */
enum class HistoryFilter(@StringRes private val labelRes: Int) {
    ALL(R.string.filter_all),
    RECEIVED(R.string.filter_received),
    SENT(R.string.filter_sent);

    val displayName: String get() = tr(labelRes)
}

/** Which QR the Receive screen is showing. */
enum class QrMode { SERVER_URL, WIFI_HOTSPOT }

/** A persisted history record (domain representation of the Room entity). */
data class TransferItem(
    val id: Long,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val direction: TransferDirection,
    val peerName: String,
    val timestamp: Long,
    val uri: String?,
    val textContent: String?
) {
    val isText: Boolean get() = textContent != null
}

/** Something queued on this device to be sent / exposed to remote clients. */
sealed interface SharedItem {
    val id: String
    val displayName: String
    val sizeBytes: Long
    val mimeType: String
}

data class SharedFile(
    override val id: String,
    val uri: Uri,
    override val displayName: String,
    override val sizeBytes: Long,
    override val mimeType: String,
    /** "Folder/sub/name.ext" when the file came from a picked folder (Pro); keeps the structure on arrival. */
    val relativePath: String? = null
) : SharedItem

data class SharedText(
    override val id: String,
    val text: String
) : SharedItem {
    override val displayName: String
        get() = text.lineSequence().firstOrNull().orEmpty().take(80).ifBlank { "Teks" }
    override val sizeBytes: Long get() = text.toByteArray(Charsets.UTF_8).size.toLong()
    override val mimeType: String get() = "text/plain"
}

/** A nearby Kirimly device discovered through mDNS. */
data class PeerDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val pinRequired: Boolean,
    val lastSeen: Long = System.currentTimeMillis(),
    /** "lan" = HTTP over Wi-Fi/hotspot, "nearby" = Nearby Connections (Bluetooth + Wi-Fi Direct). */
    val channel: String = CHANNEL_LAN,
    val deviceId: String? = null
) {
    companion object {
        const val CHANNEL_LAN = "lan"
        const val CHANNEL_NEARBY = "nearby"
    }

    val initials: String
        get() = name.split(' ', '-', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
}

/** Metadata of a file a remote client wants to upload. */
data class IncomingFileMeta(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
)

/** A remote upload request waiting for the user's Accept / Decline decision. */
data class PendingTransferRequest(
    val id: String,
    val deviceId: String?,
    val deviceName: String,
    val userAgent: String,
    val remoteAddress: String,
    val files: List<IncomingFileMeta>,
    val createdAt: Long,
    val expiresAt: Long,
    /** Short code both screens show for end-to-end transfers; matching codes rule out a man in the middle. */
    val verificationCode: String? = null,
    /** "lan" for the HTTP server, "nearby" for Nearby Connections. */
    val channel: String = "lan"
) {
    val totalSize: Long get() = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
}

/** Live progress of a single file transfer. */
data class TransferProgress(
    val id: String,
    val fileName: String,
    val direction: TransferDirection,
    val peerName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val bytesPerSecond: Long,
    val status: TransferStatus,
    val startedAt: Long,
    val errorMessage: String? = null,
    val encrypted: Boolean = false
) {
    val fraction: Float
        get() = when {
            status == TransferStatus.COMPLETED -> 1f
            totalBytes <= 0L -> 0f
            else -> (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }
    val isFinished: Boolean
        get() = status == TransferStatus.COMPLETED || status == TransferStatus.FAILED || status == TransferStatus.DECLINED
}

/** State of the embedded Ktor server. */
sealed interface ServerState {
    data object Stopped : ServerState
    data object Starting : ServerState
    data class Running(val ip: String, val port: Int) : ServerState {
        val url: String get() = "http://$ip:$port"
        val hasNetwork: Boolean get() = ip != "0.0.0.0"
    }
    data class Error(val message: String) : ServerState
}

/** State of the local-only hotspot. */
sealed interface HotspotState {
    data object Idle : HotspotState
    data object Starting : HotspotState
    data class Active(val ssid: String, val password: String) : HotspotState
    data class Manual(val ssid: String, val password: String) : HotspotState
    data class Failed(val reason: String) : HotspotState
}

/** Result of sending a queue to a peer Kirimly device. */
sealed interface PeerSendResult {
    data class Success(val fileCount: Int, val textCount: Int, val encrypted: Boolean = false) : PeerSendResult
    data object PinRejected : PeerSendResult
    data object Declined : PeerSendResult
    data object Timeout : PeerSendResult
    data class Failed(val message: String) : PeerSendResult
}

/** Persisted user preferences. */
data class UserPreferences(
    val themeMode: ThemeMode,
    val palette: AppPalette,
    val deviceNickname: String,
    val pinRequired: Boolean,
    val onboardingCompleted: Boolean,
    val autoStartServer: Boolean,
    val pinInQr: Boolean,
    val convertHeic: Boolean,
    val deviceId: String,
    val trustedDevices: List<TrustedDevice>,
    /** Stop the server after this many idle minutes while the app is in the background; 0 = never. */
    val idleStopMinutes: Int,
    /** Cached result of the last Google Play purchase check, so Pro works offline. */
    val proOwned: Boolean = false,
    /** Debug builds only: lets testers turn Pro on without a Play purchase. */
    val debugPro: Boolean = false,
    /** Pro: send copied text to remembered devices automatically when Kirimly is opened. */
    val clipboardSync: Boolean = false,
    /** Pro: name shown on the iPhone / iPad portal instead of the device name. Blank = device name. */
    val portalName: String = ""
) {
    val isPro: Boolean get() = proOwned || (com.kirimly.app.BuildConfig.DEBUG && debugPro)
}

/** A Safari / browser tab currently connected to the portal over WebSocket. */
data class ConnectedBrowser(
    val id: String,
    val name: String,
    val address: String,
    val connectedAt: Long
)

/** A remote device the user chose to trust: it skips the PIN and is accepted automatically. */
data class TrustedDevice(
    val id: String,
    val name: String,
    val addedAt: Long
)
