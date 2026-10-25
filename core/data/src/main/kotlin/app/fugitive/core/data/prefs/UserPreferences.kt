package app.fugitive.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.fugitive.model.GameConfig
import app.fugitive.model.PlayerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

enum class Units { METRIC, IMPERIAL }

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class LastLobby(val backendId: String, val code: String)

data class AppPreferences(
    val profile: PlayerProfile = PlayerProfile(),
    val onboarded: Boolean = false,
    val units: Units = Units.METRIC,
    val theme: ThemeMode = ThemeMode.DARK,
    val mapStyleId: String = "auto",
    val customStyleUrl: String = "",
    val haptics: Boolean = true,
    val sounds: Boolean = true,
    val keepScreenOn: Boolean = true,
    val locationSource: String = "auto",
    val lastLobby: LastLobby? = null,
    val practiceBots: Int = 4,
    val practiceDifficulty: Int = 1,
    val lastConfig: GameConfig? = null,
    val map3d: Boolean = false,
    val showHints: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fugitive_prefs")

/** Persistent user preferences backed by Jetpack DataStore. */
class UserPreferences(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private object K {
        val NAME = stringPreferencesKey("profile_name")
        val AVATAR = stringPreferencesKey("profile_avatar")
        val COLOR = intPreferencesKey("profile_color")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val UNITS = stringPreferencesKey("units")
        val THEME = stringPreferencesKey("theme")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val CUSTOM_STYLE = stringPreferencesKey("custom_style_url")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SOUNDS = booleanPreferencesKey("sounds")
        val KEEP_SCREEN = booleanPreferencesKey("keep_screen_on")
        val LOCATION_SOURCE = stringPreferencesKey("location_source")
        val LAST_BACKEND = stringPreferencesKey("last_backend")
        val LAST_CODE = stringPreferencesKey("last_code")
        val BOTS = intPreferencesKey("practice_bots")
        val DIFFICULTY = intPreferencesKey("practice_difficulty")
        val LAST_CONFIG = stringPreferencesKey("last_config")
        val MAP_3D = booleanPreferencesKey("map_3d")
        val HINTS = booleanPreferencesKey("show_hints")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { p ->
        AppPreferences(
            profile = PlayerProfile(p[K.NAME] ?: "", p[K.AVATAR] ?: "🦊", p[K.COLOR] ?: 0),
            onboarded = p[K.ONBOARDED] ?: false,
            units = p[K.UNITS]?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: Units.METRIC,
            theme = p[K.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            mapStyleId = p[K.MAP_STYLE] ?: "auto",
            customStyleUrl = p[K.CUSTOM_STYLE] ?: "",
            haptics = p[K.HAPTICS] ?: true,
            sounds = p[K.SOUNDS] ?: true,
            keepScreenOn = p[K.KEEP_SCREEN] ?: true,
            locationSource = p[K.LOCATION_SOURCE] ?: "auto",
            lastLobby = p[K.LAST_CODE]?.let { code -> LastLobby(p[K.LAST_BACKEND] ?: "firebase", code) },
            practiceBots = p[K.BOTS] ?: 4,
            practiceDifficulty = p[K.DIFFICULTY] ?: 1,
            lastConfig = p[K.LAST_CONFIG]?.let { runCatching { json.decodeFromString(GameConfig.serializer(), it) }.getOrNull() },
            map3d = p[K.MAP_3D] ?: false,
            showHints = p[K.HINTS] ?: true,
        )
    }

    suspend fun current(): AppPreferences = preferences.first()

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    suspend fun setProfile(profile: PlayerProfile) = edit {
        it[K.NAME] = profile.name.trim().take(MAX_NAME)
        it[K.AVATAR] = profile.avatar
        it[K.COLOR] = profile.color
    }

    suspend fun setOnboarded(done: Boolean) = edit { it[K.ONBOARDED] = done }
    suspend fun setUnits(units: Units) = edit { it[K.UNITS] = units.name }
    suspend fun setTheme(theme: ThemeMode) = edit { it[K.THEME] = theme.name }
    suspend fun setMapStyle(id: String) = edit { it[K.MAP_STYLE] = id }
    suspend fun setCustomStyleUrl(url: String) = edit { it[K.CUSTOM_STYLE] = url.trim() }
    suspend fun setHaptics(on: Boolean) = edit { it[K.HAPTICS] = on }
    suspend fun setSounds(on: Boolean) = edit { it[K.SOUNDS] = on }
    suspend fun setKeepScreenOn(on: Boolean) = edit { it[K.KEEP_SCREEN] = on }
    suspend fun setLocationSource(id: String) = edit { it[K.LOCATION_SOURCE] = id }
    suspend fun setMap3d(on: Boolean) = edit { it[K.MAP_3D] = on }
    suspend fun setShowHints(on: Boolean) = edit { it[K.HINTS] = on }
    suspend fun setPractice(bots: Int, difficulty: Int) = edit {
        it[K.BOTS] = bots.coerceIn(1, 15); it[K.DIFFICULTY] = difficulty.coerceIn(0, 2)
    }

    suspend fun setLastLobby(last: LastLobby?) = edit {
        if (last == null) { it.remove(K.LAST_CODE); it.remove(K.LAST_BACKEND) }
        else { it[K.LAST_CODE] = last.code; it[K.LAST_BACKEND] = last.backendId }
    }

    suspend fun setLastConfig(config: GameConfig) = edit {
        it[K.LAST_CONFIG] = json.encodeToString(GameConfig.serializer(), config)
    }

    companion object { const val MAX_NAME = 20 }
}
