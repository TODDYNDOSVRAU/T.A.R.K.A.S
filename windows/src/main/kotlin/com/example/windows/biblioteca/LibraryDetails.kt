package com.example.windows.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.Track
import com.example.data.playback.TrackStats
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import java.text.DateFormat
import java.util.Date

/** The right pane: the facts about the selected track, or the count and total length of a multiple selection. */
@Composable
fun DetailsPane(selected: List<Track>, stats: Map<String, TrackStats>, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(Grid.u12)) {
    when {
      selected.isEmpty() -> Text(
        lt("Select a track to see its details.", "Selecione uma faixa para ver os detalhes."),
        style = GridText.Small, color = colors.onSurfaceVariant
      )
      selected.size > 1 -> {
        Text(lt("${selected.size} tracks", "${selected.size} faixas"), style = GridText.BodyStrong, color = colors.onSurface)
        Fact(lt("Length", "Duração"), formatDuration(selected.sumOf { it.durationMs }).ifEmpty { "-" })
        Fact(lt("Plays", "Execuções"), selected.sumOf { stats[it.id]?.plays ?: 0 }.toString())
      }
      else -> {
        val t = selected.single()
        val s = stats[t.id]
        TrackArtwork(t, Grid.u96)
        Text(
          t.title, style = GridText.BodyStrong, color = colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = Grid.u12)
        )
        Text(t.artist, style = GridText.Small, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Fact(lt("Album", "Álbum"), t.album)
        Fact(lt("Genre", "Gênero"), t.genre)
        Fact(lt("Year", "Ano"), t.era)
        if (t.trackNumber > 0) Fact(lt("Track", "Faixa"), if (t.discNumber > 1) "${t.discNumber}-${t.trackNumber}" else t.trackNumber.toString())
        Fact(lt("Length", "Duração"), formatDuration(t.durationMs))
        Fact(lt("Plays", "Execuções"), (s?.plays ?: 0).toString())
        if ((s?.lastPlayedMs ?: 0) > 0) Fact(lt("Last played", "Última vez"), DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s!!.lastPlayedMs)))
        if (t.dateAddedSec > 0) Fact(lt("Added", "Adicionada"), DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.dateAddedSec * 1000L)))
        Fact(lt("Source", "Origem"), t.onlineSource ?: lt("This computer", "Este computador"))
        t.filePath?.takeIf { t.isLocalFile }?.let { Fact(lt("File", "Arquivo"), it) }
      }
    }
  }
}

@Composable
private fun Fact(label: String, value: String) {
  if (value.isBlank()) return
  val colors = MaterialTheme.colorScheme
  Text(label, style = GridText.Label, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Grid.u12))
  Text(value, style = GridText.Small, color = colors.onSurface, modifier = Modifier.fillMaxWidth())
}
