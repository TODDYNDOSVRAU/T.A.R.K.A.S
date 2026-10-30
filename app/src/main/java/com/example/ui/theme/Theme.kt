package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance

// Dark themes: T.A.L. base (Precision White and Technical Gray on Absolute Black) with the T.A.R.K.A.S.
// ice as the accent: selected tab, switch on, the playing track, the main action.
private val TarkasAmoledColorScheme = darkColorScheme(
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
  error = TalErrorDark,
  onError = TalBlack
)

private val TarkasDarkColorScheme = darkColorScheme(
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
  error = TalErrorDark,
  onError = TalBlack
)

// Light theme: the same base inverted, with the deep teal as the accent.
private val TarkasLightColorScheme = lightColorScheme(
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
  error = TalErrorLight,
  onError = TalWhite
)

@Composable
fun TarkasTheme(
  themeMode: String = "system",
  content: @Composable () -> Unit
) {
  val isDark = when (themeMode) {
    "amoled" -> true
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }

  val colorScheme = when {
    themeMode == "amoled" -> TarkasAmoledColorScheme
    isDark -> TarkasDarkColorScheme
    else -> TarkasLightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = TarkasTypography,
    content = content
  )
}

/**
 * Forces the dark scheme on [content] when [enabled]. The player page is always dark, so the floating
 * bar over it must be dark too, even in the light theme; otherwise its grey labels sit on grey glass.
 */
@Composable
fun TarkasDarkOverride(enabled: Boolean, content: @Composable () -> Unit) {
  if (!enabled || MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    content()
  } else {
    MaterialTheme(colorScheme = TarkasDarkColorScheme, typography = TarkasTypography, content = content)
  }
}
