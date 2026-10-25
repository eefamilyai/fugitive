package app.fugitive.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.fugitive.core.designsystem.component.EmojiAvatar
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.model.ChatMessage
import app.fugitive.model.Member

/** Chat list + composer. Quick replies make it usable while running. */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    members: Map<String, Member>,
    selfId: String,
    onSend: (text: String, teamOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    allowTeamChat: Boolean = false,
    quickReplies: List<String> = listOf("👋", "Ready!", "On my way", "Where are you?", "Nice catch!", "GG"),
) {
    var text by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf(allowTeamChat) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Column(modifier.imePadding()) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) item {
                Text("No messages yet. Say hi!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
            items(messages, key = { it.id }) { m ->
                val mine = m.by == selfId
                val who = members[m.by]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                    if (!mine) {
                        EmojiAvatar(who?.avatar ?: "❔", FugitiveColors.player(who?.color ?: 11), size = 28.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Column(
                        Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (!mine) Text(who?.name ?: "Player", style = MaterialTheme.typography.labelMedium, color = FugitiveColors.player(who?.color ?: 11))
                        if (m.audience.isNotEmpty()) Text("TEAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        Text(m.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (allowTeamChat) item { FilterChip(selected = team, onClick = { team = !team }, label = { Text("Team only") }) }
            items(quickReplies) { q -> FilterChip(selected = false, onClick = { onSend(q, team && allowTeamChat) }, label = { Text(q) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(280) }, modifier = Modifier.weight(1f),
                placeholder = { Text("Message") }, singleLine = true, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text, team && allowTeamChat); text = "" } }),
            )
            Box(Modifier.padding(start = 4.dp)) {
                IconButton(onClick = { if (text.isNotBlank()) { onSend(text, team && allowTeamChat); text = "" } }, enabled = text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
