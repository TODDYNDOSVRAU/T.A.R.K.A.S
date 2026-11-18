package com.example.windows.player

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.lyrics.Lyrics
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.audio.AudioOut
import com.example.windows.res.Res
import com.example.windows.res.audio_action_undo
import com.example.windows.res.audio_adv_crossfade
import com.example.windows.res.lyrics_none
import com.example.windows.res.lyrics_plain_note
import com.example.windows.res.lyrics_title
import com.example.windows.res.pd_copy_info
import com.example.windows.res.pd_crossfade_seconds
import com.example.windows.res.pd_details_artist
import com.example.windows.res.pd_details_duration
import com.example.windows.res.pd_details_file
import com.example.windows.res.pd_details_title
import com.example.windows.res.pd_lyrics_loading
import com.example.windows.res.pd_open_dialog
import com.example.windows.res.pd_open_files
import com.example.windows.res.pd_pane_close
import com.example.windows.res.pd_pane_details
import com.example.windows.res.pd_play_now
import com.example.windows.res.pd_queue_removed
import com.example.windows.res.pd_wave_pending
import com.example.windows.res.player_details_album
import com.example.windows.res.player_menu_play_next
import com.example.windows.res.player_screen_next
import com.example.windows.res.player_screen_nothing_playing
import com.example.windows.res.player_screen_off
import com.example.windows.res.player_screen_pick_song
import com.example.windows.res.player_screen_previous
import com.example.windows.res.player_screen_queue
import com.example.windows.res.player_screen_repeat
import com.example.windows.res.player_screen_shuffle
import com.example.windows.res.player_screen_sleep_timer
import com.example.windows.res.player_sleep_end_of_track
import com.example.windows.res.player_sleep_label_minutes
import com.example.windows.res.player_sleep_pauses_in
import com.example.windows.res.queue_clear
import com.example.windows.res.queue_empty_note
import com.example.windows.res.queue_handle
import com.example.windows.res.queue_header
import com.example.windows.res.queue_remove
import com.example.windows.res.queue_upcoming_header
import com.example.windows.res.tr
import com.example.windows.shell.PanelContent
import com.example.windows.shell.SidePane
import com.example.windows.shell.Panels
import com.example.windows.shell.ShellBackdrop
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.windows.state.TarkasState
import com.example.ui.viewmodel.TarkasNavTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.FileDialog
import java.io.File

/** Settings-table adapter so the player keeps its state (scope "player") between runs. */
class SettingsPlayerPrefs(private val state: TarkasState) : PlayerPrefs {
  override fun get(key: String) = state.settings.get("player", key)
  override fun put(key: String, value: String) = state.settings.put("player", key, value)
}

/**
 * One controller per run, built on the app's DesktopPlayer. Other areas play through [controller]; the shell's
 * transport bar and shortcuts use [hooks]; the shell's side pane draws [SidePane].
 */
object PlayerHost {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  @Volatile private var c: PlayerController? = null
  @Volatile private var h: PlayerHooks? = null
  @Volatile private var a: TrackAssets? = null
  private var keys: MediaKeys? = null

  fun controller(state: TarkasState): PlayerController = c ?: synchronized(this) {
    c ?: build(state.player, SettingsPlayerPrefs(state))
  }

  private fun build(out: AudioOut, prefs: PlayerPrefs): PlayerController {
    val ctl = PlayerController(out, prefs, scope)
    val assets = TrackAssets(scope, WaveformReader(WaveformCache(File(File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "TARKAS"), "waveforms"))))
    c = ctl; a = assets
    h = PlayerHooks(ctl, scope, assets.peaks)
    scope.launch { combine(h!!.state.map { it.track }.distinctUntilChanged(), assets.art) { t, art -> t to art }.collect { (t, art) ->
      ShellBackdrop.set(art, t?.let { Color(it.colorHex or 0xFF000000) }) } }
    ctl.restore()
    keys = MediaKeys(ctl).also(MediaKeys::start)
    return ctl
  }

  /** The calls the transport bar makes. Builds the player on first use. */
  fun hooks(state: TarkasState): PlayerHooks { controller(state); return h!! }

  fun assets(state: TarkasState): TrackAssets { controller(state); return a!! }

  /** Set by the transport bar: opens or closes a pane kind in the shell side pane. */
  @Volatile var paneToggler: ((PlayerPane) -> Unit)? = null

  fun shutdown() { c?.savePositionNow(); keys?.close() }

  /** A track for a file opened from disk (no library entry). */
  fun trackOf(f: File) = Track(
    id = "file_" + f.absolutePath.hashCode().toUInt().toString(16), title = f.nameWithoutExtension,
    artist = "", album = f.parentFile?.name ?: "", durationMs = 0, genre = "", era = "",
    isLocalFile = true, filePath = f.absolutePath,
  )

  /** Registers the now-playing screen in the shell slot of the Player tab. */
  fun register(slots: MutableMap<TarkasNavTab, PanelContent>) {
    slots[TarkasNavTab.MUSIC] = PanelContent { s -> PlayerScreen(hooks(s), assets(s)) }
    Panels.transport = PanelContent { s -> TransportBar(s) }
  }

  /** The content of the shell's side pane for [kind]: queue, lyrics or track details. */
  @Composable
  fun SidePane(state: TarkasState, kind: PlayerPane) {
    val hk = hooks(state)
    PlayerSidePane(hk, assets(state), kind)
  }

  /** The sleep timer button with its popup, for the transport bar. */
  @Composable
  fun SleepTimerButton(state: TarkasState) = SleepTimerButton(hooks(state).controller)

  /** Opens the file dialog and plays what was chosen. */
  fun openFiles(state: TarkasState, title: String) = openFiles(controller(state), title)
}

// Now playing ------------------------------------------------------------------------------------------

/** Window width from which the lyrics sit beside the cover (768 + 192 dp). */
private val WIDE = Grid.u384 * 2 + Grid.u192

@Composable
fun PlayerScreen(hooks: PlayerHooks, assets: TrackAssets) {
  val cs = MaterialTheme.colorScheme
  val t by hooks.state.collectAsState()
  val ui by hooks.controller.ui.collectAsState()
  val lyrics by assets.lyrics.collectAsState()
  LaunchedEffect(t.track?.id) { assets.follow(t.track) }
  val art by assets.art.collectAsState()
  val sidePane by SidePane.current.collectAsState()
  val tint = t.track?.let { Color(it.colorHex or 0xFF000000) } ?: cs.primary
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= WIDE
    val tall = maxHeight >= Grid.u384 + Grid.u192 + Grid.u48
    Row(Modifier.fillMaxSize()) {
      Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = Grid.margin, vertical = Grid.u24), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
          val ctl = hooks.controller
          val openTitle = tr(Res.string.pd_open_dialog)
          IconButton({ openFiles(ctl, openTitle) }, Modifier.size(Grid.u48)) { Icon(Icons.Filled.FolderOpen, tr(Res.string.pd_open_files), tint = cs.onSurfaceVariant) }
          CrossfadeButton(ctl, ui.crossfadeSeconds)
          SleepTimerButton(ctl)
          IconButton({ PlayerHost.paneToggler?.invoke(PlayerPane.LYRICS) }, Modifier.size(Grid.u48)) { Icon(Icons.Filled.Lyrics, tr(Res.string.lyrics_title), tint = if (sidePane?.id == PlayerPane.LYRICS.name) cs.primary else cs.onSurfaceVariant) }
          IconButton({ PlayerHost.paneToggler?.invoke(PlayerPane.DETAILS) }, Modifier.size(Grid.u48)) { Icon(Icons.Filled.Info, tr(Res.string.pd_pane_details), tint = if (sidePane?.id == PlayerPane.DETAILS.name) cs.primary else cs.onSurfaceVariant) }
          IconButton({ PlayerHost.paneToggler?.invoke(PlayerPane.QUEUE) }, Modifier.size(Grid.u48)) { Icon(Icons.Filled.QueueMusic, tr(Res.string.player_screen_queue), tint = if (sidePane?.id == PlayerPane.QUEUE.name) cs.primary else cs.onSurfaceVariant) }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u24)) {
          if (wide) {
            Column(Modifier.width(Grid.u384), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
              Cover(t.track, tint, art, if (tall) Grid.u384 else Grid.u192)
              TrackInfo(t, ui.problem?.toString())
            }
            LyricsView(lyrics, t, hooks, Modifier.weight(1f).fillMaxHeight())
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Grid.u24), verticalAlignment = Alignment.Top) {
              Cover(t.track, tint, art, Grid.u192)
              Column(Modifier.weight(1f)) { TrackInfo(t, ui.problem?.toString()) }
            }
          }
        }
        WaveformSeek(t.peaks, t.fraction, t.durationMs > 0, hooks::seekFraction, Modifier.fillMaxWidth().height(Grid.u96))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(SeekMath.label(t.positionMs), style = GridText.Label, color = cs.onSurfaceVariant)
          Text(SeekMath.label(t.durationMs), style = GridText.Label, color = cs.onSurfaceVariant)
        }
        Controls(hooks, t)
      }
    }
  }
}

@Composable
internal fun Cover(track: Track?, tint: Color, art: androidx.compose.ui.graphics.ImageBitmap?, size: androidx.compose.ui.unit.Dp) {
  if (art != null) {
    androidx.compose.foundation.Image(art, track?.title, Modifier.size(size).clip(Grid.r12), contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
    return
  }
  Box(Modifier.size(size).clip(Grid.r12).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.9f), tint.copy(alpha = 0.35f)))), contentAlignment = Alignment.Center) {
    Icon(Icons.Filled.MusicNote, track?.title, Modifier.size(size / 2), tint = MaterialTheme.colorScheme.onPrimary)
  }
}

@Composable
internal fun TrackInfo(t: TransportState, problem: String?) {
  val cs = MaterialTheme.colorScheme
  val cur = t.track
  Column(verticalArrangement = Arrangement.spacedBy(Grid.u6)) {
    Text(cur?.title ?: tr(Res.string.player_screen_nothing_playing), style = GridText.Title, color = cs.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(cur?.let { listOf(it.artist, it.album).filter(String::isNotEmpty).joinToString(" · ") } ?: tr(Res.string.player_screen_pick_song),
      style = GridText.Body, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    problem?.let { Text(it, style = GridText.Small, color = cs.primary) }
    t.error?.let { Text(it, style = GridText.Small, color = cs.primary) }
  }
}

@Composable
internal fun Controls(hooks: PlayerHooks, t: TransportState) {
  val cs = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
    IconButton(hooks::toggleShuffle, Modifier.size(Grid.u48)) {
      Icon(Icons.Filled.Shuffle, tr(Res.string.player_screen_shuffle), tint = if (t.shuffle) cs.primary else cs.onSurfaceVariant)
    }
    IconButton(hooks::prev, Modifier.size(Grid.u48)) { Icon(Icons.Filled.SkipPrevious, tr(Res.string.player_screen_previous), tint = cs.onBackground) }
    IconButton(hooks::toggle, Modifier.size(Grid.u96, Grid.u48), enabled = t.track != null) {
      Icon(if (t.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = cs.primary, modifier = Modifier.size(Grid.icon))
    }
    IconButton(hooks::next, Modifier.size(Grid.u48)) { Icon(Icons.Filled.SkipNext, tr(Res.string.player_screen_next), tint = cs.onBackground) }
    IconButton(hooks::cycleRepeat, Modifier.size(Grid.u48)) {
      Icon(if (t.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, tr(Res.string.player_screen_repeat),
        tint = if (t.repeat == RepeatMode.OFF) cs.onSurfaceVariant else cs.primary)
    }
  }
}

// Waveform seek ----------------------------------------------------------------------------------------

/**
 * The seek bar drawn as the track's loudness shape, across the whole width. A tap seeks; a drag scrubs and seeks
 * on release. With no shape yet a flat line is drawn, never a made-up one.
 */
@Composable
fun WaveformSeek(peaks: ByteArray?, fraction: Float, enabled: Boolean, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  var scrub by remember { mutableStateOf<Float?>(null) }
  val shown = scrub ?: fraction
  Canvas(
    modifier
      .pointerInput(enabled) {
        if (enabled) detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
      }
      .pointerInput(enabled) {
        if (enabled) detectDragGestures(
          onDragStart = { scrub = (it.x / size.width).coerceIn(0f, 1f) },
          onDrag = { ch, _ -> scrub = (ch.position.x / size.width).coerceIn(0f, 1f) },
          onDragEnd = { scrub?.let(onSeek); scrub = null },
          onDragCancel = { scrub = null },
        )
      }
  ) {
    val n = peaks?.size ?: 0
    val mid = size.height / 2
    if (n == 0) {
      val h = size.height / 24f
      drawRectLine(cs.onSurfaceVariant.copy(alpha = 0.4f), 0f, size.width, mid, h)
      drawRectLine(cs.primary, 0f, size.width * shown, mid, h)
      return@Canvas
    }
    val slot = size.width / n
    val bar = slot * 0.6f
    for (i in 0 until n) {
      val amp = ((peaks!![i].toInt() and 0xFF) / 255f).coerceAtLeast(0.04f)
      val h = amp * size.height
      val x = i * slot + (slot - bar) / 2
      val played = (i + 0.5f) / n <= shown
      drawRect(if (played) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.45f), Offset(x, mid - h / 2), Size(bar, h))
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRectLine(c: Color, x0: Float, x1: Float, y: Float, h: Float) =
  drawRect(c, Offset(x0, y - h / 2), Size((x1 - x0).coerceAtLeast(0f), h))

// Lyrics -----------------------------------------------------------------------------------------------

@Composable
internal fun LyricsView(lyrics: Lyrics?, t: TransportState, hooks: PlayerHooks, modifier: Modifier) {
  val cs = MaterialTheme.colorScheme
  if (lyrics == null) {
    Box(modifier, contentAlignment = Alignment.Center) {
      Text(tr(if (t.track == null) Res.string.pd_lyrics_loading else Res.string.lyrics_none), style = GridText.Body, color = cs.onSurfaceVariant)
    }
    return
  }
  val active = lyrics.activeIndex(t.positionMs)
  val list = rememberLazyListState()
  LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
  Column(modifier) {
    if (!lyrics.synced) Text(tr(Res.string.lyrics_plain_note), style = GridText.Small, color = cs.onSurfaceVariant)
    LazyColumn(Modifier.fillMaxSize(), state = list) {
      itemsIndexed(lyrics.lines) { i, l ->
        val ms = l.timeMs
        val a by androidx.compose.animation.core.animateFloatAsState(if (i == active) 1f else 0.35f, androidx.compose.animation.core.tween(250))
        Text(
          l.text, style = GridText.Title,
          color = cs.onSurface.copy(alpha = a),
          modifier = Modifier.fillMaxWidth().padding(vertical = Grid.u6).then(
            if (ms != null && t.durationMs > 0) Modifier.clickable { hooks.seekBy(ms - t.positionMs) } else Modifier
          ),
        )
      }
    }
  }
}

// Popups -----------------------------------------------------------------------------------------------

/** Sleep timer as a popup anchored to its button. */
@Composable
fun SleepTimerButton(c: PlayerController) {
  val cs = MaterialTheme.colorScheme
  val ui by c.ui.collectAsState()
  var open by remember { mutableStateOf(false) }
  val active = ui.sleepEndsAtMs != null || ui.sleepAtEndOfTrack
  Box {
    IconButton({ open = true }, Modifier.size(Grid.u48)) {
      Icon(Icons.Filled.Bedtime, tr(Res.string.player_screen_sleep_timer), tint = if (active) cs.primary else cs.onSurfaceVariant)
    }
    DropdownMenu(open, { open = false }) {
      if (ui.sleepEndsAtMs != null) {
        DropdownMenuItem({ Text(tr(Res.string.player_sleep_pauses_in, SeekMath.label(c.sleepRemainingMs() ?: 0)), style = GridText.Small) }, {}, enabled = false)
      }
      DropdownMenuItem(
        { Text(tr(Res.string.player_screen_off), style = GridText.Body) }, { c.sleepOff(); open = false },
        leadingIcon = { if (!active) Icon(Icons.Filled.Check, null, tint = cs.primary) },
      )
      listOf(15, 30, 60).forEach { m ->
        DropdownMenuItem({ Text(tr(Res.string.player_sleep_label_minutes, m), style = GridText.Body) }, { c.sleepIn(m); open = false })
      }
      DropdownMenuItem(
        { Text(tr(Res.string.player_sleep_end_of_track), style = GridText.Body) }, { c.sleepAtEndOfTrack(); open = false },
        leadingIcon = { if (ui.sleepAtEndOfTrack) Icon(Icons.Filled.Check, null, tint = cs.primary) },
      )
    }
  }
}

@Composable
private fun CrossfadeButton(c: PlayerController, seconds: Int) {
  val cs = MaterialTheme.colorScheme
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton({ open = true }, Modifier.size(Grid.u48)) {
      Icon(Icons.Filled.Tune, tr(Res.string.audio_adv_crossfade), tint = if (seconds > 0) cs.primary else cs.onSurfaceVariant)
    }
    DropdownMenu(open, { open = false }) {
      listOf(0, 3, 6, 12).forEach { s ->
        DropdownMenuItem(
          { Text(if (s == 0) tr(Res.string.player_screen_off) else tr(Res.string.pd_crossfade_seconds, s), style = GridText.Body) },
          { c.setCrossfade(s); open = false },
          leadingIcon = { if (s == seconds) Icon(Icons.Filled.Check, null, tint = cs.primary) },
        )
      }
    }
  }
}

internal fun openFiles(c: PlayerController, title: String) {
  val d = FileDialog(null as java.awt.Frame?, title, FileDialog.LOAD).apply { isMultipleMode = true; isVisible = true }
  val files = d.files.orEmpty().toList()
  if (files.isEmpty()) return
  val tracks = files.map(PlayerHost::trackOf)
  c.play(tracks.first(), tracks)
}

// Side pane --------------------------------------------------------------------------------------------

/** Queue, lyrics or details, 384 dp wide in the shell's side pane (or beside the player until the shell draws it). */
@Composable
fun PlayerSidePane(hooks: PlayerHooks, assets: TrackAssets, kind: PlayerPane) {
  val cs = MaterialTheme.colorScheme
  Column(Modifier.fillMaxSize().padding(Grid.inset)) {
    if (kind == PlayerPane.QUEUE) {
      val q by hooks.controller.ui.collectAsState()
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton({ hooks.clearQueue() }, enabled = q.queue.isNotEmpty()) { Text(tr(Res.string.queue_clear), style = GridText.Small) }
      }
    }
    when (kind) {
      PlayerPane.QUEUE -> QueuePane(hooks)
      PlayerPane.LYRICS -> {
        val l by assets.lyrics.collectAsState()
        val t by hooks.state.collectAsState()
        LyricsView(l, t, hooks, Modifier.fillMaxSize())
      }
      PlayerPane.DETAILS -> DetailsPane(hooks)
    }
  }
}

@Composable
internal fun QueuePane(hooks: PlayerHooks) {
  val cs = MaterialTheme.colorScheme
  val c = hooks.controller
  val ui by c.ui.collectAsState()
  val undo by hooks.undo.collectAsState()
  var selected by remember { mutableStateOf<Int?>(null) }
  val focus = remember { FocusRequester() }
  val rowPx = with(LocalDensity.current) { Grid.u48.toPx() }
  val playNext = tr(Res.string.player_menu_play_next)
  val playNow = tr(Res.string.pd_play_now)
  val remove = tr(Res.string.queue_remove)
  Column(
    Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent { e ->
      if (e.type != KeyEventType.KeyDown) false
      else when {
        e.key == Key.Delete -> selected?.let { hooks.removeFromQueue(it); selected = null; true } ?: false
        e.key == Key.Z && e.isCtrlPressed -> hooks.undoQueue()
        else -> false
      }
    }
  ) {
    undo?.let { u ->
      Row(Modifier.fillMaxWidth().height(Grid.u48).clip(Grid.r6).background(cs.surfaceContainerHigh).padding(horizontal = Grid.inset), verticalAlignment = Alignment.CenterVertically) {
        Text(
          tr(Res.string.pd_queue_removed, if (u.removed.size == 1) u.removed.first().title else u.removed.size.toString()),
          style = GridText.Small, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        TextButton({ hooks.undoQueue() }) { Text(tr(Res.string.audio_action_undo), style = GridText.Small, color = cs.primary) }
      }
    }
    if (ui.queue.isEmpty()) {
      Text(tr(Res.string.queue_empty_note), style = GridText.Small, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = Grid.inset))
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = true)) {
      itemsIndexed(ui.queue, key = { i, t -> "$i${t.id}" }) { i, t ->
        var dy by remember { mutableStateOf(0f) }
        ContextMenuArea(items = {
          listOf(
            ContextMenuItem(playNow) { c.playQueueItem(i) },
            ContextMenuItem(playNext) { c.queueToFront(i) },
            ContextMenuItem(remove) { hooks.removeFromQueue(i) },
          )
        }) {
          Row(
            Modifier.fillMaxWidth().height(Grid.u48).graphicsLayer { translationY = dy }
              .background(if (selected == i) cs.surfaceContainerHigh else Color.Transparent)
              .clickable { selected = i; focus.requestFocus() },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Filled.DragHandle, tr(Res.string.queue_handle), tint = cs.onSurfaceVariant,
              modifier = Modifier.size(Grid.u48).padding(Grid.u12).pointerInput(i, ui.queue.size) {
                detectDragGestures(
                  onDragStart = { selected = i },
                  onDrag = { ch, d -> ch.consume(); dy += d.y },
                  onDragEnd = {
                    val to = DragMath.target(i, dy, rowPx, ui.queue.size)
                    dy = 0f
                    if (to != i) { c.moveInQueue(i, to); selected = to }
                  },
                  onDragCancel = { dy = 0f },
                )
              },
            )
            Column(Modifier.weight(1f).clickable { c.playQueueItem(i) }) {
              Text(t.title, style = GridText.Body, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
              if (t.artist.isNotEmpty()) Text(t.artist, style = GridText.Label, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ hooks.removeFromQueue(i) }, Modifier.size(Grid.u48)) { Icon(Icons.Filled.Close, remove, tint = cs.onSurfaceVariant) }
          }
        }
      }
      val next = c.upcoming(20)
      if (next.isNotEmpty()) {
        item { Text(tr(Res.string.queue_upcoming_header), style = GridText.Small, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = Grid.u12)) }
        itemsIndexed(next) { _, t ->
          Text(t.title, style = GridText.Body, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().height(Grid.u48).padding(top = Grid.u12))
        }
      }
    }
  }
}

@Composable
private fun DetailsPane(hooks: PlayerHooks) {
  val cs = MaterialTheme.colorScheme
  val t by hooks.state.collectAsState()
  val clip = LocalClipboardManager.current
  val cur = t.track
  if (cur == null) {
    Text(tr(Res.string.player_screen_nothing_playing), style = GridText.Body, color = cs.onSurfaceVariant)
    return
  }
  val rows = listOf(
    tr(Res.string.pd_details_title) to cur.title,
    tr(Res.string.pd_details_artist) to cur.artist,
    tr(Res.string.player_details_album) to cur.album,
    tr(Res.string.pd_details_duration) to SeekMath.label(if (t.durationMs > 0) t.durationMs else cur.durationMs),
    tr(Res.string.pd_details_file) to (cur.filePath ?: ""),
  ).filter { it.second.isNotEmpty() }
  val copy = tr(Res.string.pd_copy_info)
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
    rows.forEach { (k, v) ->
      Column {
        Text(k, style = GridText.Label, color = cs.onSurfaceVariant)
        Text(v, style = GridText.Body, color = cs.onSurface)
      }
    }
    TextButton({ clip.setText(AnnotatedString(rows.joinToString("\n") { "${it.first}: ${it.second}" })) }) { Text(copy, style = GridText.Small, color = cs.primary) }
  }
}
