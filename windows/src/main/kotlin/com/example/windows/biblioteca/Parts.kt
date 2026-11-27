package com.example.windows.biblioteca

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.data.model.Track
import com.example.ui.components.highlightClickable
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/**
 * Loads the cover of a track. [Track.artworkUrl] is `tag:<file>` (embedded picture), `file:<image>` (a cover file in the
 * folder) or an http(s) address (online catalogue). Decoded pictures are kept in a small LRU cache, so scrolling a list
 * does not decode again. Returns null when the track has no picture or it cannot be read.
 */
object CoverLoader {
  private const val MAX = 128
  private val cache: MutableMap<String, ImageBitmap> =
    Collections.synchronizedMap(object : LinkedHashMap<String, ImageBitmap>(MAX, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > MAX
    })
  private val failed: MutableSet<String> = Collections.synchronizedSet(HashSet())

  fun cached(url: String): ImageBitmap? = cache[url]

  suspend fun load(url: String): ImageBitmap? {
    cache[url]?.let { return it }
    if (url in failed) return null
    val bytes = withContext(Dispatchers.IO) {
      runCatching {
        when {
          url.startsWith("tag:") -> TagReader.cover(File(url.removePrefix("tag:")))
          url.startsWith("file:") -> File(url.removePrefix("file:")).readBytes()
          url.startsWith("http") -> CatalogHttp.bytes(url)
          else -> null
        }
      }.getOrNull()
    }
    val image = bytes?.let {
      withContext(Dispatchers.Default) {
        runCatching { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull()
      }
    }
    if (image == null) failed += url else cache[url] = image
    return image
  }
}

/** True where a cover that arrives late fades in (200 ms) instead of popping in. Off by default and under reduced motion. */
internal val LocalCoverFade = staticCompositionLocalOf { false }

/** How long a cover takes to fade in once it has loaded. */
internal const val COVER_FADE_MS = 200

/** A square cover with rounded corners, or the track's colour as a gradient with a note when there is none. */
@Composable
fun TrackArtwork(track: Track, size: Dp, modifier: Modifier = Modifier) {
  val url = track.artworkUrl
  var image by remember(url) { mutableStateOf(url?.let { CoverLoader.cached(it) }) }
  LaunchedEffect(url) { if (url != null && image == null) image = CoverLoader.load(url) }
  // A cover already in the cache is shown at once; only one that arrives while the card is on screen fades in.
  val fade = LocalCoverFade.current
  val alpha = remember(url, fade) { Animatable(if (fade && url != null && image == null) 0f else 1f) }
  LaunchedEffect(image) { if (image != null && alpha.value < 1f) alpha.animateTo(1f, tween(COVER_FADE_MS)) }
  val shape = Grid.r6
  val base = Color(track.colorHex.toInt())
  Box(
    modifier
      .size(size)
      .clip(shape)
      .background(Brush.linearGradient(listOf(base.copy(alpha = 0.85f), base.copy(alpha = 0.35f)))),
    contentAlignment = Alignment.Center
  ) {
    val bmp = image
    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }, contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
    else Icon(Icons.Filled.MusicNote, null, Modifier.size(Grid.icon), tint = Color.White.copy(alpha = 0.9f))
  }
}

/** The two lines under a title in a row: artist and album, or the artist alone. */
fun Track.subtitle(): String = listOf(artistLabel(), album).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * [artist] as shown to the person. The scanner stores [LibraryScanner.UNKNOWN_ARTIST] (an English key that grouping and
 * sorting rely on) for a file without an artist tag; the screens translate it here, at display time only.
 */
fun displayArtist(artist: String): String =
  if (artist == LibraryScanner.UNKNOWN_ARTIST) lt("Unknown artist", "Artista desconhecido") else artist

/** The artist of this track as shown to the person (see [displayArtist]). */
fun Track.artistLabel(): String = displayArtist(artist)

fun formatDuration(ms: Long): String {
  if (ms <= 0) return ""
  val s = ms / 1000
  return "%d:%02d".format(s / 60, s % 60)
}

/** One song in a list: cover 48 dp, title, artist and album, length, favourite mark and the menu. */
@Composable
fun TrackRow(
  track: Track,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  playing: Boolean = false,
  favorite: Boolean = false,
  trailing: @Composable (() -> Unit)? = null,
  menu: @Composable (dismiss: () -> Unit) -> Unit = {}
) {
  var open by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  Row(
    modifier
      .fillMaxWidth()
      .highlightClickable(onClick = onClick)
      .padding(horizontal = Grid.margin, vertical = Grid.u6),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TrackArtwork(track, Grid.u48)
    Column(Modifier.weight(1f).padding(horizontal = Grid.inset)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          track.title, style = GridText.Body, maxLines = 1, overflow = TextOverflow.Ellipsis,
          color = if (playing) colors.primary else colors.onSurface,
          modifier = Modifier.weight(1f, fill = false)
        )
        ExplicitMark(track, Modifier.padding(start = Grid.u6))
      }
      Text(
        track.subtitle(), style = GridText.Small, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = colors.onSurfaceVariant
      )
    }
    if (favorite) Icon(Icons.Filled.Favorite, null, Modifier.size(Grid.icon).padding(end = Grid.u6), tint = colors.primary)
    val length = formatDuration(track.durationMs)
    if (length.isNotEmpty()) Text(length, style = GridText.Label, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = Grid.u6))
    trailing?.invoke()
    Box {
      Box(
        Modifier.size(Grid.u48).highlightClickable(onClick = { open = true }),
        contentAlignment = Alignment.Center
      ) { Icon(Icons.Filled.MoreVert, null, Modifier.size(Grid.icon), tint = colors.onSurfaceVariant) }
      DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
    }
  }
}

/** One entry of a track's menu. */
@Composable
fun MenuEntry(label: String, onClick: () -> Unit, dismiss: () -> Unit) {
  DropdownMenuItem(
    text = { Text(label, style = GridText.Body) },
    onClick = { dismiss(); onClick() }
  )
}

/** A title and a sentence where a list would be, and an optional action under them. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, action: @Composable (() -> Unit)? = null) {
  Column(
    modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = Grid.u48),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Grid.u12, Alignment.CenterVertically)
  ) {
    icon?.let { Icon(it, null, Modifier.size(Grid.u48), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(title, style = GridText.BodyStrong, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    Text(message, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    action?.invoke()
  }
}

/** The letters of the alphabet at the list's edge; tapping one jumps to the first row that starts with it. */
@Composable
fun LetterStrip(letters: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.width(Grid.u24), horizontalAlignment = Alignment.CenterHorizontally) {
    for (c in letters) {
      Text(
        c, style = GridText.Label, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.highlightClickable(onClick = { onPick(c) })
      )
    }
  }
}

/** The first letter a song is filed under in the letter strip: A-Z, or # for the rest. */
fun letterOf(title: String): Char {
  val c = java.text.Normalizer.normalize(title.trim().take(1), java.text.Normalizer.Form.NFD).firstOrNull()?.uppercaseChar()
  return if (c != null && c in 'A'..'Z') c else '#'
}
