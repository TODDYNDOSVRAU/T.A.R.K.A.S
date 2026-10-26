package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver

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

/**
 * The accent the user picks in Ajustes (and in the first-run setup). Each has a light tone for the
 * dark themes and a deeper one that keeps contrast on the light theme. [WALLPAPER] takes the
 * system's colour (Material You), on Android 12 and newer.
 */
enum class AccentColor(val key: String, val label: String, val dark: Color, val light: Color) {
  ICE("ice", "Gelo", TarkasIce, TarkasCyanPrimaryLight),
  TEAL("teal", "Verde-água", Color(0xFF5FD4B5), Color(0xFF00796B)),
  AMBER("amber", "Âmbar", Color(0xFFF2B84B), Color(0xFF9A5B00)),
  LILAC("lilac", "Lilás", Color(0xFFC3A6FF), Color(0xFF6741C4)),
  WALLPAPER("wallpaper", "Do sistema", TarkasIce, TarkasCyanPrimaryLight);

  companion object {
    fun fromKey(key: String?): AccentColor = entries.firstOrNull { it.key == key } ?: ICE
    /** Material You needs Android 12. */
    val wallpaperSupported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 31
  }
}

@Composable
fun TarkasTheme(
  themeMode: String = "system",
  accent: AccentColor = AccentColor.ICE,
  content: @Composable () -> Unit
) {
  val isDark = when (themeMode) {
    "amoled" -> true
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }

  val base = when {
    themeMode == "amoled" -> TarkasAmoledColorScheme
    isDark -> TarkasDarkColorScheme
    else -> TarkasLightColorScheme
  }
  val context = androidx.compose.ui.platform.LocalContext.current
  val primary = when {
    accent == AccentColor.ICE -> null
    accent == AccentColor.WALLPAPER && AccentColor.wallpaperSupported ->
      if (isDark) androidx.compose.material3.dynamicDarkColorScheme(context).primary
      else androidx.compose.material3.dynamicLightColorScheme(context).primary
    accent == AccentColor.WALLPAPER -> null
    isDark -> accent.dark
    else -> accent.light
  }
  val colorScheme = if (primary == null) base else base.copy(
    primary = primary,
    // Text on the accent: dark ink on the light accents of the dark themes, white on the deep ones.
    onPrimary = if (primary.luminance() > 0.45f) TarkasSymbolDark else Color.White,
    primaryContainer = primary.copy(alpha = 0.24f).compositeOver(base.surface),
    onPrimaryContainer = if (isDark) primary else base.onSurface
  )

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
