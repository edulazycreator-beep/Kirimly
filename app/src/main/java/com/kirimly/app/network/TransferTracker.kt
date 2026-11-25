package com.kirimly.app.network

import android.os.SystemClock
import com.kirimly.app.data.model.TransferDirection
import com.kirimly.app.data.model.TransferProgress
import com.kirimly.app.data.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe registry of live transfers with throttled progress & speed computation. */
class TransferTracker(private val scope: CoroutineScope) {

    private val _active = MutableStateFlow<List<TransferProgress>>(emptyList())
    val active: StateFlow<List<TransferProgress>> = _active.asStateFlow()

    private val lastEmit = ConcurrentHashMap<String, Long>()
    private val startClock = ConcurrentHashMap<String, Long>()

    fun start(
        fileName: String,
        direction: TransferDirection,
        peerName: String,
        totalBytes: Long,
        encrypted: Boolean = false
    ): String {
        val id = UUID.randomUUID().toString()
        startClock[id] = SystemClock.elapsedRealtime()
        lastEmit[id] = 0L
        val progress = TransferProgress(
            id = id,
            fileName = fileName,
            direction = direction,
            peerName = peerName,
            totalBytes = totalBytes,
            transferredBytes = 0L,
            bytesPerSecond = 0L,
            status = TransferStatus.IN_PROGRESS,
            startedAt = System.currentTimeMillis(),
            encrypted = encrypted
        )
        _active.update { it + progress }
        return id
    }

    fun update(id: String, transferredBytes: Long) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastEmit[id] ?: return
        if (now - previous < THROTTLE_MS) return
        lastEmit[id] = now
        val started = startClock[id] ?: now
        val elapsed = (now - started).coerceAtLeast(1L)
        val speed = transferredBytes * 1000L / elapsed
        _active.update { list ->
            list.map { item ->
                if (item.id == id) item.copy(transferredBytes = transferredBytes, bytesPerSecond = speed) else item
            }
        }
    }

    fun finish(id: String, status: TransferStatus, finalBytes: Long? = null, errorMessage: String? = null) {
        val now = SystemClock.elapsedRealtime()
        val started = startClock.remove(id) ?: now
        lastEmit.remove(id)
        val elapsed = (now - started).coerceAtLeast(1L)
        _active.update { list ->
            list.map { item ->
                if (item.id == id) {
                    val bytes = finalBytes ?: item.transferredBytes
                    item.copy(
                        status = status,
                        transferredBytes = bytes,
                        totalBytes = if (status == TransferStatus.COMPLETED && item.totalBytes <= 0L) bytes else item.totalBytes,
                        bytesPerSecond = bytes * 1000L / elapsed,
                        errorMessage = errorMessage
                    )
                } else item
            }
        }
        scope.launch {
            delay(AUTO_DISMISS_MS)
            dismiss(id)
        }
    }

    fun dismiss(id: String) {
        _active.update { list -> list.filterNot { it.id == id && it.isFinished } }
    }

    fun clearFinished() {
        _active.update { list -> list.filterNot { it.isFinished } }
    }

    companion object {
        private const val THROTTLE_MS = 150L
        private const val AUTO_DISMISS_MS = 15_000L
    }
}
