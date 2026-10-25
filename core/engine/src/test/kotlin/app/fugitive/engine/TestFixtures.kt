package app.fugitive.engine

import app.fugitive.engine.geo.Geo
import app.fugitive.engine.rules.GameFactory
import app.fugitive.engine.rules.GameSetupRequest
import app.fugitive.engine.rules.Referee
import app.fugitive.engine.rules.RefereeInput
import app.fugitive.engine.rules.RefereeOutcome
import app.fugitive.model.GameAction
import app.fugitive.model.GameConfig
import app.fugitive.model.GameState
import app.fugitive.model.GeoPoint
import app.fugitive.model.LocationFix
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.Role
import app.fugitive.model.jsonOf

val ORIGIN = GeoPoint(1.3521, 103.8198)

fun member(id: String, joined: Long = 0) = Member(id = id, name = id.replaceFirstChar { it.uppercase() }, joinedAt = joined)

fun fixAt(p: GeoPoint, time: Long, accuracy: Float = 5f) = LocationFix(p.lat, p.lng, accuracy = accuracy, time = time)

/** Point [meters] east (bearing 90) of the origin. */
fun east(meters: Double) = Geo.destination(ORIGIN, 90.0, meters)

fun config(modeId: String = "classic", vararg values: Pair<String, Any>) =
    GameConfig(modeId = modeId, values = values.associate { (k, v) -> k to jsonOf(v) })

class Harness(
    val engine: GameEngine = GameEngine(),
    config: GameConfig,
    ids: List<String>,
    roster: Map<PlayerId, Role> = emptyMap(),
    val now0: Long = 1_000_000L,
) {
    val referee = Referee(engine)
    val members = ids.mapIndexed { i, id -> member(id, i.toLong()) }.associateBy { it.id }
    val locations = ids.associateWith { fixAt(ORIGIN, now0) }.toMutableMap()
    var game: GameState = GameFactory.create(
        engine,
        GameSetupRequest(config, members.values.toList(), roster, locations, hostId = ids.first(), round = 1, now = now0, seed = 42),
    ).getOrThrow()
    val events = mutableListOf<app.fugitive.model.GameEvent>()

    fun move(id: String, p: GeoPoint, at: Long, accuracy: Float = 5f) { locations[id] = fixAt(p, at, accuracy) }

    fun tick(now: Long, vararg actions: GameAction): RefereeOutcome {
        val out = referee.tick(RefereeInput(game, members, locations, actions.toList(), members.keys.first(), now))
        game = out.game
        events += out.events
        return out
    }

    fun action(type: String, by: String, target: String? = null, at: Long, vararg payload: Pair<String, Any>) =
        GameAction(id = "a$at$by$type", round = game.round, type = type, by = by, target = target,
            payload = payload.associate { (k, v) -> k to jsonOf(v) }, createdAt = at)

    /** First instant the hunt is active. */
    val active get() = game.headstartEndsAt
}
