package com.example.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.data.model.CuriosityConfig
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.SurfaceStyle
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The T.A.L. grid in the first-run screens, in every theme: the main button sits on the 24 dp margin and is 48 dp tall. */
abstract class OnboardingGridUiBase {
  @get:Rule val rule = createComposeRule()

  private fun near(a: Float, b: Float) = abs(a - b) <= 0.5f

  @Test fun theMainButtonIsOnTheGridInEveryTheme() {
    val switch = rule.setTarkasContent {
      OnboardingScreen(
        onlyTerms = false, themeMode = "light", onThemeMode = {}, surfaceStyle = SurfaceStyle.SOLID, onSurfaceStyle = {},
        reduceMotion = false, onReduceMotion = {}, curiosityConfig = CuriosityConfig(), onCuriosityConfig = {},
        onAcceptTerms = {}, onFinish = {}
      )
    }
    rule.settle()
    rule.forEachTheme(switch) { mode ->
      val b = rule.onNodeWithTag("onboarding_next").getUnclippedBoundsInRoot()
      check(near(b.left.value, 24f)) { "$mode: button left ${b.left}" }
      check(near(b.width.value, 411f - 48f)) { "$mode: button width ${b.width}" }
      check(b.height.value >= 47.5f && near(b.height.value % 12f, 0f)) { "$mode: button height ${b.height}" }
    }
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h2400dp")
class OnboardingGridEnUiTest : OnboardingGridUiBase()

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h2400dp")
class OnboardingGridPtUiTest : OnboardingGridUiBase()
