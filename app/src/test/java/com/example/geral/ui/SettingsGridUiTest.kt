package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.data.model.AudioCapabilities
import com.example.data.update.UpdateState
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.UpdateGateway
import com.example.ui.viewmodel.UpdateViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The T.A.L. grid in Ajustes, by measurement: 24 dp side margin, rows and controls 48 dp or more in
 * 12 dp steps, every block starting on the 12 dp rhythm, the three layer segments equal in width.
 */
abstract class SettingsGridUiBase {
  @get:Rule val rule = createComposeRule()

  private class IdleGateway : UpdateGateway {
    override val state: StateFlow<UpdateState> = MutableStateFlow(UpdateState())
    override val installsUpdates = true
    override val autoEnabled = true
    override val wifiOnly = true
    override var installPromptAsked = false
    override var dismissedTag: String? = null
    override fun canInstallPackages() = true
    override fun setAutoEnabled(on: Boolean) {}
    override fun setWifiOnly(on: Boolean) {}
    override fun cancelDownload() {}
    override suspend fun check(): UpdateState = state.value
    override suspend fun download(userInitiated: Boolean): UpdateState = state.value
    override suspend fun install(userInitiated: Boolean): UpdateState = state.value
  }

  private val owner = object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel(IdleGateway()) as T
    }
  }

  private val capabilities = AudioCapabilities(
    deviceModel = "Test", androidVersion = "16", apiLevel = 36, activeRouteName = "Speaker", activeRouteType = AudioCapabilities.ROUTE_BUILTIN_SPEAKER,
    sampleRateHz = 48000, channelCount = 2, channelFormat = "stereo", isAudioSessionActive = false, audioSessionId = 0,
    isHardwareEqualizerAvailable = false, isBassBoostAvailable = false, isVirtualizerAvailable = false, effectsCount = 0
  )

  private fun show() {
    rule.setTarkasContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        SettingsScreen(themeMode = "light", capabilities = capabilities, onThemeModeChange = {})
      }
    }
    rule.settle()
  }

  private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

  private fun near(a: Float, b: Float) = abs(a - b) <= 0.5f

  private fun onStep(value: Float, step: Int = 12) = near(value, (value / step).roundToInt() * step.toFloat())

  private val screenWidth = 411f

  @Test fun theSearchAndTheLayerSegmentsAreOnTheGrid() {
    show()
    val search = bounds("settings_search")
    check(near(search.height.value, 48f)) { "search height ${search.height}" }
    check(near(search.left.value, 24f)) { "search left ${search.left}" }
    check(near(search.width.value, screenWidth - 48f)) { "search width ${search.width}" }

    val segments = listOf("essential", "advanced", "engineering").map { bounds("settings_layer_$it") }
    segments.forEach { check(near(it.height.value, 48f)) { "segment height ${it.height}" } }
    check(segments.all { near(it.width.value, segments[0].width.value) }) { "segment widths ${segments.map { it.width }}" }
    // The control sits 12 dp under the search and its segments fill the column, 6 dp in from each end.
    check(near(segments[0].top.value - search.bottom.value, 12f)) { "gap under search ${segments[0].top - search.bottom}" }
    check(near(segments[0].left.value, 24f + 6f)) { "first segment left ${segments[0].left}" }
    check(near(segments[2].right.value, screenWidth - 24f - 6f)) { "last segment right ${segments[2].right}" }
  }

  @Test fun groupsKeepThe24DpMarginAndRowsStepBy12() {
    show()
    val search = bounds("settings_search")
    val card = bounds("settings_theme_card")
    // The tag sits outside the group's own 24 dp margin, so the margin is measured on the rows below.
    check(onStep(card.top.value - search.top.value)) { "group top off the rhythm: ${card.top - search.top}" }
    check(onStep(card.height.value)) { "group height ${card.height}" }

    val rows = listOf(
      "theme_chip_system", "theme_chip_light", "theme_chip_dark", "theme_chip_system_colors",
      "settings_reduce_transparency", "settings_reduce_motion", "settings_shuffle", "settings_open_audio",
      "settings_notifications", "settings_clear_cache", "settings_privacy", "settings_terms", "settings_about"
    ).filter { rule.hasNodeWithTag(it) }
    check(rows.size >= 8) { "too few rows measured: $rows" }
    for (tag in rows) {
      val b = bounds(tag)
      check(b.height.value >= 47.5f) { "$tag is ${b.height} tall" }
      check(onStep(b.height.value)) { "$tag height ${b.height} is not a multiple of 12" }
      check(onStep(b.top.value - search.top.value)) { "$tag starts off the rhythm: ${b.top - search.top}" }
      check(near(b.left.value, 24f)) { "$tag left ${b.left}" }
      check(near(b.width.value, screenWidth - 48f)) { "$tag width ${b.width}" }
    }
  }

  @Test fun theOtherLayersStayOnTheRhythm() {
    show()
    val search = bounds("settings_search")
    for ((layer, tag) in listOf("advanced" to "settings_waveform", "engineering" to "settings_diagnostics_export")) {
      rule.onNodeWithTag("settings_layer_$layer").performClick()
      rule.settle()
      val b = bounds(tag)
      check(b.height.value >= 47.5f && onStep(b.height.value)) { "$tag height ${b.height}" }
      check(onStep(b.top.value - search.top.value)) { "$tag starts off the rhythm: ${b.top - search.top}" }
      check(near(b.left.value, 24f)) { "$tag left ${b.left}" }
    }
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h2400dp")
class SettingsGridEnUiTest : SettingsGridUiBase()

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h2400dp")
class SettingsGridPtUiTest : SettingsGridUiBase()
