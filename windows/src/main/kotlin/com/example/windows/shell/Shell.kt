package com.example.windows.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.base.ProvidePlatform
import com.example.windows.base.ToastHost
import com.example.windows.state.TarkasState
import org.jetbrains.compose.resources.stringResource
import com.example.ui.theme.TarkasTheme

/**
 * What an area session provides for one tab. The shell asks [content] for the tab being shown. Until an area
 * registers its screen, the shell draws [Pending] for it.
 */
fun interface PanelContent {
  @Composable fun Show(state: TarkasState)
}

/** One slot per tab. An area session replaces its slot in `Panels.kt` (e.g. `AUDIO -> equalizador.AudioPanel`). */
object Panels {
  val slots: MutableMap<TarkasNavTab, PanelContent> = mutableMapOf()
}

/** Side navigation of 192 dp (2x chain) plus the panel of the current tab, wrapped in the T.A.L. theme. */
@Composable
fun TarkasShell(state: TarkasState, back: BackDispatcher, lifecycle: AppLifecycle) {
  val mode by state.themeMode.collectAsState()
  TarkasTheme(themeMode = mode) {
    ProvidePlatform(state.toaster, back, lifecycle) {
      val c = MaterialTheme.colorScheme
      val tab by state.currentTab.collectAsState()
      Box(Modifier.fillMaxSize().background(c.background)) {
        Row(Modifier.fillMaxSize()) {
          SideNav(state, tab)
          Box(Modifier.fillMaxHeight().weight(1f)) {
            val panel = Panels.slots[tab]
            if (panel != null) panel.Show(state) else Pending(tab)
          }
        }
        ToastHost(state.toaster)
      }
    }
  }
}

@Composable
private fun SideNav(state: TarkasState, current: TarkasNavTab) {
  val c = MaterialTheme.colorScheme
  Column(
    Modifier.width(Grid.u192).fillMaxHeight().background(c.surface).padding(Grid.inset),
  ) {
    Text("T.A.R.K.A.S.", style = GridText.Title, color = c.onSurface, modifier = Modifier.height(Grid.u48).padding(horizontal = Grid.inset))
    TarkasNavTab.entries.forEach { tab ->
      val selected = tab == current
      Row(
        Modifier.fillMaxWidth().height(Grid.u48).clip(Grid.r12)
          .background(if (selected) c.surfaceContainerHigh else c.surface)
          .clickable { state.selectTab(tab) }
          .padding(horizontal = Grid.inset),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          stringResource(tab.label), style = if (selected) GridText.BodyStrong else GridText.Body,
          color = if (selected) c.primary else c.onSurfaceVariant
        )
      }
    }
  }
}

/** Honest placeholder for an area that has not registered its screen yet. No fake data. */
@Composable
private fun Pending(tab: TarkasNavTab) {
  val c = MaterialTheme.colorScheme
  Box(Modifier.fillMaxSize().padding(Grid.margin), contentAlignment = Alignment.Center) {
    Text("${stringResource(tab.label)}: port pending", style = GridText.Body, color = c.onSurfaceVariant)
  }
}
