package app.fugitive.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** On-screen joystick for the simulated location source. Reports x (east) and y (north) in -1..1. */
@Composable
internal fun Joystick(onMove: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.size(132.dp).semantics { contentDescription = "Movement joystick" }.pointerInput(Unit) {
            val radius = size.width / 2f
            detectDragGestures(
                onDragStart = { pos -> knob = clamp(pos - Offset(radius, radius), radius); onMove(knob.x / radius, -knob.y / radius) },
                onDragEnd = { knob = Offset.Zero; onMove(0f, 0f) },
                onDragCancel = { knob = Offset.Zero; onMove(0f, 0f) },
            ) { change, drag ->
                change.consume()
                knob = clamp(knob + drag, radius)
                onMove(knob.x / radius, -knob.y / radius)
            }
        },
    ) {
        Canvas(Modifier.size(132.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color.Black.copy(alpha = 0.35f), r)
            drawCircle(Color.White.copy(alpha = 0.5f), r, style = Stroke(2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.36f, center + knob)
        }
    }
}

private fun clamp(o: Offset, max: Float): Offset {
    val d = hypot(o.x, o.y)
    return if (d <= max) o else o * (max / d)
}
