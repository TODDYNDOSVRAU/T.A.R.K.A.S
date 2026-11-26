package com.example.data.audio.dsp

/**
 * Windows mirror of the Android JNI bridge (`app/.../dsp/NativeEqDsp.kt`). There is no native Rust
 * library on the desktop, so [createOrNull] is always null and [EqDsp] runs its pure-Kotlin path.
 * The members exist only so the shared [EqDsp] compiles; none is reachable at run time.
 */
class NativeEqDsp private constructor() {
  fun configure(sampleRate: Int, channels: Int) {}
  fun reset() {}
  fun updatePlan(
    enabled: Boolean, coefs: DoubleArray, masks: IntArray, slotActive: BooleanArray, hasMidSide: Boolean,
    preampGain: Float, leftGain: Float, rightGain: Float, width: Float, dryGain: Float,
    limiterEnabled: Boolean, ceiling: Float, releaseCoef: Float, softClip: Boolean
  ) {}
  fun updateFx(
    crossfeedOn: Boolean, crossfeedCutoffHz: Float, crossfeedFeedDb: Float, companderAmount: Float,
    tubeWarmth: Float, subBassHarmonic: Float, convolverEnabled: Boolean, convolverMix: Float, convolverPath: String?
  ) {}
  fun process(buffer: FloatArray, frames: Int): Long = java.lang.Float.floatToRawIntBits(1f).toLong() shl 32
  fun destroy() {}

  companion object {
    val isAvailable: Boolean = false
    fun createOrNull(): NativeEqDsp? = null
  }
}
