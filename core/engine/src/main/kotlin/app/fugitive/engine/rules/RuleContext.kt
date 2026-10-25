package app.fugitive.engine.rules

import app.fugitive.engine.GameEngine
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.mode.GameMode
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.model.GameEvent
import app.fugitive.model.GameResult
import app.fugitive.model.GameState
import app.fugitive.model.LocationFix
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/**
 * Mutable working copy of a game used during one referee tick. Rules, modes
 * and abilities read and modify the state through it; the referee collects the
 * result. Everything is deterministic given the same inputs.
 */
class RuleContext(
    initial: GameState,
    val engine: GameEngine,
    val now: Long,
    val locations: Map<PlayerId, LocationFix>,
    val members: Map<PlayerId, Member>,
    val hostId: PlayerId,
    val random: Random = Random(initial.seed xor now),
) {
    var state: GameState = initial
        private set

    val mode: GameMode = engine.mode(initial.modeId)
    val settings: Settings = engine.settings(initial.config)
    private val _events = mutableListOf<GameEvent>()
    val events: List<GameEvent> get() = _events

    val stage: GameStage get() = GameClock.stage(state, now)
    val isTimeUp: Boolean get() = state.endsAt?.let { now >= it } ?: false

    fun player(id: PlayerId?): PlayerState? = state.player(id)

    fun updateState(transform: (GameState) -> GameState) { state = transform(state) }

    fun updatePlayer(id: PlayerId, transform: (PlayerState) -> PlayerState) {
        val p = state.players[id] ?: return
        state = state.copy(players = state.players + (id to transform(p)))
    }

    fun setPlayerData(id: PlayerId, key: String, value: JsonPrimitive?) = updatePlayer(id) {
        it.copy(data = if (value == null) it.data - key else it.data + (key to value))
    }

    fun emit(
        type: String,
        text: String,
        actor: PlayerId? = null,
        target: PlayerId? = null,
        importance: Int = 0,
        audience: List<String> = emptyList(),
    ) {
        _events += GameEvent(
            round = state.round, type = type, at = now, actor = actor, target = target,
            text = text, importance = importance, audience = audience,
        )
    }

    fun hasFlag(key: String) = key in state.flags
    fun setFlag(key: String) { state = state.copy(flags = state.flags + (key to now)) }

    /** Runs [block] the first time [key] is seen in this game. */
    inline fun once(key: String, block: () -> Unit) {
        if (!hasFlag(key)) { setFlag(key); block() }
    }

    /** A location usable for rules: recent enough and accurate enough. */
    fun usableFix(id: PlayerId): LocationFix? {
        val fix = locations[id] ?: return null
        val maxAcc = settings[CoreSettings.MAX_ACCURACY_M]
        if (fix.accuracy > maxAcc) return null
        val staleMs = settings[CoreSettings.STALE_AFTER_SEC] * 1000
        if (now - fix.time > staleMs * 2) return null
        return fix
    }

    fun distance(a: PlayerId, b: PlayerId): Double? {
        val fa = usableFix(a) ?: return null
        val fb = usableFix(b) ?: return null
        return Geo.distance(fa.point, fb.point)
    }

    /**
     * Moves a player out of play (or back in) and notifies the mode, which may
     * react — e.g. assassin modes re-link targets.
     */
    fun setStatus(id: PlayerId, status: PlayerStatus, reason: String?, caughtBy: PlayerId? = null) {
        val before = player(id) ?: return
        if (before.status == status) return
        updatePlayer(id) {
            it.copy(
                status = status, statusChangedAt = now, outReason = reason,
                caughtBy = caughtBy ?: it.caughtBy, zoneExitAt = null, revealed = false,
            )
        }
        if (status.isOut) mode.onPlayerOut(this, player(id)!!)
    }

    fun end(result: GameResult) {
        if (state.result != null) return
        state = state.copy(result = result.copy(endedAt = now), pendingCatches = emptyMap())
    }

    fun name(id: PlayerId?): String = player(id)?.name ?: members[id]?.name ?: "Someone"
    fun label(p: PlayerState): String = mode.roleLabel(p.role)
}
