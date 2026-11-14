package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.PreferencesRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeOrderPreferenceTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("tarkas_prefs", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `the user's music comes first until the user chooses otherwise`() {
    assertTrue(PreferencesRepository(context).getHomeMusicFirst())
  }

  @Test
  fun `the choice is kept and can be undone`() {
    PreferencesRepository(context).saveHomeMusicFirst(false)
    assertFalse(PreferencesRepository(context).getHomeMusicFirst())
    PreferencesRepository(context).saveHomeMusicFirst(true)
    assertTrue(PreferencesRepository(context).getHomeMusicFirst())
  }

  @Test
  fun `the choice lives in the preferences file the backup already covers`() {
    PreferencesRepository(context).saveHomeMusicFirst(false)
    val raw = context.getSharedPreferences("tarkas_prefs", Context.MODE_PRIVATE)
    assertFalse(raw.getBoolean("home_music_first", true))
  }
}
