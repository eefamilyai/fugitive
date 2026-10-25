package app.fugitive.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fugitive.core.designsystem.theme.Grotesk
import app.fugitive.model.LobbyCodes

/** Six separate boxes for the lobby code; accepts pasted codes and filters ambiguous characters. */
@Composable
fun CodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester(),
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(LobbyCodes.normalize(it)) },
        modifier = modifier.focusRequester(focusRequester),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
        decorationBox = { inner ->
            Box {
                Box(Modifier.fillMaxWidth()) { inner() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(LobbyCodes.LENGTH) { i ->
                        val ch = value.getOrNull(i)
                        val active = i == value.length
                        Box(
                            Modifier.weight(1f).aspectRatio(0.82f).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    if (active) 2.dp else 1.dp,
                                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.medium,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch?.toString() ?: "", fontFamily = Grotesk, fontSize = 26.sp, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        },
    )
}
