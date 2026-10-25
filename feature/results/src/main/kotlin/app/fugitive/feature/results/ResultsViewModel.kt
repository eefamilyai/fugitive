package app.fugitive.feature.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fugitive.core.data.prefs.AppPreferences
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.data.session.SessionState
import app.fugitive.engine.GameEngine
import app.fugitive.engine.stats.GameStats
import app.fugitive.engine.stats.PlayerStats
import app.fugitive.model.GameEvent
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.PlayerId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModel(
    private val sessions: GameSessionManager,
    val engine: GameEngine,
    prefs: UserPreferences,
) : ViewModel() {
    private val session get() = sessions.current
    val selfId: PlayerId get() = session?.selfId ?: ""
    val preferences: StateFlow<AppPreferences> = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val lobby: StateFlow<LobbySnapshot?> = sessions.state.map { (it as? SessionState.Active)?.session }
        .flatMapLatest { it?.lobby ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessions.current?.lobby?.value)

    val events: StateFlow<List<GameEvent>> = sessions.state.map { (it as? SessionState.Active)?.session }
        .flatMapLatest { it?.events ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _trails = MutableStateFlow<Map<PlayerId, List<LocationFix>>>(emptyMap())
    val trails: StateFlow<Map<PlayerId, List<LocationFix>>> = _trails.asStateFlow()
    private val _stats = MutableStateFlow<List<PlayerStats>>(emptyList())
    val stats: StateFlow<List<PlayerStats>> = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            val s = session ?: return@launch
            val t = runCatching { s.trails() }.getOrDefault(emptyMap())
            _trails.value = t
            lobby.value?.game?.let { _stats.value = GameStats.compute(it, t) }
        }
        viewModelScope.launch {
            lobby.collect { l -> l?.game?.let { g -> _stats.value = GameStats.compute(g, _trails.value) } }
        }
    }

    fun playAgain() = viewModelScope.launch { runCatching { session?.playAgain() } }
    fun leave() = viewModelScope.launch { sessions.leave() }
}
