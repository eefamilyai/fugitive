package app.fugitive.feature.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fugitive.core.designsystem.component.Notice
import app.fugitive.core.designsystem.component.SectionHeader
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.ui.ModeCard
import app.fugitive.core.ui.PresetCard
import app.fugitive.core.ui.SettingsForm
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.model.GeoPoint
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSettingsScreen(onBack: () -> Unit, vm: GameSettingsViewModel = koinViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val lobby by vm.lobby.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pickerStyles by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val editable = vm.isHost
    val config = draft
    val currentLobby = lobby

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editable) "Game settings" else "Game rules") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { if (editable) IconButton(onClick = { confirmReset = true }) { Icon(Icons.Rounded.RestartAlt, "Reset to defaults") } },
            )
        },
    ) { padding ->
        if (config == null || currentLobby == null) return@Scaffold
        val settings = vm.engine.settings(config)
        val participants = currentLobby.membersByJoin.filter { it.online }
        val issues = vm.engine.validate(config, participants, currentLobby.roster, locations[currentLobby.hostId] != null)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!editable) item {
                Notice("Only the host can change the rules", "Changes the host makes show up here live.", Icons.Rounded.Lock)
            }
            items(issues) { issue ->
                Notice(
                    issue.message, null, if (issue.blocking) Icons.Rounded.Error else Icons.Rounded.Warning,
                    color = if (issue.blocking) MaterialTheme.colorScheme.error else FugitiveColors.Warning,
                )
            }
            item { SectionHeader("Quick presets") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(vm.engine.presets, key = { it.id }) { p ->
                        PresetCard(p, selected = config.presetId == p.id, enabled = editable, onClick = { vm.applyPreset(p.id) })
                    }
                }
            }
            item { SectionHeader("Game mode") }
            items(vm.engine.modes, key = { it.id }) { mode ->
                ModeCard(mode, selected = config.modeId == mode.id, enabled = editable, onClick = { vm.switchMode(mode.id) }, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show advanced settings", style = MaterialTheme.typography.titleSmall)
                        Text("Fine-tune fairness, trails, timings and more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(advanced, { advanced = it })
                }
            }
            item {
                SettingsForm(
                    settings = settings,
                    editable = editable,
                    showAdvanced = advanced,
                    onChange = { s, v -> vm.setValue(s.key, v) },
                    onPickPoint = { picking = true },
                    highlightKeys = issues.filter { it.blocking }.mapNotNull { it.settingKey }.toSet(),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (picking) {
            ZonePickerDialog(
                initial = GeoPoint.parse(settings[CoreSettings.ZONE_CENTER_POINT]),
                radius = settings[CoreSettings.ZONE_START_RADIUS_M].toDouble(),
                lobby = currentLobby, locations = locations, selfId = currentLobby.hostId,
                prefs = prefs,
                onDismiss = { picking = false },
                onPick = { vm.setZoneCenter(it); picking = false },
                onOpenStyles = { pickerStyles = true },
            )
        }
        if (pickerStyles) {
            LobbyStyleSheet(prefs, vm::setMapStyle, vm::setTerrainShading, vm::setMap3d, onDismiss = { pickerStyles = false })
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = { Text("All options return to the defaults for this game mode.") },
            confirmButton = { TextButton(onClick = { vm.reset(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
