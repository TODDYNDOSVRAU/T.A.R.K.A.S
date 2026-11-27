package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance

// Desktop twin of geral/ui/theme/Theme.kt (win-base). The colour schemes below are the Android ones,
// line for line; ThemeParityTest fails when they drift. Windows has no Material You: the phone-colours
// switch exists in the API but is always off, and the T.A.L. colours always apply.

// Dark themes: T.A.L. base (Precision White and Technical Gray on Absolute Black) with the T.A.R.K.A.S.
// ice as the accent: selected tab, switch on, the playing track, the main action.
internal val TarkasAmoledColorScheme = darkColorScheme(
  primary = TarkasIce,
  onPrimary = TalBlack,
  primaryContainer = TarkasIceContainer,
  onPrimaryContainer = TarkasIce,
  secondary = TalGrayDark,
  onSecondary = TalBlack,
  secondaryContainer = TalInkHigh,
  onSecondaryContainer = TalWhite,
  tertiary = TarkasIce,
  onTertiary = TalBlack,
  tertiaryContainer = TarkasIceContainer,
  onTertiaryContainer = TarkasIce,
  background = AmoledBlack,
  onBackground = TalWhite,
  surface = TalBlack,
  onSurface = TalWhite,
  surfaceVariant = TalInkRaised,
  onSurfaceVariant = TalGrayDark,
  outline = TalInkLine,
  outlineVariant = TalInkHigh,
  surfaceContainer = TalInkRaised,
  surfaceContainerHigh = TalInkHigh,
  // Every slot is set: an unset one falls back to Material's violet baseline (the lilac text field and chip).
  surfaceContainerHighest = TalInkContainerHigh,
  surfaceContainerLow = TalBlack,
  surfaceContainerLowest = AmoledBlack,
  surfaceBright = TalInkContainerHigh,
  surfaceDim = AmoledBlack,
  surfaceTint = TalWhite,
  inverseSurface = TalWhite,
  inverseOnSurface = TalBlack,
  inversePrimary = TarkasTealDeep,
  scrim = AmoledBlack,
  error = TalErrorDark,
  onError = TalBlack
)

internal val TarkasDarkColorScheme = darkColorScheme(
  primary = TarkasIce,
  onPrimary = TalBlack,
  primaryContainer = TarkasIceContainer,
  onPrimaryContainer = TarkasIce,
  secondary = TalGrayDark,
  onSecondary = TalBlack,
  secondaryContainer = TalInkHigh,
  onSecondaryContainer = TalWhite,
  tertiary = TarkasIce,
  onTertiary = TalBlack,
  tertiaryContainer = TarkasIceContainer,
  onTertiaryContainer = TarkasIce,
  background = TalBlack,
  onBackground = TalWhite,
  surface = TalInkRaised,
  onSurface = TalWhite,
  surfaceVariant = TalInkHigh,
  onSurfaceVariant = TalGrayDark,
  outline = TalInkLine,
  outlineVariant = TalInkHigh,
  surfaceContainer = TalInkContainer,
  surfaceContainerHigh = TalInkContainerHigh,
  surfaceContainerHighest = TalInkContainerHigh,
  surfaceContainerLow = TalInkRaised,
  surfaceContainerLowest = TalBlack,
  surfaceBright = TalInkContainerHigh,
  surfaceDim = TalBlack,
  surfaceTint = TalWhite,
  inverseSurface = TalWhite,
  inverseOnSurface = TalBlack,
  inversePrimary = TarkasTealDeep,
  scrim = TalBlack,
  error = TalErrorDark,
  onError = TalBlack
)

// Light theme: the same base inverted, with the deep teal as the accent.
internal val TarkasLightColorScheme = lightColorScheme(
  primary = TarkasTealDeep,
  onPrimary = TalWhite,
  primaryContainer = TarkasTealContainer,
  onPrimaryContainer = TarkasTealDeep,
  secondary = TalGrayOnPaper,
  onSecondary = TalWhite,
  secondaryContainer = TalPaperHigh,
  onSecondaryContainer = TalBlack,
  tertiary = TarkasTealDeep,
  onTertiary = TalWhite,
  tertiaryContainer = TarkasTealContainer,
  onTertiaryContainer = TarkasTealDeep,
  background = TalPaper,
  onBackground = TalBlack,
  surface = TalWhite,
  onSurface = TalBlack,
  surfaceVariant = TalPaperHigh,
  onSurfaceVariant = TalGrayOnPaper,
  outline = TalPaperLine,
  outlineVariant = TalPaperHigh,
  surfaceContainer = TalPaperContainer,
  surfaceContainerHigh = TalPaperContainerHigh,
  surfaceContainerHighest = TalPaperContainerHigh,
  surfaceContainerLow = TalPaper,
  surfaceContainerLowest = TalWhite,
  surfaceBright = TalWhite,
  surfaceDim = TalPaperContainerHigh,
  surfaceTint = TalBlack,
  inverseSurface = TalBlack,
  inverseOnSurface = TalWhite,
  inversePrimary = TarkasIce,
  scrim = TalBlack,
  error = TalErrorLight,
  onError = TalWhite
)
/** Android's wallpaper palette has no Windows equivalent: always false. */
fun systemColorsSupported(): Boolean = false

val LocalSystemColors = staticCompositionLocalOf { false }

/** The AMOLED variant of a dark scheme: pure black behind, Absolute Black surfaces, the accent untouched. */
internal fun ColorScheme.asAmoled(): ColorScheme = copy(background = AmoledBlack, surface = TalBlack)

/** [systemColors] is accepted for API parity with Android and ignored. */
@Composable
fun TarkasTheme(
  themeMode: String = "system",
  systemColors: Boolean = false,
  content: @Composable () -> Unit
) {
  val systemDark = isSystemInDarkTheme()
  val colorScheme = when {
    ThemeMode.isAmoled(themeMode) -> TarkasAmoledColorScheme
    ThemeMode.isDark(themeMode) { systemDark } -> TarkasDarkColorScheme
    else -> TarkasLightColorScheme
  }
  CompositionLocalProvider(LocalSystemColors provides false) {
    MaterialTheme(colorScheme = colorScheme, typography = TarkasTypography, shapes = TarkasMaterialShapes, content = content)
  }
}

/** Material's own shapes on the app's radius scale. */
val TarkasMaterialShapes = androidx.compose.material3.Shapes(
  extraSmall = Shapes.xs,
  small = Shapes.s,
  medium = Shapes.m,
  large = Shapes.l,
  extraLarge = Shapes.xl
)

/** Forces the dark scheme on [content] when [enabled] (the player page is always dark). */
@Composable
fun TarkasDarkOverride(enabled: Boolean, content: @Composable () -> Unit) {
  if (!enabled || MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    content()
  } else {
    MaterialTheme(colorScheme = TarkasDarkColorScheme, typography = TarkasTypography, shapes = TarkasMaterialShapes, content = content)
  }
}
