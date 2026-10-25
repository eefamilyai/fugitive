package app.fugitive.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fugitive.core.data.prefs.ThemeMode
import app.fugitive.core.data.prefs.Units
import app.fugitive.core.designsystem.component.ClickableRow
import app.fugitive.core.designsystem.component.EmojiAvatar
import app.fugitive.core.designsystem.component.FCard
import app.fugitive.core.designsystem.component.SectionHeader
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.location.LocationSources
import app.fugitive.core.map.MapStyles
import app.fugitive.core.map.MapStyleGrid
import app.fugitive.core.ui.ProfileEditor
import app.fugitive.core.ui.rememberPermissionState
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit, onReplayTutorial: () -> Unit, vm: AppSettingsViewModel = koinViewModel()) {
    val prefs by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val perms = rememberPermissionState()
    var editProfile by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var customUrl by rememberSaveable(prefs.customStyleUrl) { mutableStateOf(prefs.customStyleUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FCard(Modifier.fillMaxWidth(), onClick = { editProfile = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiAvatar(prefs.profile.avatar, FugitiveColors.player(prefs.profile.color), size = 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(prefs.profile.name.ifBlank { "Set your name" }, style = MaterialTheme.typography.titleLarge)
                            Text("Tap to edit your profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { SectionHeader("Appearance") }
            item {
                FCard(Modifier.fillMaxWidth()) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.padding(4.dp))
                    Segmented(ThemeMode.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) }, prefs.theme, vm::setTheme)
                    Spacer(Modifier.padding(8.dp))
                    Text("Distance units", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.padding(4.dp))
                    Segmented(listOf(Units.METRIC to "Metric (m, km)", Units.IMPERIAL to "Imperial (ft, mi)"), prefs.units, vm::setUnits)
                }
            }
            item {
                FCard(Modifier.fillMaxWidth()) {
                    Text("Map type", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.padding(4.dp))
                    val dark = app.fugitive.core.designsystem.theme.LocalDarkTheme.current
                    val styles = MapStyles.all(prefs.mapTilerKey)
                    val selected = MapStyles.resolve(prefs.mapStyleId, dark, prefs.customStyleUrl, prefs.mapTilerKey)
                    MapStyleGrid(
                        styles = styles,
                        selectedId = if (prefs.mapStyleId == MapStyles.CUSTOM) MapStyles.CUSTOM else selected.id,
                        onSelect = { vm.setMapStyle(it.id) },
                        modifier = Modifier.heightIn(max = 560.dp),
                    )
                    Text(selected.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    ToggleRow("Terrain shading", "Shade hills and valleys on street and night maps", prefs.terrainShading, vm::setTerrainShading)
                    ToggleRow("3D view", "Tilted perspective in-game — buildings pop up", prefs.map3d, vm::setMap3d)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("HD maps (optional)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Paste a free MapTiler key (maptiler.com → Account → API keys) to unlock HD satellite, outdoor maps with contour lines, topo and winter maps.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    var key by rememberSaveable(prefs.mapTilerKey) { mutableStateOf(prefs.mapTilerKey) }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() }, singleLine = true,
                        label = { Text("MapTiler API key") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        trailingIcon = { TextButton(onClick = { vm.setMapTilerKey(key) }, enabled = key != prefs.mapTilerKey) { Text("Save") } },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    RadioRow("Custom style URL", "Any MapLibre style JSON", prefs.mapStyleId == MapStyles.CUSTOM) { vm.setMapStyle(MapStyles.CUSTOM) }
                    if (prefs.mapStyleId == MapStyles.CUSTOM) {
                        OutlinedTextField(
                            value = customUrl, onValueChange = { customUrl = it }, singleLine = true,
                            label = { Text("https://…/style.json") }, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { TextButton(onClick = { vm.setCustomStyle(customUrl) }) { Text("Apply") } },
                        )
                    }
                    Text(
                        "Map data © OpenStreetMap contributors · OpenFreeMap / OpenMapTiles · Imagery © Esri, Maxar, Earthstar Geographics · Elevation: Mapzen/AWS",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item { SectionHeader("Gameplay") }
            item {
                FCard(Modifier.fillMaxWidth()) {
                    ToggleRow("Vibration", "Heartbeat when hunters are near, buzz outside the zone", prefs.haptics, vm::setHaptics)
                    ToggleRow("Keep screen on", "During games, so the map stays live", prefs.keepScreenOn, vm::setKeepScreenOn)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Practice bot difficulty", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.padding(4.dp))
                    Segmented(listOf(0 to "Easy", 1 to "Normal", 2 to "Hard"), prefs.practiceDifficulty, vm::setDifficulty)
                }
            }

            item { SectionHeader("Location") }
            item {
                FCard(Modifier.fillMaxWidth()) {
                    Text("Location source", style = MaterialTheme.typography.titleSmall)
                    RadioRow("Automatic", "Google fused location when available, otherwise the device GPS", prefs.locationSource == LocationSources.AUTO) { vm.setLocationSource(LocationSources.AUTO) }
                    RadioRow("Device GPS only", "For phones without Google Play services", prefs.locationSource == "platform") { vm.setLocationSource("platform") }
                    RadioRow("Virtual joystick", "Practice & testing: move with an on-screen stick instead of walking. Don't use in real games!", prefs.locationSource == vm.joystickId) { vm.setLocationSource(vm.joystickId) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    StatusRow(Icons.Rounded.LocationOn, "Precise location", perms.hasPreciseLocation) { if (perms.hasLocation) perms.openAppSettings() else perms.requestLocation() }
                    StatusRow(Icons.Rounded.Notifications, "Notifications", perms.hasNotifications) { if (perms.hasNotifications) perms.openAppSettings() else perms.requestNotifications() }
                    ClickableRow(onClick = perms::openBatterySettings) {
                        Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Battery optimisation", style = MaterialTheme.typography.bodyLarge)
                            Text("If your phone stops sharing location with the screen off, exclude Fugitive here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
                    }
                }
            }

            item { SectionHeader("About") }
            item {
                FCard(Modifier.fillMaxWidth()) {
                    ClickableRow(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.info.privacyPolicyUrl))) }) {
                        Text("Privacy policy", Modifier.weight(1f)); Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
                    }
                    ClickableRow(onClick = { licenses = true }) { Text("Open-source licences", Modifier.weight(1f)) }
                    ClickableRow(onClick = {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${vm.info.supportEmail}")).putExtra(Intent.EXTRA_SUBJECT, "Fugitive ${vm.info.versionName} feedback"))
                    }) { Text("Send feedback", Modifier.weight(1f)); Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }
                    ClickableRow(onClick = { vm.replayTutorial(onReplayTutorial) }) { Text("Replay the introduction", Modifier.weight(1f)) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Fugitive ${vm.info.versionName} (${vm.info.versionCode})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (vm.info.onlineConfigured) "Online play: enabled" else "Online play: not configured in this build", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (editProfile) {
        ModalBottomSheet(onDismissRequest = { editProfile = false }) {
            ProfileEditor(prefs.profile, "Save", onConfirm = { vm.setProfile(it); editProfile = false }, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp))
        }
    }
    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text("Open-source licences") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LICENSES.forEach { (name, text) ->
                        Column {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text("Close") } },
        )
    }
}

private val LICENSES = listOf(
    "MapLibre Native" to "BSD 2-Clause License. © MapLibre contributors, © Mapbox.",
    "OpenStreetMap data" to "© OpenStreetMap contributors. Available under the Open Database License (ODbL).",
    "Esri World Imagery & World Topographic Map" to "Imagery © Esri, Maxar, Earthstar Geographics, and the GIS User Community. Topographic map © Esri and contributors.",
    "Terrain elevation" to "Mapzen Terrain Tiles via AWS Open Data (SRTM, GMTED, NED, ETOPO1 and others).",
    "OpenFreeMap & OpenMapTiles" to "Map tiles by OpenFreeMap. Schema & styles © OpenMapTiles (BSD-3 / CC-BY 4.0).",
    "Jetpack Compose, AndroidX" to "Apache License 2.0. © The Android Open Source Project.",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0. © JetBrains s.r.o.",
    "Firebase Android SDK, Google Play services" to "Apache License 2.0 / Google APIs Terms of Service.",
    "Koin" to "Apache License 2.0. © Kotzilla & contributors.",
    "ZXing" to "Apache License 2.0. © ZXing authors.",
    "Space Grotesk" to "SIL Open Font License 1.1. © The Space Grotesk Project Authors.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected = value == selected, onClick = { onSelect(value) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, ok: Boolean, onClick: () -> Unit) {
    ClickableRow(onClick = onClick) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (ok) Icon(Icons.Rounded.CheckCircle, "Granted", tint = FugitiveColors.Success)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Error, null, tint = FugitiveColors.Warning)
            Spacer(Modifier.width(4.dp))
            Text("Allow", color = FugitiveColors.Warning)
        }
    }
}
