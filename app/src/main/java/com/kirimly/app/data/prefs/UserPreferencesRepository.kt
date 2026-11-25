package com.kirimly.app.data.prefs

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kirimly.app.data.model.AppPalette
import com.kirimly.app.data.model.ThemeMode
import com.kirimly.app.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.kirimlyDataStore: DataStore<Preferences> by preferencesDataStore(name = "kirimly_preferences")

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.kirimlyDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PALETTE = stringPreferencesKey("palette")
        val NICKNAME = stringPreferencesKey("device_nickname")
        val PIN_REQUIRED = booleanPreferencesKey("pin_required")
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
                pinRequired = prefs[Keys.PIN_REQUIRED] ?: true
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

    companion object {
        const val MAX_NICKNAME_LENGTH = 32

        fun defaultNickname(): String {
            val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercaseChar() }
            val model = Build.MODEL.orEmpty()
            val name = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            return name.trim().ifBlank { "Android" }.take(MAX_NICKNAME_LENGTH)
        }

        val DEFAULT = UserPreferences(
            themeMode = ThemeMode.SYSTEM,
            palette = AppPalette.OCEAN_COBALT,
            deviceNickname = defaultNickname(),
            pinRequired = true
        )
    }
}
