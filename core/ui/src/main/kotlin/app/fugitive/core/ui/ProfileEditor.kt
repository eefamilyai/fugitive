package app.fugitive.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.fugitive.core.designsystem.component.EmojiAvatar
import app.fugitive.core.designsystem.component.PrimaryButton
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.model.PlayerProfile

const val MAX_NAME_LENGTH = 20

@Composable
fun ProfileEditor(
    initial: PlayerProfile,
    confirmLabel: String,
    onConfirm: (PlayerProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var avatar by rememberSaveable { mutableStateOf(initial.avatar) }
    var color by rememberSaveable { mutableIntStateOf(initial.color) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(avatar, FugitiveColors.player(color), size = 72.dp)
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text("Your name") },
                singleLine = true,
                supportingText = { Text("${name.length}/${MAX_NAME_LENGTH}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.weight(1f),
            )
        }
        Text("Avatar", style = MaterialTheme.typography.titleSmall)
        EmojiPicker(avatar, { avatar = it })
        Text("Colour", style = MaterialTheme.typography.titleSmall)
        ColorPicker(color, { color = it })
        PrimaryButton(
            confirmLabel, onClick = { onConfirm(PlayerProfile(name.trim(), avatar, color)) },
            enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(),
        )
    }
}
