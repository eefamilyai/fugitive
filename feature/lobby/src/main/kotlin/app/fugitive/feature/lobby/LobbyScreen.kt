package app.fugitive.feature.lobby

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fugitive.core.designsystem.component.EmojiAvatar
import app.fugitive.core.designsystem.component.FCard
import app.fugitive.core.designsystem.component.Notice
import app.fugitive.core.designsystem.component.Pill
import app.fugitive.core.designsystem.component.PrimaryButton
import app.fugitive.core.designsystem.component.SectionHeader
import app.fugitive.core.designsystem.component.SecondaryButton
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.Grotesk
import app.fugitive.core.ui.ChatPanel
import app.fugitive.core.ui.Format
import app.fugitive.core.ui.LocalUnitSystem
import app.fugitive.core.ui.ModeIcon
import app.fugitive.core.ui.QrCode
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.settings.SettingFormat
import app.fugitive.model.LobbyCodes
import app.fugitive.model.Member
import app.fugitive.model.Role
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(onOpenGameSettings: () -> Unit, vm: LobbyViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val lobby = state.lobby
    val snackbar = remember { SnackbarHostState() }
    var showChat by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmStart by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var seenChat by rememberSaveable { mutableIntStateOf(0) }
    val unread = (state.chat.size - seenChat).coerceAtLeast(0)

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(showChat, state.chat.size) { if (showChat) seenChat = state.chat.size }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.isPractice) "Practice lobby" else "Lobby", style = MaterialTheme.typography.titleLarge)
                        lobby?.let { Text(vm.engine.mode(it.config.modeId).name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                actions = {
                    if (!state.connected) Icon(Icons.Rounded.WifiOff, "Offline", tint = FugitiveColors.Warning, modifier = Modifier.padding(end = 8.dp))
                    if (!state.isPractice) {
                        IconButton(onClick = { showChat = true }) {
                            BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) { Icon(Icons.Rounded.ChatBubbleOutline, "Chat") }
                        }
                    }
                    IconButton(onClick = onOpenGameSettings) { Icon(Icons.Rounded.Tune, "Game settings") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            if (state.isHost && !state.isPractice) {
                                val locked = lobby?.info?.locked == true
                                DropdownMenuItem(
                                    text = { Text(if (locked) "Unlock lobby" else "Lock lobby") },
                                    leadingIcon = { Icon(if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null) },
                                    onClick = { vm.setLocked(!locked); menu = false },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Leave lobby") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null) },
                                onClick = { menu = false; confirmLeave = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { if (lobby != null) LobbyBottomBar(state, vm, onStart = { allReady -> if (allReady) vm.start() else confirmStart = true }) },
    ) { padding ->
        if (lobby == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Connecting…") }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { if (state.isPractice) PracticeCard() else CodeCard(lobby.code, lobby.info.locked) }
            item {
                FCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    LobbyMapPreview(lobby, state.locations, state.selfId, state.playArea, state.prefs, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(24.dp)))
                }
            }
            item { ModeSummaryCard(state, vm, onOpenGameSettings) }
            items(state.issues) { issue ->
                Notice(
                    title = issue.message, body = null,
                    icon = if (issue.blocking) Icons.Rounded.Error else Icons.Rounded.Warning,
                    color = if (issue.blocking) MaterialTheme.colorScheme.error else FugitiveColors.Warning,
                )
            }
            item {
                SectionHeader("Players · ${lobby.members.size}") {
                    if (state.isPractice && state.isHost) TextButton(onClick = vm::addBot) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("Add bot") }
                }
                if (state.isHost && vm.engine.mode(lobby.config.modeId).usesHunterSelection &&
                    vm.engine.settings(lobby.config)[CoreSettings.HUNTER_SELECTION] == "manual"
                ) {
                    Text("Tap a player to make them a hunter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(lobby.membersByJoin, key = { it.id }) { m -> PlayerRow(m, state, vm) }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showChat && lobby != null) {
        ModalBottomSheet(onDismissRequest = { showChat = false }) {
            Text("Lobby chat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            ChatPanel(state.chat, lobby.members, state.selfId, onSend = { t, _ -> vm.sendChat(t) }, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp))
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the lobby?") },
            text = { Text(if (state.isHost && !state.isPractice) "Another player will become the host." else "You can join again with the code.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Not everyone is ready") },
            text = { Text("Start anyway? Players who aren't ready will still take part.") },
            confirmButton = { TextButton(onClick = { confirmStart = false; vm.start() }) { Text("Start") } },
            dismissButton = { TextButton(onClick = { confirmStart = false }) { Text("Wait") } },
        )
    }
}

@Composable
private fun CodeCard(code: String, locked: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showQr by rememberSaveable { mutableStateOf(false) }
    FCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("JOIN CODE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            if (locked) Pill("Locked", color = FugitiveColors.Warning, icon = Icons.Rounded.Lock)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                code.chunked(3).joinToString(" "), fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 40.sp,
                letterSpacing = 6.sp, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Lobby code", code))) } }) {
                Icon(Icons.Rounded.ContentCopy, "Copy code")
            }
            IconButton(onClick = { showQr = !showQr }) { Icon(Icons.Rounded.QrCode2, "Show QR code") }
            IconButton(onClick = {
                val text = "Join my Fugitive manhunt! Code: $code\n${LobbyCodes.link(code)}"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Invite players"))
            }) { Icon(Icons.Rounded.Share, "Share invite") }
        }
        AnimatedVisibility(showQr) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                    QrCode(LobbyCodes.link(code), Modifier.size(200.dp))
                }
            }
        }
    }
}

@Composable
private fun PracticeCard() {
    FCard(Modifier.fillMaxWidth(), color = FugitiveColors.Zone.copy(alpha = 0.12f), border = FugitiveColors.Zone.copy(alpha = 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SmartToy, null, tint = FugitiveColors.Zone)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Practice vs bots", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bots move around your real location. No network needed. Tip: switch to the virtual joystick in Settings to test without walking.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeSummaryCard(state: LobbyUiState, vm: LobbyViewModel, onOpen: () -> Unit) {
    val lobby = state.lobby ?: return
    val mode = vm.engine.mode(lobby.config.modeId)
    val s = vm.engine.settings(lobby.config)
    val preset = vm.engine.preset(lobby.config.presetId)
    val units = LocalUnitSystem.current
    FCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeIcon(mode)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(preset?.let { "${it.emoji} ${it.name}" } ?: mode.name, style = MaterialTheme.typography.titleMedium)
                Text(mode.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (state.isHost) "Edit" else "Rules", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(if (s[CoreSettings.TIME_LIMIT_ENABLED]) "⏱ ${SettingFormat.format(s[CoreSettings.DURATION_MIN], CoreSettings.DURATION_MIN.unit)}" else "⏱ No limit", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Pill("🏁 Head start ${CoreSettings.HEADSTART_SEC.describe(s[CoreSettings.HEADSTART_SEC])}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s[CoreSettings.ZONE_ENABLED]) Pill("🌀 ${Format.distance(s[CoreSettings.ZONE_START_RADIUS_M].toDouble(), units)} → ${Format.distance(s[CoreSettings.ZONE_END_RADIUS_M].toDouble(), units)}", color = FugitiveColors.Zone)
            Pill("👁 Runners: ${CoreSettings.RUNNER_REVEAL.describe(s[CoreSettings.RUNNER_REVEAL])}", color = FugitiveColors.Runner)
            Pill("🎯 Hunters: ${CoreSettings.HUNTER_REVEAL.describe(s[CoreSettings.HUNTER_REVEAL])}", color = FugitiveColors.Hunter)
            Pill("✋ ${CoreSettings.CATCH_METHOD.describe(s[CoreSettings.CATCH_METHOD])}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Pill("📡 Every ${s[CoreSettings.LOCATION_INTERVAL_SEC]}s", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerRow(m: Member, state: LobbyUiState, vm: LobbyViewModel) {
    val lobby = state.lobby ?: return
    val isBot = state.session?.botControl?.isBot(m.id) == true
    val role = lobby.roster[m.id]
    val mode = vm.engine.mode(lobby.config.modeId)
    var menu by remember { mutableStateOf(false) }
    val canManage = state.isHost && m.id != state.selfId
    val roleColor = role?.let { Visuals.roleColor(it) }
    Surface(
        onClick = { if (state.isHost) menu = true },
        enabled = state.isHost,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                EmojiAvatar(m.avatar, FugitiveColors.player(m.color), ring = roleColor, dimmed = !m.online && !isBot)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (m.id == state.selfId) { Spacer(Modifier.width(6.dp)); Pill("You", color = MaterialTheme.colorScheme.primary) }
                        if (m.id == lobby.hostId) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Star, "Host", tint = FugitiveColors.Warning, modifier = Modifier.size(18.dp)) }
                        if (isBot) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.SmartToy, "Bot", tint = FugitiveColors.Zone, modifier = Modifier.size(18.dp)) }
                    }
                    val status = when {
                        !m.online && !isBot -> "Offline"
                        m.ready -> "Ready"
                        else -> "Not ready"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.ready && (m.online || isBot)) Icon(Icons.Rounded.CheckCircle, null, tint = FugitiveColors.Success, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (m.wantsToHunt) Text(" · wants to hunt", style = MaterialTheme.typography.bodySmall, color = FugitiveColors.Hunter)
                        state.locations[m.id]?.let { f ->
                            if (state.locations[state.selfId] != null && m.id != state.selfId) {
                                val d = app.fugitive.engine.geo.Geo.distance(state.locations[state.selfId]!!.point, f.point)
                                Text(" · ${Format.distance(d, LocalUnitSystem.current)} away", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } ?: Text(" · no GPS yet", style = MaterialTheme.typography.bodySmall, color = FugitiveColors.Warning)
                    }
                }
                if (role != null) Pill(mode.roleLabel(role), color = roleColor ?: FugitiveColors.Mist, icon = Visuals.roleIcon(role))
                else if (mode.usesHunterSelection) Pill("Auto", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(menu, { menu = false }) {
                if (mode.usesHunterSelection) {
                    DropdownMenuItem(text = { Text("Make ${mode.roleLabel(Role.HUNTER).lowercase()}") }, leadingIcon = { Icon(Visuals.roleIcon(Role.HUNTER), null, tint = FugitiveColors.Hunter) }, onClick = { vm.setRole(m.id, Role.HUNTER); menu = false })
                    DropdownMenuItem(text = { Text("Make ${mode.roleLabel(Role.RUNNER).lowercase()}") }, leadingIcon = { Icon(Visuals.roleIcon(Role.RUNNER), null, tint = FugitiveColors.Runner) }, onClick = { vm.setRole(m.id, Role.RUNNER); menu = false })
                }
                DropdownMenuItem(text = { Text("Spectator") }, leadingIcon = { Icon(Visuals.roleIcon(Role.SPECTATOR), null) }, onClick = { vm.setRole(m.id, Role.SPECTATOR); menu = false })
                if (role != null) DropdownMenuItem(text = { Text("Automatic") }, onClick = { vm.setRole(m.id, null); menu = false })
                if (canManage) {
                    HorizontalDivider()
                    if (!isBot && !state.isPractice) DropdownMenuItem(text = { Text("Make host") }, leadingIcon = { Icon(Icons.Rounded.Star, null) }, onClick = { vm.makeHost(m.id); menu = false })
                    DropdownMenuItem(text = { Text(if (isBot) "Remove bot" else "Kick", color = MaterialTheme.colorScheme.error) }, onClick = { vm.kick(m.id); menu = false })
                }
            }
        }
    }
}

@Composable
private fun LobbyBottomBar(state: LobbyUiState, vm: LobbyViewModel, onStart: (allReady: Boolean) -> Unit) {
    val lobby = state.lobby ?: return
    val me = state.me
    val selection = vm.engine.settings(lobby.config)[CoreSettings.HUNTER_SELECTION]
    val online = lobby.members.values.filter { it.online || state.session?.botControl?.isBot(it.id) == true }
    val readyCount = online.count { it.ready }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selection == "volunteers" && vm.engine.mode(lobby.config.modeId).usesHunterSelection && me != null) {
                FilterChip(selected = me.wantsToHunt, onClick = { vm.setWantsToHunt(!me.wantsToHunt) }, label = { Text("I want to hunt") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (me != null && !(state.isPractice && state.isHost)) {
                    val ready = me.ready
                    SecondaryButton(
                        if (ready) "Ready ✓" else "Ready up", onClick = { vm.setReady(!ready) },
                        color = if (ready) FugitiveColors.Success else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.isHost) {
                    PrimaryButton(
                        "Start", icon = Icons.Rounded.PlayArrow,
                        onClick = { onStart(readyCount >= online.size) },
                        enabled = state.blocking == null,
                        modifier = Modifier.weight(1.4f),
                    )
                } else {
                    Text("Waiting for the host…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
            Text(
                "$readyCount of ${online.size} ready" + (state.blocking?.let { " · ${it.message}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
