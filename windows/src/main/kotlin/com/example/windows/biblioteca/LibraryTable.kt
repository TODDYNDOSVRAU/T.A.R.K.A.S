package com.example.windows.biblioteca

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.components.highlightClickable
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.LocalBarInset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.isCtrlPressed as pCtrl
import androidx.compose.ui.input.pointer.isShiftPressed as pShift
import com.example.windows.res.*
import kotlinx.coroutines.launch
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlin.math.roundToInt

/** Test tag of the table's scrolling rows (LibraryPageLayoutTest). */
internal const val TAG_ROWS = "library-rows"

/** Two-language text for the desktop-only labels that have no Android resource (the language follows the system). */
fun lt(en: String, pt: String): String = if (java.util.Locale.getDefault().language == "pt") pt else en

/**
 * The tracks being dragged inside the window and the places they can be dropped on. The rows report where the
 * pointer is; each drop target registers its bounds; on release the hub looks up the target under the pointer.
 */
@Stable
class DragHub {
  var payload by mutableStateOf<List<Track>>(emptyList())
    private set
  var pointer by mutableStateOf(Offset.Zero)
    private set
  var hover by mutableStateOf<String?>(null)
    private set
  private val targets: SnapshotStateMap<String, Rect> = mutableStateMapOf()

  val active get() = payload.isNotEmpty()

  fun register(id: String, bounds: Rect) { targets[id] = bounds }
  fun unregister(id: String) { targets.remove(id) }

  fun start(tracks: List<Track>, at: Offset) { payload = tracks; move(at) }

  fun move(at: Offset) {
    pointer = at
    hover = targets.entries.firstOrNull { it.value.contains(at) }?.key
  }

  /** Ends the drag; returns the dropped tracks and the id of the target they were dropped on (null when none). */
  fun finish(): Pair<List<Track>, String?> {
    val result = payload to hover
    payload = emptyList(); hover = null
    return result
  }

  fun cancel() { payload = emptyList(); hover = null }
}

/** Marks a region as a place tracks can be dropped on, and outlines it while a drag is over it. */
@Composable
fun Modifier.dropTarget(hub: DragHub, id: String): Modifier {
  DisposableEffect(id) { onDispose { hub.unregister(id) } }
  val over = hub.active && hub.hover == id
  return this
    .onGloballyPositioned { hub.register(id, it.boundsInRoot()) }
    .then(if (over) Modifier.border(Grid.u6 / 6, MaterialTheme.colorScheme.primary) else Modifier)
}

/** Accepts files and folders dragged from Explorer onto this region; [onFiles] gets them. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Modifier.explorerDrop(onFiles: (List<File>) -> Unit): Modifier {
  val target = remember(onFiles) {
    object : DragAndDropTarget {
      override fun onDrop(event: DragAndDropEvent): Boolean {
        val t = event.awtTransferable
        if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
        @Suppress("UNCHECKED_CAST")
        val files = runCatching { t.getTransferData(DataFlavor.javaFileListFlavor) as List<File> }.getOrNull() ?: return false
        onFiles(files)
        return true
      }
    }
  }
  return this.dragAndDropTarget(
    shouldStartDragAndDrop = { it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
    target = target
  )
}

/** What the table asks of the screen: every action is on a list of tracks, so one or many rows work the same. */
class TableActions(
  val play: (Track) -> Unit,
  val playAll: (List<Track>) -> Unit,
  val playNext: (List<Track>) -> Unit,
  val addToQueue: (List<Track>) -> Unit,
  val addToPlaylist: (List<Track>, playlistId: String) -> Unit,
  val reveal: (Track) -> Unit,
  val copyInfo: (List<Track>) -> Unit,
  val toggleFavorite: (Track) -> Unit,
  /** Delete key and the menu entry; null where rows cannot be removed (the library itself). */
  val remove: ((List<Track>) -> Unit)? = null,
  val removeLabel: String = ""
)

private val TABLE_ROW = Grid.u24

/**
 * The track table: sortable, resizable columns, Ctrl/Shift multi-select, right-click menu, drag out, keyboard
 * (arrows, Shift+arrows, Ctrl+A, Enter, Delete, Ctrl+C) and type-ahead on the title.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TrackTable(
  tracks: List<Track>,
  selection: Selection,
  onSelection: (Selection) -> Unit,
  sort: SortSpec?,
  onSort: (Col) -> Unit,
  widths: Map<Col, Int>,
  onWidths: (Map<Col, Int>) -> Unit,
  plays: Map<String, Int>,
  favorites: Set<String>,
  playingId: String?,
  playlists: List<Pair<String, String>>,
  actions: TableActions,
  hub: DragHub,
  modifier: Modifier = Modifier,
  focus: FocusRequester = remember { FocusRequester() }
) {
  val colors = MaterialTheme.colorScheme
  val list = rememberLazyListState()
  val order = remember(tracks) { tracks.map { it.id } }
  val typed = remember { TypeAheadBuffer() }
  val rowIds = remember(order) { order }

  val scope = androidx.compose.runtime.rememberCoroutineScope()
  fun reveal(index: Int) { scope.launch { scrollIntoView(list, index) } }

  androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
  // A narrow pane (master-detail) cannot hold the dragged widths plus a readable title: shrink them for drawing only.
  val shown = remember(widths, maxWidth) { TableLogic.fitTo(widths, maxWidth.value.toInt() - 24) }
  Column(
    Modifier.fillMaxSize()
      .focusRequester(focus)
      .focusable()
      .onKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
        val ctrl = e.isCtrlPressed
        when {
          ctrl && e.key == Key.A -> { onSelection(selection.all(rowIds)); true }
          ctrl && e.key == Key.C -> { actions.copyInfo(selection.tracks(tracks)); true }
          ctrl -> false
          e.key == Key.DirectionDown -> { val s = selection.move(1, rowIds, e.isShiftPressed); onSelection(s); reveal(s.focus); true }
          e.key == Key.DirectionUp -> { val s = selection.move(-1, rowIds, e.isShiftPressed); onSelection(s); reveal(s.focus); true }
          e.key == Key.Home -> { val s = selection.click(0, rowIds, false, e.isShiftPressed); onSelection(s); reveal(0); true }
          e.key == Key.MoveEnd -> { val s = selection.click(rowIds.lastIndex, rowIds, false, e.isShiftPressed); onSelection(s); reveal(rowIds.lastIndex); true }
          e.key == Key.Enter -> {
            val picked = selection.tracks(tracks)
            if (picked.size > 1) actions.playAll(picked) else (picked.firstOrNull() ?: tracks.getOrNull(selection.focus))?.let(actions.play)
            true
          }
          e.key == Key.Delete -> { actions.remove?.let { r -> selection.tracks(tracks).takeIf { it.isNotEmpty() }?.let(r) }; actions.remove != null }
          e.key == Key.Escape -> if (selection.ids.isNotEmpty()) { onSelection(Selection()); true } else false
          else -> {
            val ch = e.utf16CodePoint.takeIf { it in 32..0xFFFF }?.toChar()
            if (ch != null && !ch.isISOControl()) {
              val text = typed.push(ch)
              val at = TableLogic.typeAhead(tracks, text, if (text.length == 1) selection.focus + 1 else selection.focus.coerceAtLeast(0))
              if (at >= 0) { onSelection(selection.click(at, rowIds, false, false)); reveal(at) }
              true
            } else false
          }
        }
      }
  ) {
    TableHeader(sort, onSort, shown) { nw -> onWidths(widths + nw.filterValues { it > 0 }) }
    Box(Modifier.fillMaxWidth().height(Grid.u6 / 6).background(colors.outlineVariant))
    BoxedRows(tracks, list) { index, track ->
      val selected = track.id in selection.ids
      TableRow(
        index, track, selected, track.id == playingId, track.id in favorites, plays[track.id] ?: 0, shown,
        onClick = { ctrl, shift -> focus.requestFocus(); onSelection(selection.click(index, rowIds, ctrl, shift)) },
        onContext = { onSelection(selection.context(index, rowIds)) },
        onDouble = { actions.play(track) },
        menuItems = {
          val chosen = if (selected) selection.tracks(tracks) else listOf(track)
          buildMenu(chosen, playlists, actions)
        },
        dragTracks = { if (selected) selection.tracks(tracks) else listOf(track) },
        hub = hub, actions = actions
      )
    }
  }
  }
}

@Composable
private fun Column(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
  androidx.compose.foundation.layout.Column(modifier, content = content)

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BoxedRows(
  tracks: List<Track>, state: LazyListState, row: @Composable (Int, Track) -> Unit
) {
  // The corner player floats over the bottom-left of the page: the last row scrolls up clear of it.
  LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(TAG_ROWS), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = LocalBarInset.current)) {
    itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, t -> row(i, t) }
  }
}

private suspend fun scrollIntoView(state: LazyListState, index: Int) {
  if (index < 0) return
  val visible = state.layoutInfo.visibleItemsInfo
  if (visible.isEmpty()) return
  val first = visible.first().index
  val last = visible.last().index
  when {
    index <= first -> state.scrollToItem(index)
    index >= last -> state.scrollToItem((index - (last - first) + 1).coerceAtLeast(0))
  }
}

private fun buildMenu(chosen: List<Track>, playlists: List<Pair<String, String>>, a: TableActions): List<ContextMenuItem> {
  val one = chosen.singleOrNull()
  val n = chosen.size
  return buildList {
    add(ContextMenuItem(if (one != null) "Play" else lt("Play $n", "Tocar $n")) { if (one != null) a.play(one) else a.playAll(chosen) })
    add(ContextMenuItem(lt("Play next", "Tocar em seguida")) { a.playNext(chosen) })
    add(ContextMenuItem(lt("Add to queue", "Adicionar à fila")) { a.addToQueue(chosen) })
    for ((id, name) in playlists.take(12)) add(ContextMenuItem(lt("Add to playlist: $name", "Adicionar à playlist: $name")) { a.addToPlaylist(chosen, id) })
    if (one != null) add(ContextMenuItem(lt("Favourite", "Favorito")) { a.toggleFavorite(one) })
    if (one != null && TableLogic.fileOf(one) != null) add(ContextMenuItem(lt("Show in Explorer", "Mostrar no Explorer")) { a.reveal(one) })
    add(ContextMenuItem(lt("Copy info", "Copiar informações")) { a.copyInfo(chosen) })
    a.remove?.let { r -> add(ContextMenuItem(a.removeLabel) { r(chosen) }) }
  }
}

@Composable
private fun TableHeader(sort: SortSpec?, onSort: (Col) -> Unit, widths: Map<Col, Int>, onWidths: (Map<Col, Int>) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val widthsNow by androidx.compose.runtime.rememberUpdatedState(widths)
  val onWidths by androidx.compose.runtime.rememberUpdatedState(onWidths)
  Row(Modifier.fillMaxWidth().height(Grid.u24).padding(horizontal = Grid.u12), verticalAlignment = Alignment.CenterVertically) {
    for (c in Col.entries) {
      val width = widths[c]
      if (width == 0) continue
      val cell = if (width == null) Modifier.weight(1f) else Modifier.width(width.dp)
      Row(cell.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Row(
          Modifier.weight(1f).fillMaxHeight().highlightClickable(onClick = { onSort(c) }).padding(horizontal = Grid.u6),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            columnName(c), style = GridText.Label, color = if (sort?.col == c) colors.primary else colors.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
          )
          if (sort?.col == c) Icon(
            if (sort.ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null,
            Modifier.padding(start = Grid.u6).width(Grid.u12).height(Grid.u12), tint = colors.primary
          )
        }
        Box(
          Modifier.width(Grid.u12).fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Crosshair)
            .pointerInput(c) {
              var acc = 0f
              detectDragGestures(onDragStart = { acc = 0f }) { change, drag ->
                change.consume()
                acc += drag.x / density
                val whole = acc.toInt()
                if (whole != 0) { onWidths(TableLogic.resize(widthsNow, c, whole)); acc -= whole }
              }
            },
          contentAlignment = Alignment.Center
        ) { Box(Modifier.width(Grid.u6 / 6).fillMaxHeight(0.5f).background(colors.outlineVariant)) }
      }
    }
  }
}

@Composable
fun columnName(c: Col): String = when (c) {
  Col.TITLE -> tr(Res.string.library_order_title)
  Col.ARTIST -> tr(Res.string.library_order_artist)
  Col.ALBUM -> tr(Res.string.library_order_album)
  Col.DURATION -> tr(Res.string.audio_adv_duration)
  Col.PLAYS -> lt("Plays", "Execuções")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun TableRow(
  index: Int, track: Track, selected: Boolean, playing: Boolean, favorite: Boolean, plays: Int, widths: Map<Col, Int>,
  onClick: (ctrl: Boolean, shift: Boolean) -> Unit, onContext: () -> Unit, onDouble: () -> Unit,
  menuItems: () -> List<ContextMenuItem>, dragTracks: () -> List<Track>, hub: DragHub, actions: TableActions
) {
  val colors = MaterialTheme.colorScheme
  var origin by remember { mutableStateOf(Offset.Zero) }
  var lastPress by remember { mutableStateOf(0L) }
  val dragNow by androidx.compose.runtime.rememberUpdatedState(dragTracks)
  val actionsNow by androidx.compose.runtime.rememberUpdatedState(actions)
  val background = when {
    selected -> colors.primary.copy(alpha = 0.16f)
    index % 2 == 1 -> colors.onSurface.copy(alpha = 0.03f)
    else -> androidx.compose.ui.graphics.Color.Transparent
  }
  ContextMenuArea(items = menuItems) {
    Row(
      Modifier.fillMaxWidth().height(TABLE_ROW).background(background).padding(horizontal = Grid.u12)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .onPointerEvent(PointerEventType.Press) { e ->
          if (e.buttons.isSecondaryPressed) onContext()
          else {
            onClick(e.keyboardModifiers.pCtrl, e.keyboardModifiers.pShift)
            val now = System.currentTimeMillis()
            if (now - lastPress < DOUBLE_MS && !e.keyboardModifiers.pCtrl && !e.keyboardModifiers.pShift) onDouble()
            lastPress = now
          }
        }
        .pointerInput(track.id) {
          detectDragGestures(
            onDragStart = { at -> hub.start(dragNow(), origin + at) },
            onDrag = { change, drag -> change.consume(); hub.move(hub.pointer + drag) },
            onDragEnd = {
              val (tracks, target) = hub.finish()
              if (target != null) DropTargets.handle(target, tracks, actionsNow)
            },
            onDragCancel = { hub.cancel() }
          )
        },
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(Modifier.weight(1f).padding(horizontal = Grid.u6), verticalAlignment = Alignment.CenterVertically) {
        TrackArtwork(track, Grid.u24)
        Text(
          track.title, style = GridText.Small, color = if (playing) colors.primary else colors.onSurface,
          maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(start = Grid.u6)
        )
        ExplicitMark(track, Modifier.padding(start = Grid.u6))
        if (favorite) Icon(Icons.Filled.Favorite, null, Modifier.padding(start = Grid.u6).width(Grid.u12).height(Grid.u12), tint = colors.primary)
      }
      cell(Col.ARTIST, widths, track.artistLabel())
      cell(Col.ALBUM, widths, track.album)
      cell(Col.DURATION, widths, formatDuration(track.durationMs), end = true)
      cell(Col.PLAYS, widths, if (plays > 0) plays.toString() else "", end = true)
      Box(Modifier.width(Grid.u12 * 2 / 3 * 0)) // keeps the row's right edge on the grid (no extra padding)
    }
  }
}

@Composable
private fun cell(c: Col, widths: Map<Col, Int>, text: String, end: Boolean = false) {
  if (widths[c] == 0) return
  Text(
    text, style = if (end) GridText.Label else GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (end) TextAlign.End else TextAlign.Start,
    modifier = Modifier.width((widths[c] ?: c.defaultDp).dp).padding(horizontal = Grid.u6)
  )
}

private const val DOUBLE_MS = 400L

/** Where a drag of tracks can end, by target id, and what it does. */
object DropTargets {
  const val QUEUE = "queue"
  const val PLAYLIST_PREFIX = "playlist:"
  fun playlist(id: String) = PLAYLIST_PREFIX + id

  fun handle(target: String, tracks: List<Track>, a: TableActions) {
    when {
      target == QUEUE -> a.addToQueue(tracks)
      target.startsWith(PLAYLIST_PREFIX) -> a.addToPlaylist(tracks, target.removePrefix(PLAYLIST_PREFIX))
    }
  }
}

/** The small chip that follows the pointer while tracks are dragged. Place it last in the screen's root box. */
@Composable
fun DragChip(hub: DragHub, origin: Offset) {
  if (!hub.active) return
  val colors = MaterialTheme.colorScheme
  val first = hub.payload.first()
  val more = hub.payload.size - 1
  Box(
    Modifier
      .offset { IntOffset((hub.pointer.x - origin.x).roundToInt() + 12, (hub.pointer.y - origin.y).roundToInt() + 12) }
      .background(colors.surfaceVariant)
      .border(Grid.u6 / 6, colors.primary)
      .padding(horizontal = Grid.u12, vertical = Grid.u6)
  ) {
    Text(
      if (more > 0) "${first.title} +$more" else first.title,
      style = GridText.Small, color = colors.onSurface, maxLines = 1
    )
  }
}
