package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.library.AutoList
import com.example.data.library.AutoLists
import com.example.data.model.Track
import com.example.data.playback.LibrarySearch
import com.example.ui.components.GroupHeader
import com.example.ui.components.SearchField
import com.example.ui.components.SectionHeader
import com.example.ui.components.TextAction
import com.example.ui.components.highlightClickable
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** What an online list is doing: shown as a row of covers, or why it is not. */
private sealed interface Online {
  data object Loading : Online
  data object Offline : Online
  data object NoKey : Online
  data class Done(val tracks: List<Track>) : Online
}

private suspend fun fetchOnline(block: suspend () -> List<Track>): Online = try {
  Online.Done(block())
} catch (e: CancellationException) {
  throw e
} catch (_: JamendoNotConfigured) {
  Online.NoKey
} catch (_: Exception) {
  Online.Offline
}

/** The Início tab: search over the library and the online catalogue, what to resume, recent plays, and what is trending. */
@Composable
fun HomeScreen(lib: BibliotecaState, modifier: Modifier = Modifier) {
  var query by remember { mutableStateOf("") }
  var debounced by remember { mutableStateOf("") }
  LaunchedEffect(query) { delay(350); debounced = query.trim() }

  val library by lib.library.collectAsState()
  val stats by lib.store.stats.collectAsState()
  val favorites by lib.store.favorites.collectAsState()
  val ui by lib.player.ui.collectAsState()
  val engine by lib.player.engine.collectAsState()
  val recent = remember(library, stats) { AutoLists.build(AutoList.RECENTLY_PLAYED, library, stats, 5) }

  var audius by remember { mutableStateOf<Online>(Online.Loading) }
  var jamendo by remember { mutableStateOf<Online>(Online.Loading) }
  var reload by remember { mutableStateOf(0) }
  LaunchedEffect(reload) {
    audius = Online.Loading; jamendo = Online.Loading
    audius = fetchOnline { AudiusCatalog.trending() }
    jamendo = fetchOnline { JamendoCatalog.popular() }
  }

  var found by remember { mutableStateOf<Online?>(null) }
  var foundJamendo by remember { mutableStateOf<Online?>(null) }
  LaunchedEffect(debounced) {
    if (debounced.isBlank()) { found = null; foundJamendo = null; return@LaunchedEffect }
    found = Online.Loading; foundJamendo = Online.Loading
    found = fetchOnline { AudiusCatalog.search(debounced) }
    foundJamendo = fetchOnline { JamendoCatalog.search(debounced) }
  }

  Column(modifier.fillMaxSize()) {
    SectionHeader(tr(Res.string.home_mine_header))
    SearchField(query, { query = it }, tr(Res.string.home_search_placeholder), Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6))
    LazyColumn(Modifier.fillMaxSize()) {
      if (debounced.isNotBlank()) {
        val mine = LibrarySearch.filter(library, debounced)
        item { GroupHeader(tr(Res.string.home_search_library_header)) }
        if (mine.isEmpty()) item { Note(tr(Res.string.home_search_library_empty)) }
        else items(mine.take(20), key = { "m:" + it.id }) { t ->
          TrackRow(t, { lib.play(t, mine) }, playing = t.id == ui.current?.id, favorite = t.id in favorites)
        }
        item { GroupHeader("Audius") }
        onlineItems(lib, found)
        item { GroupHeader("Jamendo") }
        onlineItems(lib, foundJamendo)
      } else {
        val current = ui.current
        if (current != null) {
          item { GroupHeader(tr(Res.string.home_resume_header)) }
          item(key = "resume") {
            val position = formatDuration(engine.positionMs)
            val total = formatDuration(current.durationMs)
            TrackRow(
              current, { lib.player.togglePlayPause() }, playing = true, favorite = current.id in favorites,
              trailing = {
                Text(
                  if (total.isNotEmpty()) tr(Res.string.home_resume_position_of, position, total) else tr(Res.string.home_resume_position, position),
                  style = GridText.Label, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            )
          }
        }
        if (recent.isNotEmpty()) {
          item { GroupHeader(tr(Res.string.home_recent_header)) }
          items(recent, key = { "r:" + it.id }) { t ->
            TrackRow(t, { lib.play(t, recent) }, playing = t.id == ui.current?.id, favorite = t.id in favorites)
          }
        }
        if (library.isEmpty()) item {
          EmptyState(tr(Res.string.home_mine_empty_title), tr(Res.string.home_mine_empty_message)) {
            TextAction(tr(Res.string.home_mine_empty_action), { chooseFolder(lib) })
          }
        }
        item { GroupHeader(tr(Res.string.home_trending_title)) }
        item { Carousel(lib, audius) { reload++ } }
        item { GroupHeader(tr(Res.string.home_jamendo_popular_title)) }
        item { Carousel(lib, jamendo) { reload++ } }
      }
    }
  }
}

private fun androidx.compose.foundation.lazy.LazyListScope.onlineItems(lib: BibliotecaState, state: Online?) {
  when (state) {
    null -> {}
    Online.Loading -> item { Note("…") }
    Online.Offline -> item { Note("") }
    Online.NoKey -> item { Note("") }
    is Online.Done -> items(state.tracks.take(10), key = { "o:" + it.id }) { t ->
      TrackRow(t, { lib.play(t, state.tracks) })
    }
  }
}

@Composable
private fun Note(text: String) {
  Text(text, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u12))
}

/** A row of covers with the title under each; a state line when the list is not there. */
@Composable
private fun Carousel(lib: BibliotecaState, state: Online, onRetry: () -> Unit) {
  var slow by remember(state) { mutableStateOf(false) }
  if (state == Online.Loading) LaunchedEffect(state) { delay(1500); slow = true }
  when (state) {
    Online.Loading -> Note(if (slow) tr(Res.string.home_loading) else "")
    Online.Offline -> Column {
      Note(tr(Res.string.home_offline_catalog_message))
      TextAction(tr(Res.string.home_retry), onRetry, Modifier.padding(horizontal = Grid.u12))
    }
    Online.NoKey -> Note(tr(Res.string.home_jamendo_missing_key))
    is Online.Done ->
      if (state.tracks.isEmpty()) Note(tr(Res.string.home_catalog_empty))
      else LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Grid.margin),
        horizontalArrangement = Arrangement.spacedBy(Grid.u12)
      ) {
        items(state.tracks, key = { it.id }) { t ->
          Column(Modifier.width(Grid.u96).highlightClickable(onClick = { lib.play(t, state.tracks) })) {
            TrackArtwork(t, Grid.u96)
            Text(t.title, style = GridText.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(t.artist, style = GridText.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
  }
}
