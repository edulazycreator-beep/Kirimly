package com.kirimly.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.kirimly.app.data.model.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * Registers this device as `_kirimly._tcp` and discovers peers on the LAN. NsdManager can only
 * resolve one service at a time on older Android versions, so resolutions are serialised
 * through a channel and each resolved peer is verified with an HTTP ping before it is shown.
 */
class NsdDiscoveryManager(
    context: Context,
    private val scope: CoroutineScope,
    private val verifier: suspend (host: String, port: Int) -> ServerInfoResponse?
) {

    private val appContext = context.applicationContext
    private val nsdManager: NsdManager? = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager: WifiManager? = appContext.getSystemService(WifiManager::class.java)

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    /**
     * Services that answered mDNS but not HTTP: a strong hint that the Wi-Fi blocks traffic
     * between clients (AP / client isolation, common on office, campus and café networks).
     */
    private val _unreachablePeers = MutableStateFlow<List<String>>(emptyList())
    val unreachablePeers: StateFlow<List<String>> = _unreachablePeers.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    @Volatile
    private var localName: String = "Android"

    @Volatile
    private var registeredServiceName: String? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var resolveChannel: Channel<NsdServiceInfo>? = null
    private var resolveJob: Job? = null
    private var pruneJob: Job? = null
    private var registeredPort: Int = -1

    fun updateLocalName(name: String) {
        val changed = name != localName
        localName = name
        if (changed && registrationListener != null && registeredPort > 0) {
            val port = registeredPort
            unregisterService()
            registerService(port)
        }
    }

    // ------------------------------------------------------------------ registration

    @Synchronized
    fun registerService(port: Int) {
        val manager = nsdManager ?: return
        if (registrationListener != null) {
            if (registeredPort == port) return
            unregisterService()
        }
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = buildServiceName(localName)
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
                _isRegistered.value = true
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                registeredServiceName = null
                _isRegistered.value = false
                synchronized(this@NsdDiscoveryManager) {
                    if (registrationListener === this) registrationListener = null
                }
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                registeredServiceName = null
                _isRegistered.value = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                _isRegistered.value = false
            }
        }
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            registrationListener = listener
            registeredPort = port
        } catch (e: Exception) {
            registrationListener = null
            registeredPort = -1
        }
    }

    @Synchronized
    fun unregisterService() {
        val listener = registrationListener ?: return
        registrationListener = null
        registeredPort = -1
        try {
            nsdManager?.unregisterService(listener)
        } catch (_: Exception) {
        }
        registeredServiceName = null
        _isRegistered.value = false
    }

    // ------------------------------------------------------------------ discovery

    @Synchronized
    fun startDiscovery() {
        val manager = nsdManager ?: return
        if (discoveryListener != null) return
        acquireMulticastLock()

        val channel = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        resolveChannel = channel
        resolveJob = scope.launch {
            for (info in channel) {
                val resolved = resolve(manager, info) ?: continue
                handleResolved(resolved)
            }
        }
        pruneJob = scope.launch {
            while (isActive) {
                delay(PRUNE_INTERVAL_MS)
                verifyPeers()
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _isDiscovering.value = true
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains(SERVICE_TYPE_CORE)) return
                if (info.serviceName == registeredServiceName) return
                channel.trySend(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                _peers.update { list -> list.filterNot { it.id == info.serviceName } }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isDiscovering.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isDiscovering.value = false
                synchronized(this@NsdDiscoveryManager) {
                    if (discoveryListener === this) discoveryListener = null
                }
                scope.launch { cleanupDiscovery() }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isDiscovering.value = false
            }
        }
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            discoveryListener = listener
        } catch (e: Exception) {
            discoveryListener = null
            cleanupDiscovery()
        }
    }

    @Synchronized
    fun stopDiscovery() {
        val listener = discoveryListener
        discoveryListener = null
        if (listener != null) {
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
        cleanupDiscovery()
        _isDiscovering.value = false
        _unreachablePeers.value = emptyList()
    }

    /** Re-scans: stop and start discovery, keeping verified peers. */
    fun restartDiscovery() {
        stopDiscovery()
        startDiscovery()
    }

    @Synchronized
    private fun cleanupDiscovery() {
        resolveChannel?.close()
        resolveChannel = null
        resolveJob?.cancel()
        resolveJob = null
        pruneJob?.cancel()
        pruneJob = null
        releaseMulticastLock()
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(manager: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            suspendCancellableCoroutine<NsdServiceInfo?> { continuation ->
                try {
                    manager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            if (continuation.isActive) continuation.resume(serviceInfo)
                        }
                    })
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    private suspend fun handleResolved(info: NsdServiceInfo) {
        if (info.serviceName == registeredServiceName) return
        val host = extractHost(info) ?: return
        val ownAddresses = NetworkUtils.getAllLocalIpv4Addresses().map { it.second }.toSet()
        if (host in ownAddresses) return
        val serverInfo = verifier(host, info.port)
        if (serverInfo == null) {
            val label = info.serviceName.removePrefix("Kirimly-").ifBlank { host }
            _unreachablePeers.update { list -> (list.filterNot { it == label } + label).takeLast(5) }
            return
        }
        val reachableLabel = info.serviceName.removePrefix("Kirimly-")
        _unreachablePeers.update { list -> list.filterNot { it == reachableLabel } }
        val peer = PeerDevice(
            id = info.serviceName,
            name = serverInfo.deviceName,
            host = host,
            port = info.port,
            pinRequired = serverInfo.pinRequired,
            lastSeen = System.currentTimeMillis()
        )
        _peers.update { list -> list.filterNot { it.id == peer.id || (it.host == peer.host && it.port == peer.port) } + peer }
    }

    private suspend fun verifyPeers() {
        val snapshot = _peers.value
        for (peer in snapshot) {
            val info = verifier(peer.host, peer.port)
            if (info == null) {
                _peers.update { list -> list.filterNot { it.id == peer.id } }
            } else {
                _peers.update { list ->
                    list.map {
                        if (it.id == peer.id) it.copy(
                            name = info.deviceName,
                            pinRequired = info.pinRequired,
                            lastSeen = System.currentTimeMillis()
                        ) else it
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractHost(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val addresses = info.hostAddresses
            val v4 = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
            if (v4 != null) return v4.hostAddress
        }
        val address = info.host ?: return null
        return address.hostAddress
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        try {
            multicastLock = wifiManager?.createMulticastLock("kirimly:mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            multicastLock = null
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        multicastLock = null
    }

    private fun buildServiceName(nickname: String): String {
        val clean = nickname.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Android" }
        return "Kirimly-$clean".take(MAX_SERVICE_NAME)
    }

    companion object {
        const val SERVICE_TYPE = "_kirimly._tcp"
        private const val SERVICE_TYPE_CORE = "_kirimly._tcp"
        private const val RESOLVE_TIMEOUT_MS = 6_000L
        private const val PRUNE_INTERVAL_MS = 12_000L
        private const val MAX_SERVICE_NAME = 60
    }
}
