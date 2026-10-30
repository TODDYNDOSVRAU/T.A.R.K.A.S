package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * A variable font file declared once per weight, each pinned to that weight on the wght axis. Android 8
 * and newer render every weight from the one file; on Android 7 the axis is ignored and text shows at
 * the file's default (regular) weight.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(res: Int, vararg weights: FontWeight) = FontFamily(
  weights.map { w -> Font(res, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) }
)

// The T.A.L. typefaces (licences in docs/licenses/fonts).
/** Titles and identity. */
val SpaceGrotesk: FontFamily = variableFamily(R.font.space_grotesk, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
/** Interface and running text. */
val Inter: FontFamily = variableFamily(R.font.inter, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
/** Code, metrics and technical data. */
val JetBrainsMono: FontFamily = variableFamily(R.font.jetbrains_mono, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f, family: FontFamily = Inter) = TextStyle(
  fontFamily = family,
  fontWeight = weight,
  fontSize = size.sp,
  lineHeight = line.sp,
  letterSpacing = tracking.em
)

/**
 * The type scale, after Apple's Human Interface Guidelines (Dynamic Type, "Large" size). Every text in
 * the app uses one of these roles instead of a size of its own, so the hierarchy reads the same on
 * every screen and scales with the system font size. Large sizes are tracked slightly tighter, small
 * ones slightly looser, as the platform does.
 */
object AppText {
  /** Page titles: Início, Biblioteca, Áudio. */
  val LargeTitle = style(34, 41, FontWeight.Bold, -0.011f, SpaceGrotesk)
  /** The track title on the player, big numbers. */
  val Title1 = style(28, 34, FontWeight.Bold, -0.008f, SpaceGrotesk)
  /** Section titles inside a page. */
  val Title2 = style(22, 28, FontWeight.Bold, -0.005f, SpaceGrotesk)
  /** Sub-page titles, dialog titles. */
  val Title3 = style(20, 25, FontWeight.SemiBold, -0.003f, SpaceGrotesk)
  /** Row titles that need emphasis, buttons. */
  val Headline = style(17, 22, FontWeight.SemiBold, -0.002f)
  /** Row titles, running text. */
  val Body = style(17, 22, FontWeight.Normal, -0.002f)
  /** Secondary running text. */
  val Callout = style(16, 21, FontWeight.Normal)
  /** Row subtitles: artist, album. */
  val Subhead = style(15, 20, FontWeight.Normal)
  /** Section headers in grouped lists, notes under a group. */
  val Footnote = style(13, 18, FontWeight.Normal)
  /** Tab labels, small metadata. */
  val Caption1 = style(12, 16, FontWeight.Normal, 0.002f)
  /** Axis labels, the smallest text allowed. */
  val Caption2 = style(11, 13, FontWeight.Normal, 0.006f)
}

/** The same scale on Material's roles, for the components that read MaterialTheme.typography. */
val TarkasTypography = Typography(
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
