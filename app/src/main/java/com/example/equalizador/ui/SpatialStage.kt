package com.example.ui.screens.audio

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialPreset
import com.example.ui.theme.Grid
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/** The caption under the title and the spoken state of the stage: the mock's three phrases. */
@Composable
internal fun spatialStageCaption(enabled: Boolean, preset: SpatialPreset): String = stringResource(
  when {
    !enabled -> R.string.audio_spatial_stage_off
    preset == SpatialPreset.CINEMA -> R.string.audio_spatial_stage_cinema
    preset == SpatialPreset.SUBTLE -> R.string.audio_spatial_stage_subtle
    preset == SpatialPreset.STANDARD -> R.string.audio_spatial_stage_standard
    else -> R.string.audio_spatial_stage_wide
  }
)

private val StageLabelStyle = EqType.MonoStrong
private val LabelIds = arrayOf("L", "R", "C", "Ls", "Rs", "SBL", "SBR", "LFE")

/**
 * The 3D stage of Tarkas Spatial: a head from behind with headphones; wider modes turn them into two
 * floating speakers, and Cinema into six 5.1 boxes. Drawn in one canvas, 192 dp high; the 312x192 space
 * of the mock is scaled by the height. Animated values are read only while drawing.
 */
@Composable
internal fun SpatialStage(
  enabled: Boolean,
  preset: SpatialPreset,
  intensity: Float,
  modifier: Modifier = Modifier,
  /** Audio channel (L R C LFE Ls Rs SBL SBR = 0..7) under test, drawn lit; null when none. */
  activeChannel: Int? = null,
  /** Tap on a drawn speaker, with its audio channel. Null: the stage is only a picture. */
  onSpeakerTap: ((Int) -> Unit)? = null,
  /** Live 3D drag delta (dx, dy) in dp; lets the listener widen/rotate the stage directly. */
  onStageDrag: ((dxDp: Float, dyDp: Float) -> Unit)? = null,
  onStageDragEnd: (() -> Unit)? = null,
  onStageReset: (() -> Unit)? = null
) {
  val colors = MaterialTheme.colorScheme
  val scene = remember { StageScene() }
  val anim = remember { StageAnim(stageTargetU(enabled, preset), stageTargetM(enabled, preset), stageTargetOn(enabled)) }
  val tick = remember { mutableLongStateOf(0L) }
  val target by rememberUpdatedState(Triple(stageTargetU(enabled, preset), stageTargetM(enabled, preset), stageTargetOn(enabled)))
  val inten by rememberUpdatedState(intensity)
  val owner = LocalLifecycleOwner.current

  // Frames only while the screen is RESUMED and the stage is active or transitioning.
  // Thermal-adaptive pacing: ~60 fps normal, ~30 fps when device is thermally warm.
  LaunchedEffect(owner) {
    owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      var last = 0L
      while (true) {
        val (tu, tm, to) = target
        if (anim.isSettled(tu, tm, to)) {
          last = 0L
          delay(32L)
          continue
        }
        val minStepNs = if (com.example.data.diagnostics.ThermalMonitor.isWarm) 33_000_000L else 16_000_000L
        androidx.compose.runtime.withFrameNanos { now ->
          if (last == 0L || now - last >= minStepNs) {
            val dt = if (last == 0L) 0f else min(0.05f, (now - last) / 1_000_000_000f)
            last = now
            val (curU, curM, curOn) = target
            anim.step(dt, curU, curM, curOn)
            tick.longValue = now
          }
        }
      }
    }
  }

  val dark = colors.surface.luminance() < 0.5f
  val lo = if (dark) colors.surface else colors.onSurface
  val hi = if (dark) colors.onSurface else colors.surface
  val palette = remember(lo, hi) { IntArray(256) { lerp(lo, hi, it / 255f).toArgb() } }
  val ice = colors.primary.toArgb()
  val gray = colors.onSurfaceVariant.toArgb()
  val labelColor = colors.onSurfaceVariant

  val textMeasurer = rememberTextMeasurer()
  val labels = remember(textMeasurer) { LabelIds.map { textMeasurer.measure(it, StageLabelStyle) } }

  val fill = remember { Paint().apply { isAntiAlias = true; style = Paint.Style.FILL_AND_STROKE; strokeJoin = Paint.Join.ROUND } }
  val stroke = remember { Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND } }
  val path = remember { Path() }
  val floorDash = remember { DashPathEffect(floatArrayOf(3f, 5f), 0f) }
  val shape = Grid.r12
  val description = stringResource(R.string.audio_spatial_stage_desc, spatialStageCaption(enabled, preset))
  val testActionLabel = stringResource(R.string.audio_spatial_test_speaker)
  val tapHandler by rememberUpdatedState(onSpeakerTap)
  val dragHandler by rememberUpdatedState(onStageDrag)
  val dragEndHandler by rememberUpdatedState(onStageDragEnd)
  val resetHandler by rememberUpdatedState(onStageReset)
  val isEnabled by rememberUpdatedState(enabled)
  val speakersTappable = onSpeakerTap != null && enabled && preset.virtual51
  val activeSpeaker = if (activeChannel == null) -1 else STAGE_TO_AUDIO_CHANNEL.indexOf(activeChannel)

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(Grid.u192)
      .clip(shape)
      .background(colors.surfaceContainer)
      .border(Grid.hairline, colors.outline.copy(alpha = 0.5f), shape)
      .semantics {
        contentDescription = description
        if (speakersTappable) {
          customActions = LabelIds.indices.map { n ->
            CustomAccessibilityAction("$testActionLabel ${LabelIds[n]}") {
              tapHandler?.invoke(STAGE_TO_AUDIO_CHANNEL[n]); true
            }
          }
        }
      }
      .pointerInput(Unit) {
        // Unified gesture handler: captures 3D drag inside the stage before parent LazyColumn or swipeTabs
        // can steal the pointer, while preserving single-tap speaker test and double-tap recenter.
        var lastTapTime = 0L
        var lastTapPos = Offset.Zero
        val slopPx = viewConfiguration.touchSlop
        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val startPos = down.position
          var totalDx = 0f
          var totalDy = 0f
          var dragging = false
          val pointerId = down.id

          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
              if (dragging) {
                change.consume()
                anim.held = false
                dragEndHandler?.invoke()
              } else {
                val now = System.currentTimeMillis()
                val distSq = (startPos - lastTapPos).getDistanceSquared()
                if (now - lastTapTime <= doubleTapTimeout && distSq < (slopPx * 4f) * (slopPx * 4f)) {
                  lastTapTime = 0L
                  anim.held = false
                  anim.drag = 0f
                  resetHandler?.invoke()
                  tick.longValue = System.nanoTime()
                } else {
                  lastTapTime = now
                  lastTapPos = startPos
                  val cb = tapHandler
                  if (cb != null) {
                    val scale = size.height / STAGE_H
                    val hit = scene.hitTest(startPos.x / scale, startPos.y / scale, 24f * density / scale)
                    if (hit >= 0) cb(STAGE_TO_AUDIO_CHANNEL[hit])
                  }
                }
              }
              break
            }

            val delta = change.positionChange()
            if (delta != Offset.Zero) {
              totalDx += delta.x
              totalDy += delta.y
              if (!dragging && (totalDx * totalDx + totalDy * totalDy) > slopPx * slopPx) {
                dragging = true
                anim.held = true
              }
              if (dragging) {
                change.consume()
                val dxDp = delta.x / density
                val dyDp = delta.y / density
                anim.dragTo(anim.drag + dxDp * 0.014f)
                if (isEnabled) {
                  dragHandler?.invoke(dxDp, dyDp)
                }
                tick.longValue = System.nanoTime()
              }
            }
          }
        }
      }
  ) {
    tick.longValue // the only state read: redraws every frame without recomposing
    val scale = size.height / STAGE_H
    val wUnits = size.width / scale
    val n = scene.frame(anim, inten, wUnits, SpatialControl.headYaw.toFloat())
    val litIce = scene.lit > 0.5f
    drawIntoCanvas { c ->
      val nc = c.nativeCanvas
      nc.save()
      nc.scale(scale, scale)
      if (scene.floorAlpha > 0f) {
        stroke.color = gray
        stroke.alpha = (scene.floorAlpha * 255f).roundToInt()
        stroke.strokeWidth = 1f
        stroke.pathEffect = floorDash
        path.reset()
        for (k in 0..64) if (k == 0) path.moveTo(scene.floorX[0], scene.floorY2[0]) else path.lineTo(scene.floorX[k], scene.floorY2[k])
        nc.drawPath(path, stroke)
        stroke.pathEffect = null
      }
      for (pos in 0 until n) {
        val i = scene.sortedItem(pos)
        val al = (scene.itemAlpha[i].coerceIn(0f, 1f) * 255f).roundToInt()
        if (scene.itemType[i] != 0) {
          stroke.color = if (scene.itemType[i] == 1) ice else gray
          stroke.alpha = al
          stroke.strokeWidth = scene.itemWidth[i]
          nc.drawLine(scene.itemLine[4 * i], scene.itemLine[4 * i + 1], scene.itemLine[4 * i + 2], scene.itemLine[4 * i + 3], stroke)
          continue
        }
        val o = scene.objs[scene.itemObj[i]]
        val f = scene.itemFace[i]
        val lit = activeSpeaker >= 0 && o === scene.speakers[activeSpeaker].obj
        path.reset()
        for (k in o.fOff[f] until o.fOff[f + 1]) {
          val vi = o.fIdx[k]
          if (k == o.fOff[f]) path.moveTo(o.sx[vi], o.sy[vi]) else path.lineTo(o.sx[vi], o.sy[vi])
        }
        path.close()
        fill.color = if (lit) ice else palette[scene.itemTone[i]]
        fill.alpha = if (lit) (al * 0.85f).roundToInt() else al
        fill.strokeWidth = 0.75f
        nc.drawPath(path, fill)
        if (o.fRing[f] || lit) {
          stroke.color = if (litIce || lit) ice else gray
          stroke.alpha = al
          stroke.strokeWidth = 1.25f
          nc.drawPath(path, stroke)
        }
      }
      nc.restore()
    }
    for (k in labels.indices) {
      val a = scene.labelAlpha[k]
      if (a <= 0f) continue
      val l = labels[k]
      drawText(
        l, color = if (k == activeSpeaker) colors.primary else labelColor, alpha = a,
        topLeft = Offset(scene.labelX[k] * scale - l.size.width / 2f, scene.labelY[k] * scale - l.firstBaseline)
      )
    }
  }
}

