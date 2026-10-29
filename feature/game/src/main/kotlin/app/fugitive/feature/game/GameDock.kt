package app.fugitive.feature.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fugitive.engine.projection.AbilityState
import app.fugitive.engine.projection.GameProjection
import app.fugitive.core.designsystem.component.GlassPanel
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.Grotesk
import app.fugitive.core.ui.Format
import app.fugitive.core.ui.LocalUnitSystem
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.rules.CatchCheck
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.time.GameStage
import app.fugitive.model.PlayerStatus

/** Bottom action area: abilities, the main catch button, and quick access buttons. */
@Composable
internal fun GameDock(
    p: GameProjection,
    unreadEvents: Int,
    unreadChat: Int,
    onCatch: (targetId: String) -> Unit,
    onEnterCode: () -> Unit,
    onReportCaught: () -> Unit,
    onAbility: (String) -> Unit,
    onPlayers: () -> Unit,
    onFeed: () -> Unit,
    onChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = LocalUnitSystem.current
    val s = p.settings
    val method = s[CoreSettings.CATCH_METHOD]
    val me = p.me
    GlassPanel(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        if (p.abilities.isNotEmpty() && !p.isSpectating) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.abilities.forEach { a -> AbilityButton(a, p.now, Modifier.weight(1f)) { onAbility(a.ability.id) } }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Main action
        val canCatchAnyone = p.catchOptions.isNotEmpty()
        when {
            p.isSpectating -> {
                val text = when (me?.status) {
                    PlayerStatus.ESCAPED -> "🚁 You escaped — spectating"
                    PlayerStatus.CAUGHT -> "You were caught — spectating"
                    PlayerStatus.ELIMINATED -> "You're out — spectating"
                    else -> "Spectating"
                }
                Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
            }
            canCatchAnyone -> {
                val pending = p.outgoingCatches.firstOrNull()
                val best = p.catchOptions.filter { it.check is CatchCheck.Allowed }.minByOrNull { it.target.distanceToMe ?: Double.MAX_VALUE }
                val nearest = best ?: p.catchOptions.minByOrNull { it.target.distanceToMe ?: Double.MAX_VALUE }
                val label: String
                val sub: String?
                val enabled: Boolean
                when {
                    pending != null -> {
                        label = "WAITING…"; enabled = false
                        sub = "${p.lobby.members[pending.runnerId]?.name ?: "Runner"} must confirm · ${GameClock.format(pending.expiresAt - p.now)}"
                    }
                    method == "code" -> {
                        label = "ENTER CODE"; enabled = p.stage == GameStage.ACTIVE
                        sub = if (p.stage != GameStage.ACTIVE) "Hunters aren't released yet" else "Tagged someone? Type their code"
                    }
                    method == "auto" -> {
                        label = "AUTO-CATCH ON"; enabled = false
                        sub = nearest?.target?.distanceToMe?.let { "Nearest ${Format.distance(it, units)} · get within ${Format.distance(s[CoreSettings.CATCH_RADIUS_M].toDouble(), units)}" }
                    }
                    best != null -> {
                        label = "CATCH ${best.target.state.name.uppercase()}"; enabled = true
                        sub = best.target.distanceToMe?.let { "${Format.distance(it, units)} away" }
                    }
                    else -> {
                        label = "CATCH"; enabled = false
                        sub = (nearest?.check as? CatchCheck.Denied)?.reason
                    }
                }
                CatchButton(
                    label, sub, enabled, color = Visuals.roleColor(me?.role ?: app.fugitive.model.Role.HUNTER),
                    icon = if (method == "code") Icons.Rounded.Key else Icons.Rounded.FrontHand,
                    onClick = { if (method == "code") onEnterCode() else best?.let { onCatch(it.target.id) } },
                )
            }
            else -> {
                // Prey view: show your code if needed.
                if (method == "code" && me?.catchCode != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Pin, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Your code", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        var show by remember { mutableStateOf(false) }
                        TextButton(onClick = { show = !show }) {
                            Text(if (show) me.catchCode!! else "••••", fontFamily = Grotesk, fontSize = 22.sp, letterSpacing = 4.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onPlayers) { Icon(Icons.AutoMirrored.Rounded.List, "Players") }
            FilledTonalIconButton(onClick = onFeed) {
                BadgedBox(badge = { if (unreadEvents > 0) Badge { Text("$unreadEvents") } }) { Icon(Icons.Rounded.Notifications, "Game feed") }
            }
            if (!p.lobby.info.code.isBlank()) FilledTonalIconButton(onClick = onChat) {
                BadgedBox(badge = { if (unreadChat > 0) Badge { Text("$unreadChat") } }) { Icon(Icons.Rounded.ChatBubbleOutline, "Chat") }
            }
            Spacer(Modifier.weight(1f))
            if (p.isPrey && s[CoreSettings.ALLOW_SELF_REPORT] && p.stage != GameStage.COUNTDOWN) {
                TextButton(onClick = onReportCaught) { Text("I was caught", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun CatchButton(label: String, sub: String?, enabled: Boolean, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(68.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(alpha = 0.25f), contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.75f)),
    ) {
        Icon(icon, null, Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AbilityButton(a: AbilityState, now: Long, modifier: Modifier, onClick: () -> Unit) {
    val active = a.activeUntil != null
    val recharging = !active && now < a.readyAt
    val color = when {
        active -> FugitiveColors.Success
        a.blockedReason == null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick, enabled = a.blockedReason == null, modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = if (a.blockedReason == null || active) 0.16f else 0.06f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                if (recharging) {
                    val total = (a.readyAt - (a.activeUntil ?: now)).coerceAtLeast(1)
                    CircularProgressIndicator(progress = { 1f - ((a.readyAt - now).toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.size(30.dp), strokeWidth = 2.dp, color = color)
                }
                Icon(Visuals.icon(a.ability.icon), null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(a.ability.name, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                Text(
                    when {
                        active -> "Active ${GameClock.format(a.activeUntil!! - now)}"
                        recharging -> GameClock.format(a.readyAt - now)
                        a.usesLeft <= 0 -> "Used"
                        else -> "${a.usesLeft} left"
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
