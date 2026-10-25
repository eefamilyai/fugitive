package app.fugitive.backend.local

import app.fugitive.core.data.backend.LobbyConnection
import app.fugitive.core.data.backend.ServerClock
import app.fugitive.core.data.backend.SystemServerClock
import app.fugitive.core.data.session.BotControl
import app.fugitive.model.ChatMessage
import app.fugitive.model.GameAction
import app.fugitive.model.GameConfig
import app.fugitive.model.GameEvent
import app.fugitive.model.GameState
import app.fugitive.model.LobbyPhase
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.MapMarker
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class LocalConnection(
    private val local: LocalLobby,
    override val selfId: PlayerId,
    private val onLeave: () -> Unit,
) : LobbyConnection, BotControl {
    override val code: String = local.state.value.info.code
    override val clock: ServerClock = SystemServerClock()
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)

    private val s get() = local.state

    override val lobby: Flow<LobbySnapshot?> = s.map { it.snapshot }.distinctUntilChanged()
    override val locations: Flow<Map<PlayerId, LocationFix>> = s.map { it.locations }.distinctUntilChanged()
    override val events: Flow<List<GameEvent>> = s.map { it.events }.distinctUntilChanged()
    override val actions: Flow<List<GameAction>> = s.map { it.actions }.distinctUntilChanged()
    override val markers: Flow<List<MapMarker>> = s.map { st -> st.markers.filter { it.expiresAt > System.currentTimeMillis() } }.distinctUntilChanged()
    override val chat: Flow<List<ChatMessage>> = s.map { it.chat }.distinctUntilChanged()

    override suspend fun trails(): Map<PlayerId, List<LocationFix>> = s.value.trails

    override suspend fun updateSelf(transform: (Member) -> Member) = local.update { st ->
        val me = st.members[selfId] ?: return@update st
        st.copy(members = st.members + (selfId to transform(me)))
    }

    override suspend fun publishLocation(fix: LocationFix) = local.update { it.copy(locations = it.locations + (selfId to fix)) }

    override suspend fun appendTrail(fix: LocationFix) = local.update { st ->
        st.copy(trails = st.trails + (selfId to (st.trails[selfId].orEmpty() + fix).takeLast(5000)))
    }

    override suspend fun submitAction(action: GameAction) = local.update { st ->
        st.copy(actions = st.actions + action.copy(id = action.id.ifEmpty { local.newId("act") }))
    }

    override suspend fun sendChat(message: ChatMessage) = local.update { st ->
        st.copy(chat = (st.chat + message.copy(id = local.newId("msg"))).takeLast(200))
    }

    override suspend fun placeMarker(marker: MapMarker) = local.update { st ->
        val now = System.currentTimeMillis()
        st.copy(markers = st.markers.filter { it.expiresAt > now } + marker.copy(id = local.newId("mk")))
    }

    override suspend fun leave() = onLeave()

    override suspend fun updateConfig(config: GameConfig) = local.update { it.copy(config = config) }
    override suspend fun setRoster(roster: Map<PlayerId, Role>) = local.update { it.copy(roster = roster) }
    override suspend fun setLocked(locked: Boolean) = local.update { it.copy(info = it.info.copy(locked = locked)) }

    override suspend fun kick(id: PlayerId) = removeBot(id)
    override suspend fun transferHost(id: PlayerId) { /* the human is always host in practice */ }

    override suspend fun startGame(game: GameState, events: List<GameEvent>) = local.update { st ->
        st.copy(
            info = st.info.copy(phase = LobbyPhase.PLAYING, round = game.round),
            game = game,
            events = events.map { it.copy(id = local.newId("ev")) },
            actions = emptyList(), trails = emptyMap(), markers = emptyList(),
        )
    }

    override suspend fun commitTick(game: GameState, events: List<GameEvent>, consumedActions: List<String>, phase: LobbyPhase?) = local.update { st ->
        val consumed = consumedActions.toSet()
        st.copy(
            game = game,
            info = if (phase != null) st.info.copy(phase = phase) else st.info,
            events = (st.events + events.map { it.copy(id = local.newId("ev")) }).takeLast(400),
            actions = st.actions.filter { it.id !in consumed },
        )
    }

    override suspend fun resetToLobby() = local.update { st ->
        st.copy(
            info = st.info.copy(phase = LobbyPhase.LOBBY), game = null, events = emptyList(), actions = emptyList(),
            trails = emptyMap(), markers = emptyList(),
        )
    }

    override suspend fun claimHost(expectedHost: PlayerId): Boolean = false

    override fun close() {}

    // ── Bots ────────────────────────────────────────────────────────────
    override suspend fun addBots(count: Int, difficulty: Int) = local.bots.add(count, difficulty)
    override suspend fun removeBot(id: PlayerId) = local.bots.remove(id)
    override fun isBot(id: PlayerId): Boolean = local.bots.isBot(id)
}
