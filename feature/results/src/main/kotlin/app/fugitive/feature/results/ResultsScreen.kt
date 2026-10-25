package app.fugitive.feature.results

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fugitive.core.designsystem.component.EmojiAvatar
import app.fugitive.core.designsystem.component.FCard
import app.fugitive.core.designsystem.component.Pill
import app.fugitive.core.designsystem.component.PrimaryButton
import app.fugitive.core.designsystem.component.SectionHeader
import app.fugitive.core.designsystem.component.SecondaryButton
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.Grotesk
import app.fugitive.core.designsystem.theme.LocalDarkTheme
import app.fugitive.core.map.CameraMode
import app.fugitive.core.map.FitWhenReady
import app.fugitive.core.map.GameMap
import app.fugitive.core.map.MapCameraState
import app.fugitive.core.map.MapStyles
import app.fugitive.core.map.layers.MarkerLook
import app.fugitive.core.map.layers.PlayerMarker
import app.fugitive.core.map.layers.PlayersLayer
import app.fugitive.core.map.layers.Trail
import app.fugitive.core.map.layers.TrailsLayer
import app.fugitive.core.map.layers.ZoneLayer
import app.fugitive.core.ui.Format
import app.fugitive.core.ui.LocalUnitSystem
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.stats.PlayerStats
import app.fugitive.engine.time.GameClock
import app.fugitive.model.GameState
import app.fugitive.model.PlayerStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ResultsScreen(vm: ResultsViewModel = koinViewModel()) {
    val lobby by vm.lobby.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val trails by vm.trails.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val units = LocalUnitSystem.current
    val game = lobby?.game
    val result = game?.result
    if (game == null || result == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Waiting for results…") }
        return
    }
    val mode = vm.engine.mode(game.modeId)
    val won = vm.selfId in result.winnerIds
    val decided = result.winnerIds.isNotEmpty()
    val accent = result.winningRole?.let { Visuals.roleColor(it) } ?: FugitiveColors.Signal
    val isHost = lobby?.hostId == vm.selfId

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Hero(result.headline, result.detail, won, decided, game, accent, vm.selfId) }
        item {
            ReplayMap(game, trails, prefs, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(280.dp).clip(RoundedCornerShape(24.dp)))
        }
        item { Awards(game, stats, Modifier.padding(horizontal = 16.dp)) }
        item { SectionHeader("Leaderboard", Modifier.padding(horizontal = 16.dp)) }
        itemsIndexed(stats, key = { _, s -> s.playerId }) { i, s ->
            val p = game.players[s.playerId] ?: return@itemsIndexed
            Surface(shape = MaterialTheme.shapes.large, color = if (s.playerId == vm.selfId) accent.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(28.dp))
                    EmojiAvatar(p.avatar, FugitiveColors.player(p.color), ring = Visuals.roleColor(p.originalRole), dimmed = !s.won)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            if (s.won) { Spacer(Modifier.width(6.dp)); Text("🏆") }
                        }
                        val outcome = when (p.status) {
                            PlayerStatus.ACTIVE -> if (p.originalRole == app.fugitive.model.Role.HUNTER && p.role == app.fugitive.model.Role.HUNTER) "${p.catches} ${if (p.catches == 1) "catch" else "catches"}" else "Survived"
                            PlayerStatus.CAUGHT -> "Caught" + (p.caughtBy?.let { " by ${game.players[it]?.name ?: "?"}" } ?: "")
                            PlayerStatus.ELIMINATED -> if (p.outReason == "zone") "Zone" else "Eliminated"
                            PlayerStatus.ESCAPED -> "Escaped"
                            PlayerStatus.LEFT -> "Left"
                        }
                        Text(
                            "${mode.roleLabel(p.originalRole)}${if (p.originalRole != p.role) " → ${mode.roleLabel(p.role)}" else ""} · $outcome",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("✋ ${s.catches}  ⏱ ${GameClock.format(s.survivedMs)}", style = MaterialTheme.typography.labelMedium)
                        Text("${Format.distance(s.distanceMeters, units)} · ${Format.speed(s.topSpeedMps, units)} top", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SectionHeader("Timeline", Modifier.padding(horizontal = 16.dp)) }
        items(events.filter { it.importance >= 1 }, key = { it.id }) { e ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(GameClock.format(e.at - game.startAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                androidx.compose.material3.Icon(Visuals.eventIcon(e.type), null, tint = Visuals.eventColor(e.type, e.importance), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(e.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isHost) PrimaryButton("Play again", onClick = vm::playAgain, icon = Icons.Rounded.Replay, modifier = Modifier.fillMaxWidth())
                else Text("Waiting for the host to start another round…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                SecondaryButton("Leave", onClick = vm::leave, icon = Icons.AutoMirrored.Rounded.ExitToApp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Hero(headline: String, detail: String, won: Boolean, decided: Boolean, game: GameState, accent: Color, selfId: String) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    val inGame = selfId in game.players && decided
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))).statusBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (!inGame) "🏁" else if (won) "🏆" else "💀", fontSize = 64.sp, modifier = Modifier.scale(scale.value))
            if (inGame) Text(if (won) "VICTORY" else "DEFEAT", color = if (won) FugitiveColors.Success else FugitiveColors.Danger, style = MaterialTheme.typography.labelLarge, letterSpacing = 4.sp)
            Text(headline, fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 34.sp, textAlign = TextAlign.Center)
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            val duration = (game.result?.endedAt ?: game.startAt) - game.startAt
            Pill("Game lasted ${GameClock.format(duration)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReplayMap(game: GameState, trails: Map<String, List<app.fugitive.model.LocationFix>>, prefs: app.fugitive.core.data.prefs.AppPreferences, modifier: Modifier) {
    val camera = remember { MapCameraState(CameraMode.FREE) }
    val zone = remember { ZoneLayer("replay-zone") }
    val lines = remember { TrailsLayer("replay-trails", width = 4f, opacity = 0.9f) }
    val ends = remember { PlayersLayer("replay-ends") }
    val dark = LocalDarkTheme.current
    SideEffect {
        zone.submit(game.zone?.final ?: game.zone?.initial, null, FugitiveColors.Zone.toArgb())
        lines.submit(trails.map { (id, fixes) -> Trail(id, fixes.map { it.point }, FugitiveColors.player(game.players[id]?.color ?: 11).toArgb()) })
        ends.submit(trails.mapNotNull { (id, fixes) ->
            val p = game.players[id] ?: return@mapNotNull null
            val last = fixes.lastOrNull() ?: return@mapNotNull null
            PlayerMarker(
                id, last.point, p.avatar, Visuals.roleColor(p.role).toArgb(), FugitiveColors.player(p.color).toArgb(), p.name,
                look = if (p.status == PlayerStatus.ACTIVE || p.status == PlayerStatus.ESCAPED) MarkerLook.LIVE else MarkerLook.OUT,
            )
        }, showAccuracy = false)
    }
    FitWhenReady(camera, key = trails.size, paddingPx = 60) {
        trails.values.flatten().map { it.point } +
            (game.zone?.initial?.let { z -> listOf(0.0, 90.0, 180.0, 270.0).map { Geo.destination(z.center, it, z.radius) } } ?: emptyList())
    }
    Box(modifier) {
        GameMap(
            MapStyles.resolve(prefs.mapStyleId, dark, prefs.customStyleUrl, prefs.mapTilerKey), listOf(zone, lines, ends), camera,
            Modifier.fillMaxSize(), terrainShading = prefs.terrainShading,
        )
        if (trails.isEmpty()) {
            Text(
                "Loading routes…", modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f)).padding(10.dp),
            )
        }
    }
}

@Composable
private fun Awards(game: GameState, stats: List<PlayerStats>, modifier: Modifier) {
    val units = LocalUnitSystem.current
    if (stats.isEmpty()) return
    data class Award(val emoji: String, val title: String, val who: String?, val value: String)
    val name = { id: String -> game.players[id]?.name }
    val awards = buildList {
        stats.maxByOrNull { it.catches }?.takeIf { it.catches > 0 }?.let { add(Award("🎯", "Top hunter", name(it.playerId), "${it.catches} ${if (it.catches == 1) "catch" else "catches"}")) }
        stats.filter { game.players[it.playerId]?.originalRole?.id == "runner" }.maxByOrNull { it.survivedMs }?.let { add(Award("🛡️", "Survivor", name(it.playerId), GameClock.format(it.survivedMs))) }
        stats.maxByOrNull { it.distanceMeters }?.takeIf { it.distanceMeters > 10 }?.let { add(Award("👟", "Marathon", name(it.playerId), Format.distance(it.distanceMeters, units))) }
        stats.maxByOrNull { it.topSpeedMps }?.takeIf { it.topSpeedMps > 1 }?.let { add(Award("⚡", "Speed demon", name(it.playerId), Format.speed(it.topSpeedMps, units))) }
    }
    if (awards.isEmpty()) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(awards) { a ->
            FCard(Modifier.width(150.dp)) {
                Text(a.emoji, fontSize = 28.sp)
                Text(a.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(a.who ?: "—", style = MaterialTheme.typography.titleMedium)
                Text(a.value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
