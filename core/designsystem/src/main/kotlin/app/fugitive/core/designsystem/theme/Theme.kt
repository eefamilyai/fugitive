package app.fugitive.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = FugitiveColors.Signal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1C10),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = FugitiveColors.Runner,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF0E3A44),
    onSecondaryContainer = Color(0xFFB8F1FF),
    tertiary = FugitiveColors.Zone,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3B1A5C),
    onTertiaryContainer = Color(0xFFF0DBFF),
    error = FugitiveColors.Danger,
    onError = Color.White,
    errorContainer = Color(0xFF5C1515),
    onErrorContainer = Color(0xFFFFDAD6),
    background = FugitiveColors.Night1,
    onBackground = Color(0xFFE8ECF2),
    surface = FugitiveColors.Night1,
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = FugitiveColors.Night3,
    onSurfaceVariant = Color(0xFFB4BDCB),
    surfaceContainerLowest = FugitiveColors.Night0,
    surfaceContainerLow = Color(0xFF0F141C),
    surfaceContainer = FugitiveColors.Night2,
    surfaceContainerHigh = FugitiveColors.Night3,
    surfaceContainerHighest = FugitiveColors.Night4,
    outline = Color(0xFF3A475C),
    outlineVariant = Color(0xFF263044),
    inverseSurface = Color(0xFFE8ECF2),
    inverseOnSurface = FugitiveColors.Night1,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = FugitiveColors.SignalDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF00839A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF4FF),
    onSecondaryContainer = Color(0xFF001F26),
    tertiary = Color(0xFF8B3FD9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0DBFF),
    onTertiaryContainer = Color(0xFF2C0051),
    error = Color(0xFFC62828),
    background = Color(0xFFF6F7FA),
    onBackground = Color(0xFF10141B),
    surface = Color(0xFFF6F7FA),
    onSurface = Color(0xFF10141B),
    surfaceVariant = Color(0xFFE3E7EE),
    onSurfaceVariant = Color(0xFF444C5A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F7),
    surfaceContainer = Color(0xFFEBEEF3),
    surfaceContainerHigh = Color(0xFFE5E8EE),
    surfaceContainerHighest = Color(0xFFDDE1E8),
    outline = Color(0xFF8790A0),
    outlineVariant = Color(0xFFC9CED8),
)

val FugitiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Whether the app is rendering dark; read by screens that pick map styles etc. */
val LocalDarkTheme = staticCompositionLocalOf { true }

@Composable
fun FugitiveTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = scheme, typography = FugitiveTypography, shapes = FugitiveShapes) {
            // Text drawn directly on backgrounds (not inside a Surface) should still use the theme colour.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
