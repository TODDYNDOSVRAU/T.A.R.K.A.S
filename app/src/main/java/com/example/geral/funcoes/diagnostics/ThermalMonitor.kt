package com.example.data.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks Android's platform thermal state (API 29+). Consumers use [isWarm] / [isHot] to throttle
 * non-essential background decoding or high-refresh visual drift before the device gets uncomfortable,
 * without removing features or disturbing audio playback.
 */
object ThermalMonitor {
  const val NONE = 0
  const val LIGHT = 1
  const val MODERATE = 2
  const val SEVERE = 3
  const val CRITICAL = 4
  const val EMERGENCY = 5
  const val SHUTDOWN = 6

  @Volatile private var installed = false
  private val _status = MutableStateFlow(NONE)
  val status: StateFlow<Int> = _status.asStateFlow()

  /** Device is noticeably warm (MODERATE or above): pause bulk background jobs. */
  val isWarm: Boolean get() = _status.value >= MODERATE

  /** Device is under thermal pressure (SEVERE or above): pause cosmetic drift animations. */
  val isHot: Boolean get() = _status.value >= SEVERE

  @Synchronized
  fun install(context: Context) {
    if (installed) return
    installed = true
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching {
      val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
      _status.value = pm.currentThermalStatus
      pm.addThermalStatusListener { level ->
        _status.value = level
        if (level >= SEVERE) {
          Diagnostics.record("thermal", "Thermal status reached ${label(level)} ($level)")
        }
      }
    }
  }

  fun label(level: Int = _status.value): String = when (level) {
    NONE -> "NONE"
    LIGHT -> "LIGHT"
    MODERATE -> "MODERATE"
    SEVERE -> "SEVERE"
    CRITICAL -> "CRITICAL"
    EMERGENCY -> "EMERGENCY"
    SHUTDOWN -> "SHUTDOWN"
    else -> "UNKNOWN($level)"
  }
}
