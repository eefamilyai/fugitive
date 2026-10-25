package app.fugitive.engine.rules

import app.fugitive.engine.GameEngine
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.mode.SetupContext
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.zone.ZonePlanner
import app.fugitive.engine.zone.ZoneSpec
import app.fugitive.model.Circle
import app.fugitive.model.GameConfig
import app.fugitive.model.GameState
import app.fugitive.model.GeoPoint
import app.fugitive.model.LocationFix
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerState
import app.fugitive.model.Role
import kotlin.random.Random

/** Everything needed to start a game. */
data class GameSetupRequest(
    val config: GameConfig,
    val participants: List<Member>,
    val roster: Map<PlayerId, Role>,
    val locations: Map<PlayerId, LocationFix>,
    val hostId: PlayerId,
    val round: Int,
    val now: Long,
    val seed: Long = Random.nextLong(),
)

/** Builds the initial [GameState] for a new game. */
object GameFactory {

    fun create(engine: GameEngine, request: GameSetupRequest): Result<GameState> = runCatching {
        val mode = engine.mode(request.config.modeId)
        val settings = engine.settings(request.config)
        val random = Random(request.seed)
        val issues = engine.validate(request.config, request.participants, request.roster, request.locations[request.hostId] != null)
        issues.firstOrNull { it.blocking }?.let { error(it.message) }

        val startAt = request.now + settings[CoreSettings.COUNTDOWN_SEC] * 1000
        val headstartEndsAt = startAt + settings[CoreSettings.HEADSTART_SEC] * 1000
        val endsAt = if (settings[CoreSettings.TIME_LIMIT_ENABLED]) headstartEndsAt + settings[CoreSettings.DURATION_MIN] * 60_000 else null

        val playArea = playArea(settings, request)
        val ctx = SetupContext(
            config = request.config, settings = settings, participants = request.participants,
            roster = request.roster, locations = request.locations, hostId = request.hostId,
            now = request.now, random = random, playArea = playArea,
        )
        val roles = mode.assignRoles(ctx)
        val codes = mutableSetOf<String>()
        val players = request.participants.associate { m ->
            val role = roles[m.id] ?: Role.SPECTATOR
            val p = newPlayer(engine, settings, m, role, codes, random)
            p.catchCode?.let { codes += it }
            m.id to p
        }

        val zone = if (settings[CoreSettings.ZONE_ENABLED] && playArea != null) {
            val shrinkStart = headstartEndsAt + settings[CoreSettings.ZONE_DELAY_MIN] * 60_000
            var duration = settings[CoreSettings.ZONE_SHRINK_MIN] * 60_000
            if (endsAt != null) {
                // Make sure the zone finishes closing a little before the game ends.
                val available = endsAt - shrinkStart
                duration = if (available > 60_000) minOf(duration, (available * 0.9).toLong()) else ((endsAt - headstartEndsAt) * 0.8).toLong()
            }
            val start = if (endsAt != null && endsAt - shrinkStart <= 60_000) headstartEndsAt else shrinkStart
            ZonePlanner.plan(
                ZoneSpec(
                    center = playArea.center,
                    startRadius = playArea.radius,
                    endRadius = settings[CoreSettings.ZONE_END_RADIUS_M].toDouble(),
                    staged = settings[CoreSettings.ZONE_STYLE] == "staged",
                    stages = settings[CoreSettings.ZONE_STAGES].toInt(),
                    shift = settings[CoreSettings.ZONE_SHIFT],
                    shrinkStartAt = start,
                    shrinkDurationMs = duration,
                    warningMs = settings[CoreSettings.ZONE_WARNING_SEC] * 1000,
                    appliesTo = buildList {
                        add(Role.RUNNER.id)
                        if (settings[CoreSettings.ZONE_AFFECTS_HUNTERS]) add(Role.HUNTER.id)
                    },
                ),
                random,
            )
        } else null

        val game = GameState(
            round = request.round, modeId = mode.id, config = request.config, createdAt = request.now,
            startAt = startAt, headstartEndsAt = headstartEndsAt, endsAt = endsAt, zone = zone,
            players = players, seed = request.seed,
        )
        mode.setup(ctx, game)
    }

    /** Centre and radius of the play area according to the zone-centre setting, with sensible fallbacks. */
    fun playArea(settings: Settings, request: GameSetupRequest): Circle? {
        val radius = settings[CoreSettings.ZONE_START_RADIUS_M].toDouble()
        val fixes = request.participants.mapNotNull { request.locations[it.id]?.point }
        val host = request.locations[request.hostId]?.point
        val custom = GeoPoint.parse(settings[CoreSettings.ZONE_CENTER_POINT])
        val center = when (settings[CoreSettings.ZONE_CENTER]) {
            "custom" -> custom ?: host ?: Geo.centroid(fixes)
            "centroid" -> Geo.centroid(fixes) ?: host
            else -> host ?: Geo.centroid(fixes)
        } ?: return null
        return Circle(center.lat, center.lng, radius)
    }

    fun newPlayer(engine: GameEngine, settings: Settings, m: Member, role: Role, takenCodes: Set<String>, random: Random): PlayerState {
        var code: String
        do { code = (random.nextInt(9000) + 1000).toString() } while (code in takenCodes)
        return PlayerState(
            id = m.id, name = m.name, avatar = m.avatar, color = m.color,
            role = role, originalRole = role, catchCode = code,
        )
    }
}
