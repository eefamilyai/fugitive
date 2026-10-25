package app.fugitive.core.data.session

import app.fugitive.core.data.backend.BackendRegistry
import app.fugitive.core.data.backend.GameBackend
import app.fugitive.core.data.backend.JoinException
import app.fugitive.core.data.prefs.LastLobby
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.location.LocationSources
import app.fugitive.engine.GameEngine
import app.fugitive.model.GameConfig
import app.fugitive.model.LobbyCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionState {
    data object Idle : SessionState
    data class Connecting(val message: String) : SessionState
    data class Active(val session: GameSession) : SessionState
}

/**
 * Owns the single active [GameSession]. Survives screen changes (it lives in
 * the application scope) so a game keeps running while you navigate, lock the
 * phone, or the activity is recreated.
 */
class GameSessionManager(
    private val appScope: CoroutineScope,
    private val backends: BackendRegistry,
    private val prefs: UserPreferences,
    private val engine: GameEngine,
    private val locationSources: LocationSources,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val mutex = Mutex()

    /** Last message to show after a session ended unexpectedly. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun consumeNotice() { _notice.value = null }

    val current: GameSession? get() = (state.value as? SessionState.Active)?.session

    val onlineAvailable: Boolean get() = backends.online != null

    /** Create an online lobby (or a practice lobby with bots). */
    suspend fun create(practice: Boolean): Result<GameSession> = mutex.withLock {
        runCatching {
            val backend = (if (practice) backends.practice else backends.online)
                ?: error(if (practice) "Practice mode unavailable" else "Online play isn't set up in this build")
            _state.value = SessionState.Connecting(if (practice) "Setting up practice…" else "Creating lobby…")
            val p = prefs.current()
            val selfId = backend.signIn()
            val config = p.lastConfig ?: GameConfig()
            val code = backend.createLobby(p.profile, config)
            val session = open(backend, code, selfId)
            if (practice) session.botControl?.addBots(p.practiceBots, p.practiceDifficulty)
            session
        }.onFailure { _state.value = SessionState.Idle }
    }

    suspend fun join(rawCode: String): Result<GameSession> = mutex.withLock {
        runCatching {
            val code = LobbyCodes.normalize(rawCode)
            if (!LobbyCodes.isValid(code)) throw JoinException(JoinException.Reason.NOT_FOUND, "Codes are ${LobbyCodes.LENGTH} characters")
            val backend = backends.online ?: error("Online play isn't set up in this build")
            _state.value = SessionState.Connecting("Joining $code…")
            val selfId = backend.signIn()
            backend.joinLobby(code, prefs.current().profile)
            open(backend, code, selfId)
        }.onFailure { _state.value = SessionState.Idle }
    }

    /** Reconnect to the last lobby after the app was closed. */
    suspend fun rejoin(last: LastLobby): Result<GameSession> = mutex.withLock {
        runCatching {
            val backend = backends.get(last.backendId)?.takeIf { it.isConfigured() && it.isOnline } ?: error("Can't reconnect")
            _state.value = SessionState.Connecting("Reconnecting to ${last.code}…")
            val selfId = backend.signIn()
            runCatching { backend.joinLobby(last.code, prefs.current().profile) }
                .onFailure { e -> if (e is JoinException && e.reason == JoinException.Reason.NOT_FOUND) { prefs.setLastLobby(null); throw e } }
            open(backend, last.code, selfId)
        }.onFailure { _state.value = SessionState.Idle }
    }

    private suspend fun open(backend: GameBackend, code: String, selfId: String): GameSession {
        current?.close()
        val session = GameSession(backend, backend.connect(code, selfId), engine, prefs, locationSources, appScope)
        session.awaitLoaded() ?: run { session.close(); error("Couldn't load the lobby. Check your connection.") }
        if (backend.isOnline) prefs.setLastLobby(LastLobby(backend.id, code))
        _state.value = SessionState.Active(session)
        // Watch for the lobby closing underneath us.
        session.scope.launch {
            session.closedReason.collect { reason ->
                if (reason != null) {
                    _notice.value = reason
                    prefs.setLastLobby(null)
                    mutex.withLock { if (current === session) { session.close(); _state.value = SessionState.Idle } }
                }
            }
        }
        return session
    }

    suspend fun leave() = mutex.withLock {
        val s = current ?: return@withLock
        _state.value = SessionState.Idle
        prefs.setLastLobby(null)
        s.leave()
    }
}
