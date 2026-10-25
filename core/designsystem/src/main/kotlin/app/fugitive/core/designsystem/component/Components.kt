package app.fugitive.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Big call-to-action button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = contentColor, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Card used across the app. */
@Composable
fun FCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    border: Color? = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = border?.let { BorderStroke(1.dp, it) },
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Translucent panel for HUD elements floating over the map. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    borderColor: Color = Color.White.copy(alpha = 0.08f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.86f))
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** Small rounded label. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    filled: Boolean = false,
) {
    val bg = if (filled) color else color.copy(alpha = 0.16f)
    val fg = if (filled) Color.White else color
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(14.dp), tint = fg); Spacer(Modifier.width(4.dp)) }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Emoji avatar in a coloured circle. */
@Composable
fun EmojiAvatar(
    emoji: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    ring: Color? = null,
    dimmed: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .alpha(if (dimmed) 0.45f else 1f)
            .clip(CircleShape)
            .background(color)
            .then(if (ring != null) Modifier.border(2.5.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp, modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

/** A gently pulsing dot (live indicators, danger states). */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier.size(size).alpha(a).clip(CircleShape).background(color))
}

/** Full-width message with icon, e.g. empty lists or setup hints. */
@Composable
fun Notice(
    title: String,
    body: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), MaterialTheme.shapes.medium).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) { Spacer(Modifier.height(6.dp)); action() }
        }
    }
}

/** Vertical gradient that fades map content behind HUD panels. */
fun scrimGradient(top: Boolean, color: Color = Color.Black) = Brush.verticalGradient(
    if (top) listOf(color.copy(alpha = 0.55f), Color.Transparent) else listOf(Color.Transparent, color.copy(alpha = 0.6f)),
)

@Composable
fun ClickableRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}


/** Solid-to-transparent band behind the status bar for edge-to-edge scrolling screens. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.85f), Color.Transparent)))
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .padding(bottom = 12.dp),
    )
}
