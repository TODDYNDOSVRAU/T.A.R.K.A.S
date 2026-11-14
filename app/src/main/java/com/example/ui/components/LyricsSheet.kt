package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.LyricsState
import com.example.data.model.Track
import com.example.ui.theme.AppText
import com.example.ui.theme.Radius
import com.example.ui.theme.Size
import com.example.ui.theme.Space

/**
 * The lyrics of the playing song. When the lines carry times, the one being sung is the brightest and
 * the list follows it (unless the finger is on the list); touching a line moves the song there.
 * Lyrics without times are only text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
  track: Track,
  state: LyricsState,
  currentPositionMs: () -> Long,
  onSeek: (Long) -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    dragHandle = { SheetHandle() }
  ) {
    Column(modifier = Modifier.fillMaxSize().testTag("lyrics_sheet")) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s)) {
        Text(
          stringResource(R.string.lyrics_title),
          style = AppText.Footnote,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.semantics { heading() }
        )
        Text(track.title, style = AppText.Title3, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = AppText.Subhead, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Hairline(Modifier.padding(vertical = Space.xs))
      val lyrics = state.lyrics
      when {
        state.loading || state.trackId != track.id -> Centered {
          CircularProgressIndicator(
            modifier = Modifier.semantics { stateDescription = "" }
          )
        }
        lyrics != null -> LyricsList(lyrics, currentPositionMs, onSeek)
        state.notOnDevice -> Message(stringResource(R.string.lyrics_not_on_device))
        else -> Message(stringResource(R.string.lyrics_none))
      }
    }
  }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xl), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Message(text: String) {
  Text(
    text,
    style = AppText.Body,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Space.page, vertical = Space.l).testTag("lyrics_message")
  )
}

@Composable
private fun LyricsList(lyrics: Lyrics, currentPositionMs: () -> Long, onSeek: (Long) -> Unit) {
  val listState = rememberLazyListState()
  // Reads the position inside the snapshot: the list recomposes when the line changes, not on every tick.
  val active by remember(lyrics) { derivedStateOf { lyrics.activeIndex(currentPositionMs()) } }

  LaunchedEffect(lyrics, active) {
    if (active < 0 || listState.isScrollInProgress) return@LaunchedEffect
    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
    if (info == null) {
      listState.scrollToItem(active)
    } else {
      val target = listState.layoutInfo.viewportSize.height / 3
      listState.animateScrollBy((info.offset - target).toFloat(), tween(280, easing = LinearOutSlowInEasing))
    }
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().testTag("lyrics_list"),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.page, vertical = Space.m),
    verticalArrangement = Arrangement.spacedBy(Space.xs)
  ) {
    if (!lyrics.synced) {
      item(key = "plain_note") {
        Text(
          stringResource(R.string.lyrics_plain_note),
          style = AppText.Footnote,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = Space.s)
        )
      }
    }
    itemsIndexed(lyrics.lines, key = { i, _ -> "l_$i" }) { index, line ->
      LyricRow(line, isActive = index == active, synced = lyrics.synced, onSeek = onSeek)
    }
  }
}

@Composable
private fun LyricRow(line: LyricLine, isActive: Boolean, synced: Boolean, onSeek: (Long) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val color by animateColorAsState(
    if (isActive) colors.onSurface else colors.onSurfaceVariant,
    tween(200, easing = LinearOutSlowInEasing),
    label = "lyricColor"
  )
  val nowLabel = stringResource(R.string.lyrics_now)
  val seekLabel = stringResource(R.string.lyrics_seek_here)
  val time = line.timeMs
  if (line.text.isEmpty()) {
    // A pause between verses: space only, nothing for TalkBack to read.
    Box(modifier = Modifier.heightIn(min = Space.m).fillMaxWidth())
    return
  }
  Text(
    line.text,
    style = AppText.Title3.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal),
    color = color,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = Size.touch)
      .then(
        if (synced && time != null) Modifier.clickable(onClickLabel = seekLabel, role = Role.Button) { onSeek(time) } else Modifier
      )
      .semantics { if (isActive) stateDescription = nowLabel }
      .padding(vertical = Space.s)
  )
}
