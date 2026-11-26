package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.components.PrimaryButton
import com.example.ui.components.TonalButton
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.*

/** "1 h 05 min" / "42 min" for a total of [ms]; pure so the tests cover it. */
fun totalDuration(ms: Long): String {
  val min = (ms / 60_000).toInt()
  return if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)} min" else "$min min"
}

/** The covers of the first four songs with distinct art, for the 2x2 mosaic (fewer when the list is short). */
fun mosaicTracks(tracks: List<Track>): List<Track> = tracks.distinctBy { it.albumId ?: it.artworkUrl ?: it.album }.take(4)

/** The top of a playlist page: 2x2 mosaic cover, name, count and length, and the three actions. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlaylistHeader(name: String, tracks: List<Track>, lib: BibliotecaState, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
  // A master pane under 420 dp gets a 96 dp mosaic so the three actions keep their full labels.
  val tile = if (maxWidth < 420.dp) Grid.u24 * 2 else Grid.u48 + Grid.u24
  Row(Modifier.fillMaxWidth().padding(Grid.u12), verticalAlignment = Alignment.CenterVertically) {
    val tiles = mosaicTracks(tracks)
    Box(Modifier.size(tile * 2).clip(RoundedCornerShape(Grid.u12))) {
      Column {
        for (r in 0..1) Row {
          for (c in 0..1) Box(Modifier.size(tile)) { tiles.getOrNull(r * 2 + c)?.let { TrackArtwork(it, tile) } }
        }
      }
    }
    Column(Modifier.weight(1f).padding(start = Grid.u12)) {
      Text(name, style = GridText.Title, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        trPlural(Res.plurals.library_track_count, tracks.size, tracks.size) + " · " + totalDuration(tracks.sumOf { it.durationMs }),
        style = GridText.Small, color = colors.onSurfaceVariant
      )
      androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(top = Grid.u12), horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalArrangement = Arrangement.spacedBy(Grid.u12)
      ) {
        PrimaryButton(lt("Play all", "Tocar tudo"), { tracks.firstOrNull()?.let { lib.play(it, tracks) } })
        TonalButton(lt("Add to queue", "Adicionar à fila"), { tracks.forEach { lib.addToQueue(it) } })
        TonalButton(tr(Res.string.library_shuffle), { tracks.shuffled().let { s -> s.firstOrNull()?.let { lib.play(it, s) } } })
      }
    }
  }
  }
}
