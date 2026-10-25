package app.fugitive.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * A player's role. Roles are open string ids rather than an enum so new game
 * modes can introduce their own (e.g. "medic", "juggernaut") without touching
 * the model. The engine's RoleCatalog supplies labels, colours and icons.
 */
@Serializable
@JvmInline
value class Role(val id: String) {
    override fun toString(): String = id

    companion object {
        val HUNTER = Role("hunter")
        val RUNNER = Role("runner")
        val SPECTATOR = Role("spectator")
    }
}

/** Life-cycle of a player within one game. */
@Serializable
enum class PlayerStatus {
    /** In play. */
    ACTIVE,
    /** Tagged by a hunter. */
    CAUGHT,
    /** Knocked out by a rule: outside the zone, disconnected, … */
    ELIMINATED,
    /** Reached an objective and left the game victorious. */
    ESCAPED,
    /** Quit the game. */
    LEFT;

    val isOut: Boolean get() = this != ACTIVE
}
