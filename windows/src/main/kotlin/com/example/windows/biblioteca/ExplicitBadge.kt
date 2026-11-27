package com.example.windows.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.model.Track
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a song's own file tag marks it explicit (D9). Read lazily and cached by path, so a list asks only for the
 * rows it shows and a row never reads the file twice. A song with no such tag (or an online one, with no file) is
 * false: the "E" is never guessed.
 */
object ExplicitTags {
  private val cache = ConcurrentHashMap<String, Boolean>()

  /** The cached answer, or null when the file has not been read yet. */
  fun cached(path: String?): Boolean? = if (path == null) false else cache[path]

  /** Reads the tag (off the UI thread) the first time and remembers it. */
  suspend fun resolve(path: String?): Boolean {
    if (path == null) return false
    cache[path]?.let { return it }
    val value = withContext(Dispatchers.IO) { TagReader.isExplicit(File(path)) }
    cache[path] = value
    return value
  }

  /** Forgets everything (a rescan may have retagged files). */
  fun clear() = cache.clear()
}

/** The explicit state of [track]: false until its tag has been read, then what the file says. */
@Composable
fun rememberExplicit(track: Track): State<Boolean> {
  val path = track.filePath
  return produceState(initialValue = ExplicitTags.cached(path) ?: false, key1 = path) {
    value = ExplicitTags.resolve(path)
  }
}

/** The "E" mark: an 18 dp rounded square on the grid, drawn only for a song whose tag says explicit. */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier
      .size(Grid.u24 * 0.75f)
      .clip(RoundedCornerShape(Grid.u6))
      .background(colors.onSurfaceVariant)
      .semantics { contentDescription = "Conteúdo explícito" },
    contentAlignment = Alignment.Center
  ) {
    Text("E", style = GridText.Label, color = colors.surface, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
  }
}

/** The badge when [track]'s tag says explicit; nothing otherwise. */
@Composable
fun ExplicitMark(track: Track, modifier: Modifier = Modifier) {
  val explicit by rememberExplicit(track)
  if (explicit) ExplicitBadge(modifier)
}
