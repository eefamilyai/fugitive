package app.fugitive.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** Top-level phase of a lobby. Sub-phases (countdown, head start…) derive from GameState timestamps. */
@Serializable
enum class LobbyPhase { LOBBY, PLAYING, RESULTS }

@Serializable
data class LobbyInfo(
    val code: LobbyCode = "",
    val hostId: PlayerId = "",
    val createdAt: Long = 0L,
    val phase: LobbyPhase = LobbyPhase.LOBBY,
    /** Incremented for every game played in this lobby; scopes actions and events. */
    val round: Int = 0,
    /** When true, new players cannot join. */
    val locked: Boolean = false,
    /** Data-format version, bumped on incompatible schema changes. */
    val schema: Int = SCHEMA_VERSION,
) {
    companion object { const val SCHEMA_VERSION = 1 }
}

/** A person in the lobby. Each player writes only their own member record. */
@Serializable
data class Member(
    val id: PlayerId = "",
    val name: String = "",
    val avatar: String = "🦊",
    /** Index into the shared player colour palette. */
    val color: Int = 0,
    val joinedAt: Long = 0L,
    val ready: Boolean = false,
    val online: Boolean = true,
    val lastActive: Long = 0L,
    /** Volunteers to be a hunter (used by the "volunteers" hunter selection). */
    val wantsToHunt: Boolean = false,
    val appVersion: Int = 0,
)

/** Game configuration: the selected mode plus every setting value, keyed by setting key. */
@Serializable
data class GameConfig(
    val modeId: String = "classic",
    val presetId: String? = null,
    val values: Map<String, JsonPrimitive> = emptyMap(),
)

/** Everything about a lobby except high-frequency streams (locations, events). */
data class LobbySnapshot(
    val info: LobbyInfo,
    val config: GameConfig,
    val members: Map<PlayerId, Member>,
    /** Host's manual role assignments made in the lobby. */
    val roster: Map<PlayerId, Role>,
    val game: GameState?,
) {
    val code: LobbyCode get() = info.code
    val hostId: PlayerId get() = info.hostId
    fun isHost(id: PlayerId) = info.hostId == id
    val membersByJoin: List<Member> get() = members.values.sortedBy { it.joinedAt }
}

/** Profile of the local user, used when creating or joining lobbies. */
@Serializable
data class PlayerProfile(
    val name: String = "",
    val avatar: String = "🦊",
    val color: Int = 0,
)
