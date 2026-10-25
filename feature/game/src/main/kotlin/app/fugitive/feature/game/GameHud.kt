package app.fugitive.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fugitive.core.data.session.GameProjection
import app.fugitive.core.data.session.LocationProblem
import app.fugitive.core.designsystem.component.GlassPanel
import app.fugitive.core.designsystem.component.Pill
import app.fugitive.core.designsystem.component.PulseDot
import app.fugitive.core.designsystem.theme.ClockStyle
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.ui.Format
import app.fugitive.core.ui.LocalUnitSystem
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.engine.zone.ZoneMath
import app.fugitive.engine.zone.ZonePhase
import app.fugitive.model.Role

/** Role, objective, clock and status chips. */
@Composable
internal fun TopHud(p: GameProjection, connected: Boolean, modifier: Modifier = Modifier) {
    val role = p.me?.role ?: Role.SPECTATOR
    val roleColor = if (p.isSpectating) FugitiveColors.Spectator else Visuals.roleColor(role)
    GlassPanel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(roleColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(if (p.isSpectating) Visuals.roleIcon(Role.SPECTATOR) else Visuals.roleIcon(role), null, tint = roleColor)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (p.isSpectating && p.me?.status != null && p.me?.status != app.fugitive.model.PlayerStatus.ACTIVE) "${p.myRoleLabel} · out" else p.myRoleLabel.uppercase(),
                    color = roleColor, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp,
                )
                Text(p.objective, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val (label, millis) = when (p.stage) {
                    GameStage.COUNTDOWN -> "STARTS IN" to (p.stageEndsInMs ?: 0)
                    GameStage.HEADSTART -> "HEAD START" to (p.stageEndsInMs ?: 0)
                    else -> (if (p.timeLeftMs != null) "TIME LEFT" else "ELAPSED") to (p.timeLeftMs ?: (p.now - p.game.headstartEndsAt))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                val urgent = p.stage == GameStage.ACTIVE && p.timeLeftMs != null && p.timeLeftMs!! < 60_000
                Text(GameClock.format(millis), style = ClockStyle, fontSize = 26.sp, color = if (urgent) FugitiveColors.Danger else MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val mode = p.game.modeId
            p.activeCounts.filterKeys { it != Role.SPECTATOR }.toSortedMap(compareBy { it.id }).forEach { (r, n) ->
                Pill("$n", color = Visuals.roleColor(r), icon = Visuals.roleIcon(r))
            }
            p.zone?.let { z ->
                val text = when (z.phase) {
                    ZonePhase.WAITING -> z.phaseEndsAt?.let { "Zone ${GameClock.format(it - p.now)}" } ?: "Zone"
                    ZonePhase.ANNOUNCED -> "Moves in ${GameClock.format((z.phaseEndsAt ?: p.now) - p.now)}"
                    ZonePhase.SHRINKING -> "Shrinking"
                    ZonePhase.FINAL -> "Final zone"
                }
                Pill(text, color = FugitiveColors.Zone, icon = Icons.Rounded.Radar, filled = z.phase == ZonePhase.SHRINKING)
            }
            p.nextPingAt?.takeIf { p.stage == GameStage.ACTIVE }?.let { Pill("Ping ${GameClock.format(it - p.now)}", color = FugitiveColors.Runner) }
            Spacer(Modifier.weight(1f))
            if (!connected) Pill("Offline", color = FugitiveColors.Danger, icon = Icons.Rounded.WifiOff, filled = true)
            else Row(verticalAlignment = Alignment.CenterVertically) { PulseDot(FugitiveColors.Success, size = 8.dp); Spacer(Modifier.width(4.dp)); Text("LIVE", style = MaterialTheme.typography.labelSmall, color = FugitiveColors.Success) }
        }
    }
}

/** The single most important warning right now. */
@Composable
internal fun WarningStrip(
    p: GameProjection,
    problem: LocationProblem?,
    mapBearing: Double,
    onFixLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = LocalUnitSystem.current
    val zs = p.zoneStatus
    val me = p.me
    val warning: WarningSpec? = when {
        problem != null -> WarningSpec(
            Icons.Rounded.LocationOff, FugitiveColors.Danger,
            if (problem == LocationProblem.PERMISSION) "Location permission needed" else "Can't get your location",
            "Others can't see you and the zone can't check you.", action = "Fix",
        )
        zs != null && zs.applies && !zs.inside -> WarningSpec(
            Icons.Rounded.Warning, FugitiveColors.Danger, "OUTSIDE THE ZONE",
            buildString {
                append("${Format.distance(-zs.marginMeters, units)} to safety")
                zs.graceLeftMs?.takeIf { p.settings[CoreSettings.ZONE_PENALTY] == "eliminate" && it > 0 }?.let { append(" · out in ${GameClock.format(it)}") }
            },
            pulse = true,
        )
        p.threats.isNotEmpty() -> {
            val t = p.threats.first()
            val showDir = p.settings[CoreSettings.PROXIMITY_DIRECTION]
            WarningSpec(
                Icons.Rounded.Warning, FugitiveColors.Hunter, "${p.lobby.members[t.playerId]?.let { "Hunter" } ?: "Hunter"} nearby",
                if (showDir) "${Format.distance(t.distance, units)} · ${Geo.compass(t.bearing)}" else "Within ${Format.distance(p.settings[CoreSettings.PROXIMITY_ALERT_M].toDouble(), units)}",
                pulse = true, arrowBearing = if (showDir) (t.bearing - mapBearing).toFloat() else null,
            )
        }
        me?.frozenUntil?.let { it > p.now } == true -> WarningSpec(
            Icons.Rounded.AcUnit, FugitiveColors.Runner, "Frozen", "You can catch again in ${GameClock.format(me.frozenUntil!! - p.now)}",
        )
        zs != null && zs.applies && p.zone?.next != null && p.myFix != null &&
            ZoneMath.marginToEdge(p.myFix!!.point, p.zone!!.next!!) < 0 -> WarningSpec(
            Icons.Rounded.Radar, FugitiveColors.Zone, "Move to the next circle",
            "${Format.distance(-ZoneMath.marginToEdge(p.myFix!!.point, p.zone!!.next!!), units)} to go · " +
                (p.zone!!.phaseEndsAt?.let { if (p.zone!!.phase == ZonePhase.ANNOUNCED) "closing in ${GameClock.format(it - p.now)}" else "closing now" } ?: ""),
        )
        else -> null
    }
    AnimatedVisibility(warning != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(), modifier = modifier) {
        val w = warning ?: return@AnimatedVisibility
        val pulse = rememberInfiniteTransition(label = "warn")
        val a by pulse.animateFloat(0.75f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(w.color.copy(alpha = 0.92f))
                .alpha(if (w.pulse) a else 1f).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (w.arrowBearing != null) {
                Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(26.dp).rotate(w.arrowBearing))
            } else Icon(w.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(w.title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(w.body, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }
            if (w.action != null) TextButton(onClick = onFixLocation) { Text(w.action, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

private data class WarningSpec(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val body: String,
    val pulse: Boolean = false,
    val action: String? = null,
    val arrowBearing: Float? = null,
)
