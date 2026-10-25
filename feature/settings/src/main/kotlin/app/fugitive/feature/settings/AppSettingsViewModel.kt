package app.fugitive.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fugitive.core.data.AppInfo
import app.fugitive.core.data.prefs.AppPreferences
import app.fugitive.core.data.prefs.ThemeMode
import app.fugitive.core.data.prefs.Units
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.location.LocationSources
import app.fugitive.core.location.SimulatedLocationSource
import app.fugitive.model.PlayerProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppSettingsViewModel(
    private val prefs: UserPreferences,
    val info: AppInfo,
    val sources: LocationSources,
    private val sessions: GameSessionManager,
) : ViewModel() {
    val state: StateFlow<AppPreferences> = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    fun setProfile(p: PlayerProfile) = launch {
        prefs.setProfile(p)
        // Update the live lobby too so others see the new name/avatar.
        sessions.current?.let { runCatching { it.updateProfile(p.name, p.avatar, p.color) } }
    }
    fun setUnits(u: Units) = launch { prefs.setUnits(u) }
    fun setTheme(t: ThemeMode) = launch { prefs.setTheme(t) }
    fun setMapStyle(id: String) = launch { prefs.setMapStyle(id) }
    fun setCustomStyle(url: String) = launch { prefs.setCustomStyleUrl(url) }
    fun setHaptics(on: Boolean) = launch { prefs.setHaptics(on) }
    fun setKeepScreenOn(on: Boolean) = launch { prefs.setKeepScreenOn(on) }
    fun setMap3d(on: Boolean) = launch { prefs.setMap3d(on) }
    fun setTerrainShading(on: Boolean) = launch { prefs.setTerrainShading(on) }
    fun setMapTilerKey(key: String) = launch { prefs.setMapTilerKey(key) }
    fun setLocationSource(id: String) = launch { prefs.setLocationSource(id) }
    fun setDifficulty(d: Int) = launch { prefs.setPractice(state.value.practiceBots, d) }
    fun replayTutorial(then: () -> Unit) = launch { prefs.setOnboarded(false); then() }

    val joystickId = SimulatedLocationSource.ID

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
