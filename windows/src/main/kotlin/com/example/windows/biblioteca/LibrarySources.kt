package com.example.windows.biblioteca

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.library.AlbumGroup
import com.example.data.library.AutoList
import com.example.data.library.LibraryGroup
import com.example.data.model.Playlist
import com.example.ui.components.highlightClickable
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.*

enum class GroupKind { ALBUMS, ARTISTS, GENRES }

/** What the table shows: the library, a smaller set of it, the queue, a playlist, an automatic list, or the folders manager. */
sealed interface Source {
  data object All : Source
  data object Favorites : Source
  data object Queue : Source
  data class Group(val kind: GroupKind, val key: String) : Source
  data class PlaylistOf(val id: String) : Source
  data class Auto(val list: AutoList) : Source
  data object Folders : Source

  /** Sources whose order means something (queue, playlist, automatic ranking): the table keeps it until a header is clicked. */
  val natural: Boolean get() = this is Queue || this is PlaylistOf || this is Auto
}

@Composable
fun autoName(l: AutoList) = when (l) {
  AutoList.RECENTLY_ADDED -> tr(Res.string.library_auto_recently_added)
  AutoList.RECENTLY_PLAYED -> tr(Res.string.library_auto_recently_played)
  AutoList.MOST_PLAYED -> tr(Res.string.library_auto_most_played)
}

@Composable
fun autoEmptyTitle(l: AutoList) = when (l) {
  AutoList.RECENTLY_ADDED -> tr(Res.string.library_auto_empty_title_added)
  AutoList.RECENTLY_PLAYED -> tr(Res.string.library_auto_empty_title_played)
  AutoList.MOST_PLAYED -> tr(Res.string.library_auto_empty_title_most)
}

@Composable
fun autoEmptyMessage(l: AutoList) = when (l) {
  AutoList.RECENTLY_ADDED -> tr(Res.string.library_auto_empty_message_added)
  AutoList.RECENTLY_PLAYED -> tr(Res.string.library_auto_empty_message_played)
  AutoList.MOST_PLAYED -> tr(Res.string.library_auto_empty_message_most)
}

/** Everything the sources pane lists, already counted. */
class SourceData(
  val songs: Int,
  val favorites: Int,
  val queue: Int,
  val playlists: List<Playlist>,
  val playlistCounts: Map<String, Int>,
  val autoCounts: Map<AutoList, Int>,
  val albums: List<LibraryGroup>,
  val artists: List<LibraryGroup>,
  val genres: List<LibraryGroup>
)

/** The left pane: library sets, playlists (drop targets), automatic lists, and the groups by album, artist and genre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourcesPane(
  data: SourceData,
  selected: Source,
  onSelect: (Source) -> Unit,
  expanded: Set<GroupKind>,
  onExpand: (GroupKind) -> Unit,
  hub: DragHub,
  onNewPlaylist: () -> Unit,
  onRename: (Playlist) -> Unit,
  onDelete: (Playlist) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  LazyColumn(modifier.fillMaxHeight()) {
    item(key = "all") { SourceRow(tr(Res.string.library_section_songs), data.songs, selected == Source.All, { onSelect(Source.All) }) }
    item(key = "fav") { SourceRow(tr(Res.string.library_section_favorites), data.favorites, selected == Source.Favorites, { onSelect(Source.Favorites) }) }
    item(key = "queue") {
      SourceRow(
        tr(Res.string.library_section_queue), data.queue, selected == Source.Queue, { onSelect(Source.Queue) },
        Modifier.dropTarget(hub, DropTargets.QUEUE)
      )
    }
    item(key = "h-playlists") {
      Row(Modifier.fillMaxWidth().height(Grid.u24).padding(start = Grid.u12), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(Res.string.library_section_playlists), style = GridText.Label, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Box(Modifier.width(Grid.u24).fillMaxHeight().highlightClickable(onClick = onNewPlaylist), contentAlignment = Alignment.Center) {
          Icon(Icons.Filled.Add, tr(Res.string.library_new_playlist), Modifier.width(Grid.u12).height(Grid.u12), tint = colors.onSurfaceVariant)
        }
      }
    }
    items(data.playlists, key = { "p:" + it.id }) { p ->
      ContextMenuArea(items = {
        listOf(
          ContextMenuItem(trNow(Res.string.library_rename_desc)) { onRename(p) },
          ContextMenuItem(trNow(Res.string.library_delete)) { onDelete(p) }
        )
      }) {
        SourceRow(
          p.name, data.playlistCounts[p.id] ?: 0, selected == Source.PlaylistOf(p.id), { onSelect(Source.PlaylistOf(p.id)) },
          Modifier.dropTarget(hub, DropTargets.playlist(p.id)), indent = true
        )
      }
    }
    item(key = "h-auto") { Header(tr(Res.string.library_section_auto)) }
    items(AutoList.entries.toList(), key = { "a:" + it.name }) { l ->
      SourceRow(autoName(l), data.autoCounts[l] ?: 0, selected == Source.Auto(l), { onSelect(Source.Auto(l)) }, indent = true)
    }
    for (kind in GroupKind.entries) {
      val groups = when (kind) { GroupKind.ALBUMS -> data.albums; GroupKind.ARTISTS -> data.artists; GroupKind.GENRES -> data.genres }
      val open = kind in expanded
      item(key = "h-" + kind.name) {
        Row(
          Modifier.fillMaxWidth().height(Grid.u24).then(if (groups.isEmpty()) Modifier else Modifier.highlightClickable(onClick = { onExpand(kind) })).padding(start = Grid.u12),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            when (kind) {
              GroupKind.ALBUMS -> tr(Res.string.library_section_albums)
              GroupKind.ARTISTS -> tr(Res.string.library_section_artists)
              GroupKind.GENRES -> tr(Res.string.library_section_genres)
            },
            style = GridText.Label, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f)
          )
          Text(groups.size.toString(), style = GridText.Label, color = colors.onSurfaceVariant)
          if (groups.isEmpty()) Spacer(Modifier.width(Grid.u24)) else Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
            Modifier.padding(horizontal = Grid.u6).width(Grid.u12).height(Grid.u12), tint = colors.onSurfaceVariant
          )
        }
      }
      if (open) items(groups, key = { kind.name + ":" + it.key }) { g ->
        SourceRow(g.name, g.tracks.size, selected == Source.Group(kind, g.key), { onSelect(Source.Group(kind, g.key)) }, indent = true)
      }
    }
    item(key = "folders") {
      SourceRow(tr(Res.string.library_folders_title), null, selected == Source.Folders, { onSelect(Source.Folders) })
    }
  }
}

@Composable
private fun Header(text: String) {
  Box(Modifier.fillMaxWidth().height(Grid.u24).padding(start = Grid.u12), contentAlignment = Alignment.CenterStart) {
    Text(text, style = GridText.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** WCAG contrast ratio of two opaque colours. */
internal fun contrastRatio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
  fun lin(v: Float): Double { val d = v.toDouble(); return if (d <= 0.04045) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4) }
  fun l(c: androidx.compose.ui.graphics.Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
  val x = l(a); val y = l(b)
  return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
}

/** [fg] when it reads at 4.5:1 on [bg]; otherwise [fg] pulled toward [ink] in small steps until it does. */
internal fun legibleOn(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
  var c = fg
  var t = 0f
  while (contrastRatio(c, bg) < 4.5 && t < 1f) { t += 0.1f; c = androidx.compose.ui.graphics.lerp(fg, ink, t) }
  return c
}

@Composable
private fun SourceRow(
  label: String, count: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, indent: Boolean = false
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier.fillMaxWidth().height(if (indent) Grid.u24 + Grid.u12 else 42.dp).clip(com.example.ui.theme.SquircleShape(Grid.u12))
      .background(if (selected) colors.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
      .pointerHoverIcon(PointerIcon.Hand)
      .highlightClickable(onClick = onClick)
      .padding(start = if (indent) Grid.u24 else Grid.u12, end = Grid.u12),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      label, style = GridText.Small.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null), color = if (selected) legibleOn(colors.primary, colors.primary.copy(alpha = 0.16f).compositeOver(colors.surfaceContainer), colors.onSurface) else colors.onSurface,
      maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
    )
    if (count != null) Text(count.toString(), style = GridText.Label, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = Grid.u6))
  }
}

/** The AlbumGroup artist line shown with an album in a title, when the album has one artist. */
fun LibraryGroup.byline(): String? = (this as? AlbumGroup)?.artist
