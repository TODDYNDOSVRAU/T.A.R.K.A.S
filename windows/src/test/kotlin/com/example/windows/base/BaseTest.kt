package com.example.windows.base

import com.example.data.db.AppDatabase
import com.example.data.db.SettingEntity
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.res.Res
import com.example.windows.res.player_common_tab_audio
import com.example.windows.res.trNow
import com.example.windows.state.TarkasState
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class BaseTest {
  @Test fun roomRoundTripInMemory() = runBlocking {
    val db = AppDatabase.inMemory()
    db.settings().put(SettingEntity("global", "k", "v"))
    assertEquals("v", db.settings().scope("global").single().value)
    db.close()
  }

  @Test fun stringResourceResolves() {
    assertTrue(trNow(Res.string.player_common_tab_audio).isNotBlank())
  }

  @Test fun backDispatcherCallsLastEnabled() {
    val d = BackDispatcher()
    var a = 0; var b = 0
    d.register(true) { a++ }
    val e = d.register(true) { b++ }
    assertTrue(d.dispatch()); assertEquals(1, b)
    e.enabled = false
    assertTrue(d.dispatch()); assertEquals(1, a)
    assertFalse(BackDispatcher().dispatch())
  }

  @Test fun toasterReplacesAndDismisses() {
    val t = Toaster()
    t.show("a"); t.show("b")
    val n = t.notice.value!!
    assertEquals("b", n.text)
    t.dismiss(n.id)
    assertEquals(null, t.notice.value)
  }

  @Test fun stateNavigationAndSettingsPersist() {
    val db = AppDatabase.inMemory()
    val s = TarkasState(DesktopPlayer(), db)
    s.selectTab(TarkasNavTab.AUDIO)
    assertEquals(TarkasNavTab.AUDIO, s.currentTab.value)
    assertTrue(s.goBack())
    assertEquals(TarkasNavTab.HOME, s.currentTab.value)
    assertFalse(s.goBack())
    s.setThemeMode("amoled")
    assertEquals("amoled", s.themeMode.value)
    s.updateEq { it.copy(preampDb = 3f) }
    assertEquals(3f, s.eqSettings.value.preampDb, 0f)
    db.close()
  }
}
