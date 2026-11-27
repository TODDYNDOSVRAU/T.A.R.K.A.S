package com.example.windows.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * What a glass surface sees behind it. Compose has no `backdrop-filter`: a surface cannot read the pixels under itself, so
 * the two things that sit under the corner, the window [Backdrop] and the page, are each recorded into a [GraphicsLayer]
 * while they draw as usual ([recordsGlass]). The glass replays both inside itself, shifted by the difference of their
 * window positions, and filters that copy (refraction and blur). Nothing here draws by itself: with no recorder wired
 * ([LocalGlassBackdrop] null, or a layer not recorded yet) a glass surface keeps only its tint and its border.
 */
@Stable
class GlassBackdrop {
  /** One recorded source: its layer (null until the first composition) and where it sits in the window. */
  @Stable
  class Source internal constructor() {
    internal var layer: GraphicsLayer? = null
    internal var origin: Offset = Offset.Unspecified
    val recorded: Boolean get() = layer != null && origin.isSpecified
  }

  /** The window background (marble and cover): the first thing drawn. */
  val back = Source()
  /** The page above it (the `shell_content` box): drawn over the backdrop, under the corner. */
  val page = Source()

  /**
   * Bumped when a source's position or layer changes, so a glass surface that read it redraws its copy. A source that
   * re-records its content (a scroll) invalidates the layer's own readers; this covers the rest.
   */
  var revision: Int by mutableIntStateOf(0)
    internal set

  /** The sources in the order they are drawn: the backdrop, then the page. */
  internal val ordered: List<Source> = listOf(back, page)

  /** Where [source]'s content is drawn inside a surface whose own origin (window coordinates) is [surfaceOrigin]. */
  fun shiftOf(source: Source, surfaceOrigin: Offset): Offset = source.origin - surfaceOrigin
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** The two things a glass surface replays. */
enum class GlassPart { Back, Page }

/**
 * Records this node's content into the layer of [part] and draws it in place, unchanged, so the screen looks the same
 * with or without it. Put it on the `Backdrop()` (with the full-window box it fills) and on the page box:
 *
 *     val glass = remember { GlassBackdrop() }
 *     CompositionLocalProvider(LocalGlassBackdrop provides glass) {
 *       Backdrop(Modifier.recordsGlass(glass, GlassPart.Back))
 *       ... Box(Modifier.fillMaxSize().recordsGlass(glass, GlassPart.Page).testTag("shell_content")) { page }
 *     }
 */
@Composable
fun Modifier.recordsGlass(backdrop: GlassBackdrop, part: GlassPart): Modifier {
  val source = if (part == GlassPart.Back) backdrop.back else backdrop.page
  val layer = rememberGraphicsLayer()
  DisposableEffect(layer, source) {
    source.layer = layer
    backdrop.revision++
    onDispose { if (source.layer === layer) { source.layer = null; backdrop.revision++ } }
  }
  return this
    .onGloballyPositioned {
      val o = it.positionInWindow()
      if (o != source.origin) { source.origin = o; backdrop.revision++ }
    }
    .drawWithContent {
      layer.record { this@drawWithContent.drawContent() }
      drawLayer(layer)
    }
}

/** Draws every recorded source of [glass], each shifted into the surface whose window origin is [surfaceOrigin]. */
internal fun DrawScope.drawGlassSources(glass: GlassBackdrop, surfaceOrigin: Offset) {
  for (s in glass.ordered) {
    val layer = s.layer ?: continue
    if (!s.origin.isSpecified) continue
    val shift = glass.shiftOf(s, surfaceOrigin)
    translate(shift.x, shift.y) { drawLayer(layer) }
  }
}
