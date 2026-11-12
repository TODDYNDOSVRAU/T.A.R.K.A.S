package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.model.Track
import com.example.data.playback.PlaybackOrder
import com.example.ui.components.EmptyState
import com.example.ui.components.QueueItemMenu
import com.example.ui.components.TextAction
import com.example.ui.components.TrackRow
import com.example.ui.screens.library.TrackDivider
import com.example.ui.screens.library.trackSubtitle
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.opensPlayer
import kotlinx.coroutines.launch

/**
 * The queue: what the user queued ("Na fila"), then what the list plays after it ("Depois, da
 * lista"). Queued songs move by their handle and leave with a swipe; the order is applied when the
 * finger lifts, so nothing plays out of order mid-drag.
 */
@Composable
internal fun QueueList(
  queue: List<Track>,
  upcoming: List<Track>,
  onPlay: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onMove: (Int, Int) -> Unit,
  onClear: () -> Unit,
  onPlayUpcoming: (Track) -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val haptics = LocalHapticFeedback.current
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var confirmClear by remember { mutableStateOf(false) }

  // While a drag runs the rows follow a local order; the queue itself changes once, on release.
  var local by remember { mutableStateOf<List<Track>?>(null) }
  val shown = local ?: queue
  val keys = PlaybackOrder.entryKeys(shown)
  var dragKey by remember { mutableStateOf<String?>(null) }
  var dragFrom by remember { mutableStateOf(-1) }
  var dragStartOffset by remember { mutableFloatStateOf(0f) }
  var dragDelta by remember { mutableFloatStateOf(0f) }
  // The first queue row's index in the list (the header comes before it).
  val base = 1

  fun dragged() = dragKey?.let { k -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "q_$k" } }

  fun onDrag(dy: Float) {
    if (dragKey == null) return
    dragDelta += dy
    val item = dragged() ?: return
    val start = dragStartOffset + dragDelta
    val middle = start + item.size / 2f
    val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
      other.key != item.key && (other.key as? String)?.startsWith("q_") == true &&
        middle.toInt() in other.offset..(other.offset + other.size)
    }
    if (target != null) {
      val from = item.index - base
      val to = target.index - base
      val list = shown.toMutableList()
      if (from in list.indices && to in list.indices) {
        list.add(to, list.removeAt(from))
        local = list
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
      }
    } else {
      // Near the edges the list scrolls under the finger.
      val viewport = listState.layoutInfo.viewportEndOffset
      val edge = 120f
      val over = when {
        start < edge && dy < 0 -> dy
        start + item.size > viewport - edge && dy > 0 -> dy
        else -> 0f
      }
      if (over != 0f) scope.launch { listState.scrollBy(over) }
    }
  }

  fun endDrag() {
    val key = dragKey
    val order = local
    if (key != null && order != null) {
      val to = PlaybackOrder.entryKeys(order).indexOf(key)
      if (dragFrom >= 0 && to >= 0 && to != dragFrom) onMove(dragFrom, to)
    }
    dragKey = null
    dragFrom = -1
    dragDelta = 0f
    local = null
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    if (queue.isEmpty() && upcoming.isEmpty()) {
      item(key = "empty") {
        EmptyState(
          icon = Icons.AutoMirrored.Rounded.QueueMusic,
          title = stringResource(R.string.queue_empty_title),
          message = stringResource(R.string.queue_empty_message),
          modifier = Modifier.padding(horizontal = Space.page)
        )
      }
      return@LazyColumn
    }

    item(key = "header") {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = Size.touch)
          .padding(start = Space.page, end = Space.page - Space.s, top = Space.s),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          stringResource(R.string.queue_header),
          style = AppText.Title2,
          color = colors.onSurface,
          modifier = Modifier
            .weight(1f)
            .semantics { heading() }
        )
        if (queue.isNotEmpty()) {
          TextAction(stringResource(R.string.queue_clear), onClick = { confirmClear = true }, modifier = Modifier.testTag("library_clear_queue"))
        }
      }
    }

    itemsIndexed(shown, key = { i, _ -> "q_${keys[i]}" }) { index, track ->
      val key = keys[index]
      val isDragged = key == dragKey
      // Not saved with the row: an undone removal brings back the same key, and a saved "dismissed"
      // state would remove it again at once.
      val moveUpLabel = stringResource(R.string.queue_move_up)
      val moveDownLabel = stringResource(R.string.queue_move_down)
      val removeLabel = stringResource(R.string.queue_remove)
      val handleLabel = stringResource(R.string.queue_handle)
      val dismiss = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
      SwipeToDismissBox(
        state = dismiss,
        gesturesEnabled = dragKey == null,
        onDismiss = { onRemove(index) },
        backgroundContent = {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .background(colors.surfaceContainerHigh)
              .padding(horizontal = Space.page),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End
          ) {
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = colors.onSurfaceVariant)
          }
        },
        modifier = Modifier
          .zIndex(if (isDragged) 1f else 0f)
          .graphicsLayer {
            if (isDragged) {
              val item = dragged()
              translationY = if (item != null) dragStartOffset + dragDelta - item.offset else 0f
              shadowElevation = 8.dp.toPx()
            }
          }
          .then(if (isDragged) Modifier else Modifier.animateItem())
      ) {
        Column(modifier = Modifier.background(colors.background)) {
          if (index > 0) TrackDivider()
          TrackRow(
            track = track,
            subtitle = trackSubtitle(track),
            onClick = { onPlay(index) },
            modifier = Modifier
              .opensPlayer()
              .semantics {
                // Moving without dragging, for TalkBack and switch access.
                customActions = listOfNotNull(
                  if (index > 0) CustomAccessibilityAction(moveUpLabel) { onMove(index, index - 1); true } else null,
                  if (index < shown.lastIndex) CustomAccessibilityAction(moveDownLabel) { onMove(index, index + 1); true } else null,
                  CustomAccessibilityAction(removeLabel) { onRemove(index); true }
                )
              },
            trailing = {
              Row(verticalAlignment = Alignment.CenterVertically) {
              QueueItemMenu(
                track = track,
                canMoveUp = index > 0,
                canMoveDown = index < shown.lastIndex,
                onMoveUp = { onMove(index, index - 1) },
                onMoveDown = { onMove(index, index + 1) },
                onRemove = { onRemove(index) }
              )
              Box(
                modifier = Modifier
                  .size(Size.touch)
                  .testTag("queue_handle_$index")
                  .semantics {
                    contentDescription = handleLabel
                    role = Role.Button
                  }
                  .pointerInput(key) {
                    detectDragGestures(
                      onDragStart = {
                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "q_$key" }
                        if (info != null) {
                          dragKey = key
                          dragFrom = PlaybackOrder.entryKeys(queue).indexOf(key)
                          dragStartOffset = info.offset.toFloat()
                          dragDelta = 0f
                          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                      },
                      onDragEnd = { endDrag() },
                      onDragCancel = { endDrag() },
                      onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.y)
                      }
                    )
                  },
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Rounded.DragHandle, contentDescription = null, tint = colors.onSurfaceVariant)
              }
              }
            }
          )
        }
      }
    }

    if (queue.isEmpty()) {
      item(key = "queue_empty_note") {
        Text(
          stringResource(R.string.queue_empty_note),
          style = AppText.Footnote,
          color = colors.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s)
        )
      }
    }

    if (upcoming.isNotEmpty()) {
      item(key = "upcoming_header") {
        Text(
          stringResource(R.string.queue_upcoming_header),
          style = AppText.Title3,
          color = colors.onSurface,
          modifier = Modifier
            .padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.xs)
            .semantics { heading() }
        )
      }
      itemsIndexed(upcoming, key = { i, t -> "u_${i}_${t.id}" }) { index, track ->
        Column {
          if (index > 0) TrackDivider()
          TrackRow(
            track = track,
            subtitle = trackSubtitle(track),
            onClick = { onPlayUpcoming(track) },
            modifier = Modifier.opensPlayer()
          )
        }
      }
    }
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text(stringResource(R.string.queue_clear_title), style = AppText.Title3) },
      text = {
        Text(
          pluralStringResource(R.plurals.queue_clear_message, queue.size, queue.size),
          style = AppText.Body
        )
      },
      confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.queue_clear)) } },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.queue_cancel)) } }
    )
  }
}
