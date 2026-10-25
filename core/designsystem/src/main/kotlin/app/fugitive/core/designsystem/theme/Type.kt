package app.fugitive.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.fugitive.core.designsystem.R

private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk (SIL OFL 1.1) for display text; system sans for body copy. */
val Grotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

private val base = Typography()

val FugitiveTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
)

/** Tabular, monospaced digits for clocks so they don't jitter while counting. */
val ClockStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
