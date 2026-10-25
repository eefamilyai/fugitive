package app.fugitive.core.data.backend

import app.fugitive.model.ChatMessage
import app.fugitive.model.GameAction
import app.fugitive.model.GameConfig
import app.fugitive.model.GameEvent
import app.fugitive.model.GameState
import app.fugitive.model.LobbyCode
import app.fugitive.model.LobbyPhase
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.MapMarker
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerProfile
import app.fugitive.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport for lobbies and games. The rest of the app depends only on this
 * contract, so the networking technology can be swapped (Firebase today; a
 * self-hosted WebSocket server, Supabase, … tomorrow) by adding a module that
 * implements it and registering it in DI.
 */
interface GameBackend {
    /** Stable id, e.g. "firebase" or "local". */
    val id: String
    val displayName: String
    /** True for real multiplayer over the network; false for on-device practice. */
    val isOnline: Boolean

    /** Whether the backend has the configuration it needs (e.g. google-services.json). */
    fun isConfigured(): Boolean

    /** Signs in (anonymously where applicable) and returns the player id. */
    suspend fun signIn(): PlayerId

    /** Creates a lobby with the caller as host and returns its join code. */
    suspend fun createLobby(profile: PlayerProfile, config: GameConfig): LobbyCode

    /** Adds the caller to an existing lobby. Throws [JoinException] on failure. */
    suspend fun joinLobby(code: LobbyCode, profile: PlayerProfile)

    /** Opens live streams and commands for a lobby the caller belongs to. */
    fun connect(code: LobbyCode, selfId: PlayerId): LobbyConnection
}

class JoinException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_FOUND, LOCKED, FULL, IN_PROGRESS, INCOMPATIBLE, NETWORK, UNKNOWN }
}

interface ServerClock {
    /** Current time in server epoch millis. */
    fun now(): Long
    val offsetMs: StateFlow<Long>
}

/** Live connection to one lobby. Streams are hot-ready cold flows; collect them as needed. */
interface LobbyConnection {
    val code: LobbyCode
    val selfId: PlayerId
    val clock: ServerClock
    /** Whether the device currently has a live connection to the backend. */
    val connected: StateFlow<Boolean>

    /** Lobby state; emits null when the lobby no longer exists or we were removed. */
    val lobby: Flow<LobbySnapshot?>
    val locations: Flow<Map<PlayerId, LocationFix>>
    val events: Flow<List<GameEvent>>
    val actions: Flow<List<GameAction>>
    val markers: Flow<List<MapMarker>>
    val chat: Flow<List<ChatMessage>>

    /** Recorded movement of every player for the current round. */
    suspend fun trails(): Map<PlayerId, List<LocationFix>>

    // ── Any player ──────────────────────────────────────────────────────
    suspend fun updateSelf(transform: (Member) -> Member)
    suspend fun publishLocation(fix: LocationFix)
    suspend fun appendTrail(fix: LocationFix)
    suspend fun submitAction(action: GameAction)
    suspend fun sendChat(message: ChatMessage)
    suspend fun placeMarker(marker: MapMarker)
    /** Leaves the lobby (deleting it when the last player leaves). */
    suspend fun leave()

    // ── Host / referee only ─────────────────────────────────────────────
    suspend fun updateConfig(config: GameConfig)
    suspend fun setRoster(roster: Map<PlayerId, Role>)
    suspend fun setLocked(locked: Boolean)
    suspend fun kick(id: PlayerId)
    suspend fun transferHost(id: PlayerId)
    /** Writes a new game, bumps the round and switches the lobby to PLAYING. */
    suspend fun startGame(game: GameState, events: List<GameEvent>)
    /** Atomically writes the referee's new state, its events, and deletes consumed actions. */
    suspend fun commitTick(game: GameState, events: List<GameEvent>, consumedActions: List<String>, phase: LobbyPhase?)
    /** Clears the finished game and returns everyone to the lobby. */
    suspend fun resetToLobby()
    /** Takes over as host if [expectedHost] is still recorded as host. */
    suspend fun claimHost(expectedHost: PlayerId): Boolean

    /** Releases listeners. */
    fun close()
}
