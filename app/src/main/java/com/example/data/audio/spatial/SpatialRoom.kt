package com.example.data.audio.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The virtual room of Tarkas Spatial. An HRTF alone only moves each sound to a direction around the
 * head; with no room around it the brain hears the sound inside the head, "the same audio in each
 * ear". A room gives it distance: the left and right speakers stand 2 m away in a 6 x 4.4 x 2.8 m
 * room, the reflections of its walls, floor and ceiling (image sources up to the second order)
 * reach the two ears from their own directions with their own delays, and a feedback delay network
 * adds the diffuse tail.
 *
 * Streaming and allocation-free after construction. The room is fed with the input of the engine and
 * delayed by [alignDelay] so that it lines up with the engine's own latency.
 */
class SpatialRoom(private val sampleRate: Int, private val alignDelay: Int) {
  companion object {
    private const val SOUND_SPEED = 343.0
    private const val ROOM_X = 6.0 // front-back
    private const val ROOM_Y = 4.4 // left-right
    private const val ROOM_Z = 2.8 // floor-ceiling
    private const val LISTENER_X = 3.4
    private const val LISTENER_Y = ROOM_Y / 2
    private const val LISTENER_Z = 1.2
    private const val SPEAKER_DISTANCE = 2.0
    private const val SPEAKER_AZIMUTH = 30.0
    private const val HEAD_RADIUS = 0.0875
    /** Pressure left after one bounce on a wall. */
    private const val WALL = 0.72
    /** Reflections arriving later than this after the direct sound are left to the tail. */
    private const val MAX_TAP_S = 0.060
    private const val RT60_S = 0.38
    private const val PRE_DELAY_S = 0.010
    private const val MAX_PRE_DELAY_S = 0.060
    private const val FDN = 8
    /** Half the length of the voice band-pass, in samples: its delay, which the other path matches. */
    private const val VOICE_HALF = 191
    private val FDN_MS = doubleArrayOf(13.1, 17.3, 21.7, 26.9, 31.3, 37.9, 43.1, 49.9)
    /** Early reflections and tail, relative to the direct sound, calibrated in SpatialRoomTest. */
    internal const val EARLY_GAIN = 0.25
    internal const val TAIL_GAIN = 2.7

    private fun nextPow2(n: Int): Int { var p = 1; while (p < n) p = p shl 1; return p }
  }

  // ---- reflections: one entry per (speaker, image) ----
  private val tapCount: Int
  private val tapSource: IntArray   // 0 = left speaker, 1 = right
  private val tapDelayL: IntArray   // samples back from "now", per ear
  private val tapDelayR: IntArray
  private val tapGainL: DoubleArray
  private val tapGainR: DoubleArray
  private val tapCoefL: DoubleArray // one-pole low-pass coefficient per ear
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

  // ---- feedback delay network ----
  private val fdnLen = IntArray(FDN) { (FDN_MS[it] * sampleRate / 1000.0).roundToInt() }
  private val fdnMask = IntArray(FDN) { nextPow2(fdnLen[it] + 1) - 1 }
  private val fdnBuf = Array(FDN) { DoubleArray(fdnMask[it] + 1) }
  private val fdnGain = DoubleArray(FDN) { 10.0.pow(-3.0 * fdnLen[it] / sampleRate / RT60_S) }
  private val fdnDamp = 1 - exp(-2 * PI * 4500.0 / sampleRate)
  private val fdnLp = DoubleArray(FDN)
  private val fdnV = DoubleArray(FDN)
  private val fdnIn = doubleArrayOf(1.0, 1.0, -1.0, -1.0, 1.0, 1.0, -1.0, -1.0)
  private val fdnOutL = doubleArrayOf(1.0, 1.0, 1.0, 1.0, -1.0, -1.0, -1.0, -1.0)
  private val fdnOutR = doubleArrayOf(1.0, -1.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0)
  private var fdnPos = 0
  /** The input delay the reflections are measured from: the voice filter already delays by VOICE_HALF. */
  private val lead = max(alignDelay - VOICE_HALF, 16)
  @Volatile private var preDelay = (PRE_DELAY_S * sampleRate).roundToInt() + lead
  private var earlyMul = 1.0
  private var tailMul = 1.0
  private var lastDecay = RT60_S
  private var lastPreDelayMs = PRE_DELAY_S * 1000
  private var lastDiffusion = 0.6
  private val tailLpCoef = 1 - exp(-2 * PI * 6500.0 / sampleRate)
  private var tailLpL = 0.0
  private var tailLpR = 0.0

  private val earlyDiffuserL = Diffuser(sampleRate, doubleArrayOf(3.7, 5.9, 8.3, 11.9, 15.1), 0.6)
  private val earlyDiffuserR = Diffuser(sampleRate, doubleArrayOf(4.3, 6.7, 9.1, 12.7, 16.3), 0.6)
  private val tailDiffuser = Diffuser(sampleRate, doubleArrayOf(5.3, 7.9, 11.3, 13.7), 0.62)

  /** Linear-phase band-pass for a singing voice (300 Hz-4.5 kHz), 383 taps: it has no phase shift of its
   * own, so subtracting it from the delayed input cancels a centred voice evenly across the band. */
  private val voiceTaps = DoubleArray(2 * VOICE_HALF + 1) { n ->
    val m = n - VOICE_HALF
    val w = 0.5 + 0.5 * cos(PI * m / (VOICE_HALF + 1))
    val f1 = 300.0 / sampleRate
    val f2 = 4500.0 / sampleRate
    val ideal = if (m == 0) 2 * (f2 - f1) else (sin(2 * PI * f2 * m) - sin(2 * PI * f1 * m)) / (PI * m)
    ideal * w
  }
  private val rawMask = nextPow2(2 * VOICE_HALF + 2) - 1
  private val rawL = DoubleArray(rawMask + 1)
  private val rawR = DoubleArray(rawMask + 1)
  private val rawM = DoubleArray(rawMask + 1)
  private var rawPos = 0

  init {
    val taps = buildTaps()
    tapCount = taps.size
    tapSource = IntArray(tapCount) { taps[it].source }
    tapDelayL = IntArray(tapCount) { taps[it].delayL }
    tapDelayR = IntArray(tapCount) { taps[it].delayR }
    tapGainL = DoubleArray(tapCount) { taps[it].gainL }
    tapGainR = DoubleArray(tapCount) { taps[it].gainR }
    tapCoefL = DoubleArray(tapCount) { taps[it].coefL }
    tapCoefR = DoubleArray(tapCount) { taps[it].coefR }
    tapStateL = DoubleArray(tapCount)
    tapStateR = DoubleArray(tapCount)
    val longest = max(taps.maxOfOrNull { max(it.delayL, it.delayR) } ?: 0, lead + (MAX_PRE_DELAY_S * sampleRate).roundToInt())
    size = nextPow2(longest + 8)
    mask = size - 1
    ringL = DoubleArray(size)
    ringR = DoubleArray(size)
  }

  /** Series of Schroeder all-passes: the level stays, one echo becomes a dense short train. */
  private class Diffuser(sampleRate: Int, delaysMs: DoubleArray, var g: Double) {
    private val len = IntArray(delaysMs.size) { (delaysMs[it] * sampleRate / 1000.0).roundToInt().coerceAtLeast(2) }
    private val buf = Array(len.size) { DoubleArray(len[it]) }
    private val pos = IntArray(len.size)

    fun process(x: Double): Double {
      var v = x
      for (s in len.indices) {
        val b = buf[s]
        val d = b[pos[s]]
        val w = v + g * d
        b[pos[s]] = w
        pos[s] = if (pos[s] + 1 == len[s]) 0 else pos[s] + 1
        v = d - g * w
      }
      return v
    }

    fun clear() { buf.forEach { it.fill(0.0) }; pos.fill(0) }
  }

  private class Tap(
    val source: Int, val delayL: Int, val delayR: Int,
    val gainL: Double, val gainR: Double, val coefL: Double, val coefR: Double
  )

  /** Image sources of the two speakers up to the second order, each with its direction and loss. */
  private fun buildTaps(): List<Tap> {
    val taps = ArrayList<Tap>()
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
        val excess = (r - SPEAKER_DISTANCE) / SOUND_SPEED
        if (excess < 0 || excess > MAX_TAP_S) continue
        val azimuth = atan2(vy, vx)
        val elevation = asin((vz / r).coerceIn(-1.0, 1.0))
        // Sine of the angle to the interaural axis: +1 = straight at the left ear.
        val lateral = cos(elevation) * sin(azimuth)
        val rear = max(0.0, -cos(azimuth) * cos(elevation))
        val near = abs(lateral)
        val itd = HEAD_RADIUS / SOUND_SPEED * (asin(near.coerceIn(0.0, 1.0)) + near)
        val base = lead + excess * sampleRate
        // The ear nearer to the source hears it first: half the interaural delay each way.
        val half = itd * sampleRate / 2
        val leftIsNear = lateral >= 0
        val delayL = max(1, (if (leftIsNear) base - half else base + half).roundToInt())
        val delayR = max(1, (if (leftIsNear) base + half else base - half).roundToInt())
        val distanceGain = SPEAKER_DISTANCE / r
        val loss = WALL.pow(bounces) * distanceGain * EARLY_GAIN
        // Head shadow: the ear turned away loses level and treble.
        val nearGain = 10.0.pow(1.5 * near / 20)
        val farGain = 10.0.pow(-5.0 * near / 20)
        val gainL = loss * if (leftIsNear) nearGain else farGain
        val gainR = loss * if (leftIsNear) farGain else nearGain
        // Treble: walls, air and the head's shadow; the rear is darker (the pinna).
        val wall = 1.0 / (1 + 0.35 * (bounces - 1))
        val cutNear = (11000.0 * wall * (1 - 0.30 * rear)).coerceIn(1500.0, 14000.0)
        val cutFar = (11000.0 * wall * (1 - 0.65 * near) * (1 - 0.30 * rear)).coerceIn(1500.0, 14000.0)
        val coefNear = 1 - exp(-2 * PI * cutNear / sampleRate)
        val coefFar = 1 - exp(-2 * PI * cutFar / sampleRate)
        taps.add(
          Tap(
            source, delayL, delayR, gainL, gainR,
            if (leftIsNear) coefNear else coefFar, if (leftIsNear) coefFar else coefNear
          )
        )
      }
    }
    return taps
  }

  /** The coordinates of the source and its mirrors in the two walls of one axis, with the bounce count. */
  private fun images(s: Double, length: Double): List<Pair<Double, Int>> =
    listOf(s to 0, -s to 1, 2 * length - s to 1, s + 2 * length to 2, s - 2 * length to 2)

  /**
   * Hand settings, called from the audio thread once per hop and cheap when nothing moved: the two
   * levels (times the calibrated ones), the tail's reverberation time and pre-delay, and the feedback
   * of the diffusers.
   */
  fun tune(early: Double, tail: Double, decayS: Double, preDelayMs: Double, diffusion: Double) {
    earlyMul = early
    tailMul = tail
    if (decayS != lastDecay) {
      lastDecay = decayS
      for (j in 0 until FDN) fdnGain[j] = 10.0.pow(-3.0 * fdnLen[j] / sampleRate / decayS.coerceAtLeast(0.05))
    }
    if (preDelayMs != lastPreDelayMs) {
      lastPreDelayMs = preDelayMs
      preDelay = (preDelayMs.coerceIn(0.0, MAX_PRE_DELAY_S * 1000) * sampleRate / 1000).roundToInt() + lead
    }
    if (diffusion != lastDiffusion) {
      lastDiffusion = diffusion
      val g = diffusion.coerceIn(0.0, 0.9)
      earlyDiffuserL.g = g
      earlyDiffuserR.g = g
      tailDiffuser.g = (g + 0.02).coerceAtMost(0.9)
    }
  }

  /** Empties every line: after a seek or a new track, so nothing of the old music is heard. */
  fun clear() {
    ringL.fill(0.0); ringR.fill(0.0)
    tapStateL.fill(0.0); tapStateR.fill(0.0)
    fdnBuf.forEach { it.fill(0.0) }
    fdnLp.fill(0.0)
    earlyDiffuserL.clear(); earlyDiffuserR.clear(); tailDiffuser.clear()
    rawL.fill(0.0); rawR.fill(0.0); rawM.fill(0.0); rawPos = 0
    hpStateL = 0.0; hpStateR = 0.0
    tailLpL = 0.0; tailLpR = 0.0
    write = 0
    fdnPos = 0
  }

  /**
   * Adds the room's response to [n] samples of [inL]/[inR] into [outL]/[outR]. [levelStart] and
   * [levelEnd] are the room level at the first and the last sample; it glides between them. [voiceStart]
   * and [voiceEnd] (0..1) say how much of the centred voice is taken out of the room feed.
   */
  fun process(
    inL: DoubleArray, inR: DoubleArray, outL: DoubleArray, outR: DoubleArray,
    n: Int, levelStart: Double, levelEnd: Double,
    voiceStart: Double = 0.0, voiceEnd: Double = 0.0
  ) {
    val inv = 1.0 / FDN
    val norm = 1 / sqrt(FDN.toDouble())
    for (i in 0 until n) {
      val level = levelStart + (levelEnd - levelStart) * (i + 1) / n
      // High-pass: the room does not carry bass (it would only muddy the low end).
      // The centred voice, taken out of what the room hears: band-limited mid, scaled by its amount.
      rawL[rawPos and rawMask] = inL[i]
      rawR[rawPos and rawMask] = inR[i]
      rawM[rawPos and rawMask] = 0.5 * (inL[i] + inR[i])
      var voice = 0.0
      for (j in voiceTaps.indices) voice += voiceTaps[j] * rawM[(rawPos - j) and rawMask]
      val take = voiceStart + (voiceEnd - voiceStart) * (i + 1) / n
      // The input as it was VOICE_HALF samples ago, matching the filter output.
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
        val sl = ring[(write - tapDelayL[t]) and mask] * tapGainL[t]
        val sr = ring[(write - tapDelayR[t]) and mask] * tapGainR[t]
        tapStateL[t] += tapCoefL[t] * (sl - tapStateL[t])
        tapStateR[t] += tapCoefR[t] * (sr - tapStateR[t])
        accL += tapStateL[t]
        accR += tapStateR[t]
      }

      // Diffuse tail: the mid signal, after a pre-delay, into a Householder feedback network.
      val mid = tailDiffuser.process(0.5 * (ringL[(write - preDelay) and mask] + ringR[(write - preDelay) and mask]))
      var sum = 0.0
      for (j in 0 until FDN) {
        val raw = fdnBuf[j][(fdnPos - fdnLen[j]) and fdnMask[j]]
        fdnLp[j] += fdnDamp * (raw - fdnLp[j])
        val v = fdnLp[j] * fdnGain[j]
        fdnV[j] = v
        sum += v
      }
      val feedback = 2 * sum * inv
      var tl = 0.0
      var tr = 0.0
      for (j in 0 until FDN) {
        fdnBuf[j][fdnPos and fdnMask[j]] = fdnV[j] - feedback + mid * fdnIn[j] * norm
        tl += fdnV[j] * fdnOutL[j]
        tr += fdnV[j] * fdnOutR[j]
      }
      fdnPos++
      tailLpL += tailLpCoef * (tl * norm * TAIL_GAIN - tailLpL)
      tailLpR += tailLpCoef * (tr * norm * TAIL_GAIN - tailLpR)

      outL[i] += level * (earlyMul * earlyDiffuserL.process(accL) + tailMul * tailLpL)
      outR[i] += level * (earlyMul * earlyDiffuserR.process(accR) + tailMul * tailLpR)
      write++
    }
  }
}
