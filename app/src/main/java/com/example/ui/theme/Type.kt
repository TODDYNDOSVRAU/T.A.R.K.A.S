package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * One system sans family with real weights (the bundled single-weight files could not render bold).
 * The names are kept so older references compile.
 */
val SpaceGrotesk: FontFamily = FontFamily.SansSerif
val Inter: FontFamily = FontFamily.SansSerif

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
  fontFamily = FontFamily.SansSerif,
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
  val LargeTitle = style(34, 41, FontWeight.Bold, -0.011f)
  /** The track title on the player, big numbers. */
  val Title1 = style(28, 34, FontWeight.Bold, -0.008f)
  /** Section titles inside a page. */
  val Title2 = style(22, 28, FontWeight.Bold, -0.005f)
  /** Sub-page titles, dialog titles. */
  val Title3 = style(20, 25, FontWeight.SemiBold, -0.003f)
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
