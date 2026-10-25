package app.fugitive.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.LocalDarkTheme

/** The Fugitive mark: a shrinking-zone reticle with a target dot. */
@Composable
fun LogoMark(modifier: Modifier = Modifier, animated: Boolean = true) {
    val t = rememberInfiniteTransition(label = "logo")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(FugitiveColors.Signal.copy(alpha = 0.16f), r, c)
        drawCircle(FugitiveColors.Signal, r * 0.92f, c, style = Stroke(r * 0.09f))
        drawCircle(FugitiveColors.Zone, r * 0.62f, c, style = Stroke(r * 0.07f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(r * 0.16f, r * 0.11f))))
        if (animated) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, FugitiveColors.Signal.copy(alpha = 0.55f)), c),
                startAngle = sweep - 70f, sweepAngle = 70f, useCenter = true,
                topLeft = Offset(c.x - r * 0.92f, c.y - r * 0.92f), size = androidx.compose.ui.geometry.Size(r * 1.84f, r * 1.84f),
            )
        }
        drawCircle(Color.White, r * 0.17f * (if (animated) pulse else 1f), c)
        drawCircle(FugitiveColors.Signal, r * 0.1f, c)
    }
}

/** Atmospheric background: soft colour glows and faint radar rings. */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDarkTheme.current
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(bg)
            drawCircle(
                Brush.radialGradient(listOf(FugitiveColors.Signal.copy(alpha = if (dark) 0.22f else 0.12f), Color.Transparent), Offset(size.width * 0.1f, size.height * 0.05f), size.width * 0.9f),
                size.width * 0.9f, Offset(size.width * 0.1f, size.height * 0.05f),
            )
            drawCircle(
                Brush.radialGradient(listOf(FugitiveColors.Zone.copy(alpha = if (dark) 0.18f else 0.10f), Color.Transparent), Offset(size.width, size.height * 0.85f), size.width),
                size.width, Offset(size.width, size.height * 0.85f),
            )
            val ringColor = (if (dark) Color.White else Color.Black).copy(alpha = 0.035f)
            val origin = Offset(size.width * 0.85f, size.height * 0.12f)
            for (i in 1..7) drawCircle(ringColor, i * 70.dp.toPx(), origin, style = Stroke(1.dp.toPx()))
        }
        content()
    }
}
