package com.kirimly.app.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.kirimly.app.data.model.HotspotState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls Android's LocalOnlyHotspot. When the system refuses (tethering active, OEM
 * restriction, missing permission) the state becomes [HotspotState.Failed] and the UI offers
 * the system hotspot settings plus manual SSID / password entry for the QR code.
 */
class HotspotManager(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager: WifiManager? = appContext.getSystemService(WifiManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val state: StateFlow<HotspotState> = _state.asStateFlow()

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES
        else Manifest.permission.ACCESS_FINE_LOCATION

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (reservation != null) return
        val manager = wifiManager
        if (manager == null) {
            _state.value = HotspotState.Failed("Perangkat ini tidak mendukung Wi-Fi.")
            return
        }
        if (!hasPermission()) {
            _state.value = HotspotState.Failed(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Izin Perangkat Wi-Fi Terdekat diperlukan."
                else "Izin lokasi diperlukan untuk hotspot lokal."
            )
            return
        }
        _state.value = HotspotState.Starting
        try {
            manager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res
                    val credentials = extractCredentials(res)
                    _state.value = if (credentials != null) {
                        HotspotState.Active(credentials.first, credentials.second)
                    } else {
                        HotspotState.Failed("Hotspot aktif, tetapi SSID tidak dapat dibaca. Masukkan manual.")
                    }
                }

                override fun onStopped() {
                    reservation = null
                    if (_state.value !is HotspotState.Manual) _state.value = HotspotState.Idle
                }

                override fun onFailed(reason: Int) {
                    reservation = null
                    _state.value = HotspotState.Failed(reasonToText(reason))
                }
            }, mainHandler)
        } catch (e: SecurityException) {
            _state.value = HotspotState.Failed("Izin ditolak: ${e.message ?: "akses Wi-Fi"}")
        } catch (e: IllegalStateException) {
            _state.value = HotspotState.Failed("Hotspot sudah berjalan oleh aplikasi lain.")
        } catch (e: Exception) {
            _state.value = HotspotState.Failed(e.message ?: "Hotspot lokal tidak tersedia.")
        }
    }

    fun stop() {
        try {
            reservation?.close()
        } catch (_: Exception) {
        }
        reservation = null
        _state.value = HotspotState.Idle
    }

    fun setManualCredentials(ssid: String, password: String) {
        if (ssid.isBlank()) return
        try {
            reservation?.close()
        } catch (_: Exception) {
        }
        reservation = null
        _state.value = HotspotState.Manual(ssid.trim(), password)
    }

    fun reset() {
        if (reservation == null) _state.value = HotspotState.Idle
    }

    @Suppress("DEPRECATION")
    private fun extractCredentials(res: WifiManager.LocalOnlyHotspotReservation): Pair<String, String>? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val config = res.softApConfiguration
                val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    config.wifiSsid?.toString()
                } else {
                    config.ssid
                }
                val cleanSsid = ssid?.trim('"') ?: return null
                cleanSsid to (config.passphrase ?: "")
            } else {
                val config = res.wifiConfiguration ?: return null
                val ssid = config.SSID?.trim('"') ?: return null
                ssid to (config.preSharedKey?.trim('"') ?: "")
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun reasonToText(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "Tidak ada kanal Wi-Fi yang tersedia."
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE ->
            "Matikan hotspot/tethering sistem terlebih dahulu, atau gunakan hotspot sistem secara manual."
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Tethering dinonaktifkan oleh kebijakan perangkat."
        else -> "Hotspot lokal gagal dimulai. Pastikan Wi-Fi dan Lokasi aktif."
    }
}
