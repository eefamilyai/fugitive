package app.fugitive.backend.local

import app.fugitive.core.data.backend.GameBackend
import app.fugitive.core.data.backend.JoinException
import app.fugitive.core.data.backend.LobbyConnection
import app.fugitive.engine.GameEngine
import app.fugitive.model.GameConfig
import app.fugitive.model.LobbyCode
import app.fugitive.model.LobbyCodes
import app.fugitive.model.LobbyInfo
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerProfile
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device backend for practice games against bots. No network, no account.
 * Implements the same contract as the online backend, so every screen and
 * rule is exercised exactly as in a real game.
 */
class LocalBackend(
    private val scope: CoroutineScope,
    private val engine: GameEngine,
) : GameBackend {
    override val id = "local"
    override val displayName = "Practice (offline)"
    override val isOnline = false
    override fun isConfigured() = true

    private val lobbies = ConcurrentHashMap<LobbyCode, LocalLobby>()

    override suspend fun signIn(): PlayerId = SELF_ID

    override suspend fun createLobby(profile: PlayerProfile, config: GameConfig): LobbyCode {
        // Only one practice lobby at a time.
        lobbies.values.forEach { it.dispose() }
        lobbies.clear()
        val code = LobbyCodes.random()
        val now = System.currentTimeMillis()
        val host = Member(SELF_ID, profile.name.ifBlank { "You" }, profile.avatar, profile.color, joinedAt = now, ready = true, online = true, lastActive = now)
        lobbies[code] = LocalLobby(
            scope, engine,
            LocalState(info = LobbyInfo(code = code, hostId = SELF_ID, createdAt = now), config = config, members = mapOf(SELF_ID to host)),
        )
        return code
    }

    override suspend fun joinLobby(code: LobbyCode, profile: PlayerProfile) {
        if (!lobbies.containsKey(code)) throw JoinException(JoinException.Reason.NOT_FOUND, "Practice lobbies can't be joined")
    }

    override fun connect(code: LobbyCode, selfId: PlayerId): LobbyConnection {
        val lobby = lobbies[code] ?: throw JoinException(JoinException.Reason.NOT_FOUND, "No such practice lobby")
        return LocalConnection(lobby, selfId) { lobbies.remove(code)?.dispose() }
    }

    companion object { const val SELF_ID = "local-you" }
}
