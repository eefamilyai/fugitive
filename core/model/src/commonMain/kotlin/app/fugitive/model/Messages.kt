package app.fugitive.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * An intent submitted by any player. The host's referee validates and applies
 * it to the game state. Types are open strings (see ActionTypes in the engine)
 * so modes and plugins can define their own.
 */
@Serializable
data class GameAction(
    val id: String = "",
    val round: Int = 0,
    val type: String = "",
    val by: PlayerId = "",
    val target: PlayerId? = null,
    val payload: Map<String, JsonPrimitive> = emptyMap(),
    val createdAt: Long = 0L,
)

/** An entry in the game feed, produced by the referee. */
@Serializable
data class GameEvent(
    val id: String = "",
    val round: Int = 0,
    val type: String = "",
    val at: Long = 0L,
    val actor: PlayerId? = null,
    val target: PlayerId? = null,
    val text: String = "",
    /** 0 = info, 1 = notable, 2 = critical (alerts + vibration). */
    val importance: Int = 0,
    /** Empty = everyone; otherwise role ids and/or player ids that should see it. */
    val audience: List<String> = emptyList(),
) {
    fun isVisibleTo(playerId: PlayerId, role: Role?): Boolean =
        audience.isEmpty() || playerId in audience || (role != null && role.id in audience)
}

/** A map ping dropped by a player (e.g. "spotted here"), visible to the audience for a while. */
@Serializable
data class MapMarker(
    val id: String = "",
    val by: PlayerId = "",
    val kind: String = "spotted",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val audience: List<String> = emptyList(),
    val label: String? = null,
) {
    val point: GeoPoint get() = GeoPoint(lat, lng)
}

/** Short text message in the lobby or game chat. */
@Serializable
data class ChatMessage(
    val id: String = "",
    val by: PlayerId = "",
    val text: String = "",
    val at: Long = 0L,
    /** Empty = everyone; otherwise role ids (team chat). */
    val audience: List<String> = emptyList(),
)
