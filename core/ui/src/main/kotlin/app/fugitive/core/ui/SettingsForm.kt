package app.fugitive.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role as A11yRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fugitive.core.designsystem.component.FCard
import app.fugitive.engine.settings.ChoiceSetting
import app.fugitive.engine.settings.NumberSetting
import app.fugitive.engine.settings.PointSetting
import app.fugitive.engine.settings.Setting
import app.fugitive.engine.settings.SettingCategory
import app.fugitive.engine.settings.Settings
import app.fugitive.engine.settings.ToggleSetting
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Renders any [Settings] as an editable form, grouped by category. New
 * settings declared in the engine appear here automatically.
 */
@Composable
fun SettingsForm(
    settings: Settings,
    editable: Boolean,
    showAdvanced: Boolean,
    onChange: (Setting<*>, kotlinx.serialization.json.JsonPrimitive) -> Unit,
    onPickPoint: (PointSetting) -> Unit,
    modifier: Modifier = Modifier,
    highlightKeys: Set<String> = emptySet(),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        settings.schema.grouped().forEach { (category, items) ->
            val visible = items.filter { settings.isVisible(it) && (showAdvanced || !it.advanced) }
            if (visible.isEmpty()) return@forEach
            CategoryCard(category, visible, settings, editable, onChange, onPickPoint, highlightKeys)
        }
    }
}

@Composable
private fun CategoryCard(
    category: SettingCategory,
    items: List<Setting<*>>,
    settings: Settings,
    editable: Boolean,
    onChange: (Setting<*>, kotlinx.serialization.json.JsonPrimitive) -> Unit,
    onPickPoint: (PointSetting) -> Unit,
    highlightKeys: Set<String>,
) {
    var expanded by rememberSaveable(category.id) { mutableStateOf(true) }
    FCard(Modifier.fillMaxWidth().animateContentSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(expanded, onValueChange = { expanded = it }, role = A11yRole.Button).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Visuals.icon(category.icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(category.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                items.forEachIndexed { i, s ->
                    if (i > 0 && !s.nested) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.fillMaxWidth().padding(start = if (s.nested) 18.dp else 0.dp)
                            .then(if (s.key in highlightKeys) Modifier.padding(vertical = 2.dp) else Modifier),
                    ) {
                        SettingEditor(s, settings, editable, onChange, onPickPoint, error = s.key in highlightKeys)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingEditor(
    s: Setting<*>,
    settings: Settings,
    editable: Boolean,
    onChange: (Setting<*>, kotlinx.serialization.json.JsonPrimitive) -> Unit,
    onPickPoint: (PointSetting) -> Unit,
    error: Boolean,
) {
    when (s) {
        is ToggleSetting -> {
            val v = settings[s]
            Row(
                Modifier.fillMaxWidth().toggleable(v, enabled = editable, role = A11yRole.Switch) { onChange(s, s.encode(it)) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingTitle(s, Modifier.weight(1f), error)
                Switch(checked = v, onCheckedChange = null, enabled = editable)
            }
        }
        is NumberSetting -> NumberEditor(s, settings[s], editable, error) { onChange(s, s.encode(it)) }
        is ChoiceSetting -> ChoiceEditor(s, settings[s], editable, error) { onChange(s, s.encode(it)) }
        is PointSetting -> {
            val v = settings[s]
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingTitle(s, Modifier.weight(1f), error, valueText = s.describe(v))
                OutlinedButton(onClick = { onPickPoint(s) }, enabled = editable) {
                    Icon(Icons.Rounded.Place, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (v.isBlank()) "Pick" else "Change")
                }
            }
        }
    }
}

@Composable
private fun SettingTitle(s: Setting<*>, modifier: Modifier, error: Boolean, valueText: String? = null) {
    Column(modifier.padding(end = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (valueText != null) {
                Spacer(Modifier.width(8.dp))
                Text(valueText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (s.description.isNotBlank()) {
            Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Slider position <-> value mapping; log/sqrt scales make wide ranges usable. */
private class SliderScale(val min: Long, val max: Long, val step: Long) {
    private val mode = when {
        min > 0 && max.toDouble() / min >= 20 -> 1 // log
        min == 0L && max >= 120 -> 2 // sqrt
        else -> 0
    }
    fun toPos(v: Long): Float = when (mode) {
        1 -> (ln(v.toDouble() / min) / ln(max.toDouble() / min)).toFloat()
        2 -> sqrt((v - min).toDouble() / (max - min)).toFloat()
        else -> ((v - min).toDouble() / (max - min).coerceAtLeast(1)).toFloat()
    }.coerceIn(0f, 1f)
    fun toValue(p: Float): Long {
        val raw = when (mode) {
            1 -> min * exp(p * ln(max.toDouble() / min))
            2 -> min + (max - min) * p.toDouble().pow(2)
            else -> min + (max - min) * p.toDouble()
        }
        // Snap to the step, using coarser steps for large values so numbers stay "nice".
        val dynamicStep = when {
            raw >= 5000 -> maxOf(step, 250)
            raw >= 1000 -> maxOf(step, 50)
            raw >= 200 && step < 10 && max >= 1000 -> 10
            else -> step
        }
        return ((raw / dynamicStep).roundToLong() * dynamicStep).coerceIn(min, max)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberEditor(s: NumberSetting, value: Long, editable: Boolean, error: Boolean, onSet: (Long) -> Unit) {
    val units = LocalUnitSystem.current
    val scale = remember(s) { SliderScale(s.min, s.max, s.step) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var pos by remember(value) { mutableFloatStateOf(scale.toPos(value)) }
    val shown = dragging ?: value
    val label = s.specialLabels[shown] ?: Format.settingValue(shown, s.unit, units)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SettingTitle(s, Modifier.fillMaxWidth(), error, valueText = label)
        Slider(
            value = pos,
            onValueChange = { p -> pos = p; dragging = scale.toValue(p) },
            onValueChangeFinished = { dragging?.let(onSet); dragging = null },
            enabled = editable,
        )
        if (s.suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                s.suggestions.filter { it in s.min..s.max }.forEach { v ->
                    FilterChip(
                        selected = v == value,
                        onClick = { onSet(v) },
                        enabled = editable,
                        label = { Text(s.specialLabels[v] ?: Format.settingValue(v, s.unit, units)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceEditor(s: ChoiceSetting, value: String, editable: Boolean, error: Boolean, onSet: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingTitle(s, Modifier.fillMaxWidth(), error)
        s.options.forEach { o ->
            val selected = o.id == value
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().selectable(selected, enabled = editable, role = A11yRole.RadioButton) { onSet(o.id) },
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null, enabled = editable)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        if (o.description.isNotBlank()) Text(o.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
