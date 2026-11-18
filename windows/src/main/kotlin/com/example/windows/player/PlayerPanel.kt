package com.example.windows.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.Res
import com.example.windows.res.player_menu_play_next
import com.example.windows.res.player_screen_nothing_playing
import com.example.windows.res.player_screen_next
import com.example.windows.res.player_screen_off
import com.example.windows.res.player_screen_pick_song
import com.example.windows.res.player_screen_previous
import com.example.windows.res.player_screen_queue
import com.example.windows.res.player_screen_repeat
import com.example.windows.res.player_screen_shuffle
import com.example.windows.res.player_screen_sleep_timer
import com.example.windows.res.player_screen_volume
import com.example.windows.res.queue_clear
import com.example.windows.res.queue_header
import com.example.windows.res.queue_move_down
import com.example.windows.res.queue_move_up
import com.example.windows.res.queue_remove
import com.example.windows.res.tr
import com.example.windows.shell.PanelContent
import com.example.windows.state.TarkasState
import kotlinx.coroutines.delay
import java.awt.FileDialog
import java.io.File

/** Settings-table adapter so the player keeps its state (scope "player") between runs. */
class SettingsPlayerPrefs(private val state: TarkasState) : PlayerPrefs {
  override fun get(key: String) = state.settings.get("player", key)
  override fun put(key: String, value: String) = state.settings.put("player", key, value)
}

/** One controller per run, built on the app's DesktopPlayer; other areas play through [PlayerHost.controller]. */
object PlayerHost {
  @Volatile private var c: PlayerController? = null
  private var keys: MediaKeys? = null

  fun controller(state: TarkasState): PlayerController = c ?: synchronized(this) {
    c ?: PlayerController(state.player, SettingsPlayerPrefs(state)).also {
      c = it
      it.restore()
      keys = MediaKeys(it).also(MediaKeys::start)
    }
  }

  fun shutdown() { c?.savePositionNow(); keys?.close() }

  /** A track for a file opened from disk (no library entry). */
  fun trackOf(f: File) = Track(
    id = "file_" + f.absolutePath.hashCode().toUInt().toString(16), title = f.nameWithoutExtension,
    artist = "", album = f.parentFile?.name ?: "", durationMs = 0, genre = "", era = "",
    isLocalFile = true, filePath = f.absolutePath,
  )

  /** Registers the screen in the shell slot of the Music tab. */
  fun register(slots: MutableMap<com.example.ui.viewmodel.TarkasNavTab, PanelContent>) {
    slots[com.example.ui.viewmodel.TarkasNavTab.MUSIC] = PanelContent { s -> PlayerScreen(controller(s)) }
  }
}

@Composable
fun PlayerScreen(c: PlayerController) {
  val cs = MaterialTheme.colorScheme
  val ui by c.ui.collectAsState()
  val eng by c.engine.collectAsState()
  var dragging by remember { mutableStateOf<Float?>(null) }
  var showQueue by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { while (true) { delay(1000); c.tickSleep(System.currentTimeMillis()) } }
  val cur = ui.current
  val tint = cur?.let { Color(it.colorHex or 0xFF000000) } ?: cs.primary
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.25f), cs.background)))) {
    Column(Modifier.fillMaxSize().padding(horizontal = Grid.margin, vertical = Grid.u24), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
      Text(cur?.title ?: tr(Res.string.player_screen_nothing_playing), style = GridText.Title, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(cur?.let { listOf(it.artist, it.album).filter(String::isNotEmpty).joinToString(" · ") } ?: tr(Res.string.player_screen_pick_song),
        style = GridText.Body, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      ui.problem?.let { Text(it.toString(), style = GridText.Small, color = cs.primary) }
      eng.error?.let { Text(it, style = GridText.Small, color = cs.primary) }
      Column(Modifier.fillMaxWidth()) {
        Slider(
          value = dragging ?: SeekMath.toFraction(eng.positionMs, eng.durationMs),
          onValueChange = { dragging = it },
          onValueChangeFinished = { dragging?.let(c::seekFraction); dragging = null },
          enabled = eng.durationMs > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(SeekMath.label(eng.positionMs), style = GridText.Label, color = cs.onSurfaceVariant)
          Text(SeekMath.label(eng.durationMs), style = GridText.Label, color = cs.onSurfaceVariant)
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = c::toggleShuffle, Modifier.size(Grid.u48)) {
          Icon(Icons.Filled.Shuffle, tr(Res.string.player_screen_shuffle), tint = if (ui.shuffle) cs.primary else cs.onSurfaceVariant)
        }
        IconButton(onClick = c::previous, Modifier.size(Grid.u48)) { Icon(Icons.Filled.SkipPrevious, tr(Res.string.player_screen_previous), tint = cs.onBackground) }
        IconButton(onClick = c::togglePlayPause, Modifier.size(Grid.u96, Grid.u48), enabled = cur != null) {
          Icon(if (eng.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = cs.primary, modifier = Modifier.size(Grid.icon))
        }
        IconButton(onClick = { c.next() }, Modifier.size(Grid.u48)) { Icon(Icons.Filled.SkipNext, tr(Res.string.player_screen_next), tint = cs.onBackground) }
        IconButton(onClick = c::cycleRepeat, Modifier.size(Grid.u48)) {
          Icon(if (ui.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, tr(Res.string.player_screen_repeat),
            tint = if (ui.repeat == RepeatMode.OFF) cs.onSurfaceVariant else cs.primary)
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
        Text(tr(Res.string.player_screen_volume), style = GridText.Small, color = cs.onSurfaceVariant)
        Slider(ui.volume, c::setVolume, Modifier.weight(1f))
        Text("%d%%".format((ui.volume * 100).toInt()), style = GridText.Label, color = cs.onSurfaceVariant)
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u6)) {
        Text(tr(Res.string.player_screen_sleep_timer), style = GridText.Small, color = cs.onSurfaceVariant)
        val off = ui.sleepEndsAtMs == null && !ui.sleepAtEndOfTrack
        FilterChip(off, c::sleepOff, label = { Text(tr(Res.string.player_screen_off), style = GridText.Small) })
        listOf(15, 30, 60).forEach { m ->
          FilterChip(false, { c.sleepIn(m) }, label = { Text("$m min", style = GridText.Small) })
        }
        ui.sleepEndsAtMs?.let { Text(SeekMath.label(c.sleepRemainingMs() ?: 0), style = GridText.Label, color = cs.primary) }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u6)) {
        Text("Crossfade", style = GridText.Small, color = cs.onSurfaceVariant)
        listOf(0, 3, 6, 12).forEach { s ->
          FilterChip(ui.crossfadeSeconds == s, { c.setCrossfade(s) }, label = { Text(if (s == 0) tr(Res.string.player_screen_off) else "$s s", style = GridText.Small) })
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
        OutlinedButton(onClick = { openFiles(c) }, Modifier.height(Grid.u48)) { Text("Open", style = GridText.Body) }
        OutlinedButton(onClick = { showQueue = !showQueue }, Modifier.height(Grid.u48)) { Text(tr(Res.string.player_screen_queue) + " (${ui.queue.size})", style = GridText.Body) }
      }
      if (showQueue) QueueList(c, ui, Modifier.weight(1f))
    }
  }
}

private fun openFiles(c: PlayerController) {
  val d = FileDialog(null as java.awt.Frame?, "Open", FileDialog.LOAD).apply { isMultipleMode = true; isVisible = true }
  val files = d.files.orEmpty().toList()
  if (files.isEmpty()) return
  val tracks = files.map(PlayerHost::trackOf)
  c.play(tracks.first(), tracks)
}

@Composable
private fun QueueList(c: PlayerController, ui: PlayerUi, modifier: Modifier) {
  val cs = MaterialTheme.colorScheme
  Column(modifier) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(tr(Res.string.queue_header), style = GridText.BodyStrong, color = cs.onBackground)
      OutlinedButton(onClick = c::clearQueue, enabled = ui.queue.isNotEmpty()) { Text(tr(Res.string.queue_clear), style = GridText.Small) }
    }
    LazyColumn(Modifier.fillMaxWidth()) {
      itemsIndexed(ui.queue, key = { i, t -> "$i${t.id}" }) { i, t ->
        Row(Modifier.fillMaxWidth().height(Grid.u48).clickable { c.playQueueItem(i) }, verticalAlignment = Alignment.CenterVertically) {
          Text(t.title, style = GridText.Body, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          IconButton({ c.moveInQueue(i, i - 1) }, Modifier.size(Grid.u48), enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, tr(Res.string.queue_move_up)) }
          IconButton({ c.moveInQueue(i, i + 1) }, Modifier.size(Grid.u48), enabled = i < ui.queue.lastIndex) { Icon(Icons.Filled.ArrowDownward, tr(Res.string.queue_move_down)) }
          IconButton({ c.removeFromQueue(i) }, Modifier.size(Grid.u48)) { Icon(Icons.Filled.Close, tr(Res.string.queue_remove)) }
        }
      }
    }
  }
}
