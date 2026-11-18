package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.update.UpdateManager
import com.example.data.update.UpdateState
import com.example.data.update.UpdateState.Phase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the update screens need from the update engine, so the screens and the tests don't touch the singleton. */
interface UpdateGateway {
  val state: StateFlow<UpdateState>
  val installsUpdates: Boolean
  val autoEnabled: Boolean
  val wifiOnly: Boolean
  var installPromptAsked: Boolean
  var dismissedTag: String?
  fun canInstallPackages(): Boolean
  fun setAutoEnabled(on: Boolean)
  fun setWifiOnly(on: Boolean)
  fun cancelDownload()
  suspend fun check(): UpdateState
  suspend fun download(userInitiated: Boolean): UpdateState
  suspend fun install(userInitiated: Boolean): UpdateState
}

/** The real engine behind [UpdateGateway]. */
object UpdateManagerGateway : UpdateGateway {
  override val state: StateFlow<UpdateState> get() = UpdateManager.state
  override val installsUpdates: Boolean get() = UpdateManager.installsUpdates
  override val autoEnabled: Boolean get() = UpdateManager.autoEnabled
  override val wifiOnly: Boolean get() = UpdateManager.wifiOnly
  override var installPromptAsked: Boolean
    get() = UpdateManager.installPromptAsked
    set(v) { UpdateManager.installPromptAsked = v }
  override var dismissedTag: String?
    get() = UpdateManager.dismissedTag
    set(v) { UpdateManager.dismissedTag = v }
  override fun canInstallPackages() = UpdateManager.canInstallPackages()
  override fun setAutoEnabled(on: Boolean) = UpdateManager.setAutoEnabled(on)
  override fun setWifiOnly(on: Boolean) = UpdateManager.setWifiOnly(on)
  override fun cancelDownload() = UpdateManager.cancelDownload()
  override suspend fun check() = UpdateManager.check()
  override suspend fun download(userInitiated: Boolean) = UpdateManager.download(userInitiated)
  override suspend fun install(userInitiated: Boolean) = UpdateManager.install(userInitiated)
}

/** Which startup prompt shows, if any: the permission explanation first, then the new-release popup. */
enum class StartupPrompt { NONE, PERMISSION, RELEASE }

/** What the screens read besides [UpdateState]: the two switches and the install permission. */
data class UpdateSwitches(val auto: Boolean, val wifiOnly: Boolean, val allowed: Boolean, val permissionAsked: Boolean, val dismissedTag: String?)

/**
 * The state and actions of the update screens (Ajustes and the startup prompts). The engine stays the source
 * of truth; this holds only what the screens show and what they ask for.
 */
class UpdateViewModel(
  private val gateway: UpdateGateway = UpdateManagerGateway,
  private val io: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
  val state: StateFlow<UpdateState> = gateway.state
  val installsUpdates: Boolean get() = gateway.installsUpdates

  private val _switches = MutableStateFlow(read())
  val switches: StateFlow<UpdateSwitches> = _switches

  private fun read() = UpdateSwitches(
    auto = gateway.autoEnabled,
    wifiOnly = gateway.wifiOnly,
    allowed = gateway.canInstallPackages(),
    permissionAsked = gateway.installPromptAsked,
    dismissedTag = gateway.dismissedTag
  )

  /** The permission is changed on a system page, so the screens call this when the app comes back. */
  fun refresh() { _switches.value = read() }

  fun setAuto(on: Boolean) {
    gateway.setAutoEnabled(on)
    _switches.update { it.copy(auto = on) }
  }

  fun setWifiOnly(on: Boolean) {
    gateway.setWifiOnly(on)
    _switches.update { it.copy(wifiOnly = on) }
  }

  fun permissionPromptAnswered() {
    gateway.installPromptAsked = true
    _switches.update { it.copy(permissionAsked = true) }
  }

  fun dismissRelease(tag: String) {
    gateway.dismissedTag = tag
    _switches.update { it.copy(dismissedTag = tag) }
  }

  fun checkNow() { viewModelScope.launch(io) { gateway.check() } }

  fun cancelDownload() = gateway.cancelDownload()

  fun install() { viewModelScope.launch(io) { gateway.install(userInitiated = true) } }

  /** Downloads the offered release and installs it when the download is ready. */
  fun downloadAndInstall() {
    viewModelScope.launch(io) {
      if (state.value.phase == Phase.READY || gateway.download(userInitiated = true).phase == Phase.READY) {
        gateway.install(userInitiated = true)
      }
    }
  }

  /** The one prompt to show now. Pure over the state, so it is tested without a screen. */
  fun startupPrompt(): StartupPrompt = startupPrompt(state.value, installsUpdates, switches.value)

  companion object {
    fun startupPrompt(state: UpdateState, installs: Boolean, s: UpdateSwitches): StartupPrompt {
      if (installs && !s.allowed && !s.permissionAsked) return StartupPrompt.PERMISSION
      val release = state.release ?: return StartupPrompt.NONE
      val waiting = state.phase == Phase.AVAILABLE || state.phase == Phase.READY
      return if (waiting && s.dismissedTag != release.tag) StartupPrompt.RELEASE else StartupPrompt.NONE
    }
  }
}
