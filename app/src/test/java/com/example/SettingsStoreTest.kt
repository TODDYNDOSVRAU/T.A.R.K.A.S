package com.example

import com.example.ui.theme.ListAnimation
import com.example.ui.theme.SurfaceStyle
import com.example.ui.viewmodel.SettingsPrefs
import com.example.ui.viewmodel.SettingsStore
import com.example.ui.viewmodel.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** G5: the appearance switches have one owner; the screen and the root view model read the same value. */
class SettingsStoreTest {
  private class FakePrefs : SettingsPrefs {
    val saved = mutableListOf<String>()
    var theme = "system"
    var surface = "solid"
    var motion = false
    var anim: String? = null
    var transparency = false
    var musicFirst = true
    override fun themeMode() = theme
    override fun saveThemeMode(mode: String) { theme = mode; saved += "theme=$mode" }
    override fun surfaceStyle() = surface
    override fun saveSurfaceStyle(key: String) { surface = key; saved += "surface=$key" }
    override fun reduceMotion() = motion
    override fun saveReduceMotion(reduce: Boolean) { motion = reduce; saved += "motion=$reduce" }
    override fun listAnimation() = anim
    override fun saveListAnimation(key: String) { anim = key; saved += "anim=$key" }
    override fun reduceTransparency() = transparency
    override fun saveReduceTransparency(reduce: Boolean) { transparency = reduce; saved += "transparency=$reduce" }
    override fun homeMusicFirst() = musicFirst
    override fun saveHomeMusicFirst(on: Boolean) { musicFirst = on; saved += "musicFirst=$on" }
  }

  @Test fun startsFromWhatWasSaved() {
    val p = FakePrefs().apply { theme = "dark"; motion = true; musicFirst = false; transparency = true }
    val s = SettingsStore(p)
    assertEquals("dark", s.themeMode.value)
    assertEquals(true, s.reduceMotion.value)
    assertEquals(false, s.homeMusicFirst.value)
    assertEquals(true, s.reduceTransparency.value)
    assertEquals(SurfaceStyle.SOLID, s.surfaceStyle.value)
  }

  @Test fun aChangeIsSavedAndSeenByEveryReader() {
    val p = FakePrefs()
    val store = SettingsStore(p)
    val vm = SettingsViewModel(store)
    vm.setThemeMode("light")
    vm.setReduceTransparency(true)
    vm.setHomeMusicFirst(false)
    assertEquals(listOf("theme=light", "transparency=true", "musicFirst=false"), p.saved)
    assertEquals("light", store.themeMode.value)
    assertEquals("light", vm.themeMode.value)
    assertEquals(true, vm.reduceTransparency.value)
    assertEquals(false, store.homeMusicFirst.value)
  }

  @Test fun surfaceAndMotionAreSavedByKey() {
    val p = FakePrefs()
    val vm = SettingsViewModel(SettingsStore(p))
    val style = SurfaceStyle.entries.last()
    vm.setSurfaceStyle(style)
    vm.setReduceMotion(true)
    vm.setListAnimation(ListAnimation.entries.last())
    assertEquals(listOf("surface=${style.key}", "motion=true", "anim=${ListAnimation.entries.last().key}"), p.saved)
    assertEquals(style, vm.surfaceStyle.value)
    assertEquals(true, vm.reduceMotion.value)
  }
}
