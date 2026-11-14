package com.example.ui.screens.library

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Track
import com.example.ui.components.FilterChip
import com.example.ui.components.TrackRow
import com.example.ui.screens.library.PlayShuffleRow
import com.example.ui.screens.library.TrackDivider
import com.example.ui.screens.library.itemMotion
import com.example.ui.screens.library.trackSubtitle
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.opensPlayer
import com.example.ui.theme.rememberReducedMotion

/** The name of a song order in the current language. */
@StringRes
internal fun com.example.data.playback.SongOrder.labelRes(): Int = when (this) {
  com.example.data.playback.SongOrder.TITLE -> R.string.library_order_title
  com.example.data.playback.SongOrder.ARTIST -> R.string.library_order_artist
  com.example.data.playback.SongOrder.ALBUM -> R.string.library_order_album
  com.example.data.playback.SongOrder.RECENT -> R.string.library_order_recent
  com.example.data.playback.SongOrder.MOST_PLAYED -> R.string.library_order_most_played
}

/**
 * A list of songs with Play and Shuffle above it. [header] goes before them (a sub-page title),
 * [empty] replaces the list when there are no songs, [trailing] adds a button to each row.
 */
@Composable
internal fun TrackList(
  tracks: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onPlay: (Track) -> Unit,
  onShuffle: () -> Unit,
  empty: @Composable () -> Unit,
  header: LazyListScope.() -> Unit = {},
  /** The A–Z letter of a track; with it, a letter strip on the right jumps through the list. */
  index: ((Track) -> String?)? = null,
  /** A row's second line, when it should say more than artist and source. */
  subtitle: @Composable (Track) -> String = { trackSubtitle(it) },
  trailing: (@Composable RowScope.(Track) -> Unit)? = null
) {
  val listState = androidx.compose.foundation.lazy.rememberLazyListState()
  val letters = remember(tracks, index) {
    if (index == null) emptyList() else {
      // Each letter's first track, in list order.
      val firsts = LinkedHashMap<String, Int>()
      tracks.forEachIndexed { i, t -> index(t)?.let { firsts.putIfAbsent(it, i) } }
      firsts.entries.map { it.key to it.value }
    }
  }
  // Only worth it for a long list with several letters, and only where there is height for 26 of them.
  val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
  val showStrip = letters.size >= 3 && tracks.size >= 30 && !compact
  Box(modifier = Modifier.fillMaxSize()) {
  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    // With the strip on, rows and their dividers stop before it instead of running under it.
    contentPadding = PaddingValues(
      end = if (showStrip) LetterStripWidth else 0.dp,
      bottom = LocalBarInset.current + Space.l
    )
  ) {
    header()
    if (tracks.isEmpty()) {
      // Centered in the space the list has above the bars, below whatever the header holds. With little
      // height (landscape) it starts at the top, so the text is not pushed behind the mini player.
      item(key = "empty") {
        Box(
          modifier = Modifier.fillMaxWidth().fillParentMaxHeight(if (compact) 0.6f else 0.8f),
          contentAlignment = if (compact) Alignment.TopCenter else Alignment.Center
        ) { empty() }
      }
    } else {
      item(key = "play_shuffle") { PlayShuffleRow(onPlay = { onPlay(tracks.first()) }, onShuffle = onShuffle) }
      itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
        Column(modifier = itemMotion(index)) {
          if (index > 0) TrackDivider()
          TrackRow(
            track = track,
            subtitle = subtitle(track),
            onClick = { onPlay(track) },
            isCurrent = currentTrack?.id == track.id,
            isPlaying = isPlaying,
            modifier = Modifier.opensPlayer(),
            trailing = { trailing?.invoke(this, track) }
          )
        }
      }
    }
  }
  if (showStrip) {
    // The tracks are the list's last items: header rows and the play row come before them.
    LetterStrip(
      letters = letters.map { it.first },
      onLetter = { letter ->
        val first = letters.firstOrNull { it.first == letter }?.second ?: return@LetterStrip
        val offset = listState.layoutInfo.totalItemsCount - tracks.size
        listState.requestScrollToItem(offset + first)
      },
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .padding(bottom = LocalBarInset.current)
    )
  }
  }
}

/** The order of the device's songs: title, artist, album or newest first. */
@Composable
internal fun SongOrderRow(
  order: com.example.data.playback.SongOrder,
  onChange: (com.example.data.playback.SongOrder) -> Unit,
  foldersActive: Boolean,
  onFolders: () -> Unit,
  modifier: Modifier = Modifier
) {
  val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
  // The chosen chip comes into view: the last ones sit past the right edge on a narrow screen.
  LaunchedEffect(order) { rowState.animateScrollToItem(order.ordinal) }
  androidx.compose.foundation.lazy.LazyRow(
    state = rowState,
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(Space.xs),
    contentPadding = PaddingValues(horizontal = Space.page - Space.xs, vertical = Space.xs)
  ) {
    items(com.example.data.playback.SongOrder.entries.size) { i ->
      val o = com.example.data.playback.SongOrder.entries[i]
      FilterChip(
        label = stringResource(o.labelRes()),
        selected = o == order,
        onClick = { onChange(o) },
        modifier = Modifier.testTag("song_order_${o.key}")
      )
    }
    item(key = "folders") {
      FilterChip(
        label = stringResource(R.string.library_folders_chip),
        selected = foldersActive,
        onClick = onFolders,
        modifier = Modifier.testTag("library_folders")
      )
    }
  }
}

/** Width of the A–Z strip: a touch target close to the 48 dp minimum; sliding makes the height forgiving. */
internal val LetterStripWidth = 44.dp

/**
 * A–Z strip on the list's right edge: touching or sliding along it jumps to the first track of the
 * letter under the finger, with a tick per letter and the letter shown large beside it.
 * TalkBack reads it as one element; users of it scroll the list itself.
 */
@Composable
internal fun LetterStrip(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
  val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
  val stripDescription = stringResource(R.string.library_letter_strip)
  var active by remember { mutableStateOf<String?>(null) }
  var height by remember { mutableStateOf(1) }
  fun pick(y: Float) {
    val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
    val l = letters[i]
    if (l != active) {
      active = l
      haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
      onLetter(l)
    }
  }
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    // The bubble grows from nothing on the gel spring (a small overshoot) and keeps the last letter while it shrinks away.
    var shown by remember { mutableStateOf("") }
    active?.let { shown = it }
    val bubble by androidx.compose.animation.core.animateFloatAsState(
      targetValue = if (active != null) 1f else 0f,
      animationSpec = if (com.example.ui.theme.rememberReducedMotion()) {
        androidx.compose.animation.core.snap()
      } else {
        if (active != null) com.example.ui.theme.Motion.gel() else com.example.ui.theme.Motion.gelExit()
      },
      label = "letterBubble"
    )
    if (bubble > 0f) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .graphicsLayer {
            scaleX = bubble
            scaleY = bubble
            alpha = bubble.coerceIn(0f, 1f)
          }
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
      ) {
        Text(shown, style = AppText.Title2, color = MaterialTheme.colorScheme.onSurface)
      }
      Spacer(Modifier.width(Space.s))
    }
    Column(
      modifier = Modifier
        .width(LetterStripWidth)
        .onSizeChanged { height = it.height.coerceAtLeast(1) }
        .pointerInput(letters) {
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            pick(down.position.y)
            while (true) {
              val e = awaitPointerEvent()
              val c = e.changes.firstOrNull() ?: break
              if (!c.pressed) break
              c.consume()
              pick(c.position.y)
            }
            active = null
          }
        }
        // One element for TalkBack; the list itself scrolls with its own gestures.
        .clearAndSetSemantics { contentDescription = stripDescription }
        .testTag("letter_strip"),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      letters.forEach { l ->
        Text(
          l,
          style = AppText.Caption2,
          color = if (l == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Space.xs, vertical = Size.hairline)
        )
      }
    }
  }
}
