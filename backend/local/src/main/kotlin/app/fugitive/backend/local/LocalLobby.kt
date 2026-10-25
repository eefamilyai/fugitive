package app.fugitive.backend.local

import app.fugitive.engine.GameEngine
import app.fugitive.model.ChatMessage
import app.fugitive.model.GameAction
import app.fugitive.model.GameConfig
import app.fugitive.model.GameEvent
import app.fugitive.model.GameState
import app.fugitive.model.LobbyInfo
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.MapMarker
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Complete state of one practice lobby. */
data class LocalState(
    val info: LobbyInfo,
    val config: GameConfig,
    val members: Map<PlayerId, Member>,
    val roster: Map<PlayerId, Role> = emptyMap(),
    val game: GameState? = null,
    val locations: Map<PlayerId, LocationFix> = emptyMap(),
    val events: List<GameEvent> = emptyList(),
    val actions: List<GameAction> = emptyList(),
    val markers: List<MapMarker> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val trails: Map<PlayerId, List<LocationFix>> = emptyMap(),
) {
    val snapshot: LobbySnapshot get() = LobbySnapshot(info, config, members, roster, game)
}

class LocalLobby(scope: CoroutineScope, engine: GameEngine, initial: LocalState) {
    val state = MutableStateFlow(initial)
    private var nextId = 0L
    val bots = BotDirector(scope, engine, this)

    @Synchronized
    fun newId(prefix: String): String = "$prefix-${System.currentTimeMillis().toString(36)}-${(nextId++).toString(36)}"

    fun update(transform: (LocalState) -> LocalState) = state.update(transform)

    fun dispose() = bots.stop()
}
