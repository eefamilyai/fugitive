package app.fugitive.engine.mode

import app.fugitive.engine.rules.RuleContext
import app.fugitive.engine.role.RoleCatalog
import app.fugitive.engine.settings.Setting
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.visibility.VisibilityContext
import app.fugitive.engine.visibility.VisibilityDecision
import app.fugitive.model.GameAction
import app.fugitive.model.GameResult
import app.fugitive.model.GameState
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerState
import app.fugitive.model.Role
import kotlinx.serialization.json.JsonPrimitive

/**
 * A game mode: the pluggable rule set on top of the shared core rules.
 *
 * To add a mode, implement this interface (usually by extending [BaseMode])
 * and register it with the GameEngine — nothing else in the app needs to
 * change. Lobby UI, settings editor, map and HUD all adapt automatically.
 */
interface GameMode {
    val id: String
    val name: String
    /** One-line pitch shown on the mode card. */
    val tagline: String
    /** Full rules text shown in "How to play". */
    val rules: List<String>
    /** Icon key resolved by the UI. */
    val icon: String
    /** ARGB accent colour for the mode card. */
    val accent: Long
    val minPlayers: Int

    /** Roles that can appear in this mode, for lobby display. */
    val roles: List<Role>

    /** Whether the lobby's hunter-selection controls apply. */
    val usesHunterSelection: Boolean

    fun roleLabel(role: Role): String
    /** What a player of [role] must do, shown on their HUD. */
    fun objective(role: Role): String
    /** Objective for a specific player (e.g. naming their target); defaults to [objective]. */
    fun objectiveFor(game: GameState, player: PlayerState): String

    /** Mode-specific settings, appended to the core settings. */
    fun settings(): List<Setting<*>>
    /** Default values this mode prefers for core settings. */
    fun defaults(): Map<String, JsonPrimitive>
    /** Core setting keys that make no sense in this mode. */
    fun hiddenSettings(): Set<String>

    fun validate(ctx: ValidationContext): List<ConfigIssue>

    /** Decide every participant's starting role. */
    fun assignRoles(ctx: SetupContext): Map<PlayerId, Role>
    /** Final touches on a freshly created game (targets, objectives, …). */
    fun setup(ctx: SetupContext, game: GameState): GameState

    /** Role given to someone joining mid-game, or null to refuse. */
    fun lateJoinRole(settings: Settings, game: GameState): Role?

    /** May [catcher] catch [target]? */
    fun canCatch(game: GameState, catcher: PlayerState, target: PlayerState): Boolean
    /** Apply a confirmed catch. */
    fun onCatch(ctx: RuleContext, catcher: PlayerState?, target: PlayerState)
    /** Called whenever a player leaves play for any reason. */
    fun onPlayerOut(ctx: RuleContext, player: PlayerState)
    /** Called every referee tick after the core rules. */
    fun onTick(ctx: RuleContext)
    /** Handle a custom action type. Return true if handled. */
    fun handleAction(ctx: RuleContext, action: GameAction): Boolean

    /** Override the core visibility rules; null = use the core rules. */
    fun visibility(ctx: VisibilityContext): VisibilityDecision?

    /** Return a result to end the game. */
    fun checkResult(ctx: RuleContext): GameResult?
}

/** Sensible defaults for every hook, so a mode only overrides what it changes. */
abstract class BaseMode : GameMode {
    override val minPlayers: Int = 2
    override val roles: List<Role> = listOf(Role.HUNTER, Role.RUNNER)
    override val usesHunterSelection: Boolean = true

    override fun roleLabel(role: Role): String = RoleCatalog.info(role).label
    override fun objective(role: Role): String = when (role) {
        Role.HUNTER -> "Catch every runner"
        Role.RUNNER -> "Don't get caught"
        else -> "Watch the game"
    }
    override fun objectiveFor(game: GameState, player: PlayerState): String = objective(player.role)
    override fun settings(): List<Setting<*>> = emptyList()
    override fun defaults(): Map<String, JsonPrimitive> = emptyMap()
    override fun hiddenSettings(): Set<String> = emptySet()
    override fun validate(ctx: ValidationContext): List<ConfigIssue> = emptyList()
    override fun assignRoles(ctx: SetupContext): Map<PlayerId, Role> = RoleAssignment.huntersVsRunners(ctx)
    override fun setup(ctx: SetupContext, game: GameState): GameState = game
    override fun lateJoinRole(settings: Settings, game: GameState): Role? = RoleAssignment.lateJoinRole(settings)
    override fun canCatch(game: GameState, catcher: PlayerState, target: PlayerState): Boolean =
        catcher.role == Role.HUNTER && target.role == Role.RUNNER
    override fun onCatch(ctx: RuleContext, catcher: PlayerState?, target: PlayerState) = CoreOutcomes.eliminateOnCatch(ctx, catcher, target)
    override fun onPlayerOut(ctx: RuleContext, player: PlayerState) {}
    override fun onTick(ctx: RuleContext) {}
    override fun handleAction(ctx: RuleContext, action: GameAction): Boolean = false
    override fun visibility(ctx: VisibilityContext): VisibilityDecision? = null
}
