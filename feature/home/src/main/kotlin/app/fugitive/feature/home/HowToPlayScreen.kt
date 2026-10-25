package app.fugitive.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fugitive.core.designsystem.component.FCard
import app.fugitive.core.designsystem.component.SectionHeader
import app.fugitive.core.ui.ModeIcon
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.GameEngine
import org.koin.compose.koinInject

private val basics = listOf(
    "🎯" to "One person hosts and shares the 6-character code. Everyone joins from their own phone.",
    "🏃" to "The host picks the hunters (or lets the app choose). Everyone else runs.",
    "⏱️" to "Runners get a head start. Hunters can't see anyone until it ends.",
    "📍" to "Hunters see runners on the map — live, or only at pings, depending on the rules.",
    "🌀" to "The zone shrinks. Runners caught outside it are out — keep an eye on the dashed circle: that's where it's going.",
    "✋" to "Tag someone in real life, then tap Catch. The runner confirms on their phone.",
    "⚡" to "Abilities like Cloak, Sonar and Radar Scan can turn the game around. Use them wisely.",
    "🏁" to "When time runs out (or everyone is caught), see the results and replay everyone's routes.",
)

private val safety = listOf(
    "Stay aware of traffic. Never run across roads while looking at your phone.",
    "Agree on boundaries everyone is comfortable with — private property is off-limits.",
    "Play in daylight or well-lit areas, and keep the game friendly.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToPlayScreen(onBack: () -> Unit, engine: GameEngine = koinInject()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to play") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("The basics") }
            items(basics) { (emoji, text) ->
                Row {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(14.dp))
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { SectionHeader("Game modes") }
            // Rendered from the engine registry, so new modes document themselves.
            items(engine.modes) { mode ->
                FCard(Modifier.fillMaxWidth()) {
                    Row {
                        ModeIcon(mode)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.name, style = MaterialTheme.typography.titleMedium)
                            Text(mode.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.padding(4.dp))
                    mode.rules.forEach { rule ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("•", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(mode.accent))
                            Spacer(Modifier.width(8.dp))
                            Text(rule, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item { SectionHeader("Abilities") }
            items(engine.abilities) { a ->
                Row {
                    Icon(Visuals.icon(a.icon), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(a.name, style = MaterialTheme.typography.titleSmall)
                        Text(a.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionHeader("Play safe") }
            items(safety) { s -> Text("⚠️  $s", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
