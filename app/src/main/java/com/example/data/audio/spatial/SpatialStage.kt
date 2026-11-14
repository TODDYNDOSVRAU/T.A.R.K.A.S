package com.example.data.audio.spatial

/**
 * What [SpatialAudioProcessor] needs from a renderer: it takes interleaved frames, returns whole hops of
 * interleaved stereo, glides between the dry sound (intensity 0) and its own (intensity > 0), and delays
 * its output by [latency] frames. [SpatialEngine] is the stereo one, [SurroundVirtualizer] the 5.1 one.
 */
interface SpatialStage {
  /** Frames between a sample going in and its rendering coming out, before the hop buffering. */
  val latency: Int
  var targetIntensity: Double
  val currentIntensity: Double
  fun reset(startIntensity: Double = targetIntensity)

  /** Interleaved input frames (stereo or 5.1, whichever the stage takes); returns stereo frames written. */
  fun process(input: FloatArray, offset: Int, frames: Int, output: FloatArray): Int
}
