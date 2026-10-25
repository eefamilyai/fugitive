package app.fugitive.feature.lobby

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fugitive.core.data.prefs.AppPreferences
import app.fugitive.core.designsystem.component.GlassPanel
import app.fugitive.core.designsystem.component.PrimaryButton
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.LocalDarkTheme
import app.fugitive.core.map.CameraMode
import app.fugitive.core.map.FitWhenReady
import app.fugitive.core.map.GameMap
import app.fugitive.core.map.MapCameraState
import app.fugitive.core.map.MapStyles
import app.fugitive.core.map.layers.MapPin
import app.fugitive.core.map.layers.PinsLayer
import app.fugitive.core.map.layers.PlayerMarker
import app.fugitive.core.map.layers.PlayersLayer
import app.fugitive.core.map.layers.ZoneLayer
import app.fugitive.core.ui.Format
import app.fugitive.core.ui.LocalUnitSystem
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.geo.Geo
import app.fugitive.model.Circle
import app.fugitive.model.GeoPoint
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.PlayerId
import app.fugitive.model.Role

internal fun lobbyMarkers(lobby: LobbySnapshot, locations: Map<PlayerId, LocationFix>, selfId: PlayerId): List<PlayerMarker> =
    lobby.members.values.mapNotNull { m ->
        val fix = locations[m.id] ?: return@mapNotNull null
        val role = lobby.roster[m.id]
        PlayerMarker(
            id = m.id, point = fix.point, avatar = m.avatar,
            ringColor = (role?.let { Visuals.roleColor(it) } ?: FugitiveColors.Mist).toArgb(),
            fillColor = FugitiveColors.player(m.color).toArgb(),
            label = if (m.id == selfId) "You" else m.name, bearing = fix.bearing, accuracy = fix.accuracy,
            isMe = m.id == selfId,
        )
    }

/** Small live map of everyone in the lobby with the play-area preview. */
@Composable
internal fun LobbyMapPreview(
    lobby: LobbySnapshot,
    locations: Map<PlayerId, LocationFix>,
    selfId: PlayerId,
    playArea: Circle?,
    prefs: AppPreferences,
    modifier: Modifier = Modifier,
) {
    val camera = remember { MapCameraState(CameraMode.FREE) }
    val zone = remember { ZoneLayer() }
    val players = remember { PlayersLayer() }
    val dark = LocalDarkTheme.current
    SideEffect {
        zone.submit(playArea, null, FugitiveColors.Zone.toArgb())
        players.submit(lobbyMarkers(lobby, locations, selfId), showAccuracy = false)
    }
    // Re-frame when the play area changes (e.g. the host edits the radius) or the first GPS fix arrives.
    FitWhenReady(camera, key = playArea to locations.isNotEmpty(), paddingPx = 60) {
        buildList {
            playArea?.let { a -> listOf(0.0, 90.0, 180.0, 270.0).forEach { add(Geo.destination(a.center, it, a.radius)) } }
            if (playArea == null) addAll(locations.values.map { it.point })
        }
    }
    GameMap(
        style = MapStyles.resolve(prefs.mapStyleId, dark, prefs.customStyleUrl),
        layers = listOf(zone, players),
        camera = camera,
        modifier = modifier,
        rotateGestures = false,
    )
}

/** Full-screen map to choose the zone centre. Tap or long-press to place it. */
@Composable
internal fun ZonePickerDialog(
    initial: GeoPoint?,
    radius: Double,
    lobby: LobbySnapshot,
    locations: Map<PlayerId, LocationFix>,
    selfId: PlayerId,
    prefs: AppPreferences,
    onDismiss: () -> Unit,
    onPick: (GeoPoint) -> Unit,
) {
    var center by remember { mutableStateOf(initial ?: locations[selfId]?.point ?: locations.values.firstOrNull()?.point) }
    val camera = remember { MapCameraState(CameraMode.FREE) }
    val zone = remember { ZoneLayer("pick-zone") }
    val pin = remember { PinsLayer("pick-pin") }
    val players = remember { PlayersLayer("pick-players") }
    val dark = LocalDarkTheme.current
    val units = LocalUnitSystem.current
    SideEffect {
        val c = center
        zone.submit(c?.let { Circle(it.lat, it.lng, radius) }, null, FugitiveColors.Zone.toArgb())
        pin.submit(listOfNotNull(c?.let { MapPin("center", it, "🎯", FugitiveColors.Zone.toArgb(), "Centre") }))
        players.submit(lobbyMarkers(lobby, locations, selfId), showAccuracy = false)
    }
    val initialCenter = remember { center }
    FitWhenReady(camera, key = Unit) {
        initialCenter?.let { c -> listOf(0.0, 90.0, 180.0, 270.0).map { Geo.destination(c, it, radius * 1.15) } }.orEmpty()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            GameMap(
                style = MapStyles.resolve(prefs.mapStyleId, dark, prefs.customStyleUrl),
                layers = listOf(zone, players, pin),
                camera = camera,
                modifier = Modifier.fillMaxSize(),
                onMapClick = { center = it },
                onMapLongClick = { center = it },
            )
            Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassPanel(Modifier.weight(1f)) {
                    Text("Zone centre", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap the map to move it. The circle shows the starting zone (${Format.distance(radius, units)} radius).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cancel") }
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()) {
                Row {
                    FilledTonalIconButton(onClick = { locations[selfId]?.point?.let { center = it; camera.animateTo(it) } }) {
                        Icon(Icons.Rounded.MyLocation, "Use my location")
                    }
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.padding(4.dp))
                PrimaryButton("Set centre here", onClick = { center?.let(onPick) }, enabled = center != null, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
