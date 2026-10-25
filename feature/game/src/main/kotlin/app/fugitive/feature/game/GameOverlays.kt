package app.fugitive.feature.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fugitive.core.data.session.GameProjection
import app.fugitive.core.designsystem.component.PrimaryButton
import app.fugitive.core.designsystem.theme.ClockStyle
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.designsystem.theme.Grotesk
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.time.GameClock
import app.fugitive.model.Role

/** Pre-game countdown with the role reveal. */
@Composable
internal fun CountdownOverlay(p: GameProjection) {
    val role = p.me?.role ?: Role.SPECTATOR
    val color = Visuals.roleColor(role)
    val seconds = (((p.stageEndsInMs ?: 0) + 999) / 1000).toInt()
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(32.dp)) {
            Text("YOU ARE", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium, letterSpacing = 4.sp)
            Text(p.myRoleLabel.uppercase(), color = color, fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 52.sp, letterSpacing = 2.sp)
            Box(Modifier.size(84.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Visuals.roleIcon(role), null, tint = color, modifier = Modifier.size(48.dp))
            }
            Text(p.objective, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            AnimatedContent(seconds, transitionSpec = { (scaleIn(tween(250), 1.6f) + fadeIn()) togetherWith (scaleOut(tween(200), 0.6f) + fadeOut()) }, label = "count") { s ->
                Text("$s", style = ClockStyle, fontSize = 96.sp, color = Color.White)
            }
            val tip = when {
                role == Role.HUNTER && p.game.headstartEndsAt > p.game.startAt -> "Runners get ${GameClock.format(p.game.headstartEndsAt - p.game.startAt)} to hide. Stay put!"
                role == Role.RUNNER && p.game.headstartEndsAt > p.game.startAt -> "You'll have ${GameClock.format(p.game.headstartEndsAt - p.game.startAt)} head start. Get ready to run!"
                else -> "Get ready…"
            }
            Text(tip, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

/** Banner shown during the head start. */
@Composable
internal fun HeadstartBanner(p: GameProjection, modifier: Modifier = Modifier) {
    val hunter = p.me?.let { it.role == Role.HUNTER && it.status == app.fugitive.model.PlayerStatus.ACTIVE } == true
    val color = if (hunter) FugitiveColors.Hunter else FugitiveColors.Runner
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(color.copy(alpha = 0.92f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (hunter) "HOLD YOUR POSITION" else "RUN! HEAD START", color = Color.White, fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.5.sp)
        Text(
            (if (hunter) "Hunters released in " else "Hunters released in ") + GameClock.format(p.stageEndsInMs ?: 0),
            color = Color.White, style = ClockStyle, fontSize = 22.sp,
        )
    }
}

/** One-time notice when the local player leaves play (caught, eliminated, escaped, infected). */
@Composable
internal fun StatusChangeOverlay(title: String, body: String, emoji: String, color: Color, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(emoji, fontSize = 72.sp)
            Text(title, color = color, fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 34.sp, textAlign = TextAlign.Center)
            Text(body, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Continue", onClick = onDismiss, color = color)
        }
    }
}
