package app.fugitive.backend.local

import app.fugitive.engine.GameEngine
import app.fugitive.engine.ability.AbilityIds
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.mode.ExtractionMode
import app.fugitive.engine.rules.ActionTypes
import app.fugitive.engine.rules.CatchCheck
import app.fugitive.engine.rules.CatchRules
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.engine.visibility.VisibilityKind
import app.fugitive.engine.visibility.VisibilityRules
import app.fugitive.engine.zone.ZoneMath
import app.fugitive.model.GameAction
import app.fugitive.model.GameState
import app.fugitive.model.GeoPoint
import app.fugitive.model.LobbyPhase
import app.fugitive.model.LocationFix
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerState
import app.fugitive.model.PlayerStatus
import app.fugitive.model.jsonOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * Simulated players for practice. Each bot walks the real map: runners flee
 * hunters, stay inside the zone and race to extraction; hunters chase whoever
 * they're allowed to see. Bots act only through normal game actions, so the
 * referee treats them exactly like people.
 */
class BotDirector(
    private val scope: CoroutineScope,
    private val engine: GameEngine,
    private val lobby: LocalLobby,
) {
    private class Bot(
        val id: PlayerId,
        var position: GeoPoint?,
        var heading: Double,
        val skill: Double,
        var lastTrail: Long = 0,
        var lastAbility: Long = 0,
        var wanderTarget: GeoPoint? = null,
    )

    private val bots = LinkedHashMap<PlayerId, Bot>()
    private val random = Random(System.nanoTime())
    private var job: Job? = null
    private val respondedCatches = HashSet<String>()

    fun isBot(id: PlayerId) = synchronized(bots) { id in bots }

    fun add(count: Int, difficulty: Int) {
        val now = System.currentTimeMillis()
        val skill = when (difficulty) { 0 -> 0.75; 2 -> 1.25; else -> 1.0 }
        synchronized(bots) {
            val used = lobby.state.value.members.values.map { it.name }.toSet()
            val names = NAMES.filter { it.first !in used }.shuffled(random)
            repeat(count.coerceAtMost(names.size)) { i ->
                val (name, avatar) = names[i]
                val id = "bot-${name.lowercase()}-${random.nextInt(1000, 9999)}"
                bots[id] = Bot(id, null, random.nextDouble(360.0), skill * (0.9 + random.nextDouble() * 0.2))
                lobby.update { st ->
                    st.copy(members = st.members + (id to Member(id, name, avatar, color = random.nextInt(12), joinedAt = now + i, ready = true, online = true, lastActive = now, wantsToHunt = random.nextBoolean())))
                }
            }
        }
        ensureRunning()
    }

    fun remove(id: PlayerId) {
        synchronized(bots) { bots.remove(id) }
        lobby.update { st ->
            st.copy(members = st.members - id, locations = st.locations - id, roster = st.roster - id)
        }
    }

    fun stop() { job?.cancel(); job = null }

    private fun ensureRunning() {
        if (job?.isActive == true) return
        job = scope.launch {
            var last = System.currentTimeMillis()
            while (isActive) {
                delay(TICK_MS)
                val now = System.currentTimeMillis()
                val dt = ((now - last) / 1000.0).coerceIn(0.1, 3.0)
                last = now
                runCatching { step(now, dt) }
            }
        }
    }

    private fun step(now: Long, dt: Double) {
        val st = lobby.state.value
        val anchor = st.locations[st.info.hostId]?.point ?: return // wait for the human's GPS
        val list = synchronized(bots) { bots.values.toList() }
        val newFixes = HashMap<PlayerId, LocationFix>()
        val newActions = mutableListOf<GameAction>()
        val game = st.game?.takeIf { st.info.phase == LobbyPhase.PLAYING && it.result == null }

        for (bot in list) {
            val pos = bot.position ?: Geo.destination(anchor, random.nextDouble(360.0), 40.0 + random.nextDouble() * 110.0).also { bot.position = it }
            val me = game?.players?.get(bot.id)
            val (target, speed) = if (game != null && me != null) decide(bot, me, game, st.locations, now, newActions) else idle(bot, pos)
            val moved = if (target != null && speed > 0) moveToward(bot, pos, target, speed * dt) else pos
            bot.position = moved
            newFixes[bot.id] = LocationFix(
                moved.lat, moved.lng, accuracy = 4f + random.nextFloat() * 4f,
                bearing = bot.heading.toFloat(), speed = speed.toFloat(), time = now, battery = 80, source = "bot",
            )
        }

        lobby.update { s ->
            var trails = s.trails
            if (game != null) for (bot in list) {
                val f = newFixes[bot.id] ?: continue
                if (now - bot.lastTrail >= 5_000) {
                    bot.lastTrail = now
                    trails = trails + (bot.id to (trails[bot.id].orEmpty() + f).takeLast(5000))
                }
            }
            s.copy(
                locations = s.locations + newFixes,
                trails = trails,
                actions = s.actions + newActions.map { it.copy(id = lobby.newId("bot")) },
            )
        }
    }

    private fun idle(bot: Bot, pos: GeoPoint): Pair<GeoPoint?, Double> {
        // Mill about near the spawn point.
        val t = bot.wanderTarget
        if (t == null || Geo.distance(pos, t) < 3 || random.nextDouble() < 0.02) {
            bot.wanderTarget = Geo.destination(pos, random.nextDouble(360.0), 10.0 + random.nextDouble() * 20)
        }
        return bot.wanderTarget to 0.8
    }

    private fun decide(
        bot: Bot,
        me: PlayerState,
        game: GameState,
        locations: Map<PlayerId, LocationFix>,
        now: Long,
        actions: MutableList<GameAction>,
    ): Pair<GeoPoint?, Double> {
        val pos = bot.position!!
        val stage = GameClock.stage(game, now)
        val mode = engine.mode(game.modeId)
        val settings = engine.settings(game.config)
        respondToCatches(bot, game, locations, now, actions)
        if (me.status != PlayerStatus.ACTIVE || stage == GameStage.COUNTDOWN) return null to 0.0
        if (me.frozenUntil?.let { now < it } == true) return null to 0.0

        val zone = game.zone?.let { ZoneMath.snapshot(it, now) }
        val prey = game.players.values.filter { it.status == PlayerStatus.ACTIVE && mode.canCatch(game, me, it) }
        val threats = VisibilityRules.threats(engine, game, bot.id, locations, 220.0 * bot.skill, now)
        val isPredator = prey.isNotEmpty() && (stage == GameStage.ACTIVE)

        // 1. Stay inside (or get into) the zone, aiming for the announced next circle.
        val appliesZone = zone != null && me.role.id in game.zone!!.appliesTo && stage != GameStage.COUNTDOWN
        if (appliesZone) {
            val goal = zone!!.next ?: zone.current
            val margin = ZoneMath.marginToEdge(pos, zone.current)
            val nextMargin = ZoneMath.marginToEdge(pos, goal)
            if (margin < 25 || (zone.next != null && nextMargin < 10)) {
                return goal.center to runSpeed(bot, urgent = margin < 10)
            }
        }

        // 2. Extraction for runners.
        if (game.modeId == ExtractionMode.ID && !isPredator) {
            game.objectives.values.filter { it.type == ExtractionMode.OBJECTIVE_TYPE && it.isActive(now) }
                .minByOrNull { Geo.distance(pos, it.point) }
                ?.let { o -> return (if (Geo.distance(pos, o.point) < o.radius * 0.4) pos else o.point) to runSpeed(bot, urgent = true) }
        }

        // 3. Predators chase the nearest prey they can see (or remember).
        if (isPredator) {
            val seen = prey.mapNotNull { p ->
                val vis = VisibilityRules.decide(engine, game, bot.id, p.id, now)
                val fix = when (vis.kind) {
                    VisibilityKind.LIVE -> locations[p.id]
                    VisibilityKind.SNAPSHOT -> locations[p.id]?.let { lf -> lf.takeIf { it.time <= (vis.snapshotAt ?: 0) } ?: lastSnapshot[p.id] }
                    VisibilityKind.HIDDEN -> null
                }
                if (vis.kind == VisibilityKind.SNAPSHOT && locations[p.id] != null && locations[p.id]!!.time <= (vis.snapshotAt ?: 0)) lastSnapshot[p.id] = locations[p.id]!!
                fix?.let { p to it }
            }
            val chase = seen.minByOrNull { (_, f) -> Geo.distance(pos, f.point) }
            if (chase != null) {
                val (p, fix) = chase
                val d = Geo.distance(pos, fix.point)
                val check = CatchRules.check(engine, game, bot.id, p.id, now, locations)
                val radius = settings[CoreSettings.CATCH_RADIUS_M].toDouble()
                if (check is CatchCheck.Allowed && d <= maxOf(radius, 12.0) && game.pendingCatches.values.none { it.runnerId == p.id }) {
                    val method = settings[CoreSettings.CATCH_METHOD]
                    actions += if (method == "code") {
                        GameAction(round = game.round, type = ActionTypes.CODE_CATCH, by = bot.id, payload = mapOf("code" to jsonOf(p.catchCode)), createdAt = now)
                    } else {
                        GameAction(round = game.round, type = ActionTypes.CATCH_CLAIM, by = bot.id, target = p.id, createdAt = now)
                    }
                }
                maybeUseAbility(bot, me, game, now, actions, AbilityIds.RADAR_SCAN, chance = 0.0)
                return fix.point to runSpeed(bot, urgent = d < 120)
            }
            // Nobody visible: scan if we can, otherwise sweep towards the zone centre.
            maybeUseAbility(bot, me, game, now, actions, AbilityIds.RADAR_SCAN, chance = 0.04)
            return wander(bot, pos, zone?.current?.center, zone?.current?.radius ?: 300.0) to walkSpeed(bot)
        }

        // 4. Prey flee from nearby threats.
        if (threats.isNotEmpty()) {
            val t = threats.first()
            if (t.distance < 60) maybeUseAbility(bot, me, game, now, actions, AbilityIds.CLOAK, chance = 0.3)
            if (t.distance < 120) maybeUseAbility(bot, me, game, now, actions, AbilityIds.SONAR, chance = 0.05)
            val away = Geo.destination(pos, (t.bearing + 180 + random.nextDouble(-35.0, 35.0)) % 360, 60.0)
            // Don't flee out of the zone.
            val safe = if (zone != null && ZoneMath.marginToEdge(away, zone.current) < 30) zone.current.center else away
            return safe to runSpeed(bot, urgent = t.distance < 80)
        }

        // 5. Head start: put distance between us and the hunters' start.
        if (stage == GameStage.HEADSTART) {
            return wander(bot, pos, zone?.current?.center, (zone?.current?.radius ?: 400.0) * 0.8) to runSpeed(bot, urgent = false)
        }
        return wander(bot, pos, zone?.current?.center, (zone?.current?.radius ?: 400.0) * 0.8) to walkSpeed(bot)
    }

    private val lastSnapshot = HashMap<PlayerId, LocationFix>()

    private fun respondToCatches(bot: Bot, game: GameState, locations: Map<PlayerId, LocationFix>, now: Long, actions: MutableList<GameAction>) {
        val pending = game.pendingCatches.values.firstOrNull { it.runnerId == bot.id } ?: return
        if (pending.id in respondedCatches || now - pending.createdAt < 2_500) return
        respondedCatches += pending.id
        val d = locations[pending.hunterId]?.let { f -> bot.position?.let { Geo.distance(it, f.point) } } ?: 0.0
        actions += GameAction(
            round = game.round, type = ActionTypes.CATCH_RESPONSE, by = bot.id,
            payload = mapOf("catch" to jsonOf(pending.id), "accept" to jsonOf(d < 45)), createdAt = now,
        )
    }

    private fun maybeUseAbility(bot: Bot, me: PlayerState, game: GameState, now: Long, actions: MutableList<GameAction>, id: String, chance: Double) {
        val ability = engine.ability(id) ?: return
        val settings = engine.settings(game.config)
        if (!ability.isAvailableTo(settings, me)) return
        if ((me.abilityUses[id] ?: 0) >= settings[ability.uses]) return
        if (now < (me.abilityReadyAt[id] ?: 0) || now - bot.lastAbility < 20_000) return
        if (chance <= 0 || random.nextDouble() > chance) return
        bot.lastAbility = now
        actions += GameAction(round = game.round, type = ActionTypes.USE_ABILITY, by = bot.id, payload = mapOf("ability" to jsonOf(id)), createdAt = now)
    }

    private fun wander(bot: Bot, pos: GeoPoint, center: GeoPoint?, radius: Double): GeoPoint {
        val t = bot.wanderTarget
        if (t == null || Geo.distance(pos, t) < 8 || random.nextDouble() < 0.01) {
            bot.wanderTarget = if (center != null) Geo.randomPointInCircle(center, radius * 0.75, random)
            else Geo.destination(pos, random.nextDouble(360.0), 80.0)
        }
        return bot.wanderTarget!!
    }

    private fun runSpeed(bot: Bot, urgent: Boolean) = (if (urgent) 3.4 else 2.6) * bot.skill
    private fun walkSpeed(bot: Bot) = 1.4 * bot.skill

    private fun moveToward(bot: Bot, from: GeoPoint, to: GeoPoint, meters: Double): GeoPoint {
        val d = Geo.distance(from, to)
        if (d < 0.5) return from
        val desired = Geo.bearing(from, to)
        // Smooth turning plus a little jitter, so paths look human.
        var delta = ((desired - bot.heading + 540) % 360) - 180
        delta = delta.coerceIn(-60.0, 60.0)
        bot.heading = (bot.heading + delta + random.nextDouble(-6.0, 6.0) + 360) % 360
        val step = minOf(meters, d)
        return Geo.destination(from, if (abs(delta) < 60) bot.heading else desired, step)
    }

    companion object {
        private const val TICK_MS = 1_000L
        private val NAMES = listOf(
            "Ava" to "🦊", "Milo" to "🐺", "Zara" to "🦁", "Kai" to "🐯", "Luna" to "🦉", "Finn" to "🐸",
            "Nova" to "🐼", "Ezra" to "🦅", "Iris" to "🐙", "Leo" to "🐻", "Maya" to "🦄", "Jude" to "🐲",
            "Rhea" to "🐨", "Theo" to "🦈", "Sky" to "🐝",
        )
    }
}
