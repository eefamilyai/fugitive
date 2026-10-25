package app.fugitive.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fugitive.core.designsystem.component.FCard
import app.fugitive.core.designsystem.component.PrimaryButton
import app.fugitive.core.designsystem.component.SecondaryButton
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.ui.BrandBackground
import app.fugitive.core.ui.ProfileEditor
import app.fugitive.core.ui.LogoMark
import app.fugitive.core.ui.rememberPermissionState
import app.fugitive.model.PlayerProfile
import org.koin.compose.viewmodel.koinViewModel

private data class Slide(val title: String, val body: String, val icon: ImageVector?, val color: Color)

private val slides = listOf(
    Slide("The city is your arena", "Fugitive turns your neighbourhood into a live game of manhunt. Hunters track. Runners hide. Everyone's phone keeps score.", null, FugitiveColors.Signal),
    Slide("Watch the zone", "A play area shrinks over time. Get caught outside it and you're out. Hunters see runners on the map — runners have to be clever.", Icons.Rounded.Radar, FugitiveColors.Zone),
    Slide("Your game, your rules", "Classic, Infection, Extraction, Assassin — and dozens of settings: pings, abilities, catch rules, zone speed and more.", Icons.Rounded.Tune, FugitiveColors.Runner),
)

/** First run: intro slides → profile → permissions. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = koinViewModel()) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var profile by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<PlayerProfile, List<Any>>(
        save = { listOf(it.name, it.avatar, it.color) }, restore = { PlayerProfile(it[0] as String, it[1] as String, it[2] as Int) },
    )) { androidx.compose.runtime.mutableStateOf(PlayerProfile(avatar = "🦊", color = (0..11).random())) }

    BrandBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp)) {
            // Progress dots
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(slides.size + 2) { i ->
                    Box(
                        Modifier.padding(4.dp).size(width = if (i == step) 22.dp else 8.dp, height = 8.dp).clip(CircleShape)
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            AnimatedContent(
                targetState = step, modifier = Modifier.weight(1f),
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
                label = "onboarding",
            ) { s ->
                when {
                    s < slides.size -> SlidePage(slides[s])
                    s == slides.size -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
                        Text("Who's playing?", style = MaterialTheme.typography.headlineMedium)
                        Text("Others will see this in the lobby and on the map.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        ProfileEditor(
                            initial = profile.copy(name = profile.name.ifBlank { prefs.profile.name }),
                            confirmLabel = "Continue",
                            onConfirm = { profile = it; step++ },
                        )
                    }
                    else -> PermissionsPage(onFinish = { vm.finish(profile, onDone) })
                }
            }
            if (step < slides.size) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { step = slides.size }) { Text("Skip") }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(if (step == slides.size - 1) "Get started" else "Next", onClick = { step++ })
                }
            }
        }
    }
}

@Composable
private fun SlidePage(slide: Slide) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            if (slide.icon == null) LogoMark(Modifier.size(170.dp))
            else {
                Box(Modifier.size(160.dp).clip(CircleShape).background(slide.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(slide.icon, null, tint = slide.color, modifier = Modifier.size(84.dp))
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Text(slide.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(slide.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionsPage(onFinish: () -> Unit) {
    val perms = rememberPermissionState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Two quick permissions", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Fugitive only shares your location with players in your current game, only while a game is running, and never stores it after the game.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionRow(
            Icons.Rounded.LocationOn, "Precise location", "So hunters can find you — and you can see the zone. Choose \"While using the app\".",
            granted = perms.hasPreciseLocation, onRequest = perms::requestLocation,
        )
        PermissionRow(
            Icons.Rounded.Notifications, "Notifications", "Zone warnings, catches and hunter alerts when your screen is off.",
            granted = perms.hasNotifications, onRequest = perms::requestNotifications,
        )
        Spacer(Modifier.weight(1f, fill = false))
        PrimaryButton(if (perms.hasLocation) "Let's play" else "Continue", onClick = onFinish, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, body: String, granted: Boolean, onRequest: () -> Unit) {
    FCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (granted) FugitiveColors.Success else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) Icon(Icons.Rounded.CheckCircle, "Granted", tint = FugitiveColors.Success)
            else SecondaryButton("Allow", onClick = onRequest)
        }
    }
}
