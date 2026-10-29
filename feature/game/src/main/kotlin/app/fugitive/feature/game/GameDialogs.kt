package app.fugitive.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fugitive.engine.projection.GameProjection
import app.fugitive.engine.projection.ProjectedPlayer
import app.fugitive.core.designsystem.component.EmojiAvatar
import app.fugitive.core.designsystem.component.Pill
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.Grotesk
import app.fugitive.core.ui.Format
import app.fugitive.core.ui.LocalUnitSystem
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.time.GameClock
import app.fugitive.engine.visibility.VisibilityKind
import app.fugitive.model.GameEvent
import app.fugitive.model.PendingCatch
import app.fugitive.model.PlayerStatus
import java.text.DateFormat
import java.util.Date

@Composable
internal fun IncomingCatchDialog(p: GameProjection, pending: PendingCatch, onRespond: (Boolean) -> Unit) {
    val hunter = p.game.players[pending.hunterId]
    val units = LocalUnitSystem.current
    val total = (pending.expiresAt - pending.createdAt).coerceAtLeast(1)
    val left = (pending.expiresAt - p.now).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = {},
        icon = { Text("🚨", fontSize = 40.sp) },
        title = { Text("${hunter?.name ?: "A hunter"} says they caught you!", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Did they tag you? Be honest — it's more fun that way.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                pending.distanceMeters?.let { Text("GPS says you were ${Format.distance(it, units)} apart.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                LinearProgressIndicator(progress = { left.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Text("Auto-answer in ${GameClock.format(left)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = { onRespond(true) }) { Text("Yes, I'm caught", color = FugitiveColors.Hunter, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { onRespond(false) }) { Text("No, they missed") } },
    )
}

@Composable
internal fun CodeEntryDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter the runner's code") },
        text = {
            Column {
                Text("Ask the runner you tagged to show you the 4-digit code on their phone.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(4) },
                    singleLine = true, textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = Grotesk, letterSpacing = 8.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(code) }, enabled = code.length == 4) { Text("Catch") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SelfReportDialog(p: GameProjection, onDismiss: () -> Unit, onReport: (String?) -> Unit) {
    val hunters = p.players.filter { !it.isMe && it.state.status == PlayerStatus.ACTIVE && it.state.role != p.me?.role }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who caught you?") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(hunters) { h ->
                    Surface(onClick = { onReport(h.id) }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            EmojiAvatar(h.state.avatar, FugitiveColors.player(h.state.color), size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(h.state.name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                item { TextButton(onClick = { onReport(null) }, modifier = Modifier.fillMaxWidth()) { Text("Not sure / someone else") } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel — I'm still free") } },
    )
}

@Composable
internal fun PlayersList(p: GameProjection, onSelect: (ProjectedPlayer) -> Unit, modifier: Modifier = Modifier) {
    val units = LocalUnitSystem.current
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(p.players, key = { it.id }) { pp ->
            Surface(onClick = { onSelect(pp) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    EmojiAvatar(pp.state.avatar, FugitiveColors.player(pp.state.color), ring = Visuals.roleColor(pp.state.role), dimmed = pp.state.status != PlayerStatus.ACTIVE)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (pp.isMe) "${pp.state.name} (you)" else pp.state.name, style = MaterialTheme.typography.titleSmall)
                        val status = when (pp.state.status) {
                            PlayerStatus.ACTIVE -> when (pp.visibility.kind) {
                                VisibilityKind.LIVE -> if (pp.isStale) "Signal lost · ${Format.ago(pp.ageMs ?: 0)}" else (pp.visibility.reason ?: "Live")
                                VisibilityKind.SNAPSHOT -> "Pinged ${Format.ago(pp.ageMs ?: 0)}"
                                VisibilityKind.HIDDEN -> pp.visibility.reason?.let { "Hidden · $it" } ?: "Hidden"
                            }
                            PlayerStatus.CAUGHT -> "Caught" + (pp.state.caughtBy?.let { " by ${p.game.players[it]?.name ?: "?"}" } ?: "")
                            PlayerStatus.ELIMINATED -> if (pp.state.outReason == "zone") "Eliminated by the zone" else "Eliminated"
                            PlayerStatus.ESCAPED -> "Escaped"
                            PlayerStatus.LEFT -> "Left the game"
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Pill(pp.roleLabel, color = Visuals.roleColor(pp.state.role))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            pp.distanceToMe?.let { d ->
                                Text("${Format.distance(d, units)} ${pp.bearingFromMe?.let { Geo.compass(it) } ?: ""}", style = MaterialTheme.typography.labelMedium)
                            }
                            pp.fix?.battery?.takeIf { it <= 20 }?.let {
                                Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Battery2Bar, "Low battery", tint = FugitiveColors.Warning, modifier = Modifier.size(14.dp))
                            }
                        }
                        if (pp.state.catches > 0) Text("✋ ${pp.state.catches}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EventFeed(events: List<GameEvent>, modifier: Modifier = Modifier) {
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), reverseLayout = false) {
        if (events.isEmpty()) item { Text("Nothing has happened yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(events.asReversed(), key = { it.id }) { e ->
            val color = Visuals.eventColor(e.type, e.importance)
            Row(verticalAlignment = Alignment.Top) {
                Icon(Visuals.eventIcon(e.type), null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (e.importance >= 2) FontWeight.SemiBold else FontWeight.Normal)
                    Text(fmt.format(Date(e.at)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
