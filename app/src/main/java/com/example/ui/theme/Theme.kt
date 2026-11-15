package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
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

/** Android's own palette (Material You) exists from Android 12 (API 31) on. */
fun systemColorsSupported(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= Build.VERSION_CODES.S

/** True when the user chose the phone's colours and this Android has them; false means the T.A.L. colours. */
val LocalSystemColors = staticCompositionLocalOf { false }

/** The AMOLED variant of a dark scheme: pure black behind, Absolute Black surfaces, the accent untouched. */
internal fun ColorScheme.asAmoled(): ColorScheme = copy(background = AmoledBlack, surface = TalBlack)

/**
 * [systemColors] off (the default) is the T.A.L. identity. On, and only from Android 12, the scheme comes
 * from the phone's wallpaper colours; light, dark and AMOLED still follow [themeMode].
 */
@Composable
fun TarkasTheme(
  themeMode: String = "system",
  systemColors: Boolean = false,
  content: @Composable () -> Unit
) {
  val systemDark = isSystemInDarkTheme()
  val isDark = ThemeMode.isDark(themeMode) { systemDark }
  val amoled = ThemeMode.isAmoled(themeMode)

  val colorScheme = if (systemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val context = LocalContext.current
    when {
      amoled -> dynamicDarkColorScheme(context).asAmoled()
      isDark -> dynamicDarkColorScheme(context)
      else -> dynamicLightColorScheme(context)
    }
  } else when {
    amoled -> TarkasAmoledColorScheme
    isDark -> TarkasDarkColorScheme
    else -> TarkasLightColorScheme
  }

  CompositionLocalProvider(LocalSystemColors provides (systemColors && systemColorsSupported())) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = TarkasTypography,
      shapes = TarkasMaterialShapes,
      content = content
    )
  }
}

/** Material's own shapes (dialogs, sheets, menus, chips) on the app's radius scale. */
val TarkasMaterialShapes = androidx.compose.material3.Shapes(
  extraSmall = com.example.ui.theme.Shapes.xs,
  small = com.example.ui.theme.Shapes.s,
  medium = com.example.ui.theme.Shapes.m,
  large = com.example.ui.theme.Shapes.l,
  extraLarge = com.example.ui.theme.Shapes.xl
)

/**
 * Forces the dark scheme on [content] when [enabled]. The player page is always dark, so the floating
 * bar over it must be dark too, even in the light theme; otherwise its grey labels sit on grey glass.
 */
@Composable
fun TarkasDarkOverride(enabled: Boolean, content: @Composable () -> Unit) {
  if (!enabled || MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    content()
  } else {
    val dark = if (LocalSystemColors.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      dynamicDarkColorScheme(LocalContext.current)
    } else {
      TarkasDarkColorScheme
    }
    MaterialTheme(colorScheme = dark, typography = TarkasTypography, shapes = TarkasMaterialShapes, content = content)
  }
}
