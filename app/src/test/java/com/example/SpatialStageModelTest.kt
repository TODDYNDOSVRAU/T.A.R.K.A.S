package com.example

import com.example.data.audio.spatial.SpatialPreset
import com.example.ui.screens.audio.STAGE_MORPH_SECONDS
import com.example.ui.screens.audio.StageAnim
import com.example.ui.screens.audio.StageScene
import com.example.ui.screens.audio.stageTargetM
import com.example.ui.screens.audio.stageTargetOn
import com.example.ui.screens.audio.stageTargetU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Spatial stage: what each state asks of the scene, and that the meshes and frames are sound. */
class SpatialStageModelTest {

  @Test fun targetDistancePerState() {
    assertEquals(0f, stageTargetU(false, SpatialPreset.CINEMA), 0f)
    assertEquals(0.22f, stageTargetU(true, SpatialPreset.SUBTLE), 0f)
    assertEquals(0.5f, stageTargetU(true, SpatialPreset.STANDARD), 0f)
    assertEquals(1f, stageTargetU(true, SpatialPreset.WIDE), 0f)
    assertEquals(1f, stageTargetU(true, SpatialPreset.CINEMA), 0f)
  }

  @Test fun onlyCinemaMorphsToSixBoxes() {
    assertEquals(1f, stageTargetM(true, SpatialPreset.CINEMA), 0f)
    assertEquals(0f, stageTargetM(false, SpatialPreset.CINEMA), 0f)
    assertEquals(0f, stageTargetM(true, SpatialPreset.WIDE), 0f)
    assertEquals(0f, stageTargetOn(false), 0f)
    assertEquals(1f, stageTargetOn(true), 0f)
  }

  @Test fun meshesHaveFacesAndFiniteVertices() {
    val scene = StageScene()
    assertEquals(1 + 2 + 1 + 18 + 2 + 6, scene.objs.size)
    assertEquals(6, scene.speakers.size)
    for (o in scene.objs) {
      assertTrue(o.faceCount > 0)
      assertTrue(o.v.all { it.isFinite() })
      assertTrue(o.fRef.all { it.isFinite() })
    }
  }

  @Test fun morphStaysInBoundsAndTakesAboutTheMockTime() {
    val a = StageAnim(m = 0f)
    var elapsed = 0f
    while (a.m < 1f && elapsed < 2f) {
      a.step(0.016f, 1f, 1f, 1f)
      elapsed += 0.016f
      assertTrue(a.m in 0f..1f)
    }
    assertEquals(STAGE_MORPH_SECONDS, elapsed, 0.02f)
    repeat(10) { a.step(0.05f, 0f, 0f, 1f); assertTrue(a.m in 0f..1f) }
    assertEquals(0f, a.m, 0f)
  }

  @Test fun distanceEasesToItsTargetWithoutOvershoot() {
    val a = StageAnim(u = 0f)
    repeat(200) { a.step(0.016f, 0.5f, 0f, 1f); assertTrue(a.u in 0f..0.5f) }
    assertEquals(0.5f, a.u, 0f)
  }

  @Test fun framesAreFiniteInEveryState() {
    val scene = StageScene()
    for ((enabled, preset) in listOf(false to SpatialPreset.STANDARD) + SpatialPreset.entries.map { true to it }) {
      val a = StageAnim(stageTargetU(enabled, preset), stageTargetM(enabled, preset), stageTargetOn(enabled))
      a.t = 1.7f
      val n = scene.frame(a, 0.7f, STAGE_W_UNITS)
      assertTrue("faces drawn for $preset", n > 0)
      var prev = Long.MAX_VALUE
      for (pos in 0 until n) {
        val i = scene.sortedItem(pos)
        assertTrue(i in 0 until n)
        if (scene.itemType[i] != 0) for (k in 0 until 4) assertTrue(scene.itemLine[4 * i + k].isFinite())
        else assertTrue(scene.itemTone[i] in 0..255)
        prev = minOf(prev, i.toLong())
      }
      for (o in scene.objs) assertTrue(o.sx.all { it.isFinite() } && o.sy.all { it.isFinite() })
    }
  }

  private companion object { const val STAGE_W_UNITS = 312f }
}
