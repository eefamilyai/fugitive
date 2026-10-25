package app.fugitive.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fugitive.core.designsystem.theme.FugitiveColors

object Avatars {
    val emojis = listOf(
        "🦊", "🐺", "🦁", "🐯", "🐻", "🐼", "🐨", "🐸", "🦉", "🦅", "🐙", "🦈",
        "🐲", "🦄", "🐝", "🦇", "🐍", "🦖", "🐆", "🦌", "🐧", "🐵", "🐹", "🐰",
        "👻", "🤖", "👽", "🥷", "🕵️", "🧙", "🦸", "🧛", "🎃", "💀", "🔥", "⚡",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatars.emojis.forEach { e ->
            val isSel = e == selected
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(2.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                    .clickable { onSelect(e) }
                    .semantics { contentDescription = "Avatar $e" },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 24.sp) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FugitiveColors.players.forEachIndexed { i, c ->
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(c)
                    .border(3.dp, if (i == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .clickable { onSelect(i) }
                    .semantics { contentDescription = "Colour ${i + 1}" },
                contentAlignment = Alignment.Center,
            ) { if (i == selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
        }
    }
}
