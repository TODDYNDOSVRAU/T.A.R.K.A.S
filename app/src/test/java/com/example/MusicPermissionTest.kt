package com.example

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.library.MusicPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MusicPermissionTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `Android 13 and later ask for the audio permission`() {
    assertEquals(Manifest.permission.READ_MEDIA_AUDIO, MusicPermission.nameFor(33))
    assertEquals(Manifest.permission.READ_MEDIA_AUDIO, MusicPermission.nameFor(36))
  }

  @Test
  fun `Android 12 and earlier ask for storage`() {
    assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, MusicPermission.nameFor(32))
    assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, MusicPermission.nameFor(26))
  }

  @Test
  fun `the name on this device follows its Android version`() {
    assertEquals(Manifest.permission.READ_MEDIA_AUDIO, MusicPermission.name)
  }

  @Test
  fun `granted follows what the system says`() {
    shadowOf(context as android.app.Application).denyPermissions(MusicPermission.name)
    assertFalse(MusicPermission.granted(context))
    shadowOf(context as android.app.Application).grantPermissions(MusicPermission.name)
    assertTrue(MusicPermission.granted(context))
  }
}
