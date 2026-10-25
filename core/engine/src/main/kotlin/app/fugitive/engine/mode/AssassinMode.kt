package app.fugitive.engine.mode

import app.fugitive.engine.ability.AbilityIds
import app.fugitive.engine.rules.EndReasons
import app.fugitive.engine.rules.EventTypes
import app.fugitive.engine.rules.RuleContext
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.Setting
import app.fugitive.engine.settings.SettingCategory
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.settings.ToggleSetting
import app.fugitive.engine.visibility.VisibilityContext
import app.fugitive.engine.visibility.VisibilityDecision
import app.fugitive.engine.visibility.VisibilityRules
import app.fugitive.engine.time.GameStage
import app.fugitive.model.GameResult
import app.fugitive.model.GameState
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import kotlinx.serialization.json.JsonPrimitive

/** Everyone hunts a secret target while being hunted. Inherit your victim's target. Last agent standing wins. */
class AssassinMode : BaseMode() {
    override val id = ID
    override val name = "Assassin"
    override val tagline = "Hunt your target. Someone is hunting you."
    override val icon = "target"
    override val accent = 0xFFA855F7
    override val minPlayers = 3
    override val roles = listOf(Role.RUNNER)
    override val usesHunterSelection = false
    override val rules = listOf(
        "Every agent secretly gets one target; together the targets form a single loop.",
        "You see only your target on the map. Someone else sees you.",
        "Catch your target to take over their target.",
        "With self-defence on, catching the agent hunting you also counts.",
        "The last agent standing wins. At the time limit, most catches wins.",
    )

    val selfDefence = ToggleSetting(
        "assassin_self_defence", "Self-defence", "Catching the agent who is hunting you also counts.",
        SettingCategory.MODE, default = true,
    )

    override fun settings(): List<Setting<*>> = listOf(selfDefence)

    override fun defaults(): Map<String, JsonPrimitive> = mapOf(
        CoreSettings.RUNNER_REVEAL.key to JsonPrimitive("interval"),
        CoreSettings.RUNNER_REVEAL_INTERVAL_SEC.key to JsonPrimitive(60),
        CoreSettings.PROXIMITY_ALERT_M.key to JsonPrimitive(60),
        CoreSettings.HEADSTART_SEC.key to JsonPrimitive(60),
        CoreSettings.TIME_LIMIT_ENABLED.key to JsonPrimitive(true),
    )

    override fun hiddenSettings(): Set<String> = setOf(
        CoreSettings.HUNTER_SELECTION.key, CoreSettings.HUNTER_COUNT.key, CoreSettings.HUNTER_REVEAL.key,
        CoreSettings.HUNTER_REVEAL_INTERVAL_SEC.key, CoreSettings.SHOW_TEAMMATES.key, CoreSettings.LATE_JOIN.key,
        CoreSettings.ZONE_AFFECTS_HUNTERS.key, CoreSettings.REVEAL_DURING_HEADSTART.key,
    )

    override fun roleLabel(role: Role): String = if (role == Role.RUNNER) "Agent" else super.roleLabel(role)
    override fun objective(role: Role): String = if (role == Role.RUNNER) "Catch your target, stay alive" else super.objective(role)

    override fun objectiveFor(game: GameState, player: PlayerState): String {
        val target = game.player(player.targetId)
        return if (player.role == Role.RUNNER && target != null) "Target: ${target.name}" else objective(player.role)
    }

    override fun assignRoles(ctx: SetupContext): Map<PlayerId, Role> {
        val spectators = ctx.roster.filterValues { it == Role.SPECTATOR }.keys
        return ctx.participants.associate { it.id to if (it.id in spectators) Role.SPECTATOR else Role.RUNNER }
    }

    override fun setup(ctx: SetupContext, game: GameState): GameState {
        val agents = game.players.values.filter { it.role == Role.RUNNER }.map { it.id }.shuffled(ctx.random)
        if (agents.size < 2) return game
        val players = game.players.toMutableMap()
        agents.forEachIndexed { i, id -> players[id] = players[id]!!.copy(targetId = agents[(i + 1) % agents.size]) }
        return game.copy(players = players)
    }

    override fun lateJoinRole(settings: Settings, game: GameState): Role = Role.SPECTATOR

    override fun canCatch(game: GameState, catcher: PlayerState, target: PlayerState): Boolean {
        if (catcher.role != Role.RUNNER || target.role != Role.RUNNER) return false
        if (catcher.targetId == target.id) return true
        val defence = game.config.values[selfDefence.key]?.content?.toBooleanStrictOrNull() ?: selfDefence.default
        return defence && target.targetId == catcher.id
    }

    override fun onPlayerOut(ctx: RuleContext, player: PlayerState) {
        // Whoever was hunting the eliminated agent inherits their target.
        val hunter = ctx.state.players.values.firstOrNull { it.status == PlayerStatus.ACTIVE && it.targetId == player.id } ?: return
        val newTarget = player.targetId?.takeIf { it != hunter.id && ctx.player(it)?.status == PlayerStatus.ACTIVE }
            ?: ctx.state.players.values.firstOrNull { it.status == PlayerStatus.ACTIVE && it.id != hunter.id && it.role == Role.RUNNER }?.id
        ctx.updatePlayer(hunter.id) { it.copy(targetId = newTarget) }
        ctx.updatePlayer(player.id) { it.copy(targetId = null) }
        if (newTarget != null) {
            ctx.emit(EventTypes.TARGET, "🎯 New target: ${ctx.name(newTarget)}", target = newTarget, audience = listOf(hunter.id), importance = 2)
        }
    }

    override fun visibility(ctx: VisibilityContext): VisibilityDecision? {
        val viewer = ctx.viewer
        val subject = ctx.subject
        if (ctx.effectActive(AbilityIds.CLOAK) { it?.id == subject.id }) return VisibilityDecision.hidden("Cloaked")
        if (viewer.targetId == subject.id) {
            if (ctx.stage != GameStage.ACTIVE) return VisibilityDecision.hidden("Head start")
            if (subject.revealed) return VisibilityDecision.live("Outside zone")
            val s = ctx.settings
            return VisibilityRules.byMode(s[CoreSettings.RUNNER_REVEAL], s[CoreSettings.RUNNER_REVEAL_INTERVAL_SEC] * 1000, ctx.game.headstartEndsAt, ctx.now)
                .let { if (it.reason == null || it.reason == "Ping") it.copy(reason = "Your target") else it }
        }
        if (subject.targetId == viewer.id && ctx.effectActive(AbilityIds.SONAR) { it?.id == viewer.id }) return VisibilityDecision.live("Sonar")
        if (subject.revealed) return VisibilityDecision.live("Outside zone")
        return VisibilityDecision.HIDDEN
    }

    override fun checkResult(ctx: RuleContext): GameResult? {
        val agents = ctx.state.players.values.filter { it.role == Role.RUNNER }
        val alive = agents.filter { it.status == PlayerStatus.ACTIVE }
        if (alive.size <= 1 && agents.size >= 2) {
            val winner = alive.firstOrNull()
            return GameResult(
                reason = EndReasons.LAST_STANDING, winningRole = null, winnerIds = listOfNotNull(winner?.id),
                headline = winner?.let { "${it.name} wins!" } ?: "No survivors",
                detail = winner?.let { "Last agent standing with ${it.catches} ${if (it.catches == 1) "catch" else "catches"}." } ?: "",
            )
        }
        if (ctx.isTimeUp) {
            val best = alive.maxOfOrNull { it.catches } ?: 0
            val winners = alive.filter { it.catches == best }
            return GameResult(
                reason = EndReasons.TIME_UP, winningRole = null, winnerIds = winners.map { it.id },
                headline = if (winners.size == 1) "${winners[0].name} wins!" else "It's a tie!",
                detail = "Most catches when time ran out ($best).",
            )
        }
        return null
    }

    companion object { const val ID = "assassin" }
}
