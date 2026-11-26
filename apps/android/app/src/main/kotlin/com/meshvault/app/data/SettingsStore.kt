package com.meshvault.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val url: String = "http://192.168.1.184:8000/",
    val token: String = "",
    val cardView: String = "grid",
    /** system | light | dark */
    val themeMode: String = "system",
    /** green | blue | orange | violet */
    val accent: String = "green",
    val showGrid: Boolean = true,
    val showAxes: Boolean = true,
    val autoRotate: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "meshvault_settings")

class SettingsStore(private val context: Context) {
    private val keyUrl = stringPreferencesKey("server_url")
    private val keyToken = stringPreferencesKey("token")
    private val keyCardView = stringPreferencesKey("card_view")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyAccent = stringPreferencesKey("accent")
    private val keyGrid = booleanPreferencesKey("show_grid")
    private val keyAxes = booleanPreferencesKey("show_axes")
    private val keyAutoRotate = booleanPreferencesKey("auto_rotate")

    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            url = prefs[keyUrl] ?: "http://192.168.1.184:8000/",
            token = prefs[keyToken] ?: "",
            cardView = prefs[keyCardView] ?: "grid",
            themeMode = prefs[keyTheme] ?: "system",
            accent = prefs[keyAccent] ?: "green",
            showGrid = prefs[keyGrid] != false,
            showAxes = prefs[keyAxes] != false,
            autoRotate = prefs[keyAutoRotate] == true,
        )
    }

    suspend fun save(url: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[keyUrl] = url
            prefs[keyToken] = token
        }
    }

    suspend fun setCardView(value: String) {
        context.dataStore.edit { prefs -> prefs[keyCardView] = value }
    }

    suspend fun setThemeMode(value: String) {
        context.dataStore.edit { prefs -> prefs[keyTheme] = value }
    }

    suspend fun setAccent(value: String) {
        context.dataStore.edit { prefs -> prefs[keyAccent] = value }
    }

    suspend fun setViewerPrefs(grid: Boolean, axes: Boolean, autoRotate: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyGrid] = grid
            prefs[keyAxes] = axes
            prefs[keyAutoRotate] = autoRotate
        }
    }
}
