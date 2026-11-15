package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.repository.PreferencesRepository
import com.example.ui.theme.AppMotion
import com.example.ui.theme.ListAnimation
import com.example.ui.theme.SurfaceStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the appearance and behaviour switches of Ajustes read and write, so the store and its tests don't need Android. */
interface SettingsPrefs {
  fun themeMode(): String
  fun saveThemeMode(mode: String)
  fun surfaceStyle(): String
  fun saveSurfaceStyle(key: String)
  fun reduceMotion(): Boolean
  fun saveReduceMotion(reduce: Boolean)
  fun listAnimation(): String?
  fun saveListAnimation(key: String)
  fun reduceTransparency(): Boolean
  fun saveReduceTransparency(reduce: Boolean)
  fun homeMusicFirst(): Boolean
  fun saveHomeMusicFirst(on: Boolean)
}

/** The real preferences behind [SettingsPrefs]. */
class PreferencesSettings(private val p: PreferencesRepository) : SettingsPrefs {
  override fun themeMode() = p.getThemeMode()
  override fun saveThemeMode(mode: String) = p.saveThemeMode(mode)
  override fun surfaceStyle() = p.getSurfaceStyle()
  override fun saveSurfaceStyle(key: String) = p.saveSurfaceStyle(key)
  override fun reduceMotion() = p.getReduceMotion()
  override fun saveReduceMotion(reduce: Boolean) = p.saveReduceMotion(reduce)
  override fun listAnimation() = p.getListAnimation()
  override fun saveListAnimation(key: String) = p.saveListAnimation(key)
  override fun reduceTransparency() = p.getReduceTransparency()
  override fun saveReduceTransparency(reduce: Boolean) = p.saveReduceTransparency(reduce)
  override fun homeMusicFirst() = p.getHomeMusicFirst()
  override fun saveHomeMusicFirst(on: Boolean) = p.saveHomeMusicFirst(on)
}

/**
 * The one owner of these switches. The app's root view model and [SettingsViewModel] both read it, so the
 * value the theme uses and the value the switch shows can never differ.
 */
class SettingsStore(private val prefs: SettingsPrefs) {
  private val _themeMode = MutableStateFlow(prefs.themeMode())
  val themeMode: StateFlow<String> = _themeMode.asStateFlow()

  private val _surfaceStyle = MutableStateFlow(SurfaceStyle.fromKey(prefs.surfaceStyle()))
  val surfaceStyle: StateFlow<SurfaceStyle> = _surfaceStyle.asStateFlow()

  private val _reduceMotion = MutableStateFlow(prefs.reduceMotion().also { AppMotion.userReduced = it })
  val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

  private val _reduceTransparency = MutableStateFlow(prefs.reduceTransparency())
  val reduceTransparency: StateFlow<Boolean> = _reduceTransparency.asStateFlow()

  private val _homeMusicFirst = MutableStateFlow(prefs.homeMusicFirst())
  /** Início: the user's own music before (true) or after (false) the online catalogue rows. */
  val homeMusicFirst: StateFlow<Boolean> = _homeMusicFirst.asStateFlow()

  init {
    AppMotion.listAnimation = ListAnimation.fromKey(prefs.listAnimation())
  }

  fun setThemeMode(mode: String) {
    _themeMode.value = mode
    prefs.saveThemeMode(mode)
  }

  fun setSurfaceStyle(style: SurfaceStyle) {
    _surfaceStyle.value = style
    prefs.saveSurfaceStyle(style.key)
  }

  fun setReduceMotion(reduce: Boolean) {
    _reduceMotion.value = reduce
    AppMotion.userReduced = reduce
    prefs.saveReduceMotion(reduce)
  }

  fun setReduceTransparency(reduce: Boolean) {
    _reduceTransparency.value = reduce
    prefs.saveReduceTransparency(reduce)
  }

  fun setHomeMusicFirst(on: Boolean) {
    _homeMusicFirst.value = on
    prefs.saveHomeMusicFirst(on)
  }

  fun setListAnimation(style: ListAnimation) {
    AppMotion.listAnimation = style
    prefs.saveListAnimation(style.key)
  }
}

/** The state and actions of the appearance and behaviour switches in Ajustes. */
class SettingsViewModel(private val store: SettingsStore = com.example.AppGraph.settings) : ViewModel() {
  val themeMode: StateFlow<String> = store.themeMode
  val surfaceStyle: StateFlow<SurfaceStyle> = store.surfaceStyle
  val reduceMotion: StateFlow<Boolean> = store.reduceMotion
  val reduceTransparency: StateFlow<Boolean> = store.reduceTransparency
  val homeMusicFirst: StateFlow<Boolean> = store.homeMusicFirst

  fun setThemeMode(mode: String) = store.setThemeMode(mode)
  fun setSurfaceStyle(style: SurfaceStyle) = store.setSurfaceStyle(style)
  fun setReduceMotion(reduce: Boolean) = store.setReduceMotion(reduce)
  fun setReduceTransparency(reduce: Boolean) = store.setReduceTransparency(reduce)
  fun setHomeMusicFirst(on: Boolean) = store.setHomeMusicFirst(on)
  fun setListAnimation(style: ListAnimation) = store.setListAnimation(style)
}
