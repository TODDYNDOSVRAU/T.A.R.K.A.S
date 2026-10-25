package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.springAt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val BootInk = Color(0xFF07090B)
private val BootIce = Color(0xFF7EE3EA)
private val BootWhite = Color(0xFFEAF7F8)

/** Starts slow and falls in on itself, so the name reads as being pulled into one point. */
private val Collapse = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)
/** Bursts out fast and coasts, for the light wave. */
private val Burst = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

private const val NAME = "T.A.R.K.A.S."
/** Time each letter's spring needs to settle. */
private const val LETTER_MS = 620
private const val STAGGER_MS = 45
private const val MARK_MS = 760
private const val BOOT_WARMUP_FRAMES = 3
private val LETTERS_MS = LETTER_MS + STAGGER_MS * (NAME.length - 1)

/**
 * The opening animation, cut like a short title sequence:
 * 1. the letters of T.A.R.K.A.S. rise into place one after another;
 * 2. the name is pulled into the centre of the screen;
 * 3. a ring of light bursts out where it vanished while the mark draws itself;
 * 4. a glint runs across the finished mark, then the screen swells into the app (Início).
 * A tap skips to the logo. Only alpha, scale, offsets and a few cached paths move, so it stays
 * smooth at high refresh rates and costs almost nothing.
 */
@Composable
fun BootScreen(onFinished: () -> Unit) {
  val reduced = rememberReducedMotion()
  val letters = remember { Animatable(0f) }
  val squash = remember { Animatable(0f) }
  val ring = remember { Animatable(0f) }
  val mark = remember { Animatable(0f) }
  val glint = remember { Animatable(0f) }
  val exit = remember { Animatable(0f) }
  val skipped = remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    // On a cold start the first frames are slow while the app behind is built; starting the clock
    // only once frames flow keeps those stalls from eating the sequence.
    repeat(BOOT_WARMUP_FRAMES) { withFrameNanos { } }
    coroutineScope {
      if (!reduced) {
        val timeline = launch {
          letters.animateTo(1f, tween(LETTERS_MS, easing = LinearEasing))
          delay(200)
          squash.animateTo(1f, tween(520, easing = Collapse))
          coroutineScope {
            launch { ring.animateTo(1f, tween(760, easing = Burst)) }
            mark.animateTo(1f, tween(MARK_MS, easing = Motion.Settle))
          }
          glint.animateTo(1f, tween(420, easing = Motion.Settle))
          delay(120)
        }
        // A tap ends the wait early; otherwise the timeline finishes by itself.
        val watcher = launch {
          snapshotFlow { skipped.value }.first { it }
          timeline.cancel()
        }
        timeline.join()
        watcher.cancel()
      } else {
        delay(500)
      }
      // Skipped or finished: show the final frame (the logo) at once, then leave.
      letters.snapTo(1f)
      squash.snapTo(1f)
      ring.snapTo(1f)
      mark.snapTo(1f)
      glint.snapTo(1f)
    }
    exit.animateTo(1f, tween(if (reduced) 160 else 400, easing = Motion.Settle))
    onFinished()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer {
        alpha = 1f - exit.value
        val s = 1f + 0.10f * exit.value
        scaleX = s
        scaleY = s
      }
      .background(BootInk)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) { skipped.value = true }
      .semantics { contentDescription = "T.A.R.K.A.S." },
    contentAlignment = Alignment.Center
  ) {
    // Glow that grows with the mark, and the ring of light that bursts where the name vanished.
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(
        brush = Brush.radialGradient(
          colors = listOf(BootIce.copy(alpha = 0.16f * mark.value), Color.Transparent),
          center = center,
          radius = size.minDimension * 0.62f
        )
      )
      val r = ring.value
      if (r > 0f && r < 1f) {
        drawCircle(
          color = BootIce.copy(alpha = 0.55f * (1f - r)),
          radius = 12.dp.toPx() + size.minDimension * 0.55f * r,
          center = center,
          style = Stroke(width = 2.dp.toPx() * (1f - r) + 0.5f)
        )
      }
    }

    // 1 + 2: the letters rise in one by one, then the whole name is pulled into the centre.
    Row(
      modifier = Modifier.graphicsLayer {
        val q = squash.value
        transformOrigin = TransformOrigin.Center
        alpha = 1f - q * q
        scaleX = 1f - 0.97f * q
        scaleY = 1f - 0.45f * q
      }
    ) {
      NAME.forEachIndexed { i, ch ->
        Text(
          text = ch.toString(),
          color = BootWhite,
          fontSize = 26.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.graphicsLayer {
            // Each letter rides its own Remotion-style spring, started STAGGER_MS after the last:
            // it overshoots a little above its line and settles, instead of easing in flat.
            val ms = letters.value * LETTERS_MS - i * STAGGER_MS
            val e = springAt(ms / 1000f, damping = 12f, stiffness = 170f)
            alpha = (ms / 160f).coerceIn(0f, 1f)
            translationY = (1f - e) * 22.dp.toPx()
            rotationX = (1f - e) * 50f
            val s = 0.8f + 0.2f * e
            scaleX = s
            scaleY = s
          }
        )
        // Letter spacing, kept as gaps so each letter can move on its own.
        if (i < NAME.length - 1) Box(Modifier.size(width = 5.dp, height = 1.dp))
      }
    }

    // 3 + 4: the mark, drawn where the letters collapsed, then a glint across it.
    MarkDrawing(
      progress = { mark.value },
      glint = { glint.value },
      modifier = Modifier
        .size(width = 168.dp, height = 102.dp)
        .graphicsLayer {
          // The mark lands on a spring too, so it swells slightly past its size and settles.
          val pop = 0.7f + 0.3f * springAt(mark.value * MARK_MS / 1000f, damping = 11f, stiffness = 150f)
          scaleX = pop
          scaleY = pop
        }
    )
  }
}

/**
 * The mark on a 132x80 grid, drawn progressively. Paths are scaled and measured once per size
 * (drawWithCache); [progress] and [glint] are read only while drawing.
 */
@Composable
private fun MarkDrawing(progress: () -> Float, glint: () -> Float, modifier: Modifier = Modifier) {
  val parts = remember { buildMarkPaths() }
  Box(
    modifier = modifier.drawWithCache {
      val k = size.width / 132f
      val stroke = 12f * k
      val scaled = parts.map { part ->
        val path = Path().apply {
          addPath(part.path)
          transform(Matrix().apply { scale(k, k) })
        }
        Triple(part, path, PathMeasure().apply { setPath(path, false) })
      }
      val glow = Stroke(width = stroke * 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
      val line = Stroke(width = stroke, cap = StrokeCap.Butt, join = StrokeJoin.Miter, miter = 4f)
      val segment = Path()

      onDrawBehind {
        val p = progress()
        scaled.forEach { (part, path, measure) ->
          val local = ((p - part.start) / (part.end - part.start)).coerceIn(0f, 1f)
          if (local <= 0f) return@forEach
          val length = measure.length
          // A closed outline drawn in full uses the whole path, so its corner at the start joins cleanly.
          val shown = if (local >= 1f) path else segment.apply {
            reset()
            measure.getSegment(0f, length * local, this, true)
          }
          drawPath(shown, color = BootIce.copy(alpha = 0.10f), style = glow)
          drawPath(shown, color = part.color, style = line)
          // Bright tip while the stroke is still travelling
          if (local < 1f) {
            val tip = measure.getPosition(length * local)
            if (tip != Offset.Unspecified) drawCircle(Color.White.copy(alpha = 0.9f), radius = stroke * 0.55f, center = tip)
          }
        }

        // A glint of light sweeps left to right across the finished mark.
        val g = glint()
        if (g > 0f && g < 1f) {
          val x = -size.width * 0.4f + size.width * 1.8f * g
          val shine = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
            start = Offset(x, 0f),
            end = Offset(x + size.width * 0.35f, size.height)
          )
          scaled.forEach { (_, path, _) -> drawPath(path, brush = shine, style = line) }
        }
      }
    }
  )
}

private class MarkPart(val path: Path, val start: Float, val end: Float, val color: Color)

private fun buildMarkPaths(): List<MarkPart> {
  fun line(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply {
    moveTo(x1, y1)
    lineTo(x2, y2)
  }

  val diamond = Path().apply {
    moveTo(66f, 6f)
    lineTo(102f, 40f)
    lineTo(66f, 74f)
    lineTo(30f, 40f)
    close()
  }
  return listOf(
    MarkPart(line(0f, 40f, 29f, 40f), 0.00f, 0.22f, BootIce),
    MarkPart(diamond, 0.12f, 0.72f, BootWhite),
    MarkPart(line(103f, 40f, 132f, 40f), 0.55f, 0.78f, BootWhite),
    MarkPart(line(100f, 40f, 126f, 8f), 0.62f, 0.90f, BootWhite),
    MarkPart(line(100f, 40f, 126f, 72f), 0.66f, 1.00f, BootWhite)
  )
}
