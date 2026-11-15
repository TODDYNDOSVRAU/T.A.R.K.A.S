package com.example.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.components.InsetGroup
import com.example.ui.screens.ThemeOptionRows
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.LocalSystemColors
import com.example.ui.theme.TalBlack
import com.example.ui.theme.TarkasDarkOverride
import com.example.ui.theme.TarkasIce
import com.example.ui.theme.TarkasTealDeep
import com.example.ui.theme.TarkasTheme
import com.example.ui.theme.systemColorsSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Material You: the phone's colours are a choice, off by default, and only offered where Android has them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h2400dp")
class SystemColorsUiTest {
  @get:Rule val rule = createComposeRule()

  private class Seen {
    var scheme: ColorScheme? = null
    var local = false
    var override: ColorScheme? = null
  }

  private fun themed(mode: String, systemColors: Boolean): Seen {
    val seen = Seen()
    rule.setContent {
      TarkasTheme(themeMode = mode, systemColors = systemColors) {
        val scheme = MaterialTheme.colorScheme
        val local = LocalSystemColors.current
        SideEffect { seen.scheme = scheme; seen.local = local }
        TarkasDarkOverride(enabled = true) {
          val inner = MaterialTheme.colorScheme
          SideEffect { seen.override = inner }
        }
      }
    }
    rule.waitForIdle()
    return seen
  }

  @Test fun supportStartsAtAndroid12() {
    assertFalse(systemColorsSupported(30))
    assertTrue(systemColorsSupported(31))
    assertTrue(systemColorsSupported(36))
  }

  @Test fun offKeepsTheTalColours() {
    val seen = themed("dark", systemColors = false)
    assertEquals(TarkasIce, seen.scheme!!.primary)
    assertEquals(TalBlack, seen.scheme!!.background)
    assertFalse(seen.local)
  }

  @Test fun offKeepsTheTalColoursInLight() {
    val seen = themed("light", systemColors = false)
    assertEquals(TarkasTealDeep, seen.scheme!!.primary)
    assertEquals(TarkasIce, seen.override!!.primary)
  }

  @Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
  @Test fun onTakesThePhoneColours() {
    val seen = themed("dark", systemColors = true)
    assertTrue(seen.local)
    assertNotEquals(TarkasIce, seen.scheme!!.primary)
    assertNotEquals(TalBlack, seen.scheme!!.background)
  }

  @Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
  @Test fun onInLightIsLightAndThePlayerBarOverrideIsDarkAndNotIce() {
    val seen = themed("light", systemColors = true)
    assertNotEquals(TarkasTealDeep, seen.scheme!!.primary)
    assertTrue(seen.scheme!!.background.luminance() > 0.5f)
    assertTrue(seen.override!!.background.luminance() < 0.5f)
    assertNotEquals(TarkasIce, seen.override!!.primary)
  }

  @Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
  @Test fun amoledStaysPureBlackWithThePhoneColours() {
    val seen = themed("amoled", systemColors = true)
    assertEquals(AmoledBlack, seen.scheme!!.background)
    assertEquals(TalBlack, seen.scheme!!.surface)
    assertNotEquals(TarkasIce, seen.scheme!!.primary)
  }

  @Config(sdk = [30], qualifiers = "en-w411dp-h2400dp")
  @Test fun onAndroid11TheChoiceHasNoEffect() {
    val seen = themed("dark", systemColors = true)
    assertEquals(TarkasIce, seen.scheme!!.primary)
    assertFalse(seen.local)
  }

  @Test fun theSwitchTogglesAndReportsTheChoice() {
    var on by mutableStateOf(false)
    val calls = mutableListOf<Boolean>()
    rule.setTarkasContent {
      InsetGroup {
        ThemeOptionRows("dark", {}, tagPrefix = "t_", systemColors = on, onSystemColors = { calls += it; on = it }, systemColorsAvailable = true)
      }
    }
    rule.settle()
    rule.onNodeWithTag("t_system_colors").assertIsOff().performClick()
    rule.settle()
    assertEquals(listOf(true), calls)
    rule.onNodeWithTag("t_system_colors").assertIsOn()
  }

  @Test fun theSwitchIsAbsentWhereAndroidHasNoPhoneColours() {
    rule.setTarkasContent {
      InsetGroup {
        ThemeOptionRows("dark", {}, tagPrefix = "t_", systemColors = false, onSystemColors = {}, systemColorsAvailable = false)
      }
    }
    rule.settle()
    assertFalse(rule.hasNodeWithTag("t_system_colors"))
    assertTrue(rule.hasNodeWithTag("t_dark"))
  }
}
