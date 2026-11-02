package com.example.data.systemaudio

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.data.repository.PreferencesRepository

/**
 * The equalizer on the quick settings panel: shows whether it is on and turns it on or off. With the
 * app open the view model applies the change, so the screens and other apps follow; with it closed the
 * saved curve is changed, and the service for other apps picks it up.
 */
class EqTileService : TileService() {

  private fun currentlyOn(): Boolean =
    if (GlobalEq.appListening) GlobalEq.settings.value.enabled
    else PreferencesRepository(this).getEqSettings()?.enabled ?: true

  override fun onStartListening() {
    super.onStartListening()
    refresh(currentlyOn())
  }

  override fun onClick() {
    super.onClick()
    val on = !currentlyOn()
    android.util.Log.i("EqTileService", "Tile tapped: equalizer ${if (on) "on" else "off"} (app open: ${GlobalEq.appListening})")
    if (GlobalEq.appListening) {
      GlobalEq.enabledRequests.tryEmit(on)
    } else {
      val prefs = PreferencesRepository(this)
      val next = (prefs.getEqSettings() ?: com.example.data.model.EqSettings()).copy(enabled = on)
      prefs.saveEqSettings(next)
      GlobalEq.settings.value = next
    }
    refresh(on)
  }

  private fun refresh(on: Boolean) {
    val tile = qsTile ?: return
    tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    tile.label = "Equalizador"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (on) "Ligado" else "Desligado"
    tile.contentDescription = if (on) "Equalizador ligado" else "Equalizador desligado"
    tile.updateTile()
  }
}
