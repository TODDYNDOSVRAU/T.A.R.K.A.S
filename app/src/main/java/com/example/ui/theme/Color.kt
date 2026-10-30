package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// T.A.L. base palette (C:\dev\TAL\identidade): black, white and gray. Every other neutral below is one
// of them laid over another at a given opacity.
val TalBlack = Color(0xFF080808) // Absolute Black
val TalWhite = Color(0xFFF2F2F0) // Precision White
val TalGray = Color(0xFF6B6B6B) // Technical Gray, on light
val TalGrayDark = Color(0xFF8C8C8C) // Technical Gray, on dark (6.0:1 on black)

// The accent is the product's own: T.A.R.K.A.S. ice, from its symbol. Ice on the dark themes; on the
// light theme the symbol's teal, deepened so it keeps 4.5:1 on every light surface (the symbol's
// #0E7C86 drops to 4.1:1 on the page).
val TarkasIce = Color(0xFF7EE3EA)
val TarkasTealDeep = Color(0xFF0D6C74)

// Dark: Precision White over Absolute Black at 5, 7, 9, 11 and 16 %.
val TalInkRaised = Color(0xFF141414)
val TalInkContainer = Color(0xFF181818)
val TalInkHigh = Color(0xFF1D1D1D)
val TalInkContainerHigh = Color(0xFF222222)
val TalInkLine = Color(0xFF2D2D2D)
// Ice over Absolute Black at 16 %: the wash behind a selected item.
val TarkasIceContainer = Color(0xFF1B2B2C)

// Light: Absolute Black over Precision White at 4, 5, 7, 8 and 13 %. The page sits one step darker
// than the cards, so grouped lists keep their shape.
val TalPaper = Color(0xFFE9E9E7)
val TalPaperContainer = Color(0xFFE6E6E4)
val TalPaperHigh = Color(0xFFE2E2E0)
val TalPaperContainerHigh = Color(0xFFDFDFDD)
val TalPaperLine = Color(0xFFD4D4D2)
// Absolute Black over Precision White at 62 %. Technical Gray drops to 4.4:1 on the darker page; this
// keeps secondary text at AA (5.1:1) on every light surface.
val TalGrayOnPaper = Color(0xFF616160)
// The deep teal over Precision White at 12 %: the wash behind a selected item on light.
val TarkasTealContainer = Color(0xFFD7E2E1)

// AMOLED: the page is pure black so the panel switches its pixels off. A functional exception to the
// palette; the cards above it are Absolute Black.
val AmoledBlack = Color(0xFF000000)

// Errors. The T.A.L. palette has no status colour, and an error must not depend on the wording alone,
// so these stay: coral on dark, a deeper red that keeps 4.5:1 on light.
val TalErrorDark = Color(0xFFFF6B6B)
val TalErrorLight = Color(0xFFC62828)
