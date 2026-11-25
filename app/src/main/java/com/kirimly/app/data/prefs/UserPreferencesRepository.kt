package com.kirimly.app.data.prefs

import androidx.datastore.preferences.core.intPreferencesKey
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kirimly.app.data.model.AppPalette
import com.kirimly.app.data.model.ThemeMode
import com.kirimly.app.data.model.TrustedDevice
import com.kirimly.app.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.SecureRandom

private val Context.kirimlyDataStore: DataStore<Preferences> by preferencesDataStore(name = "kirimly_preferences")

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.kirimlyDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PALETTE = stringPreferencesKey("palette")
        val NICKNAME = stringPreferencesKey("device_nickname")
        val PIN_REQUIRED = booleanPreferencesKey("pin_required")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val AUTO_START = booleanPreferencesKey("auto_start_server")
        val PIN_IN_QR = booleanPreferencesKey("pin_in_qr")
        val CONVERT_HEIC = booleanPreferencesKey("convert_heic")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val TRUSTED = stringSetPreferencesKey("trusted_devices")
        val IDLE_STOP = intPreferencesKey("idle_stop_minutes")
        val PRO_OWNED = booleanPreferencesKey("pro_owned")
        val DEBUG_PRO = booleanPreferencesKey("debug_pro")
        val CLIPBOARD_SYNC = booleanPreferencesKey("clipboard_sync")
        val PORTAL_NAME = stringPreferencesKey("portal_name")
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            UserPreferences(
                themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE]),
                palette = AppPalette.fromName(prefs[Keys.PALETTE]),
                deviceNickname = prefs[Keys.NICKNAME]?.takeIf { it.isNotBlank() } ?: defaultNickname(),
                pinRequired = prefs[Keys.PIN_REQUIRED] ?: true,
                onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
                autoStartServer = prefs[Keys.AUTO_START] ?: true,
                pinInQr = prefs[Keys.PIN_IN_QR] ?: false,
                convertHeic = prefs[Keys.CONVERT_HEIC] ?: false,
                deviceId = prefs[Keys.DEVICE_ID].orEmpty(),
                trustedDevices = decodeTrusted(prefs[Keys.TRUSTED].orEmpty()),
                idleStopMinutes = prefs[Keys.IDLE_STOP] ?: 15,
                proOwned = prefs[Keys.PRO_OWNED] ?: false,
                debugPro = prefs[Keys.DEBUG_PRO] ?: false,
                clipboardSync = prefs[Keys.CLIPBOARD_SYNC] ?: false,
                portalName = prefs[Keys.PORTAL_NAME].orEmpty()
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPalette(palette: AppPalette) {
        dataStore.edit { it[Keys.PALETTE] = palette.name }
    }

    suspend fun setDeviceNickname(nickname: String) {
        val clean = nickname.trim().take(MAX_NICKNAME_LENGTH)
        dataStore.edit { prefs ->
            if (clean.isBlank()) prefs.remove(Keys.NICKNAME) else prefs[Keys.NICKNAME] = clean
        }
    }

    suspend fun setPinRequired(required: Boolean) {
        dataStore.edit { it[Keys.PIN_REQUIRED] = required }
    }

    suspend fun setAutoStartServer(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_START] = enabled }
    }

    suspend fun setIdleStopMinutes(minutes: Int) {
        dataStore.edit { it[Keys.IDLE_STOP] = minutes.coerceIn(0, 240) }
    }

    suspend fun setPinInQr(enabled: Boolean) {
        dataStore.edit { it[Keys.PIN_IN_QR] = enabled }
    }

    suspend fun setConvertHeic(enabled: Boolean) {
        dataStore.edit { it[Keys.CONVERT_HEIC] = enabled }
    }

    /** Creates this install's random device id once; used to be recognised as trusted by peers. */
    suspend fun ensureDeviceId() {
        dataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) prefs[Keys.DEVICE_ID] = newDeviceId()
        }
    }

    suspend fun addTrustedDevice(id: String, name: String) {
        if (id.isBlank()) return
        dataStore.edit { prefs ->
            val current = decodeTrusted(prefs[Keys.TRUSTED].orEmpty()).filterNot { it.id == id }
            val updated = current + TrustedDevice(id, name.take(40), System.currentTimeMillis())
            prefs[Keys.TRUSTED] = updated.takeLast(MAX_TRUSTED).map(::encodeTrusted).toSet()
        }
    }

    suspend fun removeTrustedDevice(id: String) {
        dataStore.edit { prefs ->
            val updated = decodeTrusted(prefs[Keys.TRUSTED].orEmpty()).filterNot { it.id == id }
            prefs[Keys.TRUSTED] = updated.map(::encodeTrusted).toSet()
        }
    }

    suspend fun setProOwned(owned: Boolean) {
        dataStore.edit { it[Keys.PRO_OWNED] = owned }
    }

    suspend fun setDebugPro(enabled: Boolean) {
        dataStore.edit { it[Keys.DEBUG_PRO] = enabled }
    }

    suspend fun setClipboardSync(enabled: Boolean) {
        dataStore.edit { it[Keys.CLIPBOARD_SYNC] = enabled }
    }

    suspend fun setPortalName(name: String) {
        val clean = name.trim().take(MAX_NICKNAME_LENGTH)
        dataStore.edit { prefs ->
            if (clean.isBlank()) prefs.remove(Keys.PORTAL_NAME) else prefs[Keys.PORTAL_NAME] = clean
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    companion object {
        const val MAX_NICKNAME_LENGTH = 32

        fun defaultNickname(): String {
            val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercaseChar() }
            val model = Build.MODEL.orEmpty()
            val name = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            return name.trim().ifBlank { "Android" }.take(MAX_NICKNAME_LENGTH)
        }

        val DEFAULT = UserPreferences(
            themeMode = ThemeMode.DARK,
            palette = AppPalette.MIDNIGHT_MONO,
            deviceNickname = defaultNickname(),
            pinRequired = true,
            onboardingCompleted = false,
            autoStartServer = true,
            pinInQr = false,
            convertHeic = false,
            deviceId = "",
            trustedDevices = emptyList(),
            idleStopMinutes = 15
        )

        private const val MAX_TRUSTED = 32
        private const val SEP = '\u001F'

        private fun newDeviceId(): String {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return bytes.joinToString("") { String.format(java.util.Locale.US, "%02x", it.toInt() and 0xFF) }
        }

        private fun encodeTrusted(device: TrustedDevice): String =
            listOf(device.id, device.name.replace(SEP, ' '), device.addedAt.toString()).joinToString(SEP.toString())

        private fun decodeTrusted(raw: Set<String>): List<TrustedDevice> = raw.mapNotNull { entry ->
            val parts = entry.split(SEP)
            if (parts.size < 3 || parts[0].isBlank()) null
            else TrustedDevice(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
        }.sortedBy { it.addedAt }
    }
}
