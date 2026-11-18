package com.example.windows.geral

import com.example.ui.legal.LegalTexts
import com.example.windows.state.TarkasState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** The three layers of the settings (T.A.L. product layers). */
enum class SettingsLayer { ESSENTIAL, ADVANCED, ENGINEERING }

/** The language the app shows: follows the system until the person picks one. */
object AppLanguage {
  const val SYSTEM = "system"
  const val PT = "pt"
  const val EN = "en"
  private val systemLocale: Locale = Locale.getDefault()

  /** The locale for a stored choice; unknown values fall back to the system's. */
  fun localeFor(choice: String): Locale = when (choice) {
    PT -> Locale.forLanguageTag("pt-BR")
    EN -> Locale.ENGLISH
    else -> systemLocale
  }

  fun apply(choice: String) { Locale.setDefault(localeFor(choice)) }
}

/**
 * Settings that belong to the Windows app and the geral area, on the same Room `settings` table as everything
 * else (scope `global`). State classes of an area take [TarkasState] as a parameter; they do not grow it.
 */
class GeralState(val app: TarkasState) {
  private val store get() = app.settings

  private fun bool(key: String, default: Boolean = false) = store.get(TarkasState.GLOBAL, key)?.let { it == "true" } ?: default
  private fun put(key: String, value: String) = store.put(TarkasState.GLOBAL, key, value)

  private val _language = MutableStateFlow(store.get(TarkasState.GLOBAL, KEY_LANGUAGE) ?: AppLanguage.SYSTEM)
  val language: StateFlow<String> = _language.asStateFlow()
  fun setLanguage(choice: String) { _language.value = choice; put(KEY_LANGUAGE, choice); AppLanguage.apply(choice) }

  private val _layer = MutableStateFlow(
    runCatching { SettingsLayer.valueOf(store.get(TarkasState.GLOBAL, KEY_LAYER) ?: "") }.getOrDefault(SettingsLayer.ESSENTIAL)
  )
  val layer: StateFlow<SettingsLayer> = _layer.asStateFlow()
  fun setLayer(l: SettingsLayer) { _layer.value = l; put(KEY_LAYER, l.name) }

  // First run: boot, onboarding, terms. Terms are asked again when LegalTexts.TERMS_VERSION goes up (LGPD art. 9 par. 2).
  val termsVersion: Int get() = store.get(TarkasState.GLOBAL, KEY_TERMS_VERSION)?.toIntOrNull() ?: 0
  val termsAcceptedAt: String? get() = store.get(TarkasState.GLOBAL, KEY_TERMS_AT)
  val needsOnboarding: Boolean get() = !bool(KEY_ONBOARDED) || termsVersion < LegalTexts.TERMS_VERSION
  val termsChanged: Boolean get() = bool(KEY_ONBOARDED) && termsVersion < LegalTexts.TERMS_VERSION
  fun acceptTerms(at: String) { put(KEY_TERMS_VERSION, LegalTexts.TERMS_VERSION.toString()); put(KEY_TERMS_AT, at); put(KEY_ONBOARDED, "true") }
  fun redoOnboarding() = put(KEY_ONBOARDED, "false")

  private val _autoUpdate = MutableStateFlow(bool(KEY_AUTO_UPDATE, true))
  val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()
  fun setAutoUpdate(on: Boolean) { _autoUpdate.value = on; put(KEY_AUTO_UPDATE, on.toString()) }
  var lastUpdateCheck: Long
    get() = store.get(TarkasState.GLOBAL, KEY_LAST_CHECK)?.toLongOrNull() ?: 0L
    set(v) = put(KEY_LAST_CHECK, v.toString())

  private val _tray = MutableStateFlow(bool(KEY_TRAY))
  /** Icon in the notification area; with [closeToTray] the close button hides the window instead of quitting. */
  val tray: StateFlow<Boolean> = _tray.asStateFlow()
  fun setTray(on: Boolean) { _tray.value = on; put(KEY_TRAY, on.toString()); if (!on) setCloseToTray(false) }
  private val _closeToTray = MutableStateFlow(bool(KEY_CLOSE_TO_TRAY))
  val closeToTray: StateFlow<Boolean> = _closeToTray.asStateFlow()
  fun setCloseToTray(on: Boolean) { _closeToTray.value = on; put(KEY_CLOSE_TO_TRAY, on.toString()) }

  companion object {
    const val KEY_LANGUAGE = "language"
    const val KEY_LAYER = "settings_layer"
    const val KEY_ONBOARDED = "onboarded"
    const val KEY_TERMS_VERSION = "terms_version"
    const val KEY_TERMS_AT = "terms_accepted_at"
    const val KEY_AUTO_UPDATE = "auto_update"
    const val KEY_LAST_CHECK = "last_update_check"
    const val KEY_TRAY = "tray"
    const val KEY_CLOSE_TO_TRAY = "close_to_tray"
  }
}

/** Texts that exist only on Windows (autostart, tray, installer). The shared string files are generated from Android's. */
fun w(pt: String, en: String): String = if (Locale.getDefault().language == "pt") pt else en
