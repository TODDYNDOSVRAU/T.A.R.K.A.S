package com.example.windows.shell

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner rule (2026-11-18): the cover backdrop sits under a veil that keeps text contrast ≥ 4.5:1 on any cover. */
class BackdropTest {
  private val black = Color(0xFF080808); private val white = Color(0xFFF2F2F0)
  private val grayDark = Color(0xFF8C8C8C); private val grayLight = Color(0xFF6B6B6B)

  @Test fun darkThemeTextReadableOverWhiteCover() {
    assertTrue(ShellBackdrop.worstContrast(white, black) >= 4.5)
    assertTrue(ShellBackdrop.worstContrast(grayDark, black) >= 3.0) // secondary text: large/UI threshold
  }

  @Test fun lightThemeTextReadableOverBlackCover() {
    assertTrue(ShellBackdrop.worstContrast(black, white) >= 4.5)
    assertTrue(ShellBackdrop.worstContrast(grayLight, white) >= 3.0)
  }
}
