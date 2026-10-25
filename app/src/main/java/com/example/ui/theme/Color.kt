package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Identity palette (from the T.A.R.K.A.S. symbol): near-black ink, ice cyan, teal.
val TarkasInk = Color(0xFF07090B)
val TarkasInkRaised = Color(0xFF0E1114)
val TarkasInkHigh = Color(0xFF161A1E)
val TarkasInkLine = Color(0xFF232930)

val TarkasIce = Color(0xFF7EE3EA)
val TarkasIceContainer = Color(0xFF0C2B2F)
val TarkasIceText = Color(0xFFBDF4F7)

val TarkasTeal = Color(0xFF0E7C86)
val TarkasTealContainer = Color(0xFFD3EDEF)

val TarkasPaper = Color(0xFFF3F4F6)
val TarkasPaperRaised = Color(0xFFFFFFFF)
val TarkasPaperHigh = Color(0xFFE8EAED)
val TarkasPaperLine = Color(0xFFD5D9DE)

val TarkasSymbolLight = Color(0xFFE9EBED)
val TarkasSymbolDark = Color(0xFF0D1114)

// Amoled variants (pure black)
val AmoledBlack = Color(0xFF000000)
val AmoledSurface = TarkasInk
val AmoledSurfaceVariant = TarkasInkRaised
val AmoledCardBorder = TarkasInkLine

// Legacy accent names kept so existing screens keep compiling. Mid cyan reads on dark and light.
val AppleMusicPink = Color(0xFF46C9D3)
val AppleMusicPinkContainer = TarkasIceContainer
val AppleMusicCyan = TarkasIce
val AppleMusicCyanContainer = TarkasIceContainer
val AppleMusicPurple = TarkasTeal

// Standard dark palette
val TarkasBackgroundDark = TarkasInk
val TarkasSurfaceDark = TarkasInkRaised
val TarkasSurfaceVariantDark = TarkasInkHigh
val TarkasOutlineDark = TarkasInkLine

val TarkasCyanPrimary = TarkasIce
val TarkasCyanContainer = TarkasIceContainer
val TarkasCyanText = TarkasIceText

val TarkasSkySecondary = Color(0xFF5BC0DE)
val TarkasSecondaryContainer = Color(0xFF10303B)

val TarkasAmberTertiary = Color(0xFFF2B84B)
val TarkasAmberContainer = Color(0xFF3F2C07)

val TarkasTextPrimaryDark = TarkasSymbolLight
val TarkasTextSecondaryDark = Color(0xFF9AA3AB)
val TarkasTextMutedDark = Color(0xFF626B73)

val TarkasSuccess = Color(0xFF34C48B)
val TarkasWarning = Color(0xFFFBBF24)
val TarkasError = Color(0xFFFF6B6B)

// Light palette
val TarkasBackgroundLight = TarkasPaper
val TarkasSurfaceLight = TarkasPaperRaised
val TarkasSurfaceVariantLight = TarkasPaperHigh
val TarkasOutlineLight = TarkasPaperLine

val TarkasCyanPrimaryLight = TarkasTeal
val TarkasCyanContainerLight = TarkasTealContainer
val TarkasSkySecondaryLight = Color(0xFF1B6F8F)
val TarkasAmberTertiaryLight = Color(0xFFB7791F)

val TarkasTextPrimaryLight = TarkasSymbolDark
val TarkasTextSecondaryLight = Color(0xFF515A63)
