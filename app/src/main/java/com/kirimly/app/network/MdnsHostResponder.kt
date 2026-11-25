package com.kirimly.app.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Collections

/**
 * Minimal mDNS responder that answers A queries for `kirimly.local`, so iPhone, iPad and Mac
 * can open http://kirimly.local:PORT instead of typing an IP. Apple devices resolve `.local`
 * names over multicast DNS natively. Android browsers generally do not.
 *
 * If two Kirimly devices run on one network, both answer and the client picks one; the IP
 * address in the QR code stays the reliable path.
 */
class MdnsHostResponder(context: Context, private val scope: CoroutineScope) {

    private val wifiManager: WifiManager? = context.applicationContext.getSystemService(WifiManager::class.java)
    private var job: Job? = null
    private var socket: MulticastSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    @Synchronized
    fun start(hostLabel: String = HOST_LABEL) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { runLoop(hostLabel.lowercase()) }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        try {
            lock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        lock = null
    }

    private fun runLoop(label: String) {
        val group = InetAddress.getByName(MDNS_GROUP)
        val multicast = try {
            MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(MDNS_PORT))
                soTimeout = 1_000
                timeToLive = 255
            }
        } catch (e: Exception) {
            return
        }
        socket = multicast
        try {
            lock = wifiManager?.createMulticastLock("kirimly:mdns-host")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
        joinAll(multicast, group)

        val buffer = ByteArray(1500)
        val fqdn = "$label.local"
        while (job?.isActive != false && !multicast.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                multicast.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                break
            }
            val query = try {
                parseQuery(packet.data, packet.length)
            } catch (_: Exception) {
                null
            } ?: continue
            val wanted = query.questions.firstOrNull { it.name.equals(fqdn, ignoreCase = true) && (it.type == TYPE_A || it.type == TYPE_ANY) }
                ?: continue
            val address = pickAddressFor(packet.address) ?: continue
            val response = buildResponse(fqdn, address)
            try {
                if (wanted.unicastResponse || packet.port != MDNS_PORT) {
                    multicast.send(DatagramPacket(response, response.size, packet.address, packet.port))
                } else {
                    multicast.send(DatagramPacket(response, response.size, group, MDNS_PORT))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun joinAll(socket: MulticastSocket, group: InetAddress) {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        var joined = false
        for (nif in interfaces) {
            val ok = try {
                nif.isUp && !nif.isLoopback && nif.supportsMulticast() &&
                    Collections.list(nif.inetAddresses).any { it is Inet4Address }
            } catch (_: Exception) {
                false
            }
            if (!ok) continue
            try {
                socket.joinGroup(InetSocketAddress(group, MDNS_PORT), nif)
                joined = true
            } catch (_: Exception) {
            }
        }
        if (!joined) {
            try {
                @Suppress("DEPRECATION")
                socket.joinGroup(group)
            } catch (_: Exception) {
            }
        }
    }

    /** Chooses the local IPv4 on the same /24 as the asker (hotspot vs Wi-Fi), else the primary. */
    private fun pickAddressFor(asker: InetAddress?): ByteArray? {
        val all = NetworkUtils.getAllLocalIpv4Addresses().map { it.second }
        val askerBytes = (asker as? Inet4Address)?.address
        val match = if (askerBytes != null) {
            all.firstOrNull { ip ->
                val b = InetAddress.getByName(ip).address
                b[0] == askerBytes[0] && b[1] == askerBytes[1] && b[2] == askerBytes[2]
            }
        } else null
        val chosen = match ?: all.firstOrNull() ?: return null
        return InetAddress.getByName(chosen).address
    }

    private class Question(val name: String, val type: Int, val unicastResponse: Boolean)
    private class Query(val questions: List<Question>)

    private fun parseQuery(data: ByteArray, length: Int): Query? {
        if (length < 12) return null
        val flags = u16(data, 2)
        if (flags and 0x8000 != 0) return null // a response, not a query
        val qdCount = u16(data, 4)
        var offset = 12
        val questions = mutableListOf<Question>()
        repeat(qdCount.coerceAtMost(16)) {
            val (name, next) = readName(data, length, offset)
            if (next + 4 > length) return Query(questions)
            val type = u16(data, next)
            val qclass = u16(data, next + 2)
            questions += Question(name, type, qclass and 0x8000 != 0)
            offset = next + 4
        }
        return Query(questions)
    }

    private fun readName(data: ByteArray, length: Int, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var offset = start
        var end = -1
        var jumps = 0
        while (offset < length) {
            val len = data[offset].toInt() and 0xFF
            if (len == 0) {
                offset += 1
                break
            }
            if (len and 0xC0 == 0xC0) {
                if (offset + 1 >= length || jumps++ > 8) break
                val pointer = ((len and 0x3F) shl 8) or (data[offset + 1].toInt() and 0xFF)
                if (end < 0) end = offset + 2
                offset = pointer
                continue
            }
            if (offset + 1 + len > length) break
            labels += String(data, offset + 1, len, Charsets.UTF_8)
            offset += 1 + len
        }
        return labels.joinToString(".") to (if (end >= 0) end else offset)
    }

    private fun buildResponse(fqdn: String, address: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun w16(v: Int) {
            out.write((v ushr 8) and 0xFF)
            out.write(v and 0xFF)
        }
        w16(0)          // id
        w16(0x8400)     // response + authoritative
        w16(0)          // questions
        w16(1)          // answers
        w16(0)
        w16(0)
        for (label in fqdn.split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        w16(TYPE_A)
        w16(0x8001)     // cache-flush + IN
        w16(0)
        w16(TTL_SECONDS) // TTL (32-bit, high word 0)
        w16(4)
        out.write(address)
        return out.toByteArray()
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    companion object {
        const val HOST_LABEL = "kirimly"
        private const val MDNS_GROUP = "224.0.0.251"
        private const val MDNS_PORT = 5353
        private const val TYPE_A = 1
        private const val TYPE_ANY = 255
        private const val TTL_SECONDS = 120
    }
}
