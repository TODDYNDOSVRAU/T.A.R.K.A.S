package com.example

import com.example.data.update.ReleaseInfo
import com.example.data.update.UpdateState
import com.example.data.update.UpdateState.Phase
import com.example.ui.viewmodel.StartupPrompt
import com.example.ui.viewmodel.UpdateGateway
import com.example.ui.viewmodel.UpdateSwitches
import com.example.ui.viewmodel.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** G5: the update screens' state and actions, without the singleton, the network or a screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()

  private class Fake(initial: UpdateState = UpdateState()) : UpdateGateway {
    val flow = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    var downloadResult: Phase = Phase.READY
    override val state: StateFlow<UpdateState> get() = flow
    override var installsUpdates = true
    var auto = true
    var wifi = true
    override val autoEnabled: Boolean get() = auto
    override val wifiOnly: Boolean get() = wifi
    override var installPromptAsked = false
    override var dismissedTag: String? = null
    var allowed = false
    override fun canInstallPackages() = allowed
    override fun setAutoEnabled(on: Boolean) { auto = on; calls += "auto=$on" }
    override fun setWifiOnly(on: Boolean) { wifi = on; calls += "wifi=$on" }
    override fun cancelDownload() { calls += "cancel" }
    override suspend fun check(): UpdateState { calls += "check"; return flow.value }
    override suspend fun download(userInitiated: Boolean): UpdateState { calls += "download"; return flow.value.copy(phase = downloadResult) }
    override suspend fun install(userInitiated: Boolean): UpdateState { calls += "install"; return flow.value }
  }

  private fun release(tag: String = "v2") = ReleaseInfo(tag, tag.removePrefix("v"), "a.apk", "https://x/a.apk", 10, null, null, "", "https://x")

  @Before fun setMain() = Dispatchers.setMain(dispatcher)
  @After fun resetMain() = Dispatchers.resetMain()

  private fun vm(g: Fake) = UpdateViewModel(g, dispatcher)

  @Test fun readsTheSwitchesFromTheEngine() {
    val g = Fake().apply { auto = false; wifi = true; allowed = true; installPromptAsked = true; dismissedTag = "v1" }
    assertEquals(UpdateSwitches(false, true, true, true, "v1"), vm(g).switches.value)
  }

  @Test fun theSwitchesGoToTheEngineAndBackToTheScreen() {
    val g = Fake()
    val v = vm(g)
    v.setAuto(false)
    v.setWifiOnly(false)
    assertEquals(listOf("auto=false", "wifi=false"), g.calls)
    assertEquals(false, v.switches.value.auto)
    assertEquals(false, v.switches.value.wifiOnly)
  }

  @Test fun refreshPicksUpThePermissionGrantedOnTheSystemPage() {
    val g = Fake()
    val v = vm(g)
    assertEquals(false, v.switches.value.allowed)
    g.allowed = true
    v.refresh()
    assertEquals(true, v.switches.value.allowed)
  }

  @Test fun permissionIsAskedFirstAndOnlyOnce() {
    val g = Fake(UpdateState(Phase.AVAILABLE, release()))
    val v = vm(g)
    assertEquals(StartupPrompt.PERMISSION, v.startupPrompt())
    v.permissionPromptAnswered()
    assertEquals(true, g.installPromptAsked)
    assertEquals(StartupPrompt.RELEASE, v.startupPrompt())
  }

  @Test fun theDevelopmentBuildNeverAsksForPermission() {
    val g = Fake(UpdateState(Phase.AVAILABLE, release())).apply { installsUpdates = false }
    assertEquals(StartupPrompt.RELEASE, vm(g).startupPrompt())
  }

  @Test fun dismissingHidesThatReleaseButNotTheNextOne() {
    val g = Fake(UpdateState(Phase.AVAILABLE, release("v2"))).apply { allowed = true }
    val v = vm(g)
    assertEquals(StartupPrompt.RELEASE, v.startupPrompt())
    v.dismissRelease("v2")
    assertEquals("v2", g.dismissedTag)
    assertEquals(StartupPrompt.NONE, v.startupPrompt())
    g.flow.value = UpdateState(Phase.AVAILABLE, release("v3"))
    assertEquals(StartupPrompt.RELEASE, v.startupPrompt())
  }

  @Test fun noPopupWithoutAWaitingRelease() {
    for (phase in listOf(Phase.IDLE, Phase.CHECKING, Phase.UP_TO_DATE, Phase.DOWNLOADING, Phase.INSTALLING, Phase.AWAITING_USER)) {
      val g = Fake(UpdateState(phase, release())).apply { allowed = true }
      assertEquals(phase.name, StartupPrompt.NONE, vm(g).startupPrompt())
    }
    assertEquals(StartupPrompt.NONE, vm(Fake().apply { allowed = true }).startupPrompt())
  }

  @Test fun downloadThenInstallOnlyWhenTheDownloadIsReady() {
    val ok = Fake(UpdateState(Phase.AVAILABLE, release()))
    vm(ok).downloadAndInstall()
    assertEquals(listOf("download", "install"), ok.calls)

    val failed = Fake(UpdateState(Phase.AVAILABLE, release())).apply { downloadResult = Phase.AVAILABLE }
    vm(failed).downloadAndInstall()
    assertEquals(listOf("download"), failed.calls)
  }

  @Test fun anAlreadyReadyUpdateInstallsWithoutDownloading() {
    val g = Fake(UpdateState(Phase.READY, release()))
    vm(g).downloadAndInstall()
    assertEquals(listOf("install"), g.calls)
  }

  @Test fun checkAndCancelReachTheEngine() {
    val g = Fake()
    val v = vm(g)
    v.checkNow()
    v.cancelDownload()
    assertEquals(listOf("check", "cancel"), g.calls)
  }
}
