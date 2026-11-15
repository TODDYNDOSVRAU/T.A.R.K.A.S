package com.example.data.audio.spatial

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Reads the head pose of headphones that have a head tracker, which Android exposes as a dynamic
 * sensor of type TYPE_HEAD_TRACKER, and turns it into the yaw [SpatialControl] gives the scene. The
 * phone's own sensors are not used: they measure the phone, not the head.
 */
class HeadTracker(context: Context) {
  private val sensors = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val filter = HeadYawFilter()
  private val clock = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "tarkas-head-status") }
  private val _status = MutableStateFlow(HeadTrackStatus.OFF)
  val status: StateFlow<HeadTrackStatus> = _status.asStateFlow()

  @Volatile private var wanted = false
  @Volatile private var lastPoseAt = 0L
  private var lastRawAt = 0L
  private var thread: HandlerThread? = null
  private var ticker: ScheduledFuture<*>? = null
  private var registered: Sensor? = null

  private val listener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) = onValues(event.values)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
  }

  private val discovery = object : SensorManager.DynamicSensorCallback() {
    override fun onDynamicSensorConnected(sensor: Sensor) { if (sensor.type == Sensor.TYPE_HEAD_TRACKER) attach() }
    override fun onDynamicSensorDisconnected(sensor: Sensor) { if (sensor === registered) detach(); refresh() }
  }

  /** Starts or stops following the head. Turning it off puts the scene back in front. */
  @Synchronized
  fun setEnabled(on: Boolean) {
    if (on == wanted) return
    wanted = on
    if (on) start() else stop()
  }

  /** The direction the listener faces now becomes the front. */
  fun recenter() = filter.recenter()

  private fun sensorPresent() = registered != null

  /** Re-reads the state: a headset was connected, or the sensor went quiet. */
  fun refresh() {
    if (!wanted) { _status.value = HeadTrackStatus.OFF; return }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { _status.value = HeadTrackStatus.NO_SENSOR; return }
    val since = if (lastPoseAt == 0L) null else SystemClock.elapsedRealtime() - lastPoseAt
    val next = HeadPose.status(true, sensorPresent(), since)
    // Without poses the scene goes back to the front instead of staying where the head last was.
    if (next != HeadTrackStatus.TRACKING && _status.value == HeadTrackStatus.TRACKING) SpatialControl.setHeadYaw(0.0)
    _status.value = next
  }

  private fun start() {
    filter.reset()
    lastPoseAt = 0L
    lastRawAt = 0L
    // The head tracker sensor type exists from Android 13.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      _status.value = HeadTrackStatus.NO_SENSOR
      return
    }
    try {
      val t = HandlerThread("tarkas-head").also { it.start() }
      thread = t
      sensors.registerDynamicSensorCallback(discovery, Handler(t.looper))
      attach()
      refresh()
      // A sensor that goes quiet sends nothing, so the status is re-read once a second.
      ticker = clock.scheduleWithFixedDelay({ refresh() }, 1, 1, TimeUnit.SECONDS)
    } catch (e: Throwable) {
      Log.w(TAG, "Could not follow the head", e)
      com.example.data.diagnostics.Diagnostics.record("head.start", "Could not start head tracking", e)
      _status.value = HeadTrackStatus.NO_SENSOR
    }
  }

  private fun attach() {
    val t = thread ?: return
    if (registered != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val s = sensors.getDynamicSensorList(Sensor.TYPE_HEAD_TRACKER).firstOrNull() ?: return
    if (sensors.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME, Handler(t.looper))) registered = s
    refresh()
  }

  private fun detach() {
    sensors.unregisterListener(listener)
    registered = null
  }

  private fun stop() {
    ticker?.cancel(false)
    ticker = null
    try {
      sensors.unregisterDynamicSensorCallback(discovery)
      detach()
    } catch (e: Throwable) {
      Log.w(TAG, "Could not stop head tracking cleanly", e)
    }
    thread?.quitSafely()
    thread = null
    SpatialControl.setHeadYaw(0.0)
    _status.value = HeadTrackStatus.OFF
  }

  private fun onValues(values: FloatArray) {
    if (!wanted) return
    val raw = HeadPose.yawFromValues(values) ?: return
    val now = SystemClock.elapsedRealtime()
    val dt = if (lastRawAt == 0L) 0.0 else (now - lastRawAt) / 1000.0
    lastRawAt = now
    lastPoseAt = now
    SpatialControl.setHeadYaw(filter.update(raw, dt))
    if (_status.value != HeadTrackStatus.TRACKING) refresh()
  }

  companion object {
    private const val TAG = "HeadTracker"
  }
}
