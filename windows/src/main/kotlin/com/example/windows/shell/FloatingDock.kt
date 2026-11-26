package com.example.windows.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.base.BackHandler
import com.example.windows.res.Res
import com.example.windows.res.home_search_placeholder
import com.example.windows.res.shell_pages
import com.example.windows.res.shell_search
import com.example.windows.res.shell_search_close
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max

/**
 * Motor of the Floating Dock, ported from the React component the owner supplied (2026-11-25). The original maps the
 * pointer's distance to each icon, [-150, 0, 150] px, to a tile of [40, 80, 40] px through a spring (mass 0.1,
 * stiffness 150, damping 12). Here the tile rests at 48 dp (the T.A.L. touch target) and keeps the same 2x peak, so
 * the reach scales by the same 48/40. All values are dp.
 */
object DockEngine {
  const val REST = 48f
  const val MAX = 96f
  const val REACH = 180f
  const val GAP = 6f
  const val PAD = 6f
  /** Search field width when the dock is open (wide and compact). */
  const val FIELD = 288f
  const val COMPACT_FIELD = 192f
  /** The original spring for unit mass: ratio = c / (2 sqrt(k m)), stiffness = k / m. */
  const val DAMPING_RATIO = 1.549f
  const val STIFFNESS = 1500f

  /** Tile size for a pointer [distance] dp from the tile's rest centre. */
  fun target(distance: Float): Float {
    val d = abs(distance)
    return if (d >= REACH) REST else MAX - (MAX - REST) * d / REACH
  }

  fun restWidth(n: Int) = n * REST + (n - 1) * GAP + 2 * PAD
  /** Centre of tile [i] at rest, measured from the capsule's left edge. */
  fun restCentre(i: Int) = PAD + i * (REST + GAP) + REST / 2

  /** Largest total growth of [n] tiles. The sum of tents peaks with the pointer on a tile centre. */
  fun maxGrowth(n: Int): Float = (0 until n).maxOf { k ->
    var s = 0f
    for (i in 0 until n) s += target(restCentre(i) - restCentre(k)) - REST
    s
  }

  /** Width the dock reserves, so neither magnification nor the open search field ever moves the layout around it. */
  fun reservedWidth(n: Int) = restWidth(n) + max(maxGrowth(n), FIELD - REST)
}

/** Pages in the dock, in the Ctrl+1..6 order, then search. */
private val DockCount = NavOrder.size + 1

/**
 * The single navigation of the desktop app: a floating capsule at the top centre with the six real pages and the
 * search. Magnifies under the mouse (never under touch), shows a tooltip with the shortcut, marks the current page
 * with a dot, and turns into a toggle plus menu when [compact].
 */
@Composable
fun FloatingDock(
  current: TarkasNavTab,
  onSelect: (TarkasNavTab) -> Unit,
  onTyped: () -> Unit,
  reduceMotion: Boolean,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  val open by ShellSearch.isOpen.collectAsState()
  val query by ShellSearch.query.collectAsState()
  val focusTick by ShellSearch.focusRequests.collectAsState()
  val fieldFocus = remember { FocusRequester() }
  val tileFocus = remember { FocusRequester() }
  var fieldFocused by remember { mutableStateOf(false) }

  BackHandler(open) { ShellSearch.close() }
  LaunchedEffect(open, focusTick) {
    if (open) { withFrameNanos { }; runCatching { fieldFocus.requestFocus() } }
  }
  LaunchedEffect(open) {
    // Esc while typing: focus goes back to the search tile instead of being lost.
    if (!open && fieldFocused) { fieldFocused = false; withFrameNanos { }; runCatching { tileFocus.requestFocus() } }
  }
  val field: @Composable (Float, () -> Float) -> Unit = { width, reveal ->
    DockSearchField(
      width = width, reveal = reveal, query = query, requester = fieldFocus,
      onQuery = { ShellSearch.setQuery(it); if (it.isNotBlank()) onTyped() },
      onFocus = { has -> if (has) fieldFocused = true else if (fieldFocused) { fieldFocused = false; if (ShellSearch.query.value.isEmpty()) ShellSearch.close() } },
    )
  }
  val searchTitle = stringResource(Res.string.shell_search)
  if (compact) CompactDock(current, onSelect, reduceMotion, open, searchTitle, tileFocus, field, modifier)
  else WideDock(current, onSelect, reduceMotion, open, searchTitle, tileFocus, field, modifier)
}

@Composable
private fun WideDock(
  current: TarkasNavTab, onSelect: (TarkasNavTab) -> Unit, reduce: Boolean, open: Boolean, searchTitle: String,
  tileFocus: FocusRequester, field: @Composable (Float, () -> Float) -> Unit, modifier: Modifier,
) {
  val c = MaterialTheme.colorScheme
  val n = DockCount
  val sizes = remember { List(n) { Animatable(DockEngine.REST) } }
  val targets = remember { List(n) { mutableFloatStateOf(DockEngine.REST) } }
  val fieldW = remember { Animatable(DockEngine.REST) }
  val widths = remember { FloatArray(n) }      // px, written while placing, read by the pointer handler
  val centres = remember { FloatArray(n) }
  var hovering by remember { mutableStateOf(false) }
  var hoverTip by remember { mutableIntStateOf(-1) }
  var focusTip by remember { mutableIntStateOf(-1) }
  val shown = if (focusTip >= 0) focusTip else if (open) -1 else hoverTip
  var lastTip by remember { mutableIntStateOf(-1) }
  if (shown >= 0) lastTip = shown
  val tipAlpha by animateFloatAsState(if (shown >= 0) 1f else 0f, tween(200, easing = LinearOutSlowInEasing))
  val glassColor = glass()
  val px = LocalDensity.current.density
  val rim = c.onSurface.copy(alpha = 0.08f)

  LaunchedEffect(Unit) {
    val spec = spring(DockEngine.DAMPING_RATIO, DockEngine.STIFFNESS, 0.01f)
    for (i in 0 until n) launch { snapshotFlow { targets[i].floatValue }.collectLatest { sizes[i].animateTo(it, spec) } }
  }
  LaunchedEffect(open, reduce) {
    if (open) targets.forEach { it.floatValue = DockEngine.REST }
    val t = if (open) DockEngine.FIELD else DockEngine.REST
    if (reduce) fieldW.snapTo(t) else fieldW.animateTo(t, tween(250, easing = FastOutSlowInEasing))
  }
  val updReduce by rememberUpdatedState(reduce)
  val updOpen by rememberUpdatedState(open)

  val labels = NavOrder.map { stringResource(it.label) }
  val tipText = when {
    lastTip in NavOrder.indices -> "${labels[lastTip]}  Ctrl+${lastTip + 1}"
    lastTip == n - 1 -> "$searchTitle  Ctrl+F"
    else -> ""
  }

  /** Slot width in dp: the search slot follows the field while it opens or closes. */
  fun slot(i: Int): Float =
    if (i == n - 1 && fieldW.value > DockEngine.REST + 0.5f) fieldW.value else sizes[i].value

  Layout(
    content = {
      NavOrder.forEachIndexed { i, tab ->
        DockTile(labels[i], tab.icon(), tab == current, { focusTip = if (it) i else if (focusTip == i) -1 else focusTip }, Modifier.testTag("dock_${tab.name.lowercase()}")) {
          SidePane.close(); onSelect(tab)
        }
      }
      if (open) field(DockEngine.FIELD) { fieldW.value * px }
      else DockTile(searchTitle, Icons.Outlined.Search, false, { focusTip = if (it) n - 1 else if (focusTip == n - 1) -1 else focusTip }, Modifier.focusRequester(tileFocus).testTag("dock_search")) {
        ShellSearch.openDock()
      }
      Tooltip(tipText)
    },
    modifier = modifier
      .testTag("floating_dock")
      .pointerInput(Unit) {
        val padPx = DockEngine.PAD.dp.toPx()
        val capsuleH = (2 * DockEngine.PAD + DockEngine.REST).dp.toPx()
        fun reset() {
          hovering = false; hoverTip = -1
          targets.forEach { it.floatValue = DockEngine.REST }
        }
        awaitPointerEventScope {
          while (true) {
            val e = awaitPointerEvent()
            val ch = e.changes.firstOrNull() ?: continue
            if (ch.type == PointerType.Touch) continue
            if (e.type == PointerEventType.Exit) { reset(); continue }
            if (e.type != PointerEventType.Move && e.type != PointerEventType.Enter) continue
            val p = ch.position
            var k = 0
            for (i in 1 until n) if (abs(p.x - centres[i]) < abs(p.x - centres[k])) k = i
            val onTile = abs(p.x - centres[k]) <= widths[k] / 2
            val inside = p.y <= capsuleH || (onTile && p.y <= padPx + widths[k])
            if (!inside) { reset(); continue }
            hovering = true
            hoverTip = if (onTile && p.y >= padPx) k else -1
            if (!updReduce && !updOpen) {
              val xDp = p.x / density
              val start = (size.width / density - DockEngine.restWidth(n)) / 2
              for (i in 0 until n) targets[i].floatValue = DockEngine.target(xDp - start - DockEngine.restCentre(i))
            }
          }
        }
      }
      .drawBehind {
        // Capsule: same arithmetic as the placement below, read here so it redraws without a layout pass.
        var total = 2 * DockEngine.PAD + (n - 1) * DockEngine.GAP
        for (i in 0 until n) total += slot(i)
        val w = total.dp.toPx()
        val h = (2 * DockEngine.PAD + DockEngine.REST).dp.toPx()
        val r = CornerRadius(h / 2)
        val tl = Offset((size.width - w) / 2, 0f)
        drawRoundRect(glassColor, tl, Size(w, h), r)
        drawRoundRect(rim, tl, Size(w, h), r, style = Stroke(Grid.hairline.toPx()))
      },
  ) { measurables, _ ->
    val rest = DockEngine.REST.dp.roundToPx()
    val pad = DockEngine.PAD.dp.roundToPx()
    val gap = DockEngine.GAP.dp.toPx()
    val width = DockEngine.reservedWidth(n).dp.roundToPx()
    val height = (2 * DockEngine.PAD + if (hovering) DockEngine.MAX else DockEngine.REST).dp.roundToPx()
    val tiles = measurables.subList(0, n).mapIndexed { i, m ->
      if (i == n - 1 && open) m.measure(Constraints.fixed(DockEngine.FIELD.dp.roundToPx(), rest)) else m.measure(Constraints.fixed(rest, rest))
    }
    val tip = measurables[n].measure(Constraints())
    layout(width, height) {
      var total = 2 * pad + (n - 1) * gap
      for (i in 0 until n) { widths[i] = slot(i).dp.toPx(); total += widths[i] }
      var x = (width - total) / 2 + pad
      for (i in 0 until n) {
        val w = widths[i]
        centres[i] = x + w / 2
        if (i == n - 1 && open) tiles[i].place(x.toInt(), pad)
        else {
          val s = w / rest
          tiles[i].placeWithLayer((centres[i] - rest / 2f).toInt(), pad) {
            scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 0f)
          }
        }
        x += w + gap
      }
      val k = lastTip
      if (k in 0 until n) {
        val tx = (centres[k] - tip.width / 2f).toInt().coerceIn(0, (width - tip.width).coerceAtLeast(0))
        val ty = (pad + widths[k] + gap).toInt()
        val lift = 10.dp.toPx()
        tip.placeWithLayer(tx, ty) { alpha = tipAlpha; translationY = -(1f - tipAlpha) * lift }
      }
    }
  }
}

/** One page of the dock: 48 dp circle, 24 dp icon, a dot when it is the current page, a primary ring on focus. */
@Composable
private fun DockTile(title: String, icon: ImageVector, active: Boolean, onFocus: (Boolean) -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { MutableInteractionSource() }
  val focused by src.collectIsFocusedAsState()
  Box(
    modifier.size(Grid.u48).clip(CircleShape)
      .background(if (active) c.primary.copy(alpha = 0.16f) else c.onSurface.copy(alpha = 0.06f))
      .then(if (focused) Modifier.border(Grid.hairline + Grid.hairline, c.primary, CircleShape) else Modifier)
      .onFocusChanged { onFocus(it.isFocused) }
      .clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
      .semantics { contentDescription = title; selected = active },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = if (active) c.primary else c.onSurfaceVariant, modifier = Modifier.size(Grid.u24))
    if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Grid.u6).size(Grid.u6).clip(CircleShape).background(c.primary))
  }
}

/** The real search field, inside the dock. [reveal] (px) clips it while it opens, so the slot grows without a re-layout. */
@Composable
private fun DockSearchField(
  width: Float, reveal: () -> Float, query: String, requester: FocusRequester,
  onQuery: (String) -> Unit, onFocus: (Boolean) -> Unit,
) {
  val c = MaterialTheme.colorScheme
  val focus = LocalFocusManager.current
  val placeholder = stringResource(Res.string.home_search_placeholder)
  val closeLabel = stringResource(Res.string.shell_search_close)
  Row(
    Modifier.width(width.dp).height(Grid.u48)
      .drawWithContent { clipRect(right = reveal()) { this@drawWithContent.drawContent() } }
      .clip(CircleShape).background(c.onSurface.copy(alpha = 0.06f)).padding(start = Grid.u12),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Search, contentDescription = null, tint = c.primary, modifier = Modifier.size(Grid.u24))
    Spacer(Modifier.width(Grid.u12))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (query.isEmpty()) Text(placeholder, style = GridText.Body, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      BasicTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        textStyle = GridText.Body.copy(color = c.onSurface), cursorBrush = SolidColor(c.primary),
        modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { onFocus(it.isFocused) }
          .onPreviewKeyEvent { e ->
            // Enter or Down: from the field to the results below it.
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionDown)) {
              focus.moveFocus(FocusDirection.Down); true
            } else false
          }
          .testTag("dock_search_field")
          .semantics { contentDescription = placeholder },
      )
    }
    Box(
      Modifier.size(Grid.u48).clip(CircleShape).clickable(role = Role.Button) { ShellSearch.close() }.testTag("dock_search_close")
        .semantics { contentDescription = closeLabel },
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Close, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(Grid.u24)) }
  }
}

/** Narrow window: the current page as a toggle that opens the pages below it, plus search. */
@Composable
private fun CompactDock(
  current: TarkasNavTab, onSelect: (TarkasNavTab) -> Unit, reduce: Boolean, open: Boolean, searchTitle: String,
  tileFocus: FocusRequester, field: @Composable (Float, () -> Float) -> Unit, modifier: Modifier,
) {
  val c = MaterialTheme.colorScheme
  var menu by remember { mutableStateOf(false) }
  val toggleFocus = remember { FocusRequester() }
  val pagesTitle = stringResource(Res.string.shell_pages)
  val dismiss = { menu = false; runCatching { toggleFocus.requestFocus() }; Unit }
  BackHandler(menu) { dismiss() }
  Row(
    modifier.testTag("floating_dock").clip(CircleShape).background(glass()).border(Grid.hairline, c.onSurface.copy(alpha = 0.08f), CircleShape)
      .padding(Grid.u6),
    horizontalArrangement = Arrangement.spacedBy(Grid.u6), verticalAlignment = Alignment.CenterVertically,
  ) {
    Box {
      DockTile("$pagesTitle: ${stringResource(current.label)}", current.icon(), true, {}, Modifier.focusRequester(toggleFocus).testTag("dock_toggle")) { menu = !menu }
      if (menu) {
        val gap = with(LocalDensity.current) { Grid.u12.roundToPx() }
        Popup(popupPositionProvider = remember(gap) { BelowAnchor(gap) }, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
          Column(
            Modifier.width(Grid.u192 + Grid.u48).clip(Grid.r24).background(glass()).border(Grid.hairline, c.onSurface.copy(alpha = 0.08f), Grid.r24)
              .padding(Grid.u6).testTag("dock_menu"),
            verticalArrangement = Arrangement.spacedBy(Grid.u6),
          ) {
            NavOrder.forEachIndexed { i, tab ->
              val a = remember { Animatable(if (reduce) 1f else 0f) }
              LaunchedEffect(Unit) { if (!reduce) { delay(i * 50L); a.animateTo(1f, tween(200, easing = LinearOutSlowInEasing)) } }
              val on = tab == current
              val label = stringResource(tab.label)
              Row(
                Modifier.fillMaxWidth().height(Grid.u48)
                  .graphicsLayer { alpha = a.value; translationY = -(1f - a.value) * Grid.u12.toPx() }
                  .clip(CircleShape).background(if (on) c.primary.copy(alpha = 0.16f) else Color.Transparent)
                  .clickable(role = Role.Tab) { menu = false; SidePane.close(); onSelect(tab) }
                  .semantics { selected = on }
                  .testTag("dock_menu_${tab.name.lowercase()}")
                  .padding(horizontal = Grid.u12),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12),
              ) {
                Icon(tab.icon(), contentDescription = null, tint = if (on) c.primary else c.onSurfaceVariant, modifier = Modifier.size(Grid.u24))
                Text(label, style = if (on) GridText.BodyStrong else GridText.Body, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("Ctrl+${i + 1}", style = GridText.Small, color = c.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
    if (open) field(DockEngine.COMPACT_FIELD) { Float.MAX_VALUE }
    else DockTile(searchTitle, Icons.Outlined.Search, false, {}, Modifier.focusRequester(tileFocus).testTag("dock_search")) { ShellSearch.openDock() }
  }
}

private class BelowAnchor(private val gap: Int) : PopupPositionProvider {
  override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
    val x = (anchorBounds.left).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
    return IntOffset(x, anchorBounds.bottom + gap)
  }
}
