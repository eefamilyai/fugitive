package app.fugitive.core.data.session

import app.fugitive.core.data.backend.GameBackend
import app.fugitive.core.data.backend.LobbyConnection
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.location.LocationPermissionException
import app.fugitive.core.location.LocationRequestSpec
import app.fugitive.core.location.LocationSources
import app.fugitive.core.location.SimulatedLocationSource
import app.fugitive.engine.GameEngine
import app.fugitive.engine.rules.ActionTypes
import app.fugitive.engine.rules.GameFactory
import app.fugitive.engine.rules.GameSetupRequest
import app.fugitive.engine.rules.Referee
import app.fugitive.engine.rules.RefereeInput
import app.fugitive.engine.rules.EventTypes
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.model.ChatMessage
import app.fugitive.model.GameAction
import app.fugitive.model.GameConfig
import app.fugitive.model.GameEvent
import app.fugitive.model.GameState
import app.fugitive.model.GeoPoint
import app.fugitive.model.LobbyPhase
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.MapMarker
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import app.fugitive.model.jsonOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Optional capability of practice lobbies: computer-controlled players. */
interface BotControl {
    suspend fun addBots(count: Int, difficulty: Int)
    suspend fun removeBot(id: PlayerId)
    fun isBot(id: PlayerId): Boolean
}

enum class LocationProblem { PERMISSION, DISABLED, UNAVAILABLE }

/**
 * A live connection to one lobby plus everything that runs while you're in
 * it: location sharing, the referee loop (on the host), host take-over, and
 * the [GameProjection] the UI renders. Backend-agnostic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameSession internal constructor(
    val backend: GameBackend,
    val connection: LobbyConnection,
    val engine: GameEngine,
    private val prefs: UserPreferences,
    private val locationSources: LocationSources,
    parentScope: CoroutineScope,
) {
    val selfId: PlayerId = connection.selfId
    val code: String = connection.code
    val isPractice: Boolean = !backend.isOnline
    val clock = connection.clock
    val botControl: BotControl? = connection as? BotControl

    val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[kotlinx.coroutines.Job]) + Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
                // A bug in one background loop must never take the whole game down.
                android.util.Log.e("GameSession", "Background task failed", e)
            },
    )
    val history = LocationHistory()
    private val referee = Referee(engine)
    private val projector = GameProjector(engine)

    // ── Streams ─────────────────────────────────────────────────────────
    private val _closedReason = MutableStateFlow<String?>(null)
    /** Set when the lobby disappears or we are removed from it. */
    val closedReason: StateFlow<String?> = _closedReason.asStateFlow()

    private var everLoaded = false
    val lobby: StateFlow<LobbySnapshot?> = connection.lobby
        .onEach { snap ->
            if (snap != null && selfId in snap.members) everLoaded = true
            else if (everLoaded && _closedReason.value == null) {
                _closedReason.value = if (snap == null) "The lobby was closed" else "You were removed from the lobby"
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val selfFix = MutableStateFlow<LocationFix?>(null)
    val myFix: StateFlow<LocationFix?> = selfFix.asStateFlow()

    private val remoteLocations: StateFlow<Map<PlayerId, LocationFix>> = connection.locations
        .onEach { history.recordAll(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Everyone's latest location; our own is merged in instantly (no network round-trip). */
    val locations: StateFlow<Map<PlayerId, LocationFix>> = combine(remoteLocations, selfFix) { remote, mine ->
        if (mine == null) remote else remote + (selfId to mine)
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val events: StateFlow<List<GameEvent>> = combine(connection.events, lobby) { all, snap ->
        val round = snap?.info?.round ?: 0
        val role = snap?.game?.players?.get(selfId)?.role
        all.filter { it.round == round && it.isVisibleTo(selfId, role) }.sortedBy { it.at }.takeLast(200)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val markers: StateFlow<List<MapMarker>> = combine(connection.markers, lobby) { all, snap ->
        val role = snap?.game?.players?.get(selfId)?.role
        all.filter { it.audience.isEmpty() || selfId in it.audience || (role != null && role.id in it.audience) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val chat: StateFlow<List<ChatMessage>> = combine(connection.chat, lobby) { all, snap ->
        val role = snap?.game?.players?.get(selfId)?.role
        all.filter { it.audience.isEmpty() || it.by == selfId || (role != null && role.id in it.audience) }.sortedBy { it.at }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val connected: StateFlow<Boolean> = connection.connected

    private val _locationProblem = MutableStateFlow<LocationProblem?>(null)
    val locationProblem: StateFlow<LocationProblem?> = _locationProblem.asStateFlow()
    private val locationRetry = MutableStateFlow(0)

    val isHost: StateFlow<Boolean> = lobby.map { it?.hostId == selfId }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Projection for the local player, refreshed twice a second while a game exists. */
    val projection: StateFlow<GameProjection?> = combine(lobby, locations, ticker(500)) { snap, locs, now ->
        snap?.let { projector.project(it, locs, history, selfId, now) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun now(): Long = clock.now()

    private fun ticker(periodMs: Long): Flow<Long> = flow {
        while (currentCoroutineContextActive()) { emit(clock.now()); delay(periodMs) }
    }

    private suspend fun currentCoroutineContextActive() = kotlinx.coroutines.currentCoroutineContext().isActive

    init {
        startLocationSharing()
        startReferee()
        startHostTakeover()
    }

    // ── Location sharing ────────────────────────────────────────────────

    private var lastTrailAt = 0L

    private fun startLocationSharing() = scope.launch {
        val specFlow = lobby.map { snap ->
            // The lobby phase and the game node arrive through separate listeners, so either may lag.
            val game = snap?.game?.takeIf { snap.info.phase == LobbyPhase.PLAYING && it.result == null }
            val seconds = game?.let { engine.settings(it.config)[CoreSettings.LOCATION_INTERVAL_SEC] } ?: 4L
            LocationRequestSpec(intervalMs = seconds * 1000, highAccuracy = true)
        }.distinctUntilChanged()
        val sourceFlow = prefs.preferences.map { it.locationSource }.distinctUntilChanged()

        combine(specFlow, sourceFlow, locationRetry) { spec, src, _ -> spec to src }
            .flatMapLatest { (spec, srcPref) ->
                val source = locationSources.resolve(srcPref)
                if (source is SimulatedLocationSource) seedSimulator(source)
                source.fixes(spec)
                    .onEach { _locationProblem.value = null }
                    .catch { e ->
                        _locationProblem.value = if (e is LocationPermissionException || e is SecurityException) LocationProblem.PERMISSION else LocationProblem.UNAVAILABLE
                    }
            }
            .collect { raw -> onLocalFix(raw.copy(time = clock.now())) }
    }

    private suspend fun seedSimulator(sim: SimulatedLocationSource) {
        if (sim.position.value != null) return
        val real = withTimeoutOrNull(3_000) {
            runCatching { locationSources.resolve("auto").fixes(LocationRequestSpec(1000)).firstOrNull() }.getOrNull()
        }
        val fallback = real?.point
            ?: remoteLocations.value.values.firstOrNull()?.point
            ?: DEFAULT_START
        sim.ensureStart(fallback)
    }

    private suspend fun onLocalFix(fix: LocationFix) {
        selfFix.value = fix
        history.record(selfId, fix)
        runCatching { connection.publishLocation(fix) }
        val snap = lobby.value
        if (snap?.info?.phase == LobbyPhase.PLAYING && snap.game?.result == null && fix.time - lastTrailAt >= TRAIL_INTERVAL_MS) {
            lastTrailAt = fix.time
            runCatching { connection.appendTrail(fix) }
        }
    }

    /** Call after the user grants permission or turns location on. */
    fun retryLocation() { locationRetry.value++ }

    // ── Referee (host only) ─────────────────────────────────────────────

    private val tickMutex = Mutex()
    private var lastWritten: GameState? = null
    private val processedActions = HashSet<String>()

    private fun startReferee() = scope.launch {
        lobby.map { it != null && it.hostId == selfId && it.info.phase == LobbyPhase.PLAYING && it.game != null }
            .distinctUntilChanged()
            .collectLatest { isReferee ->
                if (!isReferee) return@collectLatest
                lastWritten = null
                processedActions.clear()
                coroutineScope {
                    val actions = MutableStateFlow<List<GameAction>>(emptyList())
                    launch { connection.actions.collect { actions.value = it } }
                    while (isActive) {
                        val started = System.currentTimeMillis()
                        runCatching { refereeTick(actions.value) }
                        delay((REFEREE_PERIOD_MS - (System.currentTimeMillis() - started)).coerceAtLeast(100))
                    }
                }
            }
    }

    private suspend fun refereeTick(actions: List<GameAction>) = tickMutex.withLock {
        val snap = lobby.value ?: return
        val current = snap.game ?: return
        // Our own last write is the truth (we are the only writer); fall back to the snapshot after a host change.
        val base = lastWritten?.takeIf { it.round == current.round } ?: current
        if (base.result != null && snap.info.phase == LobbyPhase.RESULTS) return
        val fresh = actions.filter { it.id !in processedActions }
        val out = referee.tick(RefereeInput(base, snap.members, locations.value, fresh, selfId, clock.now()))
        if (!out.changedFrom(base) && !(out.game.result != null && snap.info.phase != LobbyPhase.RESULTS)) return
        val phase = if (out.game.result != null) LobbyPhase.RESULTS else null
        connection.commitTick(out.game, out.events, out.consumedActions, phase)
        lastWritten = out.game
        processedActions += out.consumedActions
    }

    // ── Host take-over ──────────────────────────────────────────────────

    private data class HostCheck(val hostId: PlayerId, val hostOnline: Boolean, val successor: PlayerId?)

    private fun startHostTakeover() = scope.launch {
        lobby.filterNotNull().map { l ->
            val successor = l.members.values.filter { it.online }.minByOrNull { it.joinedAt }?.id
            HostCheck(l.hostId, l.members[l.hostId]?.online == true, successor)
        }.flatMapLatest { c ->
            // Re-evaluate periodically so a host whose heartbeat silently stopped is also replaced.
            flow {
                while (true) {
                    val host = lobby.value?.members?.get(c.hostId)
                    val stale = host == null || clock.now() - host.lastActive > HOST_STALE_MS
                    emit(c.copy(hostOnline = c.hostOnline && !stale))
                    delay(10_000)
                }
            }
        }.distinctUntilChanged().collectLatest { c ->
            if (isPractice || c.hostOnline || c.hostId == selfId || c.successor != selfId) return@collectLatest
            delay(HOST_TIMEOUT_MS)
            runCatching { connection.claimHost(c.hostId) }
        }
    }

    // ── Lobby commands ──────────────────────────────────────────────────

    suspend fun setReady(ready: Boolean) = connection.updateSelf { it.copy(ready = ready) }
    suspend fun setWantsToHunt(wants: Boolean) = connection.updateSelf { it.copy(wantsToHunt = wants) }
    suspend fun updateProfile(name: String, avatar: String, color: Int) =
        connection.updateSelf { it.copy(name = name, avatar = avatar, color = color) }

    suspend fun updateConfig(config: GameConfig) {
        connection.updateConfig(config)
        prefs.setLastConfig(config)
    }

    suspend fun setRole(id: PlayerId, role: Role?) {
        val roster = lobby.value?.roster.orEmpty().toMutableMap()
        if (role == null) roster.remove(id) else roster[id] = role
        connection.setRoster(roster)
    }

    suspend fun kick(id: PlayerId) = connection.kick(id)
    suspend fun transferHost(id: PlayerId) = connection.transferHost(id)
    suspend fun setLocked(locked: Boolean) = connection.setLocked(locked)

    /** Host: validates and starts a game. */
    suspend fun startGame(): Result<Unit> = runCatching {
        val snap = lobby.value ?: error("Not connected")
        check(snap.hostId == selfId) { "Only the host can start" }
        val participants = snap.membersByJoin.filter { it.online || botControl?.isBot(it.id) == true }
        val now = clock.now()
        val game = GameFactory.create(
            engine,
            GameSetupRequest(
                config = snap.config, participants = participants, roster = snap.roster,
                locations = locations.value, hostId = selfId, round = snap.info.round + 1, now = now,
            ),
        ).getOrThrow()
        val mode = engine.mode(game.modeId)
        val hunters = game.players.values.filter { it.role == Role.HUNTER }.joinToString { it.name }
        val intro = GameEvent(
            round = game.round, type = EventTypes.GAME_STARTED, at = now, importance = 1,
            text = if (mode.usesHunterSelection && hunters.isNotEmpty()) "${mode.name} — ${mode.roleLabel(Role.HUNTER)}: $hunters" else "${mode.name} begins",
        )
        lastWritten = null
        connection.startGame(game, listOf(intro))
    }

    /** Host: back to the lobby for another round with the same players. */
    suspend fun playAgain() = connection.resetToLobby()

    // ── Game commands (validated by the referee) ────────────────────────

    private suspend fun submit(type: String, target: PlayerId? = null, vararg payload: Pair<String, Any>) {
        val round = lobby.value?.game?.round ?: return
        connection.submitAction(
            GameAction(
                round = round, type = type, by = selfId, target = target,
                payload = payload.associate { (k, v) -> k to jsonOf(v) }, createdAt = clock.now(),
            ),
        )
    }

    suspend fun claimCatch(target: PlayerId) = submit(ActionTypes.CATCH_CLAIM, target)
    suspend fun respondToCatch(catchId: String, accept: Boolean) = submit(ActionTypes.CATCH_RESPONSE, null, "catch" to catchId, "accept" to accept)
    suspend fun submitCatchCode(code: String) = submit(ActionTypes.CODE_CATCH, null, "code" to code)
    suspend fun reportCaught(by: PlayerId?) = submit(ActionTypes.SELF_REPORT, by)
    suspend fun useAbility(id: String) = submit(ActionTypes.USE_ABILITY, null, "ability" to id)
    suspend fun forfeit() = submit(ActionTypes.LEAVE)
    suspend fun endGameEarly() = submit(ActionTypes.HOST_END)

    suspend fun sendChat(text: String, teamOnly: Boolean) {
        val clean = text.trim().take(280)
        if (clean.isEmpty()) return
        val role = lobby.value?.game?.players?.get(selfId)?.takeIf { it.status == PlayerStatus.ACTIVE }?.role
        connection.sendChat(ChatMessage(by = selfId, text = clean, at = clock.now(), audience = if (teamOnly && role != null) listOf(role.id) else emptyList()))
    }

    /** Drops a short-lived ping on the map for your team (or everyone outside a game). */
    suspend fun dropMarker(kind: String, point: GeoPoint, teamOnly: Boolean = true) {
        val role = lobby.value?.game?.players?.get(selfId)?.role
        val now = clock.now()
        connection.placeMarker(
            MapMarker(
                by = selfId, kind = kind, lat = point.lat, lng = point.lng, createdAt = now, expiresAt = now + MARKER_TTL_MS,
                audience = if (teamOnly && role != null) listOf(role.id) else emptyList(),
            ),
        )
    }

    suspend fun trails(): Map<PlayerId, List<LocationFix>> = connection.trails()

    fun member(id: PlayerId?): Member? = id?.let { lobby.value?.members?.get(it) }

    internal suspend fun leave() {
        runCatching { if (lobby.value?.game?.result == null) forfeit() }
        runCatching { connection.leave() }
        close()
    }

    internal fun close() {
        connection.close()
        scope.cancel()
    }

    suspend fun awaitLoaded(timeoutMs: Long = 15_000): LobbySnapshot? =
        withTimeoutOrNull(timeoutMs) { lobby.filterNotNull().first { selfId in it.members } }

    companion object {
        const val REFEREE_PERIOD_MS = 1_000L
        const val HOST_TIMEOUT_MS = 8_000L
        const val HOST_STALE_MS = 50_000L
        const val TRAIL_INTERVAL_MS = 5_000L
        const val MARKER_TTL_MS = 120_000L
        val DEFAULT_START = GeoPoint(51.5073, -0.1657)
    }
}
