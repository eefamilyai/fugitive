package app.fugitive.feature.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fugitive.core.data.session.GameProjection
import app.fugitive.core.designsystem.component.EmojiAvatar
import app.fugitive.core.designsystem.component.GlassPanel
import app.fugitive.core.designsystem.component.Pill
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.LocalDarkTheme
import app.fugitive.core.map.CameraMode
import app.fugitive.core.map.GameMap
import app.fugitive.core.map.MapCameraState
import app.fugitive.core.map.MapStyles
import app.fugitive.core.map.MapLayersButton
import app.fugitive.core.map.MapStyleSheet
import app.fugitive.core.map.ScaleBar
import app.fugitive.core.map.ZoomControls
import app.fugitive.core.ui.ChatPanel
import app.fugitive.core.ui.Format
import app.fugitive.core.ui.LocalUnitSystem
import app.fugitive.core.ui.Visuals
import app.fugitive.core.ui.rememberPermissionState
import app.fugitive.engine.geo.Geo
import app.fugitive.engine.rules.CatchCheck
import app.fugitive.engine.time.GameStage
import app.fugitive.model.GameEvent
import app.fugitive.model.GeoPoint
import app.fugitive.model.PlayerStatus
import app.fugitive.model.Role
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(onOpenAppSettings: () -> Unit, vm: GameViewModel = koinViewModel()) {
    val projection by vm.projection.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val markers by vm.markers.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val problem by vm.locationProblem.collectAsStateWithLifecycle()
    val myFix by vm.myFix.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val units = LocalUnitSystem.current
    val view = LocalView.current
    val permissions = rememberPermissionState { vm.retryLocation() }

    val camera = remember { MapCameraState(CameraMode.FOLLOW) }
    val layers = rememberGameLayers()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) } // players | feed | chat
    var dialog by rememberSaveable { mutableStateOf<String?>(null) } // code | report | leave | end | ping
    var pingPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showStyles by rememberSaveable { mutableStateOf(false) }
    var seenEvents by rememberSaveable { mutableIntStateOf(0) }
    var seenChat by rememberSaveable { mutableIntStateOf(0) }
    val banners = remember { mutableStateListOf<Pair<GameEvent, Long>>() }
    var statusOverlay by remember { mutableStateOf<StatusNotice?>(null) }
    var lastStatus by rememberSaveable { mutableStateOf<String?>(null) }

    // Keep the screen awake while playing (configurable).
    DisposableEffect(prefs.keepScreenOn) {
        view.keepScreenOn = prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { dialog = "leave" }

    // Banners for important events.
    LaunchedEffect(Unit) {
        vm.banners.collect { e ->
            banners.add(0, e to System.currentTimeMillis())
            while (banners.size > 3) banners.removeAt(banners.lastIndex)
        }
    }
    LaunchedEffect(banners.size) {
        while (banners.isNotEmpty()) {
            delay(500)
            val now = System.currentTimeMillis()
            banners.removeAll { now - it.second > 4_500 }
        }
    }
    LaunchedEffect(sheet, events.size, chat.size) {
        if (sheet == "feed") seenEvents = events.size
        if (sheet == "chat") seenChat = chat.size
    }

    // Camera follows the local player.
    var framedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(myFix, camera.isReady) {
        val f = myFix ?: return@LaunchedEffect
        if (!camera.isReady) return@LaunchedEffect
        if (!framedOnce) { camera.jumpTo(f.point, 16.5); framedOnce = true }
        else camera.follow(f.point, f.bearing)
    }

    val p = projection
    if (p == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { Text("Loading game…") }
        return
    }

    // Detect my status changes for the one-time overlay.
    LaunchedEffect(p.me?.status, p.me?.role) {
        val me = p.me ?: return@LaunchedEffect
        val key = "${me.status}/${me.role}"
        val prev = lastStatus
        lastStatus = key
        if (prev == null || prev == key) return@LaunchedEffect
        statusOverlay = when {
            me.status == PlayerStatus.CAUGHT -> StatusNotice("Caught!", "${me.caughtBy?.let { p.game.players[it]?.name } ?: "A hunter"} got you. You can keep watching the game.", "🫵", FugitiveColors.Hunter)
            me.status == PlayerStatus.ELIMINATED -> StatusNotice("Eliminated", if (me.outReason == "zone") "You were outside the zone." else "You lost connection for too long.", "☠️", FugitiveColors.Danger)
            me.status == PlayerStatus.ESCAPED -> StatusNotice("You escaped!", "The chopper's got you. Watch the others try.", "🚁", FugitiveColors.Success)
            prev.endsWith("/runner") && me.role == Role.HUNTER -> StatusNotice("Infected!", "You're one of them now. Hunt down the survivors.", "🧟", Color(0xFF84CC16))
            else -> null
        }
    }

    Box(Modifier.fillMaxSize()) {
        BindGameLayers(layers, p, vm.session?.history, markers, selected, units)
        val mapStyle = MapStyles.resolve(prefs.mapStyleId, dark, prefs.customStyleUrl, prefs.mapTilerKey)
        GameMap(
            style = mapStyle,
            layers = layers.all,
            camera = camera,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 150.dp, bottom = 230.dp),
            tilt3d = prefs.map3d,
            terrainShading = prefs.terrainShading,
            onFeatureClick = { _, id -> selected = if (selected == id) null else id; true },
            onMapClick = { selected = null },
            onMapLongClick = { pt -> if (!p.isSpectating) { pingPoint = pt; dialog = "ping" } },
            onUserGesture = { camera.mode = CameraMode.FREE },
        )

        // ── Top HUD ─────────────────────────────────────────────────────
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TopHud(p, connected)
            WarningStrip(
                p, problem, camera.bearing,
                onFixLocation = { if (!permissions.hasLocation) permissions.requestLocation() else permissions.openLocationSettings() },
            )
            if (p.stage == GameStage.HEADSTART) HeadstartBanner(p)
            banners.forEach { (e, _) -> EventBanner(e) }
        }

        // ── Map controls ────────────────────────────────────────────────
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                MapButton(Icons.Rounded.MoreVert, "Menu") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    if (p.lobby.hostId == vm.selfId) DropdownMenuItem(
                        text = { Text("End game for everyone") }, leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                        onClick = { menu = false; dialog = "end" },
                    )
                    DropdownMenuItem(text = { Text("App settings") }, leadingIcon = { Icon(Icons.Rounded.Settings, null) }, onClick = { menu = false; onOpenAppSettings() })
                    DropdownMenuItem(
                        text = { Text(if (p.isSpectating) "Leave game" else "Give up & leave") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null) },
                        onClick = { menu = false; dialog = "leave" },
                    )
                }
            }
            val modeIcon = when (camera.mode) {
                CameraMode.FOLLOW -> Icons.Rounded.MyLocation
                CameraMode.FOLLOW_HEADING -> Icons.Rounded.Navigation
                else -> Icons.Rounded.MyLocation
            }
            MapButton(modeIcon, "Follow me", active = camera.mode == CameraMode.FOLLOW || camera.mode == CameraMode.FOLLOW_HEADING) {
                camera.mode = if (camera.mode == CameraMode.FOLLOW) CameraMode.FOLLOW_HEADING else CameraMode.FOLLOW
                if (camera.mode == CameraMode.FOLLOW) camera.resetNorth()
                myFix?.let { camera.animateTo(it.point, maxOf(camera.zoom, 16.0)) }
            }
            MapButton(Icons.Rounded.CropFree, "Show everything") {
                camera.mode = CameraMode.OVERVIEW
                val pts = buildList {
                    p.zone?.current?.let { z -> listOf(0.0, 90.0, 180.0, 270.0).forEach { add(Geo.destination(z.center, it, z.radius)) } }
                    p.players.mapNotNull { it.fix?.point }.forEach { add(it) }
                    p.objectives.forEach { add(it.point) }
                }
                camera.fit(pts, 140)
            }
            if (camera.bearing != 0.0) {
                MapButton(Icons.Rounded.Explore, "Reset north", rotation = -camera.bearing.toFloat()) { camera.resetNorth() }
            }
            MapLayersButton(mapStyle, onClick = { showStyles = true })
            ZoomControls(camera)
        }

        ScaleBar(
            camera, format = { Format.distance(it, units) },
            imperial = units == app.fugitive.core.ui.UnitSystem.IMPERIAL,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp, top = 120.dp),
        )

        // ── Bottom ──────────────────────────────────────────────────────
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.isSimulated) Joystick(vm::setStick)
            selected?.let { id -> p.players.firstOrNull { it.id == id } }?.let { sp ->
                SelectedPlayerCard(p, sp, onClose = { selected = null }, onCatch = { vm.claimCatch(sp.id) }, onCenter = {
                    sp.fix?.let { camera.mode = CameraMode.FREE; camera.animateTo(it.point) }
                })
            }
            GameDock(
                p,
                unreadEvents = (events.size - seenEvents).coerceAtLeast(0).coerceAtMost(99),
                unreadChat = (chat.size - seenChat).coerceAtLeast(0).coerceAtMost(99),
                onCatch = vm::claimCatch,
                onEnterCode = { dialog = "code" },
                onReportCaught = { dialog = "report" },
                onAbility = vm::useAbility,
                onPlayers = { sheet = "players" },
                onFeed = { sheet = "feed" },
                onChat = { sheet = "chat" },
            )
        }

        toast?.let { t ->
            LaunchedEffect(t) { delay(2500); vm.consumeToast() }
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(bottom = 240.dp, start = 16.dp, end = 16.dp)) { Text(t) }
        }

        if (p.stage == GameStage.COUNTDOWN) CountdownOverlay(p)
        statusOverlay?.let { s -> StatusChangeOverlay(s.title, s.body, s.emoji, s.color) { statusOverlay = null } }
    }

    if (showStyles) {
        MapStyleSheet(
            styles = MapStyles.all(prefs.mapTilerKey),
            selectedId = MapStyles.resolve(prefs.mapStyleId, dark, prefs.customStyleUrl, prefs.mapTilerKey).id,
            terrainShading = prefs.terrainShading,
            tilt3d = prefs.map3d,
            onSelect = { vm.setMapStyle(it.id) },
            onTerrainShading = vm::setTerrainShading,
            onTilt3d = vm::setMap3d,
            onDismiss = { showStyles = false },
        )
    }

    // ── Dialogs ─────────────────────────────────────────────────────────
    p.incomingCatch?.let { pending -> IncomingCatchDialog(p, pending) { vm.respondToCatch(pending.id, it) } }
    when (dialog) {
        "code" -> CodeEntryDialog(onDismiss = { dialog = null }, onSubmit = { vm.submitCode(it); dialog = null })
        "report" -> SelfReportDialog(p, onDismiss = { dialog = null }, onReport = { vm.reportCaught(it); dialog = null })
        "leave" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (p.isSpectating) "Leave the game?" else "Give up?") },
            text = { Text(if (p.isSpectating) "You'll return home. You can rejoin with the code." else "You'll be out of this game and leave the lobby.") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.leaveLobby() }) { Text("Leave", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Keep playing") } },
        )
        "end" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("End the game for everyone?") },
            text = { Text("The game stops now and everyone sees the results.") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.endGame() }) { Text("End game", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "ping" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Ping your team") },
            text = {
                Column {
                    listOf("spotted" to "👀  Spotted someone here", "going" to "🏃  I'm heading here", "danger" to "⚠️  Danger — avoid").forEach { (kind, label) ->
                        TextButton(onClick = { pingPoint?.let { vm.dropMarker(kind, it) }; dialog = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }

    // ── Sheets ──────────────────────────────────────────────────────────
    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            when (sheet) {
                "players" -> {
                    Text("Players", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                    PlayersList(p, onSelect = { pp ->
                        selected = pp.id; sheet = null
                        pp.fix?.let { camera.mode = CameraMode.FREE; camera.animateTo(it.point) }
                    }, modifier = Modifier.fillMaxWidth().height(480.dp))
                }
                "feed" -> {
                    Text("Game feed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                    EventFeed(events, Modifier.fillMaxWidth().height(480.dp))
                }
                "chat" -> {
                    Text("Chat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                    ChatPanel(
                        chat, p.lobby.members, vm.selfId, onSend = vm::sendChat,
                        allowTeamChat = !p.isSpectating, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}

private data class StatusNotice(val title: String, val body: String, val emoji: String, val color: Color)

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, rotation: Float = 0f, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
            contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Icon(icon, label, Modifier.rotate(rotation)) }
}

@Composable
private fun EventBanner(e: GameEvent) {
    val color = Visuals.eventColor(e.type, e.importance)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Visuals.eventIcon(e.type), null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(e.text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (e.importance >= 2) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SelectedPlayerCard(
    p: GameProjection,
    sp: app.fugitive.core.data.session.ProjectedPlayer,
    onClose: () -> Unit,
    onCatch: () -> Unit,
    onCenter: () -> Unit,
) {
    val units = LocalUnitSystem.current
    val option = p.catchOptions.firstOrNull { it.target.id == sp.id }
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(sp.state.avatar, FugitiveColors.player(sp.state.color), ring = Visuals.roleColor(sp.state.role))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sp.state.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Pill(sp.roleLabel, color = Visuals.roleColor(sp.state.role))
                }
                val parts = buildList {
                    sp.distanceToMe?.let { add("${Format.distance(it, units)} ${sp.bearingFromMe?.let { b -> Geo.compass(b) } ?: ""}") }
                    sp.ageMs?.let { add(if (it < 5_000) "live" else Format.ago(it)) }
                    sp.visibility.reason?.takeIf { it != "Teammate" }?.let { add(it) }
                    sp.fix?.speed?.takeIf { it > 0.8f }?.let { add(Format.speed(it.toDouble(), units)) }
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onCenter) { Text("Find") }
            FilledTonalIconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
        }
        if (option != null && option.check is CatchCheck.Allowed && p.settings[app.fugitive.engine.settings.CoreSettings.CATCH_METHOD] in setOf("confirm", "proximity")) {
            TextButton(onClick = onCatch, modifier = Modifier.fillMaxWidth()) { Text("Catch ${sp.state.name}", color = FugitiveColors.Hunter, fontWeight = FontWeight.Bold) }
        }
    }
}
