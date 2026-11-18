package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S1: Ajustes in three layers with a search that looks in every layer. */
abstract class SettingsLayersUiBase(private val diagnosticsQuery: String) {
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

  /** The update group asks for its view model from the owner, so the test supplies one with no network. */
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

  private fun show(): ThemeSwitch = rule.setTarkasContent {
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
      SettingsScreen(themeMode = "light", capabilities = capabilities, onThemeModeChange = {})
    }
  }

  private fun exists(tag: String) = rule.hasNodeWithTag(tag)

  @Test fun startsOnEssentialAndHidesTheOtherLayers() {
    val theme = show()
    rule.settle()
    rule.forEachTheme(theme) {
      rule.onNodeWithTag("settings_theme_card").assertIsDisplayed()
      rule.onNodeWithTag("settings_layer_essential").assertIsDisplayed()
      check(!exists("settings_waveform")) { "advanced row shown on Essential" }
      check(!exists("settings_diagnostics_export")) { "engineering row shown on Essential" }
    }
  }

  @Test fun theLayerChipsSwitchTheGroups() {
    show()
    rule.settle()
    rule.onNodeWithTag("settings_layer_advanced").performClick()
    rule.settle()
    check(exists("settings_waveform")) { "advanced row missing on Advanced" }
    check(!exists("settings_theme_card")) { "essential group shown on Advanced" }
    rule.onNodeWithTag("settings_layer_engineering").performClick()
    rule.settle()
    check(exists("settings_diagnostics_export")) { "diagnostics missing on Engineering" }
    check(!exists("settings_waveform")) { "advanced row shown on Engineering" }
  }

  @Test fun aSearchFindsASettingInAnotherLayerAndEmptyingItGoesBack() {
    show()
    rule.settle()
    rule.onNode(hasSetTextAction()).performTextInput(diagnosticsQuery)
    rule.settle()
    check(exists("settings_diagnostics_export")) { "search did not reach Engineering" }
    check(!exists("settings_theme_card")) { "unrelated group stayed" }
    // Picking a layer empties the search and shows that layer again.
    rule.onNodeWithTag("settings_layer_essential").performClick()
    rule.settle()
    check(exists("settings_theme_card")) { "Essential did not come back" }
    check(!exists("settings_diagnostics_export")) { "search result stayed" }
  }

  @Test fun aSearchWithNoMatchSaysSo() {
    show()
    rule.settle()
    rule.onNode(hasSetTextAction()).performTextInput("zzzqq")
    rule.settle()
    rule.onNodeWithTag("settings_search_empty").assertIsDisplayed()
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h2400dp")
class SettingsLayersEnUiTest : SettingsLayersUiBase(diagnosticsQuery = "diagnostics")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h2400dp")
class SettingsLayersPtUiTest : SettingsLayersUiBase(diagnosticsQuery = "diagnostico")
