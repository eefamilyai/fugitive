package app.fugitive.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * The authoritative state of one running (or finished) game.
 *
 * Exactly one device — the host's referee — writes this node; every other
 * device only reads it and submits [GameAction]s. All timestamps are server
 * epoch millis.
 */
@Serializable
data class GameState(
    val round: Int = 1,
    val modeId: String = "classic",
    /** Frozen copy of the lobby configuration at the moment the game started. */
    val config: GameConfig = GameConfig(),
    val createdAt: Long = 0L,
    /** Countdown finishes; runners are released. */
    val startAt: Long = 0L,
    /** Head start finishes; hunters are released. */
    val headstartEndsAt: Long = 0L,
    /** Scheduled end, or null when there is no time limit. */
    val endsAt: Long? = null,
    val zone: ZonePlan? = null,
    val players: Map<PlayerId, PlayerState> = emptyMap(),
    val effects: Map<String, Effect> = emptyMap(),
    val pendingCatches: Map<String, PendingCatch> = emptyMap(),
    val objectives: Map<String, Objective> = emptyMap(),
    /** One-shot markers so announcements fire once, even across host migration. */
    val flags: Map<String, Long> = emptyMap(),
    /** Free-form storage for game modes and plugins. */
    val data: Map<String, JsonPrimitive> = emptyMap(),
    val seed: Long = 0L,
    val result: GameResult? = null,
) {
    val isOver: Boolean get() = result != null
    fun player(id: PlayerId?): PlayerState? = id?.let { players[it] }
    fun active(role: Role): List<PlayerState> = players.values.filter { it.role == role && it.status == PlayerStatus.ACTIVE }
}

@Serializable
data class PlayerState(
    val id: PlayerId = "",
    val name: String = "",
    val avatar: String = "🦊",
    val color: Int = 0,
    val role: Role = Role.RUNNER,
    /** Role assigned at the start, kept for results (e.g. infection converts runners). */
    val originalRole: Role = Role.RUNNER,
    val status: PlayerStatus = PlayerStatus.ACTIVE,
    val statusChangedAt: Long? = null,
    /** Machine-readable reason for leaving play: "caught", "zone", "disconnect", "left", … */
    val outReason: String? = null,
    val caughtBy: PlayerId? = null,
    val catches: Int = 0,
    /** Hunters/infected may be frozen for a while (after a catch, after converting). */
    val frozenUntil: Long? = null,
    /** When the player was first seen outside the zone, for grace periods. */
    val zoneExitAt: Long? = null,
    /** Set while a rule forces this player's location to be revealed. */
    val revealed: Boolean = false,
    /** Assassin-style modes: the player this one must catch. */
    val targetId: PlayerId? = null,
    /** Short code a hunter can type to verify a catch in person. */
    val catchCode: String? = null,
    val abilityUses: Map<String, Int> = emptyMap(),
    val abilityReadyAt: Map<String, Long> = emptyMap(),
    val data: Map<String, JsonPrimitive> = emptyMap(),
)

/** A temporary effect, typically produced by an ability (cloak, radar scan, …). */
@Serializable
data class Effect(
    val id: String = "",
    val type: String = "",
    val ownerId: PlayerId = "",
    val targetId: PlayerId? = null,
    val startsAt: Long = 0L,
    val endsAt: Long = 0L,
) {
    fun isActive(now: Long) = now in startsAt until endsAt
}

/** A catch claimed by a hunter that awaits the runner's confirmation. */
@Serializable
data class PendingCatch(
    val id: String = "",
    val hunterId: PlayerId = "",
    val runnerId: PlayerId = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val distanceMeters: Double? = null,
)

/** A point of interest on the map, e.g. an extraction point. */
@Serializable
data class Objective(
    val id: String = "",
    val type: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radius: Double = 30.0,
    val activeFrom: Long = 0L,
    val activeUntil: Long? = null,
    val label: String? = null,
    /** Which roles can see this objective; empty = everyone. */
    val visibleTo: List<String> = emptyList(),
) {
    val point: GeoPoint get() = GeoPoint(lat, lng)
    fun isActive(now: Long) = now >= activeFrom && (activeUntil == null || now < activeUntil)
}

@Serializable
data class GameResult(
    val endedAt: Long = 0L,
    /** Machine-readable reason, see EndReasons in the engine. */
    val reason: String = "",
    /** The winning role, if a whole side won. */
    val winningRole: Role? = null,
    val winnerIds: List<PlayerId> = emptyList(),
    val headline: String = "",
    val detail: String = "",
)

/** Shrinking zone timeline. Every device interpolates the same plan, so no live updates are needed. */
@Serializable
data class ZonePlan(
    val stages: List<ZoneStage> = emptyList(),
    val appliesTo: List<String> = listOf(Role.RUNNER.id),
) {
    val initial: Circle? get() = stages.firstOrNull()?.from
    val final: Circle? get() = stages.lastOrNull()?.to
}

@Serializable
data class Circle(val lat: Double = 0.0, val lng: Double = 0.0, val radius: Double = 0.0) {
    val center: GeoPoint get() = GeoPoint(lat, lng)
}

@Serializable
data class ZoneStage(
    val index: Int = 0,
    /** When the next circle is announced (shown as an outline). */
    val announceAt: Long = 0L,
    val shrinkStartAt: Long = 0L,
    val shrinkEndAt: Long = 0L,
    val from: Circle = Circle(),
    val to: Circle = Circle(),
)
