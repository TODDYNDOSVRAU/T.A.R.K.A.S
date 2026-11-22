package com.example.data.assistant

import com.example.data.model.CrossfeedPreset
import com.example.data.model.EqSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Sprint5AssistantProfilesTest {

  @Test
  fun parseDspCommand_recognizesCrossfeedModesAndOff() {
    assertEquals(DspCommand.Crossfeed(CrossfeedPreset.BS2B_DEFAULT), parseDspCommand("ativar crossfeed bs2b"))
    assertEquals(DspCommand.Crossfeed(CrossfeedPreset.BS2B_CMOY), parseDspCommand("ligar crossfeed chu moy"))
    assertEquals(DspCommand.Crossfeed(CrossfeedPreset.BS2B_JMEIER), parseDspCommand("crossfeed jan meier"))
    assertEquals(DspCommand.Crossfeed(CrossfeedPreset.OFF), parseDspCommand("desligar crossfeed"))
  }

  @Test
  fun parseDspCommand_recognizesAdvancedDspAndAppliesWithDefaults() {
    val base = EqSettings()

    val loudCmd = parseDspCommand("ativar equal loudness")
    assertEquals(DspCommand.EqualLoudness(true), loudCmd)
    assertTrue(applyDspCommand(base, loudCmd!!)!!.equalLoudnessEnabled)

    val tubeCmd = parseDspCommand("colocar calor valvulado 60%")
    assertEquals(DspCommand.TubeWarmth(0.6f), tubeCmd)
    assertEquals(0.6f, applyDspCommand(base, tubeCmd!!)!!.tubeWarmth, 1e-4f)

    val subCmd = parseDspCommand("sub-grave 40%")
    assertEquals(DspCommand.SubBass(0.4f), subCmd)
    assertEquals(0.4f, applyDspCommand(base, subCmd!!)!!.subBassHarmonic, 1e-4f)

    val compCmd = parseDspCommand("ativar compander 30%")
    assertEquals(DspCommand.Compander(0.3f), compCmd)
    assertEquals(0.3f, applyDspCommand(base, compCmd!!)!!.companderAmount, 1e-4f)

    val softCmd = parseDspCommand("ativar soft clip")
    assertEquals(DspCommand.Limiter(softClip = true), softCmd)
    assertTrue(applyDspCommand(base, softCmd!!)!!.limiterSoftClip)

    val offSoftCmd = parseDspCommand("desligar soft-clip")
    assertFalse(applyDspCommand(base.copy(limiterSoftClip = true), offSoftCmd!!)!!.limiterSoftClip)
  }

  @Test
  fun parseDspCommand_recognizesPeqBandAdjustAndAbLoop() {
    val base = EqSettings()
    val boost = parseDspCommand("subir 60 hz em 3 db") as DspCommand.PeqBandAdjust
    assertEquals(60f, boost.freqHz, 0.1f)
    assertEquals(3f, boost.deltaDb, 0.01f)
    val afterBoost = applyDspCommand(base, boost)!!
    // Closest graphic band to 60 Hz is 63 Hz (index 1)
    assertEquals(3f, afterBoost.bands[1].gainDb, 0.01f)

    val cut = parseDspCommand("cortar 4k 2.5 db") as DspCommand.PeqBandAdjust
    assertEquals(4000f, cut.freqHz, 0.1f)
    assertEquals(-2.5f, cut.deltaDb, 0.01f)

    val loop = parseDspCommand("marcar ponto a loop ab")
    assertEquals(DspCommand.AbLoop, loop)
    assertNull(applyDspCommand(base, loop!!))
  }
}
