package com.example.ui.theme

import android.app.Activity
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * The screen's frame rate. The app asks for the highest rate the panel offers at its current
 * resolution (90, 120, 144, 165 Hz...) so motion is drawn at that rate; the system may still hold it
 * lower, for instance when the user chose the standard 60 Hz mode or the battery saver is on.
 */
object RefreshRate {
  /** Asks the window for the fastest display mode with the same resolution as the current one. */
  fun requestHighest(activity: Activity) {
    val display = activity.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return
    val current = display.mode
    val best = display.supportedModes
      .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
      .maxByOrNull { it.refreshRate } ?: return
    if (best.refreshRate <= current.refreshRate + 0.5f) return
    activity.window.attributes = activity.window.attributes.apply {
      preferredDisplayModeId = best.modeId
    }
  }

  /** The rate the display is running at now, in Hz. */
  fun current(activity: Activity): Float =
    activity.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
}
