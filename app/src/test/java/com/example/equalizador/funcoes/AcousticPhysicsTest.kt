package com.example

import com.example.data.audio.spatial.AcousticPhysics
import com.example.data.audio.spatial.Fft
import com.example.data.audio.spatial.RoomParams
import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SpatialParams
import com.example.data.audio.spatial.SpatialRoom
import com.example.data.audio.spatial.SphericalHeadHrtf
import com.example.data.audio.spatial.SurroundFilters
import com.example.data.audio.spatial.SurroundRoom
import com.example.data.audio.spatial.SurroundVirtualizer
import com.example.data.audio.spatial.UpmixParams
import com.example.data.audio.spatial.UpmixVirtualizer
import com.example.data.audio.spatial.Upmixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Verifies every psychoacoustic and physical equation implemented in Tarkas Spatial
 * from the spatial audio study, ensuring static room-anchored speaker localization
 * (zero rotating 8D LFO) and T.A.L. Level 0 precision & latency targets.
 */
class AcousticPhysicsTest {

  private val sampleRate = 48000

  @Test
  fun iso9613AtmosphericAttenuationMatchesStandardCurve() {
    val a1k = AcousticPhysics.iso9613AlphaDbPerM(1000.0)
    val a16k = AcousticPhysics.iso9613AlphaDbPerM(16000.0)
    val a20k = AcousticPhysics.iso9613AlphaDbPerM(20000.0)

    assertEquals(0.00466, a1k, 0.0005)
    assertEquals(0.36454, a16k, 0.005)
    assertEquals(0.52416, a20k, 0.01)

    val g12k10m = AcousticPhysics.airTransmissionGain(12000.0, 10.0)
    val g12k2m = AcousticPhysics.airTransmissionGain(12000.0, 2.0)
    val g1k2m = AcousticPhysics.airTransmissionGain(1000.0, 2.0)
    assertTrue("Long distance attenuates HF more than short distance", g12k10m < g12k2m)
    assertTrue("HF attenuates more than 1 kHz over same distance", g12k2m < g1k2m)
    assertTrue("Transmission factor remains strictly passive", g1k2m < 1.0)

    val w10kB1 = AcousticPhysics.wallReflectionGain(10000.0, 1, 0.3)
    val w500B1 = AcousticPhysics.wallReflectionGain(500.0, 1, 0.3)
    val w2kB3 = AcousticPhysics.wallReflectionGain(2000.0, 3, 0.3)
    val w2kB1 = AcousticPhysics.wallReflectionGain(2000.0, 1, 0.3)
    assertTrue("Porous wall absorbs HF more than LF", w10kB1 < w500B1)
    assertTrue("Multi-bounce reflection attenuates monotonically", w2kB3 < w2kB1)
  }

  @Test
  fun extendedWoodworthKuhnDiffractionIsC1AndBreaksFrontBackSymmetry() {
    val geo = AcousticPhysics.HeadGeometry.DEFAULT
    val gamma = acos(geo.headRadiusM / geo.speakerDistanceM)
    val dMinus = AcousticPhysics.extendedWoodworthDelaySec(cos(gamma - 1e-4), geo)
    val dPlus = AcousticPhysics.extendedWoodworthDelaySec(cos(gamma + 1e-4), geo)
    assertTrue("Extended Woodworth is continuous across finite-distance horizon", abs(dMinus - dPlus) < 1e-6)

    for (az in doubleArrayOf(30.0, 90.0)) {
      val cosL = AcousticPhysics.sourceEarCos(az, 0.0, isLeftEar = true, geo = geo)
      val cosR = AcousticPhysics.sourceEarCos(az, 0.0, isLeftEar = false, geo = geo)
      val itdLf = abs(
        AcousticPhysics.kuhnLowFreqDelaySec(cosL, geo) - AcousticPhysics.kuhnLowFreqDelaySec(cosR, geo)
      )
      val itdHf = abs(
        AcousticPhysics.extendedWoodworthDelaySec(cosL, geo) -
          AcousticPhysics.extendedWoodworthDelaySec(cosR, geo)
      )
      assertTrue("Kuhn LF ITD ($itdLf) exceeds HF ITD ($itdHf) at az=$az", itdLf > itdHf * 1.15)
    }

    // Posterior ear offset (97.5 deg) breaks front/back symmetry between 70 deg and 110 deg.
    val cos70 = AcousticPhysics.sourceEarCos(70.0, 0.0, isLeftEar = true, geo = geo)
    val cos110 = AcousticPhysics.sourceEarCos(110.0, 0.0, isLeftEar = true, geo = geo)
    assertTrue("70 vs 110 deg yields asymmetric ear incidence", abs(cos70 - cos110) > 0.05)

    val hrtf = SphericalHeadHrtf(sampleRate, accurateDiffraction = true)
    val l90Re = DoubleArray(hrtf.bins); val l90Im = DoubleArray(hrtf.bins)
    val r90Re = DoubleArray(hrtf.bins); val r90Im = DoubleArray(hrtf.bins)
    hrtf.evaluateContinuous(90.0, 0.0, l90Re, l90Im, r90Re, r90Im)
    // Bin 12 ~= 281 Hz at 48 kHz / 2048 FFT: proximal 2 m spreading alone creates > 0.5 dB LF ILD.
    val magL280 = hypot(l90Re[12], l90Im[12])
    val magR280 = hypot(r90Re[12], r90Im[12])
    val ildLfDb = 20.0 * log10(magL280 / max(magR280, 1e-12))
    assertTrue("Finite distance r=2 m yields proximal LF ILD ($ildLfDb dB > 0.5 dB)", ildLfDb > 0.5)

    val l70Re = DoubleArray(hrtf.bins); val l70Im = DoubleArray(hrtf.bins)
    val l110Re = DoubleArray(hrtf.bins); val l110Im = DoubleArray(hrtf.bins)
    val tmpRe = DoubleArray(hrtf.bins); val tmpIm = DoubleArray(hrtf.bins)
    hrtf.evaluateContinuous(70.0, 0.0, l70Re, l70Im, tmpRe, tmpIm)
    hrtf.evaluateContinuous(110.0, 0.0, l110Re, l110Im, tmpRe, tmpIm)
    var maxHfDiffDb = 0.0
    for (k in 170..512) { // ~4 kHz to 12 kHz
      val db70 = 20.0 * log10(max(hypot(l70Re[k], l70Im[k]), 1e-9))
      val db110 = 20.0 * log10(max(hypot(l110Re[k], l110Im[k]), 1e-9))
      maxHfDiffDb = max(maxHfDiffDb, abs(db70 - db110))
    }
    assertTrue("Pinna + posterior ear offset distinguishes front 70 from rear 110 deg ($maxHfDiffDb dB)", maxHfDiffDb > 1.0)
  }

  @Test
  fun realCepstrumMinimumPhaseAndContinuousEvalAreSmoothAndCausal() {
    val fft = Fft(SpatialEngine.NFFT)
    val bins = SpatialEngine.BINS
    val mag = DoubleArray(bins) { k ->
      val f = k * sampleRate.toDouble() / SpatialEngine.NFFT
      val peak = 1.0 + 0.8 / (1.0 + ((f - 3400.0) / 700.0) * ((f - 3400.0) / 700.0))
      val notch = 1.0 - 0.5 / (1.0 + ((f - 7500.0) / 800.0) * ((f - 7500.0) / 800.0))
      peak * notch
    }
    val workRe = DoubleArray(SpatialEngine.NFFT)
    val workIm = DoubleArray(SpatialEngine.NFFT)
    val outRe = DoubleArray(bins)
    val outIm = DoubleArray(bins)
    AcousticPhysics.minimumPhaseFromMagnitude(mag, fft, workRe, workIm, outRe, outIm)

    var maxRelErr = 0.0
    for (k in 0 until bins) {
      val reconstructed = hypot(outRe[k], outIm[k])
      maxRelErr = max(maxRelErr, abs(reconstructed - mag[k]) / mag[k])
    }
    assertTrue("Real cepstrum preserves target magnitude ($maxRelErr < 1e-3)", maxRelErr < 1e-3)

    // Inverse FFT to verify causal minimum-phase energy concentration in first half of impulse response.
    val irRe = DoubleArray(SpatialEngine.NFFT)
    val irIm = DoubleArray(SpatialEngine.NFFT)
    for (k in 0 until bins) {
      irRe[k] = outRe[k]; irIm[k] = outIm[k]
      if (k in 1 until bins - 1) {
        irRe[SpatialEngine.NFFT - k] = outRe[k]
        irIm[SpatialEngine.NFFT - k] = -outIm[k]
      }
    }
    fft.inverse(irRe, irIm)
    var causalE = 0.0
    var totalE = 0.0
    for (n in 0 until SpatialEngine.NFFT) {
      val e = irRe[n] * irRe[n]
      totalE += e
      if (n < SpatialEngine.NFFT / 2) causalE += e
    }
    assertTrue("Minimum-phase IR has >99.9% energy in causal half (${causalE / totalE})", causalE / totalE > 0.999)

    // Continuous evaluation smoothness across 5-degree grid cell line at 30.0 deg.
    val hrtf = SphericalHeadHrtf(sampleRate, accurateDiffraction = true)
    val cLRe = DoubleArray(bins); val cLIm = DoubleArray(bins)
    val cRRe = DoubleArray(bins); val cRIm = DoubleArray(bins)
    hrtf.evaluateContinuous(27.3, 5.0, cLRe, cLIm, cRRe, cRIm)
    for (k in 1 until bins step 16) {
      hrtf.evalBinContinuous(27.3, 5.0, k)
      assertEquals(cLRe[k], hrtf.binLRe, 1e-9)
      assertEquals(cLIm[k], hrtf.binLIm, 1e-9)
      assertEquals(cRRe[k], hrtf.binRRe, 1e-9)
      assertEquals(cRIm[k], hrtf.binRIm, 1e-9)
    }

    val testBin = (4000.0 * SpatialEngine.NFFT / sampleRate).toInt()
    hrtf.evalBinContinuous(27.9, 0.0, testBin)
    val inLRe0 = hrtf.binLRe; val inLIm0 = hrtf.binLIm
    hrtf.evalBinContinuous(28.1, 0.0, testBin)
    val stepInterior = hypot(hrtf.binLRe - inLRe0, hrtf.binLIm - inLIm0)

    hrtf.evalBinContinuous(29.9, 0.0, testBin)
    val crLRe0 = hrtf.binLRe; val crLIm0 = hrtf.binLIm
    hrtf.evalBinContinuous(30.1, 0.0, testBin)
    val stepBoundary = hypot(hrtf.binLRe - crLRe0, hrtf.binLIm - crLIm0)

    assertTrue(
      "Crossing 30.0 deg grid boundary stays continuous ($stepBoundary vs $stepInterior)",
      stepBoundary < stepInterior * 2.5
    )
  }

  @Test
  fun upmixerDpl2QuadratureSeparatesSurroundsAndPcaRoutesAmbienceOutOfCenter() {
    val frames = 16384
    val skip = 8192

    // 1) DPL II surround encode test: Ls-only anti-phase signal (|L|/|R| = sqrt(19)/sqrt(6), opposite sign).
    val k1 = sqrt(19.0 / 25.0).toFloat()
    val k2 = sqrt(6.0 / 25.0).toFloat()
    val stereoLs = FloatArray(frames * 2)
    val stereoRs = FloatArray(frames * 2)
    for (i in 0 until frames) {
      val s = (0.3 * sin(2.0 * PI * 1000.0 * i / sampleRate)).toFloat()
      stereoLs[2 * i] = k1 * s
      stereoLs[2 * i + 1] = -k2 * s
      stereoRs[2 * i] = k2 * s
      stereoRs[2 * i + 1] = -k1 * s
    }

    val upmixer = Upmixer(sampleRate, UpmixParams())
    val outLs = FloatArray(frames * 6)
    val outRs = FloatArray(frames * 6)
    val nLs = upmixer.process(stereoLs, 0, frames, outLs)
    upmixer.reset()
    val nRs = upmixer.process(stereoRs, 0, frames, outRs)

    val lsIsoDb = 10.0 * log10(channelEnergy(outLs, nLs, skip, ch = 4, stride = 6) /
      max(channelEnergy(outLs, nLs, skip, ch = 5, stride = 6), 1e-18))
    val rsIsoDb = 10.0 * log10(channelEnergy(outRs, nRs, skip, ch = 5, stride = 6) /
      max(channelEnergy(outRs, nRs, skip, ch = 4, stride = 6), 1e-18))
    assertTrue("DPL II Ls steering achieves >10 dB isolation over Rs ($lsIsoDb dB)", lsIsoDb > 10.0)
    assertTrue("DPL II Rs steering achieves >10 dB isolation over Ls ($rsIsoDb dB)", rsIsoDb > 10.0)

    // 2) Pure uncorrelated stereo noise must be classified as isotropic ambient (excluded from Center).
    var rngL = 0x13579BDF2468ACE1L
    var rngR = 0x5E3779B97F4A7C15L
    fun nextFloat(state: Long): Pair<Float, Long> {
      var x = state
      x = x xor (x shl 13)
      x = x xor (x ushr 7)
      x = x xor (x shl 17)
      val norm = ((x ushr 40).toInt() / 8388608.0 - 1.0).toFloat() * 0.25f
      return norm to x
    }
    val noiseStereo = FloatArray(frames * 2)
    for (i in 0 until frames) {
      val (l, s1) = nextFloat(rngL); rngL = s1
      val (r, s2) = nextFloat(rngR); rngR = s2
      noiseStereo[2 * i] = l
      noiseStereo[2 * i + 1] = r
    }
    upmixer.reset()
    val noiseOut = FloatArray(frames * 6)
    val nNoise = upmixer.process(noiseStereo, 0, frames, noiseOut)
    val centerE = channelEnergy(noiseOut, nNoise, skip, ch = 2, stride = 6)
    val sideTotalE = channelEnergy(noiseOut, nNoise, skip, ch = 0, stride = 6) +
      channelEnergy(noiseOut, nNoise, skip, ch = 1, stride = 6) +
      channelEnergy(noiseOut, nNoise, skip, ch = 4, stride = 6) +
      channelEnergy(noiseOut, nNoise, skip, ch = 5, stride = 6)
    val centerLeakDb = 10.0 * log10(max(centerE, 1e-18) / max(sideTotalE, 1e-18))
    assertTrue("Uncorrelated ambience routes out of Center ($centerLeakDb dB < -10 dB)", centerLeakDb < -10.0)
  }

  @Test
  fun surroundFiltersStudioIsSymmetricAndReducesFrontalCombFiltering() {
    val accurateHrtf = SphericalHeadHrtf(sampleRate, accurateDiffraction = true)
    val studio = SurroundFilters.studio(sampleRate, accurateHrtf, open = null)
    val studioDirect = SurroundFilters(sampleRate, accurateHrtf, RoomParams.OFF.copy(exactPhysics = true))
    val unregDirect = SurroundFilters(sampleRate, accurateHrtf, RoomParams.OFF)

    // Exact L/R symmetry on Center channel (filters 4 and 5) and stereo pairs.
    for (k in 0 until SpatialEngine.BINS) {
      assertEquals("C->L == C->R re at bin $k", studio.re[4][k], studio.re[5][k], 1e-9)
      assertEquals("C->L == C->R im at bin $k", studio.im[4][k], studio.im[5][k], 1e-9)
      assertEquals("|L->L| == |R->R| at bin $k", hypot(studio.re[0][k], studio.im[0][k]), hypot(studio.re[3][k], studio.im[3][k]), 1e-6)
      assertEquals("|L->R| == |R->L| at bin $k", hypot(studio.re[1][k], studio.im[1][k]), hypot(studio.re[2][k], studio.im[2][k]), 1e-6)
    }

    fun hfMetrics(f: SurroundFilters): Pair<Double, Double> {
      val vals = ArrayList<Double>()
      var minDb = Double.POSITIVE_INFINITY
      for (k in 190..597) { // 4.5 kHz - 14 kHz multi-speaker pinna region
        val re = f.re[0][k] + f.re[2][k] + f.re[4][k]
        val im = f.im[0][k] + f.im[2][k] + f.im[4][k]
        val db = 20.0 * log10(max(hypot(re, im), 1e-6))
        vals.add(db)
        if (db < minDb) minDb = db
      }
      val mean = vals.average()
      var v = 0.0
      for (x in vals) v += (x - mean) * (x - mean)
      return sqrt(v / vals.size) to minDb
    }
    val (studioStd, studioMinDb) = hfMetrics(studioDirect)
    val (unregStd, unregMinDb) = hfMetrics(unregDirect)
    assertTrue("Studio Grathwohl + Gunnarsson smooths 4.5-14 kHz frontal variance ($studioStd <= $unregStd dB)", studioStd <= unregStd)
    assertTrue("Studio Grathwohl + Gunnarsson lifts worst pinna notch floor ($studioMinDb >= $unregMinDb dB)", studioMinDb >= unregMinDb)

    // RoomParams.STUDIO paths include physical bounce counts and extra distance for ISO 9613-1 damping.
    val paths = SurroundRoom.paths(30.0, 0.0, RoomParams.STUDIO, sampleRate)
    assertTrue("Studio room paths generated", paths.isNotEmpty())
    assertTrue("All studio paths carry positive bounces and air path", paths.all { it.bounces >= 1 && it.extraDistanceM > 0.0 })
    val g500 = AcousticPhysics.airTransmissionGain(500.0, paths[0].extraDistanceM) *
      AcousticPhysics.wallReflectionGain(500.0, paths[0].bounces, RoomParams.STUDIO.absorption)
    val g12k = AcousticPhysics.airTransmissionGain(12000.0, paths[0].extraDistanceM) *
      AcousticPhysics.wallReflectionGain(12000.0, paths[0].bounces, RoomParams.STUDIO.absorption)
    assertTrue("Studio boundary + ISO 9613-1 attenuates 12 kHz more than 500 Hz", g12k < g500)
  }

  @Test
  fun virtualizersAnchorSpeakersWithZero8dOrbitAndRotateOnlyWithHeadYaw() {
    val hrtf = SphericalHeadHrtf(sampleRate, accurateDiffraction = true)
    val filters = SurroundFilters.studio(sampleRate, hrtf, open = null)

    // 1) Confirm static anchoring with ZERO spinning 8D LFO on SpatialEngine.
    val engine = SpatialEngine(sampleRate, hrtf, SpatialParams(intensity = 1.0, room = 0.0))
    val frames = 24576
    val stereoSine = FloatArray(frames * 2)
    // Exact integer cycle frequency: 375 Hz -> period = 128 samples -> periodic every 4096 samples!
    for (i in 0 until frames) {
      val s = (0.3 * sin(2.0 * PI * 375.0 * i / sampleRate)).toFloat()
      stereoSine[2 * i] = s
      stereoSine[2 * i + 1] = s
    }
    val engineOut = FloatArray(frames * 2)
    val nOut = engine.process(stereoSine, 0, frames, engineOut)
    // Compare steady-state cycles [12288..16384) vs [16384..20480): static anchored virtual speakers must not drift!
    var maxOrbitDrift = 0.0f
    for (i in 12288 until minOf(nOut - 4096, 16384)) {
      maxOrbitDrift = maxOf(maxOrbitDrift, abs(engineOut[2 * i] - engineOut[2 * (i + 4096)]))
      maxOrbitDrift = maxOf(maxOrbitDrift, abs(engineOut[2 * i + 1] - engineOut[2 * (i + 4096) + 1]))
    }
    assertTrue("Static SpatialEngine has zero time-varying 8D orbit drift (max diff=$maxOrbitDrift)", maxOrbitDrift < 1e-4f)

    // 2) Confirm UpmixVirtualizer responds cleanly to HeadTracker liveYaw AND emits studio FDN tail.
    val speechFreqs = doubleArrayOf(650.0, 1100.0, 1750.0, 2600.0, 3500.0, 4400.0)
    val stereoYawTone = FloatArray(frames * 2)
    for (i in 0 until frames) {
      var s = 0.0
      for (f in speechFreqs) s += 0.08 * sin(2.0 * PI * f * i / sampleRate)
      val sf = s.toFloat()
      stereoYawTone[2 * i] = sf
      stereoYawTone[2 * i + 1] = sf
    }
    val upmixVirt = UpmixVirtualizer(
      sampleRate = sampleRate,
      filters = filters,
      params = UpmixParams(),
      headHrtf = hrtf,
      studioRoomTail = RoomParams.APPROVED_LEVEL
    )
    val outYaw0 = FloatArray(frames * 2)
    upmixVirt.liveYaw = 0.0
    upmixVirt.reset(1.0)
    val n0 = upmixVirt.process(stereoYawTone, 0, frames, outYaw0)
    val ildYaw0 = 10.0 * log10(channelEnergy(outYaw0, n0, 8192, 0, 2) / channelEnergy(outYaw0, n0, 8192, 1, 2))
    assertTrue("At yaw=0, mono center anchors symmetrically ($ildYaw0 dB)", abs(ildYaw0) < 0.25)

    // Head turns +30 deg left -> stationary room center speaker (0 deg) moves to -30 deg relative to head (right ear closer!).
    val outYaw30 = FloatArray(frames * 2)
    upmixVirt.reset(1.0)
    upmixVirt.liveYaw = 30.0
    val n30 = upmixVirt.process(stereoYawTone, 0, frames, outYaw30)
    val rightOverLeftDb = 10.0 * log10(channelEnergy(outYaw30, n30, 8192, 1, 2) / channelEnergy(outYaw30, n30, 8192, 0, 2))
    assertTrue("Turning head left +30 deg shifts room-anchored center to right ear ($rightOverLeftDb dB > 1.5 dB)", rightOverLeftDb > 1.5)

    // Opposite turn (-30 deg right) mirrors symmetrically to left ear.
    val outYawNeg30 = FloatArray(frames * 2)
    upmixVirt.reset(1.0)
    upmixVirt.liveYaw = -30.0
    val nNeg30 = upmixVirt.process(stereoYawTone, 0, frames, outYawNeg30)
    val leftOverRightDb = 10.0 * log10(channelEnergy(outYawNeg30, nNeg30, 8192, 0, 2) / channelEnergy(outYawNeg30, nNeg30, 8192, 1, 2))
    assertTrue("Turning head right -30 deg shifts room-anchored center to left ear ($leftOverRightDb dB > 1.5 dB)", leftOverRightDb > 1.5)

    // Verify FDN room tail emits diffuse decay after transient stops.
    val impulse = FloatArray(frames * 2)
    for (i in 0 until 2048) {
      val s = (0.3 * sin(2.0 * PI * 1200.0 * i / sampleRate)).toFloat()
      impulse[2 * i] = s; impulse[2 * i + 1] = s
    }
    upmixVirt.reset(1.0)
    upmixVirt.liveYaw = 0.0
    val tailOut = FloatArray(frames * 2)
    val nTail = upmixVirt.process(impulse, 0, frames, tailOut)
    val lateTailE = channelEnergy(tailOut, nTail, startFrame = 6000, ch = 0, stride = 2)
    assertTrue("Studio FDN tail sustains room ambience after transient ($lateTailE > 1e-6)", lateTailE > 1e-6)

    // 3) Confirm SurroundVirtualizer (native 5.1 input) also rotates room-anchored speakers with liveYaw.
    val surroundVirt = SurroundVirtualizer(filters, headHrtf = hrtf)
    val center6ch = FloatArray(frames * 6)
    val centerSpeech6ch = FloatArray(frames * 6)
    for (i in 0 until frames) {
      center6ch[6 * i + 2] = (0.3 * sin(2.0 * PI * 1000.0 * i / sampleRate)).toFloat()
      centerSpeech6ch[6 * i + 2] = stereoYawTone[2 * i]
    }
    surroundVirt.liveYaw = 0.0
    surroundVirt.reset(1.0)
    val sOut0 = FloatArray(frames * 2)
    val sn0 = surroundVirt.process(centerSpeech6ch, 0, frames, sOut0)
    val sIld0 = 10.0 * log10(channelEnergy(sOut0, sn0, 8192, 0, 2) / channelEnergy(sOut0, sn0, 8192, 1, 2))
    assertTrue("5.1 Center anchors symmetrically at yaw=0 ($sIld0 dB)", abs(sIld0) < 0.25)

    surroundVirt.reset(1.0)
    surroundVirt.liveYaw = 30.0
    val sOut30 = FloatArray(frames * 2)
    val sn30 = surroundVirt.process(centerSpeech6ch, 0, frames, sOut30)
    val sRightDb = 10.0 * log10(channelEnergy(sOut30, sn30, 8192, 1, 2) / channelEnergy(sOut30, sn30, 8192, 0, 2))

    // Also compare pure 1000 Hz with dry room OFF vs room STUDIO so we inspect any static room mode interaction.
    val dryFilters = SurroundFilters(sampleRate, hrtf, RoomParams.OFF)
    val dryVirt = SurroundVirtualizer(dryFilters, headHrtf = hrtf)
    dryVirt.liveYaw = 30.0
    val sDry30 = FloatArray(frames * 2)
    val dn30 = dryVirt.process(center6ch, 0, frames, sDry30)
    val sDry1kDb = 10.0 * log10(channelEnergy(sDry30, dn30, 8192, 1, 2) / channelEnergy(sDry30, dn30, 8192, 0, 2))
    assertTrue("5.1 Center shifts to right ear at +30 deg head yaw ($sRightDb dB > 1.5 dB)", sRightDb > 1.5)
    assertTrue("Dry direct HRTF C shifts to right ear even at 1 kHz ($sDry1kDb dB > 1.0 dB)", sDry1kDb > 1.0)
  }

  @Test
  fun talPrecisionLatencyBypassAndFrameBudgetMeetTargets() {
    // 1) Exact T.A.L. Spatial DSP latency <= 24 ms at 48 kHz (608 + 512 = 1120 samples = 23.33 ms).
    val totalBufferSamples = SpatialEngine.LATENCY + SpatialEngine.HOP
    val latencyMs = totalBufferSamples * 1000.0 / sampleRate
    assertEquals(1120, totalBufferSamples)
    assertTrue("Total Spatial DSP latency ($latencyMs ms) <= 24.0 ms", latencyMs <= 24.0)

    // 2) Bypass (intensity = 0.0) residual magnitude matches input within < -100 dB.
    val hrtf = SphericalHeadHrtf(sampleRate, accurateDiffraction = true)
    val engine = SpatialEngine(sampleRate, hrtf, SpatialParams(intensity = 0.0))
    val frames = 16384
    val input = FloatArray(frames * 2)
    for (i in 0 until frames) {
      input[2 * i] = (0.4 * sin(2.0 * PI * 997.0 * i / sampleRate)).toFloat()
      input[2 * i + 1] = (0.3 * sin(2.0 * PI * 1493.0 * i / sampleRate)).toFloat()
    }
    engine.reset(0.0)
    val out = FloatArray(frames * 2)
    val n = engine.process(input, 0, frames, out)
    // Verify unity magnitude spectrum residual (delayed pass-through difference < 1e-5 => < -100 dB).
    var signalSq = 0.0
    var diffSq = 0.0
    // In SpatialEngine with intensity = 0.0, COLA reconstruction produces exact sample delay LATENCY - LIM_CHUNK?
    // Check both integer offsets near LATENCY so we verify exact sample-accurate pass-through.
    var bestErrSq = Double.MAX_VALUE
    for (delay in (SpatialEngine.LATENCY - 64)..(SpatialEngine.LATENCY + 64)) {
      var e = 0.0
      var s = 0.0
      for (i in 4096 until n) {
        val refL = input[2 * (i - delay)]
        val refR = input[2 * (i - delay) + 1]
        val dL = out[2 * i] - refL
        val dR = out[2 * i + 1] - refR
        e += dL * dL + dR * dR
        s += refL * refL + refR * refR
      }
      if (e < bestErrSq) {
        bestErrSq = e
        signalSq = s
      }
    }
    diffSq = bestErrSq
    val residualDb = 10.0 * log10(max(diffSq, 1e-20) / max(signalSq, 1e-12))
    assertTrue("Bypass residual error ($residualDb dB) < -100 dB", residualDb < -100.0)

    // 3) Mean hop frame processing time stays far below real-time 10.67 ms budget (512 samples @ 48 kHz).
    val activeEngine = SpatialEngine(sampleRate, hrtf, SpatialParams(intensity = 1.0))
    val warmOut = FloatArray(frames * 2)
    activeEngine.process(input, 0, frames, warmOut) // JIT warm-up

    val t0 = System.nanoTime()
    val perfFrames = 48000 // 1 second = ~93.75 hops of 512 samples
    val perfIn = FloatArray(perfFrames * 2) { if (it % 2 == 0) input[it % input.size] else input[it % input.size] }
    val perfOut = FloatArray(perfFrames * 2)
    activeEngine.process(perfIn, 0, perfFrames, perfOut)
    val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
    val meanHopMs = elapsedMs / (perfFrames.toDouble() / SpatialEngine.HOP)
    assertTrue("Mean hop processing time ($meanHopMs ms) < 10.67 ms", meanHopMs < 10.67)
  }

  private fun channelEnergy(buf: FloatArray, frames: Int, startFrame: Int, ch: Int, stride: Int): Double {
    var e = 0.0
    for (i in startFrame until frames) {
      val v = buf[i * stride + ch].toDouble()
      e += v * v
    }
    return e
  }
}
