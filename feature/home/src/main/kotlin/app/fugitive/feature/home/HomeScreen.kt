package app.fugitive.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fugitive.core.designsystem.component.EmojiAvatar
import app.fugitive.core.designsystem.component.FCard
import app.fugitive.core.designsystem.component.Notice
import app.fugitive.core.designsystem.component.PrimaryButton
import app.fugitive.core.designsystem.component.SecondaryButton
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.Grotesk
import app.fugitive.core.ui.BrandBackground
import app.fugitive.core.ui.ProfileEditor
import app.fugitive.core.ui.LogoMark
import app.fugitive.core.ui.rememberPermissionState
import app.fugitive.model.LobbyCodes
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    initialJoinCode: String?,
    onOpenSettings: () -> Unit,
    onOpenHowToPlay: () -> Unit,
    vm: HomeViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf(LobbyCodes.normalize(initialJoinCode ?: "")) }
    var showProfile by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissions = rememberPermissionState { result ->
        // Continue whatever the user was doing once location is granted (or not — practice still works with the joystick).
        pendingAction?.invoke(); pendingAction = null
    }
    val busy = state.busyMessage != null

    fun withLocation(action: () -> Unit) {
        if (permissions.hasLocation) action() else { pendingAction = action; permissions.requestAll() }
    }

    LaunchedEffect(initialJoinCode) {
        if (initialJoinCode != null && LobbyCodes.isValid(LobbyCodes.normalize(initialJoinCode)) && state.onlineAvailable) {
            withLocation { vm.join(initialJoinCode) }
        }
    }

    BrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("FUGITIVE", fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 3.sp)
                    Text("Real-world manhunt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showProfile = true }) {
                    EmojiAvatar(state.prefs.profile.avatar, FugitiveColors.player(state.prefs.profile.color), size = 36.dp)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
            }

            Text(
                "Hi ${state.prefs.profile.name.ifBlank { "there" }} 👋",
                style = MaterialTheme.typography.headlineSmall,
            )

            AnimatedVisibility(state.error != null) {
                Notice(
                    title = state.error ?: "", body = null, icon = Icons.Rounded.CloudOff, color = MaterialTheme.colorScheme.error,
                    action = { TextButton(onClick = vm::dismissError) { Text("Dismiss") } },
                )
            }

            val last = state.prefs.lastLobby
            if (last != null && state.onlineAvailable) {
                FCard(Modifier.fillMaxWidth(), color = FugitiveColors.Signal.copy(alpha = 0.12f), border = FugitiveColors.Signal.copy(alpha = 0.5f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, null, tint = FugitiveColors.Signal)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Game in progress", style = MaterialTheme.typography.titleSmall)
                            Text("Lobby ${last.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = vm::forgetLastLobby, enabled = !busy) { Text("Forget") }
                        TextButton(onClick = { withLocation { vm.rejoin() } }, enabled = !busy) { Text("Rejoin") }
                    }
                }
            }

            if (!state.onlineAvailable) {
                Notice(
                    title = "Online play isn't set up in this build",
                    body = "Add a Firebase project (see docs/FIREBASE_SETUP.md) to play with friends. Practice mode works offline.",
                    icon = Icons.Rounded.CloudOff,
                )
            }

            // Host
            FCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Host a game", style = MaterialTheme.typography.titleLarge)
                        Text("Create a lobby and share the code", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    "Create lobby", onClick = { withLocation { vm.host() } }, icon = Icons.Rounded.Add,
                    enabled = state.onlineAvailable && !busy, loading = busy && state.busyMessage?.startsWith("Creating") == true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Join
            FCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Login, null, tint = FugitiveColors.Runner, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Join a game", style = MaterialTheme.typography.titleLarge)
                        Text("Enter the 6-character code", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(
                        onClick = {
                            GmsBarcodeScanning.getClient(context).startScan().addOnSuccessListener { barcode ->
                                val raw = barcode.rawValue ?: return@addOnSuccessListener
                                val parsed = LobbyCodes.fromLink(raw) ?: return@addOnSuccessListener
                                code = parsed
                                withLocation { vm.join(parsed) }
                            }
                        },
                        enabled = state.onlineAvailable && !busy,
                    ) { Icon(Icons.Rounded.QrCodeScanner, "Scan QR code") }
                }
                Spacer(Modifier.height(14.dp))
                CodeInput(code, { code = it }, onDone = { if (LobbyCodes.isValid(code)) withLocation { vm.join(code) } })
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    "Join", onClick = { withLocation { vm.join(code) } },
                    enabled = LobbyCodes.isValid(code) && state.onlineAvailable && !busy,
                    loading = busy && state.busyMessage?.startsWith("Joining") == true,
                    color = FugitiveColors.Runner, contentColor = FugitiveColors.Night1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Practice
            FCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SmartToy, null, tint = FugitiveColors.Zone, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Practice vs bots", style = MaterialTheme.typography.titleLarge)
                        Text("Learn the ropes offline — bots run around your real surroundings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bots", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { vm.setPractice(state.prefs.practiceBots - 1, state.prefs.practiceDifficulty) }, enabled = state.prefs.practiceBots > 1) {
                        Icon(Icons.Rounded.Remove, "Fewer bots")
                    }
                    Text("${state.prefs.practiceBots}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    FilledTonalIconButton(onClick = { vm.setPractice(state.prefs.practiceBots + 1, state.prefs.practiceDifficulty) }, enabled = state.prefs.practiceBots < 15) {
                        Icon(Icons.Rounded.Add, "More bots")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val levels = listOf("Easy", "Normal", "Hard")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    levels.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = state.prefs.practiceDifficulty == i,
                            onClick = { vm.setPractice(state.prefs.practiceBots, i) },
                            shape = SegmentedButtonDefaults.itemShape(i, levels.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    "Start practice", onClick = { withLocation { vm.practice() } }, icon = Icons.Rounded.SmartToy,
                    enabled = !busy, modifier = Modifier.fillMaxWidth(), color = FugitiveColors.Zone,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("How to play", onClick = onOpenHowToPlay, icon = Icons.AutoMirrored.Rounded.HelpOutline, modifier = Modifier.weight(1f))
                if (!permissions.hasLocation) {
                    SecondaryButton("Allow location", onClick = { permissions.requestAll() }, icon = Icons.Rounded.LocationOn, modifier = Modifier.weight(1f), color = FugitiveColors.Warning)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        app.fugitive.core.designsystem.component.StatusBarScrim()
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            icon = { LogoMark(Modifier.size(56.dp)) },
            title = { Text(state.busyMessage ?: "") },
            text = { Text("Hang tight…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }

    if (showProfile) {
        ModalBottomSheet(onDismissRequest = { showProfile = false }) {
            ProfileEditor(
                initial = state.prefs.profile,
                confirmLabel = "Save",
                onConfirm = { vm.saveProfile(it); showProfile = false },
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            )
        }
    }
}
