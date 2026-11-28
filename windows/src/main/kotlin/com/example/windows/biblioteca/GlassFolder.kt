package com.example.windows.biblioteca

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.asComposeRenderEffect as asRenderEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.equalizador.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MaskFilter
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The Glass Folder, in Compose. A folder that holds a few sheets under a pane of frosted glass: a click on the glass opens
 * it, the sheets fan out, and each sheet is an action. The look and the motion are those of the React component the owner
 * supplied; nothing of React or a web view is in the app. What is drawn is the folder's back panel with its art, the
 * sheets in its pocket and the glass over them. The glass is the real thing: the back panel and the sheets are recorded
 * into a layer, and the glass replays that layer blurred and saturated inside its own shape, then tints it.
 *
 * Text and colours come from MaterialTheme and the Grid tokens; the only hex values are the art recipes (GlassArt.kt).
 * Everything that moves reads its value inside a graphicsLayer or draw lambda, so a frame of the folder never recomposes it.
 */

/**
 * One sheet in the pocket: its name, a short line beside it, and the art it wears. [shortName] is what the label shows when
 * the full name does not fit in the part of the sheet that the sheets in front leave uncovered.
 */
data class FolderSheet(
  val name: String,
  val meta: String,
  val art: ArtPreset? = null,
  val seed: Long = hashString(name),
  val shortName: String? = null,
)

/** What the key at the corner does: add (a plus), open a menu (three dots), or nothing. */
enum class GlassFolderAction { Add, More, None }

/** One line of the menu that the [GlassFolderAction.More] key opens. */
data class GlassFolderMenuItem(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** The words the folder speaks for itself: its accessibility labels. */
data class GlassFolderStrings(
  val open: (label: String, count: Int) -> String = { l, n -> lt("Open $l, $n ${if (n == 1) "item" else "items"}", "Abrir $l, $n ${if (n == 1) "item" else "itens"}") },
  val close: (label: String, count: Int) -> String = { l, n -> lt("Close $l, $n ${if (n == 1) "item" else "items"}", "Fechar $l, $n ${if (n == 1) "item" else "itens"}") },
  val add: String = lt("Add", "Adicionar"),
  val more: String = lt("More actions", "Mais ações"),
)

/** What the folder is doing, and where it is on screen (for the press-outside rule). Shared by the folder and [glassFolderDismiss]. */
@Stable
class GlassFolderState {
  internal var interaction by mutableStateOf(FolderInteraction())
  internal var folderBounds: Rect = Rect.Zero
  internal var keyBounds: Rect = Rect.Zero
  internal var menuBounds: Rect = Rect.Zero
  internal val hitFocus = FocusRequester()
  internal val keyFocus = FocusRequester()

  val open: Boolean get() = interaction.open

  fun open() { interaction = FolderInteraction(open = true) }
  fun close() { interaction = FolderInteraction() }
}

@Composable
fun rememberGlassFolderState(): GlassFolderState = remember { GlassFolderState() }

/**
 * Put on a container that holds the folder (the whole page is best): a press outside the folder closes it, a press outside
 * its menu closes the menu. The press is only watched, never consumed.
 */
@Composable
fun Modifier.glassFolderDismiss(state: GlassFolderState): Modifier {
  val origin = remember { floatArrayOf(0f, 0f) }
  return this
    .onGloballyPositioned { c -> val p = c.positionInRoot(); origin[0] = p.x; origin[1] = p.y }
    .pointerInput(state) {
      awaitPointerEventScope {
        while (true) {
          val ev = awaitPointerEvent(PointerEventPass.Initial)
          if (ev.type != PointerEventType.Press) continue
          val s = state.interaction
          if (!s.open && !s.menuOpen) continue
          val p = ev.changes.first().position + Offset(origin[0], origin[1])
          state.interaction = pressAt(
            s,
            insideFolder = state.folderBounds.contains(p),
            insideMenuOrKey = state.keyBounds.contains(p) || state.menuBounds.contains(p),
          )
        }
      }
    }
}

/** A pose that travels: from where it was to where it goes, along one curve, so a retarget in mid-flight starts from where the folder is. */
@Stable
private class PoseMotion(initial: GlassPose) {
  var from by mutableStateOf(initial)
  var to by mutableStateOf(initial)
  val progress = Animatable(1f)

  fun current(): GlassPose = GlassPoses.between(from, to, progress.value)

  suspend fun go(target: GlassPose, ms: Int?) {
    val here = current()
    if (ms == null) {
      from = target; to = target
      progress.snapTo(1f)
      return
    }
    from = here; to = target
    progress.snapTo(0f)
    progress.animateTo(1f, tween(ms, easing = GfSpring))
  }
}

@Stable
private class SheetMotion {
  val pose = PoseMotion(GlassPoses.Rest)
  val lift = Animatable(0f)
  val hover = MutableInteractionSource()
  var focused by mutableStateOf(false)
  val focus = FocusRequester()
}

/** The look of the folder in the current theme: all of it taken from the colour scheme, none from a hex. */
@Stable
private class Look(cs: ColorScheme) {
  val dark = cs.background.luminance() < 0.5f
  val ink = cs.onSurface
  val soft = cs.onSurfaceVariant
  val focus = cs.primary
  val danger = cs.error
  val surface = cs.surface
  val backTop = if (dark) lerp(cs.surface, cs.onSurface, 0.14f) else lerp(cs.surface, Color.White, 0.5f)
  val backBottom = if (dark) lerp(cs.surface, cs.onSurface, 0.03f) else lerp(cs.surface, cs.onSurface, 0.06f)
  val sheetTop = if (dark) lerp(cs.surface, cs.onSurface, 0.15f) else Color.White
  val sheetBottom = if (dark) lerp(cs.surface, cs.onSurface, 0.07f) else cs.surface
  val shadow = if (dark) Color.Black.copy(alpha = 0.62f) else cs.onSurface.copy(alpha = 0.34f)
  val outline = cs.outlineVariant
  val keyTop = lerp(cs.onSurface, cs.surface, 0.18f)
  val keyBottom = cs.onSurface
  val keyGlyph = cs.surface
  val menuSurface = cs.surface.copy(alpha = 0.94f)
}

/** The 5x4 colour matrix of `saturate(s)` as Skia wants it: row-major, the last column the offset. */
internal fun saturationMatrix(s: Float): FloatArray = floatArrayOf(
  0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s, 0f, 0f,
  0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s, 0f, 0f,
  0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s, 0f, 0f,
  0f, 0f, 0f, 1f, 0f,
)

private fun stagePath(svg: String, k: Float, dy: Float): Path {
  val path = PathParser().parsePathString(svg).toPath()
  path.transform(Matrix().apply { translate(0f, dy); scale(k, k) })
  return path
}

/**
 * The folder. [width] is a step of the 2x chain (the folder is 340x272 stage units drawn at `width/340`); pass
 * [glassFolderWidth] of the room you have. [sheets] show their last four. [onSheet] gets the index of the sheet that was
 * chosen, after the folder has closed. [state] is shared with [glassFolderDismiss].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassFolder(
  label: String,
  meta: String,
  sheets: List<FolderSheet>,
  onSheet: (Int) -> Unit,
  modifier: Modifier = Modifier,
  width: Dp = Grid.u384,
  state: GlassFolderState = rememberGlassFolderState(),
  action: GlassFolderAction = GlassFolderAction.Add,
  onAction: () -> Unit = {},
  menu: List<GlassFolderMenuItem> = emptyList(),
  art: ArtPreset? = null,
  seed: Long = hashString(label),
  appear: Boolean = true,
  appearDelayMs: Int = 0,
  reduceMotion: Boolean = LocalReducedMotion.current,
  strings: GlassFolderStrings = GlassFolderStrings(),
) {
  val cs = MaterialTheme.colorScheme
  val look = remember(cs) { Look(cs) }
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val wPx = with(density) { width.toPx() }
  val k = wPx / GF_W
  val topU = headroomFor(sheets.size)
  val rootHPx = (GF_H + topU) * k
  val first = visibleSheets(sheets.size).first
  val count = visibleSheets(sheets.size).count()
  val s = state.interaction

  val hoverSource = remember { MutableInteractionSource() }
  val hovered by hoverSource.collectIsHoveredAsState()
  var hitFocused by remember { mutableStateOf(false) }
  var keyboardUsed by remember { mutableStateOf(false) }
  val focusVisible = hitFocused && keyboardUsed
  val poseTarget = GlassPoses.target(s.open, hovered, focusVisible)

  val clock = remember { Animatable(0f) }
  LaunchedEffect(appear, reduceMotion) {
    if (reduceMotion) clock.snapTo(GlassEntrance.TOTAL_MS.toFloat())
    else if (appear) {
      delay(appearDelayMs.toLong())
      clock.animateTo(GlassEntrance.TOTAL_MS.toFloat(), tween(GlassEntrance.TOTAL_MS, easing = LinearEasing))
    }
  }

  val hinge = remember { PoseMotion(GlassPoses.Rest) }
  LaunchedEffect(poseTarget, reduceMotion) { hinge.go(GlassPoses.at(poseTarget), if (reduceMotion) null else GF_HINGE_MS) }

  val px = remember { Animatable(0f) }
  val py = remember { Animatable(0f) }
  var rootSize by remember { mutableStateOf(IntSize(1, 1)) }

  val sceneLayer = rememberGraphicsLayer()
  val blurLayer = rememberGraphicsLayer()

  val backPath = remember(k, topU) { stagePath(GF_BACK_PATH, k, topU * k) }
  val frontPath = remember(k) { stagePath(GF_FRONT_PATH, k, 0f) }
  val blurEffect = remember(k) {
    val sigma = 22f * k
    ImageFilter.makeColorFilter(
      ColorFilter.makeMatrix(org.jetbrains.skia.ColorMatrix(*saturationMatrix(1.5f))),
      ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP),
      null,
    ).asRenderEffect()
  }

  val artScale = min(1.5f, density.density) / density.density
  val backArtPreset = art
  val backArt = rememberArt(
    backArtPreset, seed,
    (364f * k * artScale).roundToInt(), (296f * k * artScale).roundToInt(),
  )
  val backFade = remember(backArt.value != null) { Animatable(if (backArt.value != null) 1f else 0f) }
  LaunchedEffect(backArt.value, reduceMotion) {
    if (backArt.value == null) return@LaunchedEffect
    if (reduceMotion) backFade.snapTo(1f) else backFade.animateTo(1f, tween(1200, easing = GfEase))
  }

  val motions = remember(count) { List(count) { SheetMotion() } }
  val keyRequester = state.keyFocus
  val menuRequesters = remember(menu.size) { List(menu.size) { FocusRequester() } }

  fun ease(delay: Float, dur: Float): Float = GfEase.transform(GlassEntrance.linear(clock.value, delay, dur))

  val mod = modifier
    .size(width, with(density) { rootHPx.toDp() })
    .onGloballyPositioned { state.folderBounds = it.boundsInRoot() }
    .onSizeChanged { rootSize = it }
    .hoverable(hoverSource)
    .onPointerEvent(PointerEventType.Press) { keyboardUsed = false }
    .onPointerEvent(PointerEventType.Move) {
      if (reduceMotion) return@onPointerEvent
      val p = it.changes.first().position
      val nx = ((p.x / rootSize.width) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
      val ny = ((p.y / rootSize.height) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
      scope.launch { px.animateTo(nx, tween(500, easing = GfEase)) }
      scope.launch { py.animateTo(ny, tween(500, easing = GfEase)) }
    }
    .onPointerEvent(PointerEventType.Exit) {
      scope.launch { px.animateTo(0f, tween(700, easing = GfEase)) }
      scope.launch { py.animateTo(0f, tween(700, easing = GfEase)) }
    }
    .onPreviewKeyEvent { e ->
      if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
      keyboardUsed = true
      if (e.key == Key.Escape && escapeIsOurs(state.interaction)) {
        val step = escape(state.interaction)
        state.interaction = step.state
        when (step.focus) {
          FocusTarget.Key -> runCatching { state.keyFocus.requestFocus() }
          FocusTarget.Hit -> runCatching { state.hitFocus.requestFocus() }
          FocusTarget.None -> Unit
        }
        true
      } else false
    }

  Box(mod) {
    // The scene: what the glass sees through itself. The shadow, the back panel and the sheets, drawn as they are and
    // recorded into one layer that the glass replays blurred.
    Box(
      Modifier.fillMaxSize().drawWithContent {
        sceneLayer.record { this@drawWithContent.drawContent() }
        drawLayer(sceneLayer)
      },
    ) {
      Box(Modifier.fillMaxSize().drawBehind {
        val a = ease(GlassEntrance.BACK_DELAY, GlassEntrance.SHADOW_MS)
        if (a <= 0f) return@drawBehind
        drawFolderShadow(look, k, topU, px.value, a)
      })
      // The back panel with its art.
      Box(
        Modifier.fillMaxSize()
          .graphicsLayer {
            val e = ease(GlassEntrance.BACK_DELAY, GlassEntrance.BACK_MS)
            alpha = e
            translationY = (1f - e) * GlassEntrance.BACK_DROP * k
          }
          .drawBehind { drawBack(look, backPath, k, topU) },
      ) {
        Box(
          Modifier.fillMaxSize()
            .graphicsLayer {
              val a = backFade.value
              alpha = a
              renderEffect = if (a < 1f) androidx.compose.ui.graphics.BlurEffect(14f * k * (1f - a), 14f * k * (1f - a)) else null
            }
            .drawBehind {
              val bmp = backArt.value ?: return@drawBehind
              clipPath(backPath) {
                drawImage(
                  bmp,
                  dstOffset = androidx.compose.ui.unit.IntOffset(
                    ((-12f + px.value * -7f) * k).roundToInt(),
                    ((topU - 12f + py.value * -6f) * k).roundToInt(),
                  ),
                  dstSize = IntSize((364f * k * 1.02f).roundToInt(), (296f * k * 1.02f).roundToInt()),
                  filterQuality = FilterQuality.Medium,
                )
              }
            }
            .drawBehind { drawBackSheen(backPath, k, topU) },
        )
        Box(Modifier.fillMaxSize().drawBehind { drawBackRim(look, backPath, k) })
      }
      // The sheets in the pocket. Nothing hangs out below the pocket's floor, however high the pile.
      Box(
        Modifier.fillMaxSize().drawWithContent {
          clipRect(left = -4000f, top = -4000f, right = size.width + 4000f, bottom = (topU + GF_H - 10f) * k) {
            this@drawWithContent.drawContent()
          }
        },
      ) {
      val jitters = remember(sheets, first, count) { (0 until count).map { sheetJitter(sheets[first + it].name, first + it) } }
      for (v in 0 until count) {
        val index = first + v
        val sheet = sheets[index]
        SheetItem(
          sheet = sheet, index = index, v = v, count = count, k = k, topU = topU, jitters = jitters,
          open = s.open, poseTarget = poseTarget, reduce = reduceMotion, look = look,
          density = density.density, artScale = artScale, motion = motions[v],
          entrance = { d -> ease(GlassEntrance.sheetDelay(d), GlassEntrance.SHEET_MS) },
          prev = if (v == 0) state.hitFocus else motions[v - 1].focus,
          next = if (v == count - 1) keyRequester else motions[v + 1].focus,
          onChosen = { state.close(); runCatching { state.hitFocus.requestFocus() }; onSheet(index) },
        )
      }
      }
    }

    // The glass.
    Box(
      Modifier
        .offset { IntOffset(0, ((topU + GF_FRONT_TOP) * k).roundToInt()) }
        .size(width, with(density) { (GF_FRONT_H * k).toDp() })
        .zIndex(10f)
        .graphicsLayer {
          transformOrigin = TransformOrigin(0.5f, 1f)
          rotationX = HINGE_SIGN * hinge.current().hinge
          cameraDistance = cameraDistanceFor(k, density.density)
          val e = ease(GlassEntrance.FRONT_DELAY, GlassEntrance.FRONT_MS)
          alpha = e
          translationY = (1f - e) * GlassEntrance.FRONT_DROP * k
          compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawBehind {
          val frontTop = (topU + GF_FRONT_TOP) * k
          blurLayer.renderEffect = blurEffect
          blurLayer.record(size = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))) {
            translate(0f, -frontTop) { drawLayer(sceneLayer) }
          }
          clipPath(frontPath) {
            drawLayer(blurLayer)
            drawFrontTint(look)
            drawFrontSpec(look, px.value, py.value)
          }
          drawFrontRim(look, frontPath, k, focusVisible)
          if (focusVisible) drawPath(frontPath, look.focus, style = Stroke(2.dp.toPx()))
        },
    )

    // The label, over the glass.
    Box(
      Modifier.align(Alignment.BottomStart).zIndex(11f)
        .padding(start = with(density) { (28f * k).toDp() }, bottom = with(density) { (32f * k).toDp() })
        .widthIn(max = with(density) { (200f * k).toDp() })
        .graphicsLayer { alpha = ease(GlassEntrance.FRONT_DELAY, GlassEntrance.FRONT_MS) },
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
          label,
          style = GridText.Body.copy(fontWeight = FontWeight.Medium),
          color = look.ink,
          maxLines = 1, overflow = TextOverflow.Ellipsis,
          modifier = Modifier.drawBehind {
            if (focusVisible) {
              val y = size.height - 1.dp.toPx()
              drawLine(look.ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
          },
        )
        if (reduceMotion) Text(meta, style = GridText.Label, color = look.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        else AnimatedContent(
          targetState = meta,
          transitionSpec = { (fadeIn(tween(500)) + slideInVertically(tween(500)) { 7 }) togetherWith fadeOut(tween(150)) },
          label = "glass-folder-meta",
        ) { m -> Text(m, style = GridText.Label, color = look.soft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
      }
    }

    // The button that is the glass itself.
    val hitDescription = (if (s.open) strings.close else strings.open)(label, sheets.size)
    Box(
      Modifier
        .offset { IntOffset(0, ((topU + GF_FRONT_TOP) * k).roundToInt()) }
        .size(width, with(density) { (GF_FRONT_H * k).toDp() })
        .zIndex(12f)
        .testTag("glass-folder-hit")
        .focusRequester(state.hitFocus)
        .focusProperties { next = if (s.open && count > 0) motions[0].focus else keyRequester }
        .onFocusChanged { hitFocused = it.isFocused }
        .semantics { contentDescription = hitDescription; role = Role.Button }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
          state.interaction = toggleOpen(state.interaction)
        },
    )

    // The key and its menu.
    if (action != GlassFolderAction.None) {
      val keySource = remember { MutableInteractionSource() }
      val keyHover by keySource.collectIsHoveredAsState()
      val keyPressed by keySource.collectIsPressedAsState()
      val keyDescription = if (action == GlassFolderAction.Add) strings.add else strings.more
      Box(
        Modifier.align(Alignment.BottomEnd).zIndex(13f)
          .padding(end = with(density) { (22f * k).toDp() }, bottom = with(density) { (22f * k).toDp() })
          .size(Grid.u48)
          .onGloballyPositioned { state.keyBounds = it.boundsInRoot() }
          .graphicsLayer {
            val a = ease(GlassEntrance.KEY_DELAY, GlassEntrance.KEY_ALPHA_MS)
            alpha = a
            val sc = GlassEntrance.KEY_SCALE_FROM + (1f - GlassEntrance.KEY_SCALE_FROM) *
              GfSpring.transform(GlassEntrance.linear(clock.value, GlassEntrance.KEY_DELAY, GlassEntrance.KEY_SCALE_MS))
            val press = if (keyPressed) 0.93f else 1f
            scaleX = sc * press; scaleY = sc * press
            translationY = if (keyHover) -2.dp.toPx() else 0f
          }
          .testTag("glass-folder-key")
          .focusRequester(keyRequester)
          .focusProperties { previous = if (s.open && count > 0) motions[count - 1].focus else state.hitFocus }
          .semantics { contentDescription = keyDescription; role = Role.Button }
          .hoverable(keySource)
          .clickable(interactionSource = keySource, indication = null) {
            if (action == GlassFolderAction.Add) onAction()
            else {
              val opening = !state.interaction.menuOpen
              state.interaction = state.interaction.copy(menuOpen = opening)
              if (opening && menuRequesters.isNotEmpty()) scope.launch { delay(30); runCatching { menuRequesters[0].requestFocus() } }
            }
          }
          .drawBehind {
            drawRoundRect(
              Brush.radialGradient(listOf(look.keyTop, look.keyBottom), center = Offset(size.width * 0.3f, size.height * 0.25f), radius = size.width),
              cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.31f),
            )
            val c = Offset(size.width / 2f, size.height / 2f)
            val stroke = 2.dp.toPx()
            val half = Grid.icon.toPx() * 0.3f
            if (action == GlassFolderAction.Add) {
              val turn = -90f * (1f - GfSpring.transform(GlassEntrance.linear(clock.value, GlassEntrance.KEY_DELAY, GlassEntrance.KEY_SCALE_MS)))
              withTransform({ rotate(turn, c) }) {
                drawLine(look.keyGlyph, Offset(c.x - half, c.y), Offset(c.x + half, c.y), stroke, StrokeCap.Round)
                drawLine(look.keyGlyph, Offset(c.x, c.y - half), Offset(c.x, c.y + half), stroke, StrokeCap.Round)
              }
            } else {
              for (i in -1..1) drawCircle(look.keyGlyph, radius = stroke * 0.9f, center = Offset(c.x + i * half * 0.9f, c.y))
            }
          },
      )
      if (action == GlassFolderAction.More && s.menuOpen) {
        GlassFolderMenu(
          items = menu, requesters = menuRequesters, look = look, k = k,
          onBounds = { state.menuBounds = it },
          onDone = { state.interaction = state.interaction.copy(menuOpen = false) },
          onEsc = {
            state.interaction = state.interaction.copy(menuOpen = false)
            runCatching { state.keyFocus.requestFocus() }
          },
          modifier = Modifier.align(Alignment.BottomEnd).zIndex(20f)
            .padding(end = with(density) { (22f * k).toDp() }, bottom = with(density) { (90f * k).toDp() }),
        )
      }
    }
  }
}

private const val HINGE_SIGN = 1f

/** The camera sits 760 stage units from the glass; Compose measures the distance in pixels and scales it by the density itself. */
private fun cameraDistanceFor(k: Float, density: Float): Float = 760f * k

@Composable
private fun SheetItem(
  sheet: FolderSheet, index: Int, v: Int, count: Int, k: Float, topU: Float, jitters: List<Pair<Float, Float>>,
  open: Boolean, poseTarget: Float, reduce: Boolean, look: Look, density: Float, artScale: Float,
  motion: SheetMotion, entrance: (depth: Int) -> Float,
  prev: FocusRequester, next: FocusRequester, onChosen: () -> Unit,
) {
  val depth = count - 1 - v
  val slot = fanSlot(v, count).toFloat()
  val (jx, jr) = jitters[v]
  val hovered by motion.hover.collectIsHoveredAsState()
  val lifted = open && (hovered || motion.focused)
  val d = LocalDensity.current

  LaunchedEffect(poseTarget, reduce) {
    if (!reduce) delay((depth * GF_SHEET_STAGGER_MS).toLong())
    motion.pose.go(GlassPoses.at(poseTarget), if (reduce) null else GF_SHEET_MS)
  }
  LaunchedEffect(lifted, reduce) {
    val to = if (lifted) 1f else 0f
    if (reduce) motion.lift.snapTo(to) else motion.lift.animateTo(to, tween(GF_SHEET_MS, easing = GfSpring))
  }

  val wPx = GF_SHEET_W * k
  val hPx = GF_SHEET_H * k
  val bitmap = rememberArt(sheet.art, sheet.seed, (wPx * artScale).roundToInt(), (hPx * artScale).roundToInt())
  val artFade = remember(bitmap.value != null) { Animatable(if (bitmap.value != null) 1f else 0f) }
  LaunchedEffect(bitmap.value, reduce) {
    if (bitmap.value == null) return@LaunchedEffect
    if (reduce) artFade.snapTo(1f) else artFade.animateTo(1f, tween(1200, easing = GfEase))
  }
  val shape = RoundedCornerShape(CornerSize(16f * k))
  val ring = look.ink.copy(alpha = if (sheet.art != null) 0f else 0.12f)
  // Behind the art until it has rendered: the surface, never a loose colour.
  val placeholder = MaterialTheme.colorScheme.surfaceVariant

  Box(
    Modifier
      .offset { IntOffset((GF_SHEET_LEFT * k).roundToInt(), ((topU + GF_SHEET_TOP) * k).roundToInt()) }
      .size(with(d) { wPx.toDp() }, with(d) { hPx.toDp() })
      .zIndex((v + 1).toFloat())
      .graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        val pl = sheetPlacement(motion.pose.current(), slot, depth, jx, jr, motion.lift.value)
        val e = entrance(depth)
        translationX = pl.tx * k
        translationY = (pl.ty + (1f - e) * GlassEntrance.SHEET_RISE) * k
        rotationZ = pl.rotation
        alpha = e
        val raise = motion.lift.value
        shadowElevation = (6f + 10f * raise) * k
        ambientShadowColor = look.shadow
        spotShadowColor = look.shadow
        this.shape = shape
        clip = true
      }
      .testTag("glass-folder-sheet-$index")
      .focusRequester(motion.focus)
      .focusProperties { this.previous = prev; this.next = next }
      .onFocusChanged { motion.focused = it.isFocused }
      .hoverable(motion.hover)
      .semantics { contentDescription = sheet.name }
      .clickable(enabled = open, role = Role.Button, onClick = onChosen)
      .drawBehind {
        if (sheet.art == null) {
          drawRect(Brush.verticalGradient(listOf(look.sheetTop, look.sheetBottom)))
          val line = look.ink.copy(alpha = 0.10f)
          for (i in 0 until 3) {
            val y = size.height * (0.46f + i * 0.14f)
            drawRoundRect(line, Offset(size.width * 0.07f, y), Size(size.width * (0.86f - i * 0.18f), 6f * k), androidx.compose.ui.geometry.CornerRadius(3f * k))
          }
        } else {
          drawRect(placeholder)
          bitmap.value?.let { bmp ->
            drawImage(bmp, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = artFade.value, filterQuality = FilterQuality.Medium)
          }
          drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * k), style = Stroke(1.dp.toPx()))
        }
        if (ring.alpha > 0f) drawRoundRect(ring, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * k), style = Stroke(1.dp.toPx()))
      },
  ) {
    // The label stands where no sheet in front reaches (the fan covers the top of the sheet behind), on a pill of the
    // colour scheme: its contrast is the scheme's, never the art's.
    val padU = with(d) { Grid.u6.toPx() } / k
    val roomPx = remember(jitters, v, count, k, d.density) {
      openLabelRoom(v, count, jitters, padU, padU, with(d) { Grid.u24.toPx() } / k) * k
    }
    SheetLabel(sheet, roomPx, look, Modifier.offset(Grid.u6, Grid.u6).testTag("glass-folder-label-$index"))
  }
}

/** The pill (the scheme's surface on every sheet, art or not: the sheet's own gradient is not the scheme's): at most [roomPx] wide, holding the name (the short one when the full one does not fit) and, when both fit, the meta. */
@Composable
private fun SheetLabel(sheet: FolderSheet, roomPx: Float, look: Look, modifier: Modifier) {
  val measurer = rememberTextMeasurer()
  val d = LocalDensity.current
  val nameStyle = GridText.Small.copy(fontWeight = FontWeight.SemiBold)
  val metaStyle = GridText.Label
  val padPx = with(d) { Grid.u6.roundToPx() }
  val gapPx = with(d) { Grid.u12.roundToPx() }
  val inner = (roomPx.toInt() - 2 * padPx).coerceAtLeast(0)
  val choice = remember(sheet, inner, d.density, d.fontScale) {
    fun w(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    chooseSheetLabel(w(sheet.name, nameStyle), sheet.shortName?.let { w(it, nameStyle) }, w(sheet.meta, metaStyle), gapPx, inner)
  }
  val shape = Grid.r12
  Row(
    modifier
      .widthIn(max = with(d) { roomPx.toDp() })
      .height(Grid.u24)
      .background(look.surface, shape)
      .border(Grid.hairline, look.outline, shape)
      .padding(horizontal = Grid.u6),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      if (choice.useShort) sheet.shortName ?: sheet.name else sheet.name,
      style = nameStyle, color = look.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    if (choice.showMeta) {
      Spacer(Modifier.width(Grid.u12))
      Text(sheet.meta, style = metaStyle, color = look.soft, maxLines = 1, overflow = TextOverflow.Clip)
    }
  }
}

@Composable
private fun GlassFolderMenu(
  items: List<GlassFolderMenuItem>, requesters: List<FocusRequester>, look: Look, k: Float,
  onBounds: (Rect) -> Unit, onDone: () -> Unit, onEsc: () -> Unit, modifier: Modifier,
) {
  val d = LocalDensity.current
  val shape = Grid.r12
  var at by remember { mutableIntStateOf(0) }
  fun moveTo(i: Int) {
    val n = i.coerceIn(0, requesters.lastIndex)
    at = n
    runCatching { requesters[n].requestFocus() }
  }
  Column(
    modifier
      .defaultMinSize(minWidth = with(d) { (176f * k).toDp() })
      .onGloballyPositioned { onBounds(it.boundsInRoot()) }
      .background(look.menuSurface, shape)
      .border(Grid.hairline, look.outline, shape)
      .padding(6.dp)
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
          Key.Escape -> { onEsc(); true }
          Key.Tab -> { onDone(); false }
          Key.DirectionDown -> { moveTo(at + 1); true }
          Key.DirectionUp -> { moveTo(at - 1); true }
          Key.MoveHome -> { moveTo(0); true }
          Key.MoveEnd -> { moveTo(requesters.lastIndex); true }
          else -> false
        }
      },
  ) {
    items.forEachIndexed { i, item ->
      val src = remember { MutableInteractionSource() }
      val hov by src.collectIsHoveredAsState()
      var focused by remember { mutableStateOf(false) }
      Row(
        Modifier
          .fillMaxWidth()
          .height(Grid.u48)
          .background(if (hov || focused) look.ink.copy(alpha = 0.08f) else Color.Transparent, Grid.r6)
          .focusRequester(requesters[i])
          .onFocusChanged { focused = it.isFocused; if (it.isFocused) at = i }
          .hoverable(src)
          .clickable(interactionSource = src, indication = null, role = Role.Button) { onDone(); item.onClick() }
          .padding(horizontal = Grid.u12),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(item.label, style = GridText.Small, color = if (item.danger) look.danger else look.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}


@Composable
private fun rememberArt(preset: ArtPreset?, seed: Long, w: Int, h: Int): State<ImageBitmap?> {
  val key = preset?.let { GlassArtPainter.Key(it.recipe, seed, w.coerceAtLeast(1), h.coerceAtLeast(1)) }
  return produceState(key?.let { GlassArtPainter.cached(it) }, key) {
    value = if (key == null) null else GlassArtPainter.cached(key) ?: GlassArtPainter.paint(key)
  }
}

private fun DrawScope.drawFolderShadow(look: Look, k: Float, topU: Float, px: Float, alpha: Float) {
  drawIntoCanvas { canvas ->
    val paint = Paint().apply { color = look.shadow.copy(alpha = look.shadow.alpha * alpha) }
    paint.asFrameworkPaint().maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 11f * k)
    val dx = px * -8f
    val l = (18f + dx) * k
    val r = (GF_W - 18f + dx) * k
    val b = (topU + GF_H + 6f) * k
    val t = b - 60f * k
    canvas.nativeCanvas.drawRRect(
      org.jetbrains.skia.RRect.makeLTRB(l, t, r, b, 40f * k),
      paint.asFrameworkPaint(),
    )
  }
}

private fun DrawScope.drawBack(look: Look, path: Path, k: Float, topU: Float) {
  clipPath(path) {
    drawRect(Brush.verticalGradient(listOf(look.backTop, look.backBottom), startY = topU * k, endY = (topU + GF_H) * k))
  }
}

private fun DrawScope.drawBackSheen(path: Path, k: Float, topU: Float) {
  clipPath(path) {
    val top = topU * k
    drawRect(
      Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = top + GF_H * 0.22f * k),
    )
    drawRect(
      Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
        center = Offset(GF_W * 0.8f * k, top), radius = GF_W * 0.55f * k,
      ),
    )
  }
}

private fun DrawScope.drawBackRim(look: Look, path: Path, k: Float) {
  clipPath(path) {
    drawPath(path, Color.White.copy(alpha = if (look.dark) 0.16f else 0.45f), style = Stroke(2f * k))
  }
}


private fun DrawScope.drawFrontTint(look: Look) {
  val base = look.surface
  val stops = if (look.dark) arrayOf(0f to base.copy(alpha = 0.18f), 0.46f to base.copy(alpha = 0.62f), 1f to base.copy(alpha = 0.94f))
  else arrayOf(0f to base.copy(alpha = 0.16f), 0.42f to base.copy(alpha = 0.5f), 1f to base.copy(alpha = 0.93f))
  drawRect(Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height))
}

private fun DrawScope.drawFrontSpec(look: Look, px: Float, py: Float) {
  val cx = size.width * (0.5f + px * 0.23f)
  val cy = size.height * (0.30f + py * 0.20f)
  val rx = size.width * 0.38f
  withTransform({ scale(1f, 200f / 260f, Offset(cx, cy)) }) {
    drawRect(
      Brush.radialGradient(
        listOf(Color.White.copy(alpha = if (look.dark) 1f else 0.85f), Color.Transparent),
        center = Offset(cx, cy), radius = rx,
      ),
      blendMode = if (look.dark) BlendMode.Screen else BlendMode.Softlight,
      alpha = if (look.dark) 0.16f else 0.9f,
    )
  }
}

private fun DrawScope.drawFrontRim(look: Look, path: Path, k: Float, focusVisible: Boolean) {
  val w = (if (focusVisible) 5f else 2.4f) * k
  val a = if (look.dark) 0.55f else 1f
  clipPath(path) {
    drawPath(
      path,
      Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.95f * a),
        0.35f to Color.White.copy(alpha = 0.35f * a),
        1f to Color.White.copy(alpha = 0.12f * a),
        startY = 0f, endY = size.height,
      ),
      style = Stroke(w * 2f),
    )
  }
}
