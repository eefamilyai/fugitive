package app.fugitive.core.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fugitive.engine.mode.GameMode
import app.fugitive.engine.preset.GamePreset

@Composable
fun ModeIcon(mode: GameMode, modifier: Modifier = Modifier, size: Int = 44) {
    val accent = Color(mode.accent)
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape((size / 3.5).dp)).background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Visuals.icon(mode.icon), null, tint = accent, modifier = Modifier.size((size * 0.55).dp)) }
}

@Composable
fun ModeCard(mode: GameMode, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = Color(mode.accent)
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeIcon(mode)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.name, style = MaterialTheme.typography.titleMedium)
                Text(mode.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = accent)
        }
    }
}

@Composable
fun PresetCard(preset: GamePreset, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.width(156.dp),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preset.emoji, fontSize = 26.sp)
            Text(preset.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, minLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
