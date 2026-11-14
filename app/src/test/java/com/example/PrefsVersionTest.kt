package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.PrefsVersion
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsVersionTest {
  private fun prefs(name: String) =
    ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE).also { it.edit().clear().commit() }

  @Test fun stepsRunOnceInOrderAndTheVersionIsStored() {
    val p = prefs("v1")
    p.edit().putString("accent", "x").putString("keep", "y").commit()
    val ran = mutableListOf<Int>()
    val steps = listOf<(android.content.SharedPreferences.Editor) -> Unit>(
      { e -> ran += 1; e.remove("accent") },
      { e -> ran += 2; e.putString("added", "z") }
    )
    assertEquals(2, PrefsVersion.upgrade(p, steps))
    assertEquals(listOf(1, 2), ran)
    assertFalse(p.contains("accent"))
    assertEquals("y", p.getString("keep", null))
    assertEquals("z", p.getString("added", null))
    PrefsVersion.upgrade(p, steps)
    assertEquals("a second run does nothing", listOf(1, 2), ran)
  }

  @Test fun anOlderFileOnlyRunsTheMissingSteps() {
    val p = prefs("v2")
    p.edit().putInt(PrefsVersion.KEY, 1).commit()
    val ran = mutableListOf<Int>()
    PrefsVersion.upgrade(p, listOf({ _ -> ran += 1 }, { _ -> ran += 2 }))
    assertEquals(listOf(2), ran)
    assertEquals(2, p.getInt(PrefsVersion.KEY, 0))
  }

  @Test fun aFileFromANewerAppIsLeftAlone() {
    val p = prefs("v3")
    p.edit().putInt(PrefsVersion.KEY, 5).putString("k", "v").commit()
    assertEquals(5, PrefsVersion.upgrade(p, listOf({ e -> e.clear() })))
    assertEquals("v", p.getString("k", null))
  }

  @Test fun themeModesMeanOneThingEverywhere() {
    assertTrue(ThemeMode.isDark("amoled") { false })
    assertTrue(ThemeMode.isDark("dark") { false })
    assertFalse(ThemeMode.isDark("light") { true })
    assertTrue(ThemeMode.isDark("system") { true })
    assertFalse(ThemeMode.isDark("unknown") { false })
    assertTrue(ThemeMode.isAmoled("amoled"))
    assertFalse(ThemeMode.isAmoled("dark"))
  }
}
