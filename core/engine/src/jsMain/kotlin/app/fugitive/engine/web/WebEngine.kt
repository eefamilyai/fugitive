package app.fugitive.engine.web

import app.fugitive.engine.GameEngine
import app.fugitive.engine.projection.GameProjector
import app.fugitive.engine.projection.LocationHistory
import app.fugitive.engine.projection.ProjectedPlayer
import app.fugitive.engine.role.RoleCatalog
import app.fugitive.engine.rules.CatchCheck
import app.fugitive.engine.rules.GameFactory
import app.fugitive.engine.rules.GameSetupRequest
import app.fugitive.engine.rules.Referee
import app.fugitive.engine.rules.RefereeInput
import app.fugitive.engine.settings.ChoiceSetting
import app.fugitive.engine.settings.NumberSetting
import app.fugitive.engine.settings.PointSetting
import app.fugitive.engine.settings.Setting
import app.fugitive.engine.settings.ToggleSetting
import app.fugitive.engine.stats.GameStats
import app.fugitive.engine.time.GameClock
import app.fugitive.model.GameAction
import app.fugitive.model.GameConfig
import app.fugitive.model.GameState
import app.fugitive.model.LobbyInfo
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.Role
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.JsExport
import kotlin.random.Random

/**
 * The shared Kotlin game engine, exported to JavaScript for the web client.
 *
 * Everything crosses the boundary as JSON strings in exactly the format stored in
 * the database, so web and Android players run identical rules on identical data.
 */
@JsExport
class WebEngine {
    private val engine = GameEngine()
    private val projector = GameProjector(engine)
    private val referee = Referee(engine)
    private val history = LocationHistory()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }

    private val locationsSer = MapSerializer(String.serializer(), LocationFix.serializer())
    private val membersSer = MapSerializer(String.serializer(), Member.serializer())

    // ── Catalog & settings ──────────────────────────────────────────────

    fun catalog(): String {
        val roles = listOf(Role.HUNTER, Role.RUNNER, Role.SPECTATOR).map { RoleCatalog.info(it) }
        return json.encodeToString(
            WebCatalog.serializer(),
            WebCatalog(
                modes = engine.modes.map { m ->
                    WebMode(
                        m.id, m.name, m.tagline, m.icon, hex(m.accent), m.rules, m.minPlayers, m.usesHunterSelection,
                        roleLabels = listOf(Role.HUNTER, Role.RUNNER, Role.SPECTATOR).associate { it.id to m.roleLabel(it) },
                        roles = m.roles.map { it.id },
                    )
                },
                presets = engine.presets.map { WebPreset(it.id, it.name, it.description, it.modeId, it.emoji) },
                abilities = engine.abilities.map { WebAbility(it.id, it.name, it.description, it.icon, it.roles.map { r -> r.id }) },
                roles = roles.map { WebRole(it.role.id, it.label, hex(it.color), it.icon) },
                schemaVersion = LobbyInfo.SCHEMA_VERSION,
            ),
        )
    }

    /** Every setting of the config's mode, grouped for the settings editor. */
    fun settingsForm(configJson: String): String {
        val config = decodeConfig(configJson)
        val settings = engine.settings(config)
        val categories = settings.schema.grouped().map { (cat, items) ->
            FormCategory(cat.id, cat.title, cat.icon, items.map { formSetting(it, settings) })
        }
        val values = settings.schema.settings.associate { s -> s.key to (encodedValue(s, settings) as JsonElement) }
        return json.encodeToString(WebForm.serializer(), WebForm(config.modeId, config.presetId, categories, values))
    }

    private fun encodedValue(s: Setting<*>, settings: app.fugitive.engine.settings.Settings): JsonPrimitive = when (s) {
        is ToggleSetting -> s.encode(settings[s])
        is NumberSetting -> s.encode(settings[s])
        is ChoiceSetting -> s.encode(settings[s])
        is PointSetting -> s.encode(settings[s])
    }

    private fun formSetting(s: Setting<*>, settings: app.fugitive.engine.settings.Settings): FormSetting {
        val value = encodedValue(s, settings)
        val base = FormSetting(
            key = s.key, type = "", title = s.title, description = s.description, advanced = s.advanced, nested = s.nested,
            visible = settings.isVisible(s), value = value, display = s.describeRaw(value),
        )
        return when (s) {
            is ToggleSetting -> base.copy(type = "toggle")
            is NumberSetting -> base.copy(
                type = "number", min = s.min, max = s.max, step = s.step, unit = s.unit.name,
                specialLabels = s.specialLabels.mapKeys { it.key.toString() }, suggestions = s.suggestions,
            )
            is ChoiceSetting -> base.copy(type = "choice", options = s.options.map { FormOption(it.id, it.label, it.description) })
            is PointSetting -> base.copy(type = "point")
        }
    }

    /** Returns the config with one setting changed. [valueJson] is a JSON primitive (true, 30, "live"). */
    fun setSetting(configJson: String, key: String, valueJson: String): String {
        val config = decodeConfig(configJson)
        val value = json.parseToJsonElement(valueJson)
        val values = if (value is JsonNull) config.values - key else config.values + (key to value.jsonPrimitive)
        return encodeConfig(config.copy(values = values, presetId = null))
    }

    fun applyPreset(presetId: String): String = encodeConfig(engine.preset(presetId)?.let { engine.applyPreset(it) } ?: GameConfig())
    fun switchMode(configJson: String, modeId: String): String = encodeConfig(engine.switchMode(decodeConfig(configJson), modeId))
    fun resetConfig(configJson: String): String = encodeConfig(GameConfig(modeId = decodeConfig(configJson).modeId))

    // ── Lobby helpers ───────────────────────────────────────────────────

    /** Configuration problems that block or warn before starting. [participantIdsJson]: ids taking part. */
    fun validate(lobbyJson: String, locationsJson: String, participantIdsJson: String): String {
        val lobby = decodeLobby(lobbyJson)
        val locs = decodeLocations(locationsJson)
        val participants = participants(lobby, participantIdsJson)
        val issues = engine.validate(lobby.config, participants, roster(lobby), locs[lobby.info.hostId] != null)
        return json.encodeToString(ListSerializer(WebIssue.serializer()), issues.map { WebIssue(it.message, it.blocking, it.settingKey) })
    }

    /** The starting play area for a lobby as a circle JSON, or "null" when the zone is off. */
    fun playArea(lobbyJson: String, locationsJson: String, participantIdsJson: String): String {
        val lobby = decodeLobby(lobbyJson)
        val settings = engine.settings(lobby.config)
        if (!settings[app.fugitive.engine.settings.CoreSettings.ZONE_ENABLED]) return "null"
        val area = GameFactory.playArea(
            settings,
            GameSetupRequest(lobby.config, participants(lobby, participantIdsJson), roster(lobby), decodeLocations(locationsJson), lobby.info.hostId, 0, 0),
        ) ?: return "null"
        return json.encodeToString(app.fugitive.model.Circle.serializer(), area)
    }

    /** Creates a new game for the lobby (host only). */
    fun startGame(lobbyJson: String, locationsJson: String, participantIdsJson: String, now: Double): String {
        val lobby = decodeLobby(lobbyJson)
        val t = now.toLong()
        val result = GameFactory.create(
            engine,
            GameSetupRequest(
                config = lobby.config, participants = participants(lobby, participantIdsJson), roster = roster(lobby),
                locations = decodeLocations(locationsJson), hostId = lobby.info.hostId, round = lobby.info.round + 1, now = t,
                seed = Random.nextLong(),
            ),
        )
        val out = result.fold(
            onSuccess = { StartResult(true, it, listOf(GameFactory.introEvent(engine, it, t))) },
            onFailure = { StartResult(false, error = it.message ?: "Couldn't start the game") },
        )
        return json.encodeToString(StartResult.serializer(), out)
    }

    /** One referee step (host only). Pure: same inputs → same outputs. */
    fun refereeTick(gameJson: String, membersJson: String, locationsJson: String, actionsJson: String, hostId: String, now: Double): String {
        val game = json.decodeFromString(GameState.serializer(), gameJson)
        val members = json.decodeFromString(membersSer, membersJson).mapValues { (id, m) -> m.copy(id = id) }
        val actions = json.decodeFromString(MapSerializer(String.serializer(), GameAction.serializer()), actionsJson)
            .map { (id, a) -> a.copy(id = id) }
        val out = referee.tick(RefereeInput(game, members, decodeLocations(locationsJson), actions, hostId, now.toLong()))
        return json.encodeToString(
            TickResult.serializer(),
            TickResult(out.changedFrom(game), out.game, out.events, out.consumedActions, out.game.result != null),
        )
    }

    // ── In-game view ────────────────────────────────────────────────────

    /** Remembers locations so periodic "ping" reveals can show where someone was at ping time. */
    fun recordLocations(locationsJson: String) = history.recordAll(decodeLocations(locationsJson))

    fun clearHistory() = history.clear()

    /** What the local player may see right now. Returns "null" when no game exists. */
    fun project(lobbyJson: String, locationsJson: String, selfId: String, now: Double): String {
        val lobby = decodeLobby(lobbyJson)
        val locs = decodeLocations(locationsJson)
        history.recordAll(locs)
        val snapshot = LobbySnapshot(lobby.info, lobby.config, lobby.members.mapValues { (id, m) -> m.copy(id = id) }, roster(lobby), lobby.game)
        val t = now.toLong()
        val p = projector.project(snapshot, locs, history, selfId, t) ?: return "null"
        val s = p.settings
        val showTrails = s[app.fugitive.engine.settings.CoreSettings.SHOW_TRAILS]
        val trailFrom = t - s[app.fugitive.engine.settings.CoreSettings.TRAIL_MINUTES] * 60_000
        fun player(pp: ProjectedPlayer) = WebPlayer(
            id = pp.id, name = pp.state.name, avatar = pp.state.avatar, color = pp.state.color,
            role = pp.state.role.id, originalRole = pp.state.originalRole.id, roleLabel = pp.roleLabel, status = pp.state.status.name,
            outReason = pp.state.outReason, caughtBy = pp.state.caughtBy, catches = pp.state.catches,
            visibility = pp.visibility.kind.name, visibilityReason = pp.visibility.reason,
            fix = pp.fix?.let { WebFix(it.lat, it.lng, it.accuracy, it.bearing, it.speed, it.time, it.battery) },
            ageMs = pp.ageMs, isStale = pp.isStale, distance = pp.distanceToMe, bearing = pp.bearingFromMe,
            isMe = pp.isMe, online = pp.online,
            trail = if (showTrails && pp.visibility.kind.name == "LIVE" && pp.state.status.name == "ACTIVE")
                history.since(pp.id, trailFrom).map { listOf(it.lng, it.lat) } else emptyList(),
        )
        val players = p.players.map(::player)
        val values = engine.settings(p.game.config).let { st -> st.schema.settings.associate { it.key to (encodedValue(it, st) as JsonElement) } }
        val web = WebProjection(
            now = t, stage = p.stage.name, round = p.game.round, modeId = p.game.modeId, modeName = p.modeName,
            myRoleLabel = p.myRoleLabel, objective = p.objective,
            me = players.firstOrNull { it.isMe }, isSpectating = p.isSpectating, isPrey = p.isPrey, players = players,
            zone = p.zone?.let { z -> WebZone(z.current, z.next, z.phase.name, z.phaseEndsAt, z.stageIndex, z.stageCount) },
            zoneStatus = p.zoneStatus?.let { WebZoneStatus(it.inside, it.marginMeters, it.outsideSince, it.graceLeftMs, it.applies) },
            threats = p.threats.map { WebThreat(it.playerId, it.distance, it.bearing) },
            catchOptions = p.catchOptions.map { o ->
                when (val c = o.check) {
                    is CatchCheck.Allowed -> WebCatchOption(o.target.id, true, null, o.target.distanceToMe)
                    is CatchCheck.Denied -> WebCatchOption(o.target.id, false, c.reason, o.target.distanceToMe)
                }
            },
            incomingCatch = p.incomingCatch?.let { WebPending(it.id, it.hunterId, it.runnerId, it.createdAt, it.expiresAt, it.distanceMeters) },
            outgoingCatches = p.outgoingCatches.map { WebPending(it.id, it.hunterId, it.runnerId, it.createdAt, it.expiresAt, it.distanceMeters) },
            abilities = p.abilities.map { a ->
                WebAbilityState(a.ability.id, a.ability.name, a.ability.icon, a.usesLeft, a.readyAt, a.activeUntil, a.blockedReason)
            },
            objectives = p.objectives, timeLeftMs = p.timeLeftMs, stageEndsInMs = p.stageEndsInMs, nextPingAt = p.nextPingAt,
            activeCounts = p.activeCounts.mapKeys { it.key.id },
            startAt = p.game.startAt, headstartEndsAt = p.game.headstartEndsAt, endsAt = p.game.endsAt, result = p.game.result,
            codeLockedMs = projector.codeLockout(p.game, selfId, t),
            settings = values,
        )
        return json.encodeToString(WebProjection.serializer(), web)
    }

    fun stats(gameJson: String, trailsJson: String): String {
        val game = json.decodeFromString(GameState.serializer(), gameJson)
        val trails = json.decodeFromString(MapSerializer(String.serializer(), ListSerializer(LocationFix.serializer())), trailsJson)
        val stats = GameStats.compute(game, trails)
        return json.encodeToString(
            ListSerializer(WebStats.serializer()),
            stats.map { WebStats(it.playerId, it.survivedMs, it.catches, it.distanceMeters, it.topSpeedMps, it.won) },
        )
    }

    fun formatClock(ms: Double): String = GameClock.format(ms.toLong())

    // ── Internals ───────────────────────────────────────────────────────

    private fun decodeConfig(s: String) = if (s.isBlank() || s == "null") GameConfig() else json.decodeFromString(GameConfig.serializer(), s)
    private fun encodeConfig(c: GameConfig) = json.encodeToString(GameConfig.serializer(), c)
    private fun decodeLobby(s: String) = json.decodeFromString(WebLobby.serializer(), s)
    private fun decodeLocations(s: String): Map<PlayerId, LocationFix> =
        if (s.isBlank() || s == "null") emptyMap() else json.decodeFromString(locationsSer, s)

    private fun roster(l: WebLobby): Map<PlayerId, Role> = l.roster.mapValues { Role(it.value) }

    private fun participants(l: WebLobby, idsJson: String): List<Member> {
        val ids = json.decodeFromString(ListSerializer(String.serializer()), idsJson).toSet()
        return l.members.map { (id, m) -> m.copy(id = id) }.filter { it.id in ids }.sortedBy { it.joinedAt }
    }

    private fun hex(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
}
