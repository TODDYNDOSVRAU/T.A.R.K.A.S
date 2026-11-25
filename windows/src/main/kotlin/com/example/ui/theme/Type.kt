package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.windows.res.Res
import kotlinx.coroutines.runBlocking

// Desktop twin of geral/ui/theme/Type.kt: same names and scale (win-base). The font files come from
// composeResources/font (generated from app/src/main/res/font), read once from the classpath.
private fun fontFamily(file: String, vararg weights: FontWeight): FontFamily {
  val bytes = runBlocking { Res.readBytes("font/$file.ttf") }
  return FontFamily(weights.map { w -> androidx.compose.ui.text.platform.Font(identity = "$file-${w.weight}", data = bytes, weight = w) as Font })
}

/** Titles and identity. */
val SpaceGrotesk: FontFamily by lazy { fontFamily("space_grotesk", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold) }
/** Interface and running text. */
val Inter: FontFamily by lazy { fontFamily("inter", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold) }
/** Code, metrics and technical data. */
val JetBrainsMono: FontFamily by lazy { fontFamily("jetbrains_mono", FontWeight.Normal, FontWeight.Medium, FontWeight.Bold) }

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f, family: () -> FontFamily = { Inter }) = TextStyle(
  fontFamily = family(),
  fontWeight = weight,
  fontSize = size.sp,
  lineHeight = line.sp,
  letterSpacing = tracking.em
)

object AppText {
  val LargeTitle = style(34, 41, FontWeight.Bold, -0.011f, { SpaceGrotesk })
  val Title1 = style(28, 34, FontWeight.Bold, -0.008f, { SpaceGrotesk })
  val Title2 = style(22, 28, FontWeight.Bold, -0.005f, { SpaceGrotesk })
  val Title3 = style(20, 25, FontWeight.SemiBold, -0.003f, { SpaceGrotesk })
  val Headline = style(17, 22, FontWeight.SemiBold, -0.002f)
  val Body = style(17, 22, FontWeight.Normal, -0.002f)
  val Callout = style(16, 21, FontWeight.Normal)
  val Subhead = style(15, 20, FontWeight.Normal)
  val Footnote = style(13, 18, FontWeight.Normal)
  val Caption1 = style(12, 16, FontWeight.Normal, 0.002f)
  val Caption2 = style(11, 13, FontWeight.Normal, 0.006f)
}

/** The three text sizes of the T.A.L. grid: 24, 18 and 12 sp (line heights are multiples of 6). */
object GridText {
  val Title = style(28, 34, FontWeight.Bold, -0.025f, { SpaceGrotesk })
  val Body = style(18, 24, FontWeight.Normal)
  val BodyStrong = style(18, 24, FontWeight.SemiBold)
  val Small = style(12, 18, FontWeight.Normal)
  val Label = style(12, 18, FontWeight.Normal, 0f, { JetBrainsMono })
}

val TarkasTypography: Typography by lazy {
  Typography(
    displayLarge = AppText.LargeTitle,
    displayMedium = AppText.Title1,
    displaySmall = AppText.Title2,
    headlineLarge = AppText.Title1,
    headlineMedium = AppText.Title2,
    headlineSmall = AppText.Title3,
    titleLarge = AppText.Title3,
    titleMedium = AppText.Headline,
    titleSmall = AppText.Subhead.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = AppText.Body,
    bodyMedium = AppText.Subhead,
    bodySmall = AppText.Footnote,
    labelLarge = AppText.Headline.copy(fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = AppText.Caption1.copy(fontWeight = FontWeight.Medium),
    labelSmall = AppText.Caption2.copy(fontWeight = FontWeight.Medium)
  )
}
