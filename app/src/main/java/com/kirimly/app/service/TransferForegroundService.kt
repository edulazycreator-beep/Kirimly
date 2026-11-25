package com.kirimly.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kirimly.app.KirimlyApplication
import com.kirimly.app.MainActivity
import com.kirimly.app.R
import com.kirimly.app.data.model.PendingTransferRequest
import com.kirimly.app.data.model.ServerState
import com.kirimly.app.data.model.TransferProgress
import com.kirimly.app.network.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the embedded Ktor server alive while the app is in the background, shows a persistent
 * status/progress notification and actionable notifications for incoming transfer requests.
 */
class TransferForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as KirimlyApplication).container }
    private var observerJob: Job? = null
    private var requestObserverJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isForeground = false
    private val shownRequestIds = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
            }
            ACTION_APPROVE -> {
                intent.getStringExtra(EXTRA_REQUEST_ID)?.let { container.serverManager.respondToRequest(it, true) }
                if (!isForeground) stopSelf(startId)
            }
            ACTION_DECLINE -> {
                intent.getStringExtra(EXTRA_REQUEST_ID)?.let { container.serverManager.respondToRequest(it, false) }
                if (!isForeground) stopSelf(startId)
            }
            else -> startServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        if (!enterForeground(buildStatusNotification(getString(R.string.notif_server_starting), null, null))) {
            stopSelf()
            return
        }
        acquireWifiLock()
        observeState()
        serviceScope.launch {
            val state = container.serverManager.start()
            if (state is ServerState.Running) {
                container.nsdManager.registerService(state.port)
            }
        }
    }

    private fun stopServer() {
        serviceScope.launch {
            container.nsdManager.unregisterService()
            container.serverManager.stop()
            releaseWifiLock()
            cancelRequestNotifications()
            ServiceCompat.stopForeground(this@TransferForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForeground = false
            stopSelf()
        }
    }

    private fun enterForeground(notification: Notification): Boolean {
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(this, STATUS_NOTIFICATION_ID, notification, type)
            isForeground = true
            true
        } catch (e: Exception) {
            isForeground = false
            false
        }
    }

    private fun observeState() {
        if (observerJob?.isActive == true) return
        val manager = container.serverManager
        observerJob = serviceScope.launch {
            combine(manager.serverState, container.transferTracker.active) { state, transfers -> state to transfers }
                .distinctUntilChanged()
                .collect { (state, transfers) -> updateStatusNotification(state, transfers) }
        }
        requestObserverJob = serviceScope.launch {
            manager.pendingRequests.collect { requests -> syncRequestNotifications(requests) }
        }
    }

    private fun updateStatusNotification(state: ServerState, transfers: List<TransferProgress>) {
        if (!isForeground) return
        val running = transfers.filterNot { it.isFinished }
        val title: String
        var progress: Int? = null
        var detail: String? = null
        when (state) {
            is ServerState.Running -> {
                title = getString(R.string.notif_server_running, "${state.ip}:${state.port}")
                if (running.isNotEmpty()) {
                    val total = running.sumOf { it.totalBytes.coerceAtLeast(0L) }
                    val done = running.sumOf { it.transferredBytes }
                    val percent = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else 0
                    val speed = running.sumOf { it.bytesPerSecond }
                    progress = percent
                    detail = getString(R.string.notif_transfers, running.size, percent) +
                        " • " + NetworkUtils.formatSpeed(speed)
                }
            }
            ServerState.Starting -> title = getString(R.string.notif_server_starting)
            is ServerState.Error -> title = getString(R.string.notif_server_error, state.message)
            ServerState.Stopped -> return
        }
        postNotification(STATUS_NOTIFICATION_ID, buildStatusNotification(title, detail, progress))
    }

    private fun buildStatusNotification(title: String, detail: String?, progress: Int?): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, TransferForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_kirimly)
            .setContentTitle(title)
            .setContentText(detail ?: "Buka Kirimly untuk melihat QR dan PIN")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
        if (progress != null) builder.setProgress(100, progress, false)
        return builder.build()
    }

    private fun syncRequestNotifications(requests: List<PendingTransferRequest>) {
        val currentIds = requests.map { it.id }.toSet()
        val manager = NotificationManagerCompat.from(this)
        shownRequestIds.filterNot { it in currentIds }.forEach { id ->
            manager.cancel(requestNotificationId(id))
        }
        shownRequestIds.retainAll(currentIds)
        requests.filterNot { it.id in shownRequestIds }.forEach { request ->
            shownRequestIds += request.id
            postNotification(requestNotificationId(request.id), buildRequestNotification(request))
        }
    }

    private fun buildRequestNotification(request: PendingTransferRequest): Notification {
        val notificationId = requestNotificationId(request.id)
        val accept = PendingIntent.getService(
            this,
            notificationId * 2,
            Intent(this, TransferForegroundService::class.java)
                .setAction(ACTION_APPROVE)
                .putExtra(EXTRA_REQUEST_ID, request.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val decline = PendingIntent.getService(
            this,
            notificationId * 2 + 1,
            Intent(this, TransferForegroundService::class.java)
                .setAction(ACTION_DECLINE)
                .putExtra(EXTRA_REQUEST_ID, request.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = getString(R.string.notif_request_body, request.files.size, NetworkUtils.formatFileSize(request.totalSize))
        val fileList = request.files.take(5).joinToString("\n") { "• ${it.name}" } +
            if (request.files.size > 5) "\n…dan ${request.files.size - 5} lainnya" else ""
        return NotificationCompat.Builder(this, CHANNEL_REQUESTS)
            .setSmallIcon(R.drawable.ic_stat_kirimly)
            .setContentTitle(getString(R.string.notif_request_title, request.deviceName))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$fileList"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter((request.expiresAt - System.currentTimeMillis()).coerceAtLeast(1_000L))
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.notif_decline), decline)
            .addAction(0, getString(R.string.notif_accept), accept)
            .build()
    }

    private fun cancelRequestNotifications() {
        val manager = NotificationManagerCompat.from(this)
        shownRequestIds.forEach { manager.cancel(requestNotificationId(it)) }
        shownRequestIds.clear()
    }

    private fun postNotification(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_CODE_OPEN,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_RECEIVE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        try {
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java) ?: return
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "kirimly:transfer").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            wifiLock = null
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wifiLock = null
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopServer()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        requestObserverJob?.cancel()
        releaseWifiLock()
        cancelRequestNotifications()
        val appContainer = container
        appContainer.appScope.launch {
            appContainer.nsdManager.unregisterService()
            appContainer.serverManager.stop()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.kirimly.app.action.START_SERVER"
        const val ACTION_STOP = "com.kirimly.app.action.STOP_SERVER"
        const val ACTION_APPROVE = "com.kirimly.app.action.APPROVE"
        const val ACTION_DECLINE = "com.kirimly.app.action.DECLINE"
        const val EXTRA_REQUEST_ID = "request_id"

        const val CHANNEL_STATUS = "kirimly_status"
        const val CHANNEL_REQUESTS = "kirimly_requests"
        private const val STATUS_NOTIFICATION_ID = 4210
        private const val REQUEST_CODE_STOP = 11
        private const val REQUEST_CODE_OPEN = 12

        private fun requestNotificationId(requestId: String): Int = 5000 + (requestId.hashCode() and 0x0FFFFF)

        fun start(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val status = NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notif_channel_status),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_status_desc)
                setShowBadge(false)
            }
            val requests = NotificationChannel(
                CHANNEL_REQUESTS,
                context.getString(R.string.notif_channel_requests),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_requests_desc)
                enableVibration(true)
            }
            manager.createNotificationChannels(listOf(status, requests))
        }
    }
}
