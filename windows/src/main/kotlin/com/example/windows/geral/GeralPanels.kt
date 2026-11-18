package com.example.windows.geral

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.shell.PanelContent
import com.example.windows.shell.Panels
import com.example.windows.state.TarkasState
import java.io.File

/** What the geral area gives the shell: the SETTINGS panel (with its sub-pages) and the start-up gate. */
class GeralPanels(val state: TarkasState, val services: GeralServices) {

  fun install() {
    Panels.slots[TarkasNavTab.SETTINGS] = PanelContent { s ->
      val page by s.settingsPage.collectAsState()
      val p = page
      if (p != null) LegalPage(p, s, services.geral) else SettingsScreen(s, services)
    }
  }

  /** Boot, then onboarding when needed, then [content]. [skipBoot] is true for a start with Windows. */
  @Composable
  fun Gate(skipBoot: Boolean = false, content: @Composable () -> Unit) {
    val reduce by state.reduceMotion.collectAsState()
    var booted by remember { mutableStateOf(skipBoot) }
    var onboarded by remember { mutableStateOf(!services.geral.needsOnboarding) }
    when {
      !booted -> BootScreen(reduce) { booted = true }
      !onboarded -> {
        val page by state.settingsPage.collectAsState()
        val p = page
        if (p != null) LegalPage(p, state, services.geral) else OnboardingScreen(state, services.geral) { onboarded = true }
      }
      else -> content()
    }
  }

  companion object {
    fun create(state: TarkasState, appVersion: String = Diagnostics.APP_VERSION): GeralPanels {
      val geral = GeralState(state)
      AppLanguage.apply(geral.language.value)
      val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "TARKAS/updates")
      return GeralPanels(state, GeralServices(geral, Updater(appVersion, dir), Autostart(), Diagnostics(), UserDataBackup(state.db)))
    }
  }
}
