package com.kirimly.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Joins a nearby hotspot (typically an iPhone's Personal Hotspot) from inside the app with
 * WifiNetworkSpecifier (Android 10+). The iPhone keeps its mobile data, and the iPhone can then
 * open this phone's Kirimly page over that link. Android shows its own confirmation dialog.
 */
class WifiJoiner(context: Context) {

    sealed interface State {
        data object Idle : State
        data class Connecting(val ssid: String) : State
        data class Connected(val ssid: String) : State
        data class Failed(val reason: String) : State
    }

    private val connectivity: ConnectivityManager? = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var callback: ConnectivityManager.NetworkCallback? = null

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun join(ssid: String, password: String, onConnected: () -> Unit) {
        if (!supported) {
            _state.value = State.Failed("Butuh Android 10 atau lebih baru")
            return
        }
        disconnect()
        attempt(ssid.trim(), password, useWpa3 = false, onConnected = onConnected)
    }

    private fun attempt(ssid: String, password: String, useWpa3: Boolean, onConnected: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val manager = connectivity ?: run {
            _state.value = State.Failed("Wi-Fi tidak tersedia")
            return
        }
        _state.value = State.Connecting(ssid)
        val specifier = WifiNetworkSpecifier.Builder().setSsid(ssid).apply {
            if (password.isNotEmpty()) {
                if (useWpa3) setWpa3Passphrase(password) else setWpa2Passphrase(password)
            }
        }.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _state.value = State.Connected(ssid)
                onConnected()
            }

            override fun onUnavailable() {
                callback = null
                if (!useWpa3 && password.isNotEmpty()) {
                    // Newer iPhones use WPA3 unless "Maximise Compatibility" is on.
                    attempt(ssid, password, useWpa3 = true, onConnected = onConnected)
                } else {
                    _state.value = State.Failed("Tidak bisa bergabung ke \"$ssid\". Periksa nama, kata sandi, dan nyalakan Maximise Compatibility di iPhone.")
                }
            }

            override fun onLost(network: Network) {
                _state.value = State.Failed("Koneksi ke $ssid terputus")
            }
        }
        try {
            manager.requestNetwork(request, cb, 30_000)
            callback = cb
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Permintaan Wi-Fi ditolak")
        }
    }

    fun disconnect() {
        val cb = callback ?: run {
            if (_state.value !is State.Idle) _state.value = State.Idle
            return
        }
        callback = null
        try {
            connectivity?.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        }
        _state.value = State.Idle
    }
}
