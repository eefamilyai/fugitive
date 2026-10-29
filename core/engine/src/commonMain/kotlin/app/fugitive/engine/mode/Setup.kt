package app.fugitive.engine.mode

import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.Settings
import app.fugitive.model.Circle
import app.fugitive.model.GameConfig
import app.fugitive.model.LocationFix
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.Role
import kotlin.random.Random

/** Inputs available when a game is being created. */
class SetupContext(
    val config: GameConfig,
    val settings: Settings,
    /** Players taking part, in join order. */
    val participants: List<Member>,
    /** Manual role picks made by the host in the lobby. */
    val roster: Map<PlayerId, Role>,
    val locations: Map<PlayerId, LocationFix>,
    val hostId: PlayerId,
    val now: Long,
    val random: Random,
    /** Centre and size of the play area (the initial zone, even if the zone is disabled). */
    val playArea: Circle?,
)

/** Inputs available when validating a configuration in the lobby. */
class ValidationContext(
    val config: GameConfig,
    val settings: Settings,
    val participants: List<Member>,
    val roster: Map<PlayerId, Role>,
    val hostHasLocation: Boolean,
)

data class ConfigIssue(val message: String, val blocking: Boolean, val settingKey: String? = null)

object RoleAssignment {
    /** Standard hunters-vs-runners assignment honouring the hunter-selection setting. */
    fun huntersVsRunners(ctx: SetupContext): Map<PlayerId, Role> {
        val ids = ctx.participants.map { it.id }
        if (ids.isEmpty()) return emptyMap()
        val wanted = ctx.settings[CoreSettings.HUNTER_COUNT].toInt().coerceIn(1, (ids.size - 1).coerceAtLeast(1))
        val hunters: Set<PlayerId> = when (ctx.settings[CoreSettings.HUNTER_SELECTION]) {
            "one_runner" -> (ids - ids.random(ctx.random)).toSet().ifEmpty { setOf(ids.first()) }
            "random" -> ids.shuffled(ctx.random).take(wanted).toSet()
            "volunteers" -> {
                val vols = ctx.participants.filter { it.wantsToHunt }.map { it.id }.shuffled(ctx.random)
                val rest = (ids - vols.toSet()).shuffled(ctx.random)
                (vols + rest).take(wanted).toSet()
            }
            else -> { // manual
                val picked = ctx.roster.filterValues { it == Role.HUNTER }.keys.filter { it in ids }.toSet()
                picked.ifEmpty { ids.shuffled(ctx.random).take(wanted).toSet() }
            }
        }
        val spectators = ctx.roster.filterValues { it == Role.SPECTATOR }.keys
        return ids.associateWith { id ->
            when (id) {
                in spectators -> Role.SPECTATOR
                in hunters -> Role.HUNTER
                else -> Role.RUNNER
            }
        }
    }

    fun lateJoinRole(settings: Settings): Role? = when (settings[CoreSettings.LATE_JOIN]) {
        "hunter" -> Role.HUNTER
        "runner" -> Role.RUNNER
        "spectator" -> Role.SPECTATOR
        else -> null
    }
}
