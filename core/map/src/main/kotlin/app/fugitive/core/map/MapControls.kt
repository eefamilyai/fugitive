package app.fugitive.core.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private fun categoryIcon(c: StyleCategory): ImageVector = when (c) {
    StyleCategory.SATELLITE -> Icons.Rounded.Satellite
    StyleCategory.STREETS -> Icons.Rounded.Map
    StyleCategory.TERRAIN -> Icons.Rounded.Landscape
    StyleCategory.DARK -> Icons.Rounded.Nightlight
}

/** Thumbnail for a style: a real preview image when bundled, otherwise a tinted placeholder. */
@Composable
fun MapStyleThumbnail(style: MapStyle, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        val res = style.previewRes
        if (res != null) {
            Image(painterResource(res), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val colors = when (style.category) {
                StyleCategory.SATELLITE -> listOf(Color(0xFF2F4F2F), Color(0xFF6B5B3E))
                StyleCategory.TERRAIN -> listOf(Color(0xFFDCD3B6), Color(0xFF8DA36B))
                StyleCategory.DARK -> listOf(Color(0xFF1B2433), Color(0xFF0B0F15))
                StyleCategory.STREETS -> listOf(Color(0xFFF2EFE9), Color(0xFFCAD8E8))
            }
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(style.category), null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(30.dp))
            }
        }
    }
}

/**
 * Bottom sheet for choosing the base map and view options. Pure UI: the caller persists choices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapStyleSheet(
    styles: List<MapStyle>,
    selectedId: String,
    terrainShading: Boolean,
    tilt3d: Boolean,
    onSelect: (MapStyle) -> Unit,
    onTerrainShading: (Boolean) -> Unit,
    onTilt3d: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Map type", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            MapStyleGrid(styles, selectedId, onSelect, Modifier.heightIn(max = 540.dp))
            Spacer(Modifier.height(12.dp))
            val selected = styles.firstOrNull { it.id == selectedId }
            OptionRow(
                Icons.Rounded.Landscape, "Terrain shading",
                when {
                    selected?.supportsTerrainShading != false -> "Show hills and valleys"
                    selected.category == StyleCategory.SATELLITE -> "Not needed on aerial photos"
                    else -> "Built into this map type"
                },
                terrainShading, enabled = selected?.supportsTerrainShading != false, onTerrainShading,
            )
            OptionRow(Icons.Rounded.ViewInAr, "3D view", "Tilt the map — buildings pop up on street maps", tilt3d, enabled = true, onTilt3d)
            footer?.invoke()
        }
    }
}

@Composable
fun MapStyleGrid(styles: List<MapStyle>, selectedId: String, onSelect: (MapStyle) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        GridCells.Fixed(3), modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(styles, key = { it.id }) { st ->
            val selected = st.id == selectedId || (selectedId == MapStyles.AUTO && st.id == MapStyles.HYBRID.id)
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(st) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    onClick = { onSelect(st) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    Box {
                        MapStyleThumbnail(st, Modifier.fillMaxSize().padding(3.dp))
                        if (selected) Icon(
                            Icons.Rounded.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.White, RoundedCornerShape(50)),
                        )
                        if (st.premium) Text(
                            "HD", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    st.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Stacked +/− zoom buttons. */
@Composable
fun ZoomControls(camera: MapCameraState, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f)),
    ) {
        ZoomButton(Icons.Rounded.Add, "Zoom in", enabled = camera.zoom < camera.maxZoom - 0.05) { camera.zoomBy(1.0) }
        Box(Modifier.width(48.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        ZoomButton(Icons.Rounded.Remove, "Zoom out", enabled = camera.zoom > 3) { camera.zoomBy(-1.0) }
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

/** Round "layers" button that opens the [MapStyleSheet]. */
@Composable
fun MapLayersButton(style: MapStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp), modifier = modifier.size(48.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            MapStyleThumbnail(style, Modifier.fillMaxSize().padding(5.dp))
            Icon(Icons.Rounded.Workspaces, "Map type", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Distance scale that adapts to zoom (e.g. "50 m"), drawn with a halo so it reads on imagery.
 * [format] turns metres into a label honouring the user's units.
 */
@Composable
fun ScaleBar(camera: MapCameraState, format: (Double) -> String, modifier: Modifier = Modifier, imperial: Boolean = false, maxWidthDp: Int = 96) {
    val density = LocalDensity.current
    val mpp = camera.metersPerPixel
    if (mpp <= 0.0) return
    val maxPx = with(density) { maxWidthDp.dp.toPx() }
    val unit = if (imperial) 0.3048 else 1.0 // feet or metres
    val maxUnits = maxPx * mpp / unit
    val nice = niceNumber(maxUnits)
    val widthPx = (nice * unit / mpp).toFloat()
    val animated by animateFloatAsState(widthPx, label = "scale")
    val widthDp = with(density) { animated.toDp() }
    Column(modifier) {
        Text(
            format(nice * unit), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Canvas(Modifier.width(widthDp).height(6.dp)) {
            val stroke = 2.dp.toPx()
            drawRect(Color(0xAA000000), size = this.size)
            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - stroke), size = androidx.compose.ui.geometry.Size(size.width, stroke))
            drawRect(Color.White, size = androidx.compose.ui.geometry.Size(stroke, size.height))
            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(size.width - stroke, 0f), size = androidx.compose.ui.geometry.Size(stroke, size.height))
        }
    }
}

private fun niceNumber(max: Double): Double {
    if (max <= 0) return 1.0
    val exp = floor(log10(max))
    val base = 10.0.pow(exp)
    val f = max / base
    val nice = when { f >= 5 -> 5.0; f >= 2 -> 2.0; else -> 1.0 }
    return nice * base
}
