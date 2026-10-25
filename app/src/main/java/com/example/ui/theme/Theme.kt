package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val TarkasAmoledColorScheme = darkColorScheme(
  primary = TarkasIce,
  onPrimary = TarkasSymbolDark,
  primaryContainer = TarkasIceContainer,
  onPrimaryContainer = TarkasIceText,
  secondary = TarkasSkySecondary,
  onSecondary = TarkasSymbolDark,
  secondaryContainer = TarkasSecondaryContainer,
  onSecondaryContainer = Color(0xFFCBEFFA),
  tertiary = TarkasAmberTertiary,
  onTertiary = Color(0xFF452B00),
  tertiaryContainer = TarkasAmberContainer,
  onTertiaryContainer = Color(0xFFFFDDB3),
  background = AmoledBlack,
  onBackground = Color.White,
  surface = AmoledSurface,
  onSurface = Color.White,
  surfaceVariant = AmoledSurfaceVariant,
  onSurfaceVariant = Color(0xFFA0A0A5),
  outline = AmoledCardBorder,
  error = TarkasError,
  onError = Color.White
)

private val TarkasDarkColorScheme = darkColorScheme(
  primary = TarkasCyanPrimary,
  onPrimary = TarkasSymbolDark,
  primaryContainer = TarkasCyanContainer,
  onPrimaryContainer = TarkasCyanText,
  secondary = TarkasSkySecondary,
  onSecondary = TarkasSymbolDark,
  secondaryContainer = TarkasSecondaryContainer,
  onSecondaryContainer = Color(0xFFCBE6FF),
  tertiary = TarkasAmberTertiary,
  onTertiary = Color(0xFF452B00),
  tertiaryContainer = TarkasAmberContainer,
  onTertiaryContainer = Color(0xFFFFDDB3),
  background = TarkasBackgroundDark,
  onBackground = TarkasTextPrimaryDark,
  surface = TarkasSurfaceDark,
  onSurface = TarkasTextPrimaryDark,
  surfaceVariant = TarkasSurfaceVariantDark,
  onSurfaceVariant = TarkasTextSecondaryDark,
  outline = TarkasOutlineDark,
  error = TarkasError,
  onError = Color.White
)

private val TarkasLightColorScheme = lightColorScheme(
  primary = TarkasCyanPrimaryLight,
  onPrimary = Color.White,
  primaryContainer = TarkasCyanContainerLight,
  onPrimaryContainer = Color(0xFF053A40),
  secondary = TarkasSkySecondaryLight,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFD9ECF3),
  onSecondaryContainer = Color(0xFF0D3F52),
  tertiary = TarkasAmberTertiaryLight,
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFEF3C7),
  onTertiaryContainer = Color(0xFF92400E),
  background = TarkasBackgroundLight,
  onBackground = TarkasTextPrimaryLight,
  surface = TarkasSurfaceLight,
  onSurface = TarkasTextPrimaryLight,
  surfaceVariant = TarkasSurfaceVariantLight,
  onSurfaceVariant = TarkasTextSecondaryLight,
  outline = TarkasOutlineLight,
  error = TarkasError,
  onError = Color.White
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
