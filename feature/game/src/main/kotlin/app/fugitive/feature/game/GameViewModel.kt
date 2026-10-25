package app.fugitive.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fugitive.core.data.alerts.GameAlerts
import app.fugitive.core.data.prefs.AppPreferences
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.data.session.GameProjection
import app.fugitive.core.data.session.GameSession
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.data.session.LocationProblem
import app.fugitive.core.data.session.SessionState
import app.fugitive.core.location.SimulatedLocationSource
import app.fugitive.engine.GameEngine
import app.fugitive.model.ChatMessage
import app.fugitive.model.GameEvent
import app.fugitive.model.GeoPoint
import app.fugitive.model.LocationFix
import app.fugitive.model.MapMarker
import app.fugitive.model.PlayerId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    private val sessions: GameSessionManager,
    val engine: GameEngine,
    prefs: UserPreferences,
    alerts: GameAlerts,
    private val simulator: SimulatedLocationSource,
) : ViewModel() {
    private val sessionFlow = sessions.state.map { (it as? SessionState.Active)?.session }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessions.current)

    val session: GameSession? get() = sessionFlow.value

    private fun <T> fromSession(default: T, pick: (GameSession) -> kotlinx.coroutines.flow.Flow<T>): StateFlow<T> =
        sessionFlow.flatMapLatest { s -> s?.let(pick) ?: flowOf(default) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), default)

    val projection: StateFlow<GameProjection?> = fromSession(null) { it.projection }
    val myFix: StateFlow<LocationFix?> = fromSession(null) { it.myFix }
    val events: StateFlow<List<GameEvent>> = fromSession(emptyList()) { it.events }
    val markers: StateFlow<List<MapMarker>> = fromSession(emptyList()) { it.markers }
    val chat: StateFlow<List<ChatMessage>> = fromSession(emptyList()) { it.chat }
    val connected: StateFlow<Boolean> = fromSession(true) { it.connected }
    val locationProblem: StateFlow<LocationProblem?> = fromSession(null) { it.locationProblem }
    val preferences: StateFlow<AppPreferences> = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())
    val banners: SharedFlow<GameEvent> = alerts.banners

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    val selfId: PlayerId get() = session?.selfId ?: ""

    fun claimCatch(target: PlayerId) = act { session?.claimCatch(target) }
    fun respondToCatch(id: String, accept: Boolean) = act { session?.respondToCatch(id, accept) }
    fun submitCode(code: String) = act { session?.submitCatchCode(code) }
    fun reportCaught(by: PlayerId?) = act { session?.reportCaught(by) }
    fun useAbility(id: String) = act { session?.useAbility(id) }
    fun forfeit() = act { session?.forfeit() }
    fun endGame() = act { session?.endGameEarly() }
    fun leaveLobby() = act { sessions.leave() }
    fun sendChat(text: String, team: Boolean) = act { session?.sendChat(text, team) }
    fun dropMarker(kind: String, point: GeoPoint) = act {
        session?.dropMarker(kind, point)
        _toast.value = "Ping sent to your team"
    }
    fun retryLocation() { session?.retryLocation() }

    val isSimulated: Boolean get() = preferences.value.locationSource == SimulatedLocationSource.ID
    fun setStick(x: Float, y: Float) = simulator.setStick(x, y)

    private fun act(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _toast.value = it.message ?: "Couldn't reach the game" }
    }
}
