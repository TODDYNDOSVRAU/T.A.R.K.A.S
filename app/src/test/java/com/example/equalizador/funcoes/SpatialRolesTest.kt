package com.example

import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SpeakerTest
import com.example.data.audio.spatial.SphericalHeadHrtf
import com.example.data.audio.spatial.SurroundFilters
import com.example.data.audio.spatial.UpmixParams
import com.example.data.audio.spatial.Upmixer
import com.example.data.audio.spatial.Upmixer.Companion.LATENCY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/**
 * What each channel of the Cinema preset carries (centre = voice, rears = wide content and ambience, LFE =
 * managed bass), and the speaker test. Channel order: L R C LFE Ls Rs. The signals are synthetic: a source
 * panned dead centre stands for the lead voice, independent noise on each side for wide instruments.
 */
class SpatialRolesTest {
  private companion object {
    const val FS = 48000
    const val N = 65536
    const val SKIP = 12288
  }

  private val hrtf: SphericalHeadHrtf by lazy { SphericalHeadHrtf(FS) }
  private val filters: SurroundFilters by lazy { SurroundFilters(FS, hrtf, channels = 8) }

  private fun noise(seed: Long) = Random(seed).let { r -> DoubleArray(N) { 0.2 * r.nextGaussian() } }
  /** A voice-band stand-in: 60 tones of random phase between 300 Hz and 3 kHz. */
  private fun voiceBand(seed: Long): DoubleArray {
    val r = Random(seed)
    val f = DoubleArray(60) { 300.0 + (3000.0 - 300.0) * it / 59 + r.nextDouble() * 20 }
    val ph = DoubleArray(60) { r.nextDouble() * 2 * PI }
    return DoubleArray(N) { n -> var s = 0.0; for (k in 0 until 60) s += sin(2 * PI * f[k] * n / FS + ph[k]); 0.2 * s / 7.75 }
  }
  private fun interleave(l: DoubleArray, r: DoubleArray) = FloatArray(2 * N) { (if (it % 2 == 0) l[it / 2] else r[it / 2]).toFloat() }
  private fun db(x: Double) = 10 * log10(x.coerceAtLeast(1e-30))

  private fun upmix(stereo: FloatArray, params: UpmixParams): Array<FloatArray> {
    val up = Upmixer(FS, params)
    val ch = up.channels
    val padded = stereo.copyOf(stereo.size + 2 * (LATENCY + SpatialEngine.HOP * 2))
    val total = padded.size / 2
    val out = FloatArray((total + SpatialEngine.HOP) * ch)
    var written = 0
    var pos = 0
    val buf = FloatArray((4096 + SpatialEngine.HOP) * ch)
    while (pos < total) {
      val n = minOf(4096, total - pos)
      val w = up.process(padded, pos * 2, n, buf)
      System.arraycopy(buf, 0, out, written * ch, w * ch)
      written += w
      pos += n
    }
    val have = minOf(N, written - LATENCY)
    return Array(ch) { c -> FloatArray(have) { out[ch * (it + LATENCY) + c] } }
  }

  private fun power(x: FloatArray): Double {
    var s = 0.0
    for (i in SKIP until x.size) s += x[i].toDouble() * x[i]
    return s / (x.size - SKIP)
  }

  @Test fun centredVoiceGoesToCentreAndStaysOutOfTheRears() {
    val v = voiceBand(1)
    val y = upmix(interleave(v, v), UpmixParams.CINEMA)
    val base = upmix(interleave(v, v), UpmixParams())
    val c = power(y[2]); val rear = power(y[4]) + power(y[5])
    assertTrue("centre carries more than before: ${db(c)} vs ${db(power(base[2]))} dB", c > power(base[2]))
    assertTrue("centre carries most of a centred voice: C ${db(c)} dB, L ${db(power(y[0]))} dB", c > 4 * power(y[0]))
    assertTrue("rears under -20 dB of C: ${db(rear)} vs ${db(c)} dB", rear < c * 0.01)
  }

  @Test fun wideInstrumentsFillTheRearsOnTheirOwnSide() {
    val l = noise(2); val zero = DoubleArray(N)
    val y = upmix(interleave(l, zero), UpmixParams.CINEMA)
    assertTrue("a left-only source reaches Ls more than Rs by 6 dB: ${db(power(y[4]))} vs ${db(power(y[5]))}", power(y[4]) > 4 * power(y[5]))
    // A coherent instrument panned to the left (right = 0.4 x left) plus a little independent ambience.
    val a = noise(3); val amb = noise(4)
    val b = DoubleArray(N) { 0.4 * a[it] + 0.03 * amb[it] }
    val w = upmix(interleave(a, b), UpmixParams.CINEMA)
    val wb = upmix(interleave(a, b), UpmixParams())
    val rear = power(w[4]) + power(w[5]); val rearBase = power(wb[4]) + power(wb[5])
    assertTrue("rear gain of at least 3 dB: ${db(rear)} vs ${db(rearBase)}", rear > 2 * rearBase)
    assertTrue("front stays above the rears: ${db(power(w[0]) + power(w[1]))} vs ${db(rear)}", power(w[0]) + power(w[1]) > rear)
  }

  @Test fun backPairCarriesAmbienceAndFillNotTheVoice() {
    val v = voiceBand(1)
    val voice = upmix(interleave(v, v), UpmixParams.CINEMA)
    val c = power(voice[2]); val backs = power(voice[6]) + power(voice[7])
    assertTrue("a centred voice stays out of the backs: ${db(backs)} vs ${db(c)} dB", backs < c * 0.01)
    val amb = upmix(interleave(noise(20), noise(21)), UpmixParams.CINEMA)
    val sides = power(amb[4]) + power(amb[5]); val back = power(amb[6]) + power(amb[7])
    assertTrue("ambience reaches the backs: ${db(back)} vs sides ${db(sides)} dB", back > 0.1 * sides && back > 1e-5)
    val left = upmix(interleave(noise(22), DoubleArray(N)), UpmixParams.CINEMA)
    assertTrue("a left-only source reaches SBL more than SBR: ${db(power(left[6]))} vs ${db(power(left[7]))}", power(left[6]) > 2 * power(left[7]))
  }

  @Test fun neutralUpmixStaysSixChannels() {
    assertEquals(6, Upmixer(FS, UpmixParams()).channels)
    assertEquals(8, Upmixer(FS, UpmixParams.CINEMA).channels)
  }

  @Test fun totalEnergyIsKeptAndNothingClips() {
    for (seed in 10L..12L) {
      val a = noise(seed); val b = noise(seed + 100)
      val input = if (seed == 10L) interleave(a, a) else interleave(a, b)
      val y = upmix(input, UpmixParams.CINEMA)
      var pout = 0.0
      for (c in y.indices) pout += power(y[c])
      var pin = 0.0
      for (j in SKIP until N) pin += input[2 * j].toDouble() * input[2 * j] + input[2 * j + 1].toDouble() * input[2 * j + 1]
      pin /= (N - SKIP)
      assertEquals("seed $seed total power within 1.5 dB", 0.0, db(pout) - db(pin), 1.5)
      for (c in y.indices) for (v in y[c]) assertTrue("finite, under 1.0", v.isFinite() && abs(v) < 1.0f)
    }
  }

  @Test fun bassPlaysInTheLfeOnlyAndIsNotDoubled() {
    val tone = DoubleArray(N) { 0.2 * sin(2 * PI * 60.0 * it / FS) }
    val y = upmix(interleave(tone, tone), UpmixParams.CINEMA)
    val lfe = power(y[3]); val mains = power(y[0]) + power(y[1]) + power(y[2])
    assertTrue("LFE carries the bass: ${db(lfe)} dB", lfe > 1e-4)
    assertTrue("mains under -20 dB of the LFE: ${db(mains)} vs ${db(lfe)}", mains < lfe * 1e-2)
    val neutral = upmix(interleave(tone, tone), UpmixParams())
    assertTrue("neutral upmix keeps the bass in the mains", power(neutral[0]) > 1e-4)
  }

  // ---- speaker test ------------------------------------------------------------------------

  @Test fun testSoundLastsAtLeastTwoSeconds() {
    for (ch in 0..7) {
      val b = SpeakerTest.burst(ch, FS)
      var last = 0
      for (i in 0 until b.size / 8) if (abs(b[8 * i + ch]) > 0.01f) last = i
      assertTrue("channel $ch audible for ${last / FS.toDouble()} s", last >= 2 * FS)
    }
  }

  @Test fun burstPlaysInOneChannelOnly() {
    for (ch in 0..7) {
      val b = SpeakerTest.burst(ch, FS)
      for (c in 0..7) {
        var peak = 0f
        for (i in 0 until b.size / 8) peak = maxOf(peak, abs(b[8 * i + c]))
        if (c == ch) assertTrue("channel $ch has signal", peak > 0.05f) else assertEquals("channel $ch leaks into $c", 0f, peak, 0f)
      }
    }
  }

  @Test fun renderedSpeakersSitWhereTheStageDrawsThem() {
    val r = (0..7).associateWith { SpeakerTest.render(it, filters, hrtf) }
    fun ears(ch: Int) = SpeakerTest.earLevels(r.getValue(ch))
    val (ll, lr) = ears(SpeakerTest.L); val (rl, rr) = ears(SpeakerTest.R)
    val (cl, cr) = ears(SpeakerTest.C); val (sl, sr) = ears(SpeakerTest.LS); val (tl, tr) = ears(SpeakerTest.RS)
    assertTrue("L is louder in the left ear (${db(ll)} vs ${db(lr)})", ll > 1.5 * lr)
    assertTrue("R is louder in the right ear", rr > 1.5 * rl)
    assertEquals("C is centred within 1 dB", 0.0, db(cl) - db(cr), 1.0)
    assertTrue("Ls is louder in the left ear (${db(sl)} vs ${db(sr)})", sl > 1.5 * sr)
    assertTrue("Rs is louder in the right ear", tr > 1.5 * tl)
    val (bl, bll) = ears(SpeakerTest.SBL); val (brl, brr) = ears(SpeakerTest.SBR)
    assertTrue("SBL is louder in the left ear (${db(bl)} vs ${db(bll)})", bl > 1.2 * bll)
    assertTrue("SBR is louder in the right ear", brr > 1.2 * brl)
    assertTrue("the back pair is less lateral than the side pair", bl / bll < sl / sr)
    for (ch in 0..7) {
      var peak = 0.0
      for (v in r.getValue(ch)) peak = maxOf(peak, abs(v.toDouble()))
      assertTrue("channel $ch peak $peak within the limit", peak <= SpeakerTest.PEAK_LIMIT + 1e-6)
      assertTrue("channel $ch audible", peak > 0.05)
    }
  }

  @Test fun loudspeakerTestPansToTheSideAndStaysUnderTheLimit() {
    val r = (0..7).associateWith { SpeakerTest.renderLoudspeaker(it) }
    fun ears(ch: Int) = SpeakerTest.earLevels(r.getValue(ch))
    val (ll, lr) = ears(SpeakerTest.L); val (rl, rr) = ears(SpeakerTest.R); val (cl, cr) = ears(SpeakerTest.C)
    val (sl, sr) = ears(SpeakerTest.LS); val (tl, tr) = ears(SpeakerTest.RS)
    val (bl, br) = ears(SpeakerTest.SBL); val (el, er) = ears(SpeakerTest.LFE)
    assertTrue("L plays left", ll > 4 * lr); assertTrue("R plays right", rr > 4 * rl)
    assertEquals("C is centred", 0.0, db(cl) - db(cr), 0.1)
    assertTrue("Ls is wider than L", sl / sr > ll / lr); assertTrue("Rs plays right", tr > 20 * tl)
    assertTrue("SBL leans left but is less wide than Ls", bl > br && bl / br < sl / sr)
    assertEquals("LFE is centred", 0.0, db(el) - db(er), 0.1)
    for (ch in 0..7) {
      var peak = 0.0
      for (v in r.getValue(ch)) peak = maxOf(peak, abs(v.toDouble()))
      assertTrue("channel $ch audible and within the limit ($peak)", peak > 0.05 && peak <= SpeakerTest.PEAK_LIMIT + 1e-6)
    }
  }
}
