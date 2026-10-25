package app.fugitive.core.data.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.data.session.SessionState
import app.fugitive.core.location.LocationPermissions
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.engine.zone.ZonePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service of type "location". While a game runs it keeps the
 * process alive and allowed to receive location updates with the screen off
 * or the app in the background. Started while the app is visible, so no
 * background-location permission is needed.
 */
class TrackingService : LifecycleService() {
    private val sessions: GameSessionManager by inject()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopSelfCleanly(); return START_NOT_STICKY }
        if (!LocationPermissions.hasLocation(this)) { stopSelf(); return START_NOT_STICKY }
        val notification = Notifications.tracking(this, "Game in progress", "Sharing your location")
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_TRACKING, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) { started = true; observe() }
        return START_STICKY
    }

    private var started = false

    private fun observe() = lifecycleScope.launch {
        sessions.state.collectLatest { state ->
            val session = (state as? SessionState.Active)?.session
            if (session == null) { stopSelfCleanly(); return@collectLatest }
            while (isActive) {
                val p = session.projection.value
                val title: String
                val text: String
                if (p == null || p.isOver) {
                    title = "Fugitive"; text = if (p?.isOver == true) "Game over — tap to see results" else "In lobby ${session.code}"
                } else {
                    title = "${p.myRoleLabel} · ${p.modeName}"
                    val parts = mutableListOf<String>()
                    when (p.stage) {
                        GameStage.COUNTDOWN -> parts += "Starting in ${GameClock.format(p.stageEndsInMs ?: 0)}"
                        GameStage.HEADSTART -> parts += "Head start ${GameClock.format(p.stageEndsInMs ?: 0)}"
                        else -> p.timeLeftMs?.let { parts += "${GameClock.format(it)} left" }
                    }
                    p.zone?.let { z ->
                        when (z.phase) {
                            ZonePhase.SHRINKING -> parts += "Zone closing"
                            ZonePhase.ANNOUNCED -> parts += "Zone moves in ${GameClock.format((z.phaseEndsAt ?: p.now) - p.now)}"
                            else -> {}
                        }
                    }
                    p.zoneStatus?.takeIf { it.applies && !it.inside }?.let { parts += "⚠ OUTSIDE ZONE" }
                    if (p.threats.isNotEmpty()) parts += "⚠ Hunter ${p.threats.first().distance.toInt()} m"
                    if (p.isSpectating) parts += "Spectating"
                    text = parts.joinToString(" · ").ifEmpty { "Sharing your location" }
                }
                runCatching {
                    NotificationManagerCompat.from(this@TrackingService).notify(Notifications.ID_TRACKING, Notifications.tracking(this@TrackingService, title, text))
                }
                delay(5_000)
            }
        }
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_STOP = "app.fugitive.tracking.STOP"

        /** Must be called while the app is in the foreground. Returns false if Android refused. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
