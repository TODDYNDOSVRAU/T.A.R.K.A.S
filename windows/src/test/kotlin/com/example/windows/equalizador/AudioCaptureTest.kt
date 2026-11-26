package com.example.windows.equalizador

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.data.audio.loudness.LoudnessUi
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.ui.theme.TarkasTheme
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Evidence for the Audio screen: the window only (captureToImage, never the display), at the four desktop
 * sizes in light and dark. Runs only with `AUDIO_CAPTURE_DIR=<folder>`, so the normal suite stays fast.
 * Besides the PNGs it asserts: no text with visual overflow, nothing outside the window, contrast of the
 * three text roles, and that Ice (#7EE3EA) never appears on a light capture.
 */
@OptIn(ExperimentalTestApi::class)
class AudioCaptureTest {
  private val sizes = listOf(1024 to 640, 1366 to 768, 1920 to 1080, 2560 to 1440)
  private val ice = Color(0xFF7EE3EA)

  private fun contrast(a: Color, b: Color): Double {
    val l1 = a.luminance().toDouble(); val l2 = b.luminance().toDouble()
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
  }

  @Test fun captureAudioScreen() {
    val dir = System.getenv("AUDIO_CAPTURE_DIR") ?: System.getProperty("audio.capture.dir")
    assumeTrue(dir != null)
    val out = File(dir!!).also { it.mkdirs() }
    val base = EqSettings()
    val settings = base.copy(
      bands = base.bands.mapIndexed { i, b -> if (i % 3 == 0) b.copy(gainDb = if (i % 2 == 0) 4f else -3f) else b },
      bassDb = 2f
    )
    val problems = mutableListOf<String>()
    val shots = sizes.flatMap { (w, h) -> listOf("light", "dark").map { Triple(w to h, it, false) } } +
      listOf("light", "dark").map { Triple(1366 to 768, it, true) }
    for ((size, theme, parametric) in shots) runDesktopComposeUiTest(width = size.first, height = size.second) {
      val (w, h) = size
      val kind = if (parametric) "parametric" else "graphic"
      val label = "${w}x$h $theme $kind"
      var scheme: androidx.compose.material3.ColorScheme? = null
      setContent {
        TarkasTheme(themeMode = theme) {
          scheme = MaterialTheme.colorScheme
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AudioScreen(
              capabilities = AudioCapabilities(
                deviceModel = "Windows PC", androidVersion = "Windows 11", apiLevel = 0, activeRouteName = "Speakers",
                activeRouteType = AudioCapabilities.ROUTE_OTHER, sampleRateHz = 48_000, channelCount = 2, channelFormat = "stereo",
                isAudioSessionActive = true, audioSessionId = 0, isHardwareEqualizerAvailable = false, isBassBoostAvailable = false,
                isVirtualizerAvailable = false, effectsCount = 0
              ),
              eqSettings = if (parametric) settings.withLayout(true) else settings, presets = EqPresets.BUILT_IN, activePresetId = EqPresets.BUILT_IN.first().id, eqModified = true,
              spectrumBars = { List(32) { (it % 7) / 8f } }, loudness = LoudnessUi(),
              onSetEnabled = {}, onUpdateEq = {}, onUpdateBand = { _, _ -> }, onSelectPreset = {}, onSavePresetAs = {},
              onOverwritePreset = {}, onRenamePreset = { _, _ -> }, onDeletePreset = {}, onReset = {}
            )
          }
        }
      }
      waitForIdle()
      val image = onRoot().captureToImage().toAwtImage()
      assertEquals("$label width", w, image.width); assertEquals("$label height", h, image.height)
      assertTrue(ImageIO.write(image, "png", File(out, if (parametric) "audio-${w}x$h-$theme-parametric-landscape.png" else "audio-${w}x$h-$theme-landscape.png")))

      // Text that does not fit its box (checked again on the preset pane and the advanced page below).
      fun checkTexts(where: String) {
        val texts = onAllNodes(SemanticsMatcher("text") { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }).fetchSemanticsNodes()
        for (n in texts) {
          val results = mutableListOf<TextLayoutResult>()
          n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
          val r = results.firstOrNull() ?: continue
          val shown = r.layoutInput.text.text
          val last = r.lineCount - 1
          val cut = r.multiParagraph.didExceedMaxLines || (0..last).any { r.isLineEllipsized(it) } || r.getLineEnd(last, visibleEnd = true) < shown.length
          if (cut) problems += "$where: text overflows: \"$shown\""
          if (n.boundsInRoot.right > w + 0.5f || n.boundsInRoot.bottom > h + 0.5f || n.boundsInRoot.left < -0.5f)
            problems += "$where: text outside the window: \"$shown\" ${n.boundsInRoot}"
        }
      }
      checkTexts(label)
      // The grid: the rack is 360 dp wide and its cards fill it (visible in the PNGs); the card tags sit inside the 12 dp padding, so they measure 336.
      if (!parametric) {
        fun box(tag: String) = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertEquals("$label rack width", 360f, box("eq_tablet_rack").width.value, 0.5f)
        for (tag in listOf("eq_rack_tone_gain", "eq_rack_output_protection")) assertEquals("$label $tag content width (tag sits inside the 12 dp padding: 360 dp card less 12 dp each side)", 336f, box(tag).width.value, 0.5f)
        for (tag in listOf("switch_limiter", "eq_settings_advanced")) assertEquals("$label $tag height", 48f, box(tag).height.value, 0.5f)
        // The three gain controls are one control: a 48 dp knob each, and a 48 dp value target.
        for (tag in listOf("knob_preamp", "knob_bass", "knob_treble")) {
          assertEquals("$label $tag width", 48f, box(tag).width.value, 0.5f); assertEquals("$label $tag height", 48f, box(tag).height.value, 0.5f)
        }
        for (tag in listOf("fader_preamp_strip", "value_bass", "value_treble")) assertEquals("$label $tag height", 48f, box(tag).height.value, 0.5f)
        // The knobs of the three rows share one column; the two cards do not overlap and stay in the window.
        assertEquals("$label knob column", box("knob_preamp").left.value, box("knob_bass").left.value, 0.5f)
        assertEquals("$label knob column", box("knob_bass").left.value, box("knob_treble").left.value, 0.5f)
        assertTrue("$label cards overlap", box("eq_rack_tone_gain").bottom.value <= box("eq_rack_output_protection").top.value + 0.5f)
        // Tall enough for both: the two cards share the column height, so neither is left much shorter than the other.
        if (h >= 768) {
          val a = box("eq_rack_tone_gain").height.value; val b = box("eq_rack_output_protection").height.value
          assertTrue("$label cards unequal: $a vs $b", kotlin.math.abs(a - b) <= 24f)
        }
      }
      // The preset pane and the advanced page, same checks: nothing cut, nothing outside the window.
      if (!parametric) {
        onNodeWithTag("eq_preset_button").performClick(); waitForIdle()
        val pane = onNodeWithTag("audio_side_pane").getUnclippedBoundsInRoot()
        assertTrue("$label preset pane outside the window: $pane", pane.right.value <= w + 0.5f && pane.bottom.value <= h + 0.5f)
        assertTrue(ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "audio-${w}x$h-$theme-presets-landscape.png")))
        checkTexts("$label presets")
        onNodeWithTag("audio_side_pane_close").performClick(); waitForIdle()
        onNodeWithTag("eq_settings_advanced").performClick(); waitForIdle()
        val back = onNodeWithTag("eq_subpage_back").getUnclippedBoundsInRoot()
        assertTrue("$label advanced header outside the window: $back", back.right.value <= w + 0.5f)
        assertTrue(ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "audio-${w}x$h-$theme-advanced-landscape.png")))
        checkTexts("$label advanced")
      }
      // Contrast of the three text roles on the surfaces they sit on (WCAG AA: 4.5).
      val c = scheme!!
      for ((name, fg, bg) in listOf(
        Triple("onSurface/background", c.onSurface, c.background),
        Triple("onSurfaceVariant/background", c.onSurfaceVariant, c.background),
        Triple("primary/background", c.primary, c.background)
      )) if (contrast(fg, bg) < 4.5) problems += "$label: contrast $name ${"%.2f".format(contrast(fg, bg))}"
      // Ice never on light: no pixel within a small distance of #7EE3EA in a light capture.
      if (theme == "light") {
        var hits = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
          val p = image.getRGB(x, y)
          val dr = ((p shr 16) and 255) - 0x7E; val dg = ((p shr 8) and 255) - 0xE3; val db = (p and 255) - 0xEA
          if (dr * dr + dg * dg + db * db < 12 * 12) hits++
        }
        if (hits > 0) problems += "$label: $hits pixels of Ice on light"
      }
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }
}
