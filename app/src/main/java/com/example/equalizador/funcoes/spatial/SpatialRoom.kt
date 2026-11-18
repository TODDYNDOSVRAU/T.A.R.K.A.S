package com.example.data.audio.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The virtual room of Tarkas Spatial. An HRTF alone only moves each sound to a direction around the
 * head; with no room around it the brain hears the sound inside the head, "the same audio in each
 * ear". A room gives it distance: the left and right speakers stand 2 m away in a 6 x 4.4 x 2.8 m
 * room, the reflections of its walls, floor and ceiling (image sources up to the second order)
 * reach the two ears from their own directions with fractional-sample delays, extended Kuhn-Woodworth
 * ITD, ISO 9613-1 atmospheric absorption, and realistic wall boundary damping, while the shared
 * Householder FDN adds a smooth diffuse tail.
 *
 * Streaming and allocation-free after construction. The room is fed with the input of the engine and
 * delayed by [alignDelay] so that it lines up with the engine's own latency.
 */
class SpatialRoom(private val sampleRate: Int, private val alignDelay: Int) {
  companion object {
    private const val SOUND_SPEED = AcousticPhysics.SOUND_SPEED
    private const val ROOM_X = 6.0 // front-back
    private const val ROOM_Y = 4.4 // left-right
    private const val ROOM_Z = 2.8 // floor-ceiling
    private const val LISTENER_X = 3.4
    private const val LISTENER_Y = ROOM_Y / 2
    private const val LISTENER_Z = 1.2
    private const val SPEAKER_DISTANCE = 2.0
    private const val SPEAKER_AZIMUTH = 30.0
    /** Midband pressure retained per wall bounce (corresponds to ~0.36 energy absorption). */
    private const val WALL = 0.78
    private const val MAX_TAP_S = 0.060
    private const val RT60_S = 0.38
    private const val PRE_DELAY_S = 0.010
    private const val MAX_PRE_DELAY_S = 0.060
    /** Half the length of the voice band-pass, in samples: its delay, which the other path matches. */
    private const val VOICE_HALF = 191
    /** Early reflections and tail, relative to the direct sound, calibrated in SpatialRoomTest. */
    internal const val EARLY_GAIN = 0.25
    internal const val TAIL_GAIN = 2.7
  }

  // ---- reflections: one entry per (speaker, image) ----
  private val tapCount: Int
  private val tapSource: IntArray   // 0 = left speaker, 1 = right
  private val tapDelayL0: IntArray  // integer sample delay per ear + linear fractional weight
  private val tapFracL: DoubleArray
  private val tapDelayR0: IntArray
  private val tapFracR: DoubleArray
  private val tapGainL: DoubleArray
  private val tapGainR: DoubleArray
  private val tapCoefL: DoubleArray // one-pole low-pass modeling air (ISO 9613-1) + wall HF + head shadow
  private val tapCoefR: DoubleArray
  private val tapStateL: DoubleArray
  private val tapStateR: DoubleArray

  // ---- input lines (high-passed copies of the two channels) ----
  private val size: Int
  private val mask: Int
  private val ringL: DoubleArray
  private val ringR: DoubleArray
  private var write = 0
  private val hpCoef = 1 - exp(-2 * PI * 150.0 / sampleRate)
  private var hpStateL = 0.0
  private var hpStateR = 0.0

  // ---- feedback delay network (shared Householder tail) ----
  private val lead = max(alignDelay - VOICE_HALF, 16)
  @Volatile private var preDelay = (PRE_DELAY_S * sampleRate).roundToInt() + lead
  private var earlyMul = 1.0
  private var tailMul = 1.0
  private var lastPreDelayMs = PRE_DELAY_S * 1000
  private var lastDiffusion = 0.6

  private val earlyDiffuserL = AcousticPhysics.Diffuser(sampleRate, doubleArrayOf(3.7, 5.9, 8.3, 11.9, 15.1), 0.6)
  private val earlyDiffuserR = AcousticPhysics.Diffuser(sampleRate, doubleArrayOf(4.3, 6.7, 9.1, 12.7, 16.3), 0.6)
  private val tailFdn = AcousticPhysics.HouseholderTail(
    sampleRate = sampleRate,
    rt60Sec = RT60_S,
    diffusion = 0.6,
    dampHz = 4500.0,
    outLpHz = 6500.0,
    tailGain = TAIL_GAIN
  )

  /** Linear-phase band-pass for a singing voice (300 Hz-4.5 kHz), 383 taps: zero phase distortion. */
  private val voiceTaps = DoubleArray(2 * VOICE_HALF + 1) { n ->
    val m = n - VOICE_HALF
    val w = 0.5 + 0.5 * cos(PI * m / (VOICE_HALF + 1))
    val f1 = 300.0 / sampleRate
    val f2 = 4500.0 / sampleRate
    val ideal = if (m == 0) 2 * (f2 - f1) else (sin(2 * PI * f2 * m) - sin(2 * PI * f1 * m)) / (PI * m)
    ideal * w
  }
  private val rawMask = AcousticPhysics.HouseholderTail.nextPow2(2 * VOICE_HALF + 2) - 1
  private val rawL = DoubleArray(rawMask + 1)
  private val rawR = DoubleArray(rawMask + 1)
  private val rawM = DoubleArray(rawMask + 1)
  private var rawPos = 0

  init {
    val taps = buildTaps()
    tapCount = taps.size
    tapSource = IntArray(tapCount) { taps[it].source }
    tapDelayL0 = IntArray(tapCount) { taps[it].delayL0 }
    tapFracL = DoubleArray(tapCount) { taps[it].fracL }
    tapDelayR0 = IntArray(tapCount) { taps[it].delayR0 }
    tapFracR = DoubleArray(tapCount) { taps[it].fracR }
    tapGainL = DoubleArray(tapCount) { taps[it].gainL }
    tapGainR = DoubleArray(tapCount) { taps[it].gainR }
    tapCoefL = DoubleArray(tapCount) { taps[it].coefL }
    tapCoefR = DoubleArray(tapCount) { taps[it].coefR }
    tapStateL = DoubleArray(tapCount)
    tapStateR = DoubleArray(tapCount)
    val longest = max(
      taps.maxOfOrNull { max(it.delayL0, it.delayR0) + 2 } ?: 0,
      lead + (MAX_PRE_DELAY_S * sampleRate).roundToInt() + 2
    )
    size = AcousticPhysics.HouseholderTail.nextPow2(longest + 8)
    mask = size - 1
    ringL = DoubleArray(size)
    ringR = DoubleArray(size)
  }

  private class Tap(
    val source: Int,
    val delayL0: Int, val fracL: Double,
    val delayR0: Int, val fracR: Double,
    val gainL: Double, val gainR: Double,
    val coefL: Double, val coefR: Double
  )

  /** Image sources of the two speakers up to the second order with sub-sample ITD, ISO 9613-1 air loss and wall absorption. */
  private fun buildTaps(): List<Tap> {
    val taps = ArrayList<Tap>()
    val geo = AcousticPhysics.HeadGeometry.DEFAULT
    for (source in 0..1) {
      val side = if (source == 0) 1.0 else -1.0
      val az = Math.toRadians(SPEAKER_AZIMUTH) * side
      val sx = LISTENER_X + SPEAKER_DISTANCE * cos(az)
      val sy = LISTENER_Y + SPEAKER_DISTANCE * sin(az)
      val sz = LISTENER_Z
      val xs = images(sx, ROOM_X)
      val ys = images(sy, ROOM_Y)
      val zs = images(sz, ROOM_Z)
      for (ix in xs) for (iy in ys) for (iz in zs) {
        val bounces = ix.second + iy.second + iz.second
        if (bounces == 0 || bounces > 2) continue
        val vx = ix.first - LISTENER_X
        val vy = iy.first - LISTENER_Y
        val vz = iz.first - LISTENER_Z
        val r = sqrt(vx * vx + vy * vy + vz * vz)
        val excessDist = r - SPEAKER_DISTANCE
        val excess = excessDist / SOUND_SPEED
        if (excess < 0 || excess > MAX_TAP_S) continue

        val azimuth = atan2(vy, vx)
        val elevation = asin((vz / r).coerceIn(-1.0, 1.0))
        val azDeg = Math.toDegrees(azimuth)
        val elDeg = Math.toDegrees(elevation)

        // Exact reflection geometry relative to posterior ear offset & reflection distance.
        val reflGeo = geo.copy(speakerDistanceM = max(r, 0.5))
        val dotL = AcousticPhysics.sourceEarCos(azDeg, elDeg, true, reflGeo)
        val dotR = AcousticPhysics.sourceEarCos(azDeg, elDeg, false, reflGeo)
        // Average of Kuhn-Woodworth midband and HF arrival for broadband early tap delay.
        val tauMidL = 0.35 * AcousticPhysics.kuhnLowFreqDelaySec(dotL, reflGeo) +
          0.65 * AcousticPhysics.extendedWoodworthDelaySec(dotL, reflGeo)
        val tauMidR = 0.35 * AcousticPhysics.kuhnLowFreqDelaySec(dotR, reflGeo) +
          0.65 * AcousticPhysics.extendedWoodworthDelaySec(dotR, reflGeo)

        val base = lead + excess * sampleRate
        val exactL = max(1.0, base + tauMidL * sampleRate)
        val exactR = max(1.0, base + tauMidR * sampleRate)
        val dL0 = floor(exactL).toInt()
        val fL = exactL - dL0
        val dR0 = floor(exactR).toInt()
        val fR = exactR - dR0

        val distanceGain = SPEAKER_DISTANCE / r
        val loss = WALL.pow(bounces) * distanceGain * EARLY_GAIN
        // Head shadow + pinna rear darkening + ISO 9613-1 air & wall HF damping -> combined 1-pole cutoff.
        val lateral = abs(cos(elevation) * sin(azimuth))
        val rear = max(0.0, -cos(azimuth) * cos(elevation))
        val leftNear = dotL >= dotR
        val nearWeight = abs(dotL - dotR) * 0.5
        val nearGain = 10.0.pow(1.5 * nearWeight / 20.0)
        val farGain = 10.0.pow(-5.0 * nearWeight / 20.0)
        val gainL = loss * if (leftNear) nearGain else farGain
        val gainR = loss * if (leftNear) farGain else nearGain

        val wallFactor = AcousticPhysics.wallReflectionGain(8000.0, bounces, 0.36) /
          AcousticPhysics.wallReflectionGain(500.0, bounces, 0.36)
        val airFactor = AcousticPhysics.airTransmissionGain(10000.0, excessDist)
        val hfDamp = (wallFactor * airFactor).coerceIn(0.35, 1.0)
        val cutNear = (12000.0 * hfDamp * (1.0 - 0.28 * rear)).coerceIn(1500.0, 14000.0)
        val cutFar = (12000.0 * hfDamp * (1.0 - 0.62 * lateral) * (1.0 - 0.28 * rear)).coerceIn(1400.0, 14000.0)
        val coefNear = 1.0 - exp(-2 * PI * cutNear / sampleRate)
        val coefFar = 1.0 - exp(-2 * PI * cutFar / sampleRate)
        taps.add(
          Tap(
            source, dL0, fL, dR0, fR, gainL, gainR,
            if (leftNear) coefNear else coefFar, if (leftNear) coefFar else coefNear
          )
        )
      }
    }
    return taps
  }

  /** The coordinates of the source and its mirrors in the two walls of one axis, with the bounce count. */
  private fun images(s: Double, length: Double): List<Pair<Double, Int>> =
    listOf(s to 0, -s to 1, 2 * length - s to 1, s + 2 * length to 2, s - 2 * length to 2)

  /** Hand settings updated once per hop off-allocation. */
  fun tune(early: Double, tail: Double, decayS: Double, preDelayMs: Double, diffusion: Double) {
    earlyMul = early
    tailMul = tail
    tailFdn.setParams(decayS, diffusion)
    if (preDelayMs != lastPreDelayMs) {
      lastPreDelayMs = preDelayMs
      preDelay = (preDelayMs.coerceIn(0.0, MAX_PRE_DELAY_S * 1000) * sampleRate / 1000).roundToInt() + lead
    }
    if (diffusion != lastDiffusion) {
      lastDiffusion = diffusion
      val g = diffusion.coerceIn(0.0, 0.9)
      earlyDiffuserL.g = g
      earlyDiffuserR.g = g
    }
  }

  /** Empties every line after a seek or track change. */
  fun clear() {
    ringL.fill(0.0); ringR.fill(0.0)
    tapStateL.fill(0.0); tapStateR.fill(0.0)
    tailFdn.clear()
    earlyDiffuserL.clear(); earlyDiffuserR.clear()
    rawL.fill(0.0); rawR.fill(0.0); rawM.fill(0.0); rawPos = 0
    hpStateL = 0.0; hpStateR = 0.0
    write = 0
  }

  /**
   * Adds the room's response to [n] samples of [inL]/[inR] into [outL]/[outR], gliding levels and
   * centred-voice isolation smoothly across the block.
   */
  fun process(
    inL: DoubleArray, inR: DoubleArray, outL: DoubleArray, outR: DoubleArray,
    n: Int, levelStart: Double, levelEnd: Double,
    voiceStart: Double = 0.0, voiceEnd: Double = 0.0
  ) {
    for (i in 0 until n) {
      val level = levelStart + (levelEnd - levelStart) * (i + 1) / n
      rawL[rawPos and rawMask] = inL[i]
      rawR[rawPos and rawMask] = inR[i]
      rawM[rawPos and rawMask] = 0.5 * (inL[i] + inR[i])
      var voice = 0.0
      for (j in voiceTaps.indices) voice += voiceTaps[j] * rawM[(rawPos - j) and rawMask]
      val take = voiceStart + (voiceEnd - voiceStart) * (i + 1) / n
      val xl = rawL[(rawPos - VOICE_HALF) and rawMask] - take * voice
      val xr = rawR[(rawPos - VOICE_HALF) and rawMask] - take * voice
      rawPos++
      hpStateL += hpCoef * (xl - hpStateL)
      hpStateR += hpCoef * (xr - hpStateR)
      ringL[write and mask] = xl - hpStateL
      ringR[write and mask] = xr - hpStateR

      var accL = 0.0
      var accR = 0.0
      for (t in 0 until tapCount) {
        val ring = if (tapSource[t] == 0) ringL else ringR
        val dL = tapDelayL0[t]; val fL = tapFracL[t]
        val sL0 = ring[(write - dL) and mask]
        val sL1 = ring[(write - dL - 1) and mask]
        val sl = (sL0 + fL * (sL1 - sL0)) * tapGainL[t]

        val dR = tapDelayR0[t]; val fR = tapFracR[t]
        val sR0 = ring[(write - dR) and mask]
        val sR1 = ring[(write - dR - 1) and mask]
        val sr = (sR0 + fR * (sR1 - sR0)) * tapGainR[t]

        tapStateL[t] += tapCoefL[t] * (sl - tapStateL[t])
        tapStateR[t] += tapCoefR[t] * (sr - tapStateR[t])
        accL += tapStateL[t]
        accR += tapStateR[t]
      }

      tailFdn.step(0.5 * (ringL[(write - preDelay) and mask] + ringR[(write - preDelay) and mask]))
      outL[i] += level * (earlyMul * earlyDiffuserL.process(accL) + tailMul * tailFdn.outLeft)
      outR[i] += level * (earlyMul * earlyDiffuserR.process(accR) + tailMul * tailFdn.outRight)
      write++
    }
  }
}
