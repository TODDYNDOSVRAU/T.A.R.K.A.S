package com.example.data.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The one place that knows which permission reads the device's music; the screen and the ViewModel both use it. */
object MusicPermission {
  /** READ_MEDIA_AUDIO from Android 13 (API 33), READ_EXTERNAL_STORAGE before. */
  fun nameFor(sdk: Int): String =
    if (sdk >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

  /** The permission on this device's Android version. */
  val name: String get() = nameFor(Build.VERSION.SDK_INT)

  fun granted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}
