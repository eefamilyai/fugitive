package app.fugitive.core.data.alerts

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.data.service.Notifications
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.data.session.SessionState
import app.fugitive.engine.rules.EventTypes
import app.fugitive.engine.time.GameStage
import app.fugitive.model.GameEvent
import app.fugitive.model.PlayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turns game events and danger into feedback: in-app banners (via [banners]),
 * vibrations, and notifications when the app is in the background.
 */
class GameAlerts(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sessions: GameSessionManager,
    private val prefs: UserPreferences,
) {
    val haptics = Haptics(context)
    private val _banners = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)
    /** Events worth a banner in the UI. */
    val banners: SharedFlow<GameEvent> = _banners.asSharedFlow()

    private val inForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    fun start() {
        scope.launch { prefs.preferences.collect { haptics.enabled = it.haptics } }
        scope.launch {
            sessions.state.collectLatest { state ->
                val session = (state as? SessionState.Active)?.session ?: return@collectLatest
                val seen = HashSet<String>()
                var primed = false
                launch {
                    session.events.collect { events ->
                        if (!primed) { events.forEach { seen += it.id }; primed = true; return@collect }
                        events.filter { seen.add(it.id) }.forEach { onEvent(it, session.selfId) }
                    }
                }
                // Continuous danger feedback: hunters nearby, outside the zone, pings.
                launch {
                    var lastBeat = 0L
                    var lastDanger = 0L
                    var lastPing: Long? = null
                    var lastStage: GameStage? = null
                    while (isActive) {
                        val p = session.projection.value
                        val now = System.currentTimeMillis()
                        if (p != null && !p.isOver) {
                            if (lastStage == GameStage.COUNTDOWN && p.stage == GameStage.HEADSTART) haptics.play(HapticPattern.ALERT)
                            lastStage = p.stage
                            val me = p.me
                            if (me != null && me.status == PlayerStatus.ACTIVE) {
                                val threat = p.threats.firstOrNull()
                                if (threat != null) {
                                    val radius = p.settings[app.fugitive.engine.settings.CoreSettings.PROXIMITY_ALERT_M].toDouble().coerceAtLeast(1.0)
                                    val closeness = (threat.distance / radius).coerceIn(0.0, 1.0)
                                    val period = (600 + 2400 * closeness).toLong()
                                    if (now - lastBeat >= period) { lastBeat = now; haptics.play(HapticPattern.HEARTBEAT) }
                                }
                                val zs = p.zoneStatus
                                if (zs != null && zs.applies && !zs.inside && now - lastDanger >= 2_500) {
                                    lastDanger = now; haptics.play(HapticPattern.DANGER)
                                }
                            }
                            val ping = p.nextPingAt
                            if (lastPing != null && ping != null && ping != lastPing && p.stage == GameStage.ACTIVE) haptics.play(HapticPattern.TICK)
                            lastPing = ping
                        }
                        delay(250)
                    }
                }
            }
        }
    }

    private suspend fun onEvent(event: GameEvent, selfId: String) {
        val aboutMe = event.target == selfId || event.audience.contains(selfId)
        val pattern = when (event.type) {
            EventTypes.CATCH_CLAIMED -> if (event.target == selfId) HapticPattern.CAUGHT else HapticPattern.NOTICE
            EventTypes.CAUGHT, EventTypes.INFECTED, EventTypes.ELIMINATED -> if (event.target == selfId) HapticPattern.CAUGHT else HapticPattern.NOTICE
            EventTypes.RELEASED, EventTypes.ZONE_SHRINKING, EventTypes.ZONE_WARNING, EventTypes.REVEALED -> HapticPattern.ALERT
            EventTypes.OUTSIDE_ZONE -> HapticPattern.DANGER
            EventTypes.ESCAPED, EventTypes.GAME_OVER -> HapticPattern.SUCCESS
            EventTypes.TARGET -> HapticPattern.ALERT
            else -> if (event.importance >= 1) HapticPattern.NOTICE else null
        }
        if (event.importance >= 1 || aboutMe) {
            pattern?.let { haptics.play(it) }
            _banners.tryEmit(event)
        }
        if (!inForeground && (event.importance >= 2 || (aboutMe && event.importance >= 1))) {
            withContext(Dispatchers.Main) { Notifications.alert(context, titleFor(event), event.text) }
        }
    }

    private fun titleFor(e: GameEvent) = when (e.type) {
        EventTypes.CATCH_CLAIMED -> "Catch claimed!"
        EventTypes.OUTSIDE_ZONE -> "Get back in the zone!"
        EventTypes.ZONE_WARNING, EventTypes.ZONE_SHRINKING -> "Zone update"
        EventTypes.GAME_OVER -> "Game over"
        EventTypes.RELEASED -> "Hunters released"
        else -> "Fugitive"
    }
}
