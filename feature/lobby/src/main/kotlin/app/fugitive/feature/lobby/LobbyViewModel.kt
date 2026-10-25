package app.fugitive.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fugitive.core.data.prefs.AppPreferences
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.data.session.GameSession
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.data.session.SessionState
import app.fugitive.engine.GameEngine
import app.fugitive.engine.mode.ConfigIssue
import app.fugitive.engine.rules.GameFactory
import app.fugitive.engine.rules.GameSetupRequest
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.model.ChatMessage
import app.fugitive.model.Circle
import app.fugitive.model.GameConfig
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.PlayerId
import app.fugitive.model.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LobbyUiState(
    val session: GameSession? = null,
    val lobby: LobbySnapshot? = null,
    val selfId: PlayerId = "",
    val locations: Map<PlayerId, LocationFix> = emptyMap(),
    val issues: List<ConfigIssue> = emptyList(),
    val playArea: Circle? = null,
    val chat: List<ChatMessage> = emptyList(),
    val connected: Boolean = true,
    val prefs: AppPreferences = AppPreferences(),
) {
    val isHost: Boolean get() = lobby?.hostId == selfId
    val me get() = lobby?.members?.get(selfId)
    val isPractice: Boolean get() = session?.isPractice == true
    val blocking: ConfigIssue? get() = issues.firstOrNull { it.blocking }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LobbyViewModel(
    private val sessions: GameSessionManager,
    val engine: GameEngine,
    prefs: UserPreferences,
) : ViewModel() {
    private val sessionFlow: Flow<GameSession?> = sessions.state.map { (it as? SessionState.Active)?.session }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<LobbyUiState> = sessionFlow.flatMapLatest { s ->
        if (s == null) flowOf(LobbyUiState())
        else combine(s.lobby, s.locations, s.chat, s.connected, prefs.preferences) { lobby, locs, chat, connected, p ->
            val participants = lobby?.membersByJoin?.filter { it.online || s.botControl?.isBot(it.id) == true }.orEmpty()
            val issues = lobby?.let { engine.validate(it.config, participants, it.roster, locs[it.hostId] != null) }.orEmpty()
            val area = lobby?.let {
                GameFactory.playArea(
                    engine.settings(it.config),
                    GameSetupRequest(it.config, participants, it.roster, locs, it.hostId, 0, 0),
                )
            }
            LobbyUiState(s, lobby, s.selfId, locs, issues, area, chat, connected, p)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LobbyUiState())

    private val session get() = sessions.current

    fun setReady(ready: Boolean) = act { session?.setReady(ready) }
    fun setWantsToHunt(w: Boolean) = act { session?.setWantsToHunt(w) }
    fun setRole(id: PlayerId, role: Role?) = act { session?.setRole(id, role) }
    fun kick(id: PlayerId) = act { val s = session; if (s?.botControl?.isBot(id) == true) s.botControl?.removeBot(id) else s?.kick(id) }
    fun makeHost(id: PlayerId) = act { session?.transferHost(id) }
    fun setLocked(locked: Boolean) = act { session?.setLocked(locked) }
    fun addBot() = act { session?.botControl?.addBots(1, 1) }
    fun sendChat(text: String) = act { session?.sendChat(text, teamOnly = false) }
    fun leave() = act { sessions.leave() }

    fun start() = act {
        val s = session ?: return@act
        s.startGame().onFailure { _message.value = it.message ?: "Couldn't start the game" }
    }

    fun consumeMessage() { _message.value = null }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _message.value = it.message ?: "Something went wrong" }
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GameSettingsViewModel(
    private val sessions: GameSessionManager,
    val engine: GameEngine,
    prefs: UserPreferences,
) : ViewModel() {
    private val session get() = sessions.current
    private val _draft = MutableStateFlow<GameConfig?>(null)
    /** Local copy that updates instantly while edits sync to the lobby in the background. */
    val draft: StateFlow<GameConfig?> = _draft.asStateFlow()
    val preferences = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val lobby: StateFlow<LobbySnapshot?> = sessions.state.map { (it as? SessionState.Active)?.session }
        .flatMapLatest { it?.lobby ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val locations: StateFlow<Map<PlayerId, LocationFix>> = sessions.state.map { (it as? SessionState.Active)?.session }
        .flatMapLatest { it?.locations ?: flowOf(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val isHost: Boolean get() = lobby.value?.hostId == session?.selfId

    private var dirty = false

    init {
        viewModelScope.launch {
            val first = lobby.filterNotNull().first()
            _draft.value = first.config
            // Follow remote changes when we're not the one editing (e.g. a non-host watching).
            lobby.filterNotNull().collect { if (!dirty) _draft.value = it.config }
        }
        viewModelScope.launch {
            _draft.filterNotNull().debounce(350).collect { cfg ->
                if (dirty && isHost) {
                    runCatching { session?.updateConfig(cfg) }
                    dirty = false
                }
            }
        }
    }

    fun edit(transform: (GameConfig) -> GameConfig) {
        if (!isHost) return
        val current = _draft.value ?: return
        dirty = true
        _draft.value = transform(current)
    }

    fun setValue(key: String, value: kotlinx.serialization.json.JsonPrimitive) =
        edit { it.copy(values = it.values + (key to value), presetId = null) }

    fun switchMode(modeId: String) = edit { engine.switchMode(it, modeId) }
    fun applyPreset(presetId: String) = edit { c -> engine.preset(presetId)?.let { engine.applyPreset(it) } ?: c }
    fun reset() = edit { GameConfig(modeId = it.modeId) }
    fun setZoneCenter(point: app.fugitive.model.GeoPoint) = edit {
        it.copy(values = it.values + (CoreSettings.ZONE_CENTER_POINT.key to kotlinx.serialization.json.JsonPrimitive(point.toString())) +
            (CoreSettings.ZONE_CENTER.key to kotlinx.serialization.json.JsonPrimitive("custom")))
    }
}
