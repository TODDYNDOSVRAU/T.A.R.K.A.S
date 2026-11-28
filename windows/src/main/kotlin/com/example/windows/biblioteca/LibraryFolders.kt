package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.library.FolderCheck
import com.example.data.library.FolderTree
import com.example.ui.components.IconAction
import com.example.ui.components.InsetGroup
import com.example.ui.components.ListRow
import com.example.ui.components.PrimaryButton
import com.example.ui.components.TarkasAlertDialog
import com.example.ui.components.TextAction
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.LocalBarInset
import com.example.windows.res.*

/** Asks for a playlist name. [onDone] gets the text as typed; the store trims it and supplies a default when empty. */
@Composable
fun NamePlaylistDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
  var text by remember { mutableStateOf(initial) }
  TarkasAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = GridText.BodyStrong) },
    text = { OutlinedTextField(text, { text = it }, singleLine = true, textStyle = GridText.Body) },
    confirmButton = { TextAction(confirm, { onDone(text) }) },
    dismissButton = { TextAction(tr(Res.string.library_cancel), onDismiss) }
  )
}

/** The folders manager, in the main area: which folders are scanned and which of their subfolders count. */
@Composable
fun FoldersPane(lib: BibliotecaState, modifier: Modifier = Modifier) {
  val catalog by lib.catalog.collectAsState()
  val rules by lib.rules.collectAsState()
  val roots by lib.roots.collectAsState()
  var expanded by remember { mutableStateOf(setOf<String>()) }
  val tree = remember(catalog) { FolderTree.build(catalog.folderCounts) }
  val rows = remember(tree, expanded) { FolderTree.rows(tree, expanded) }
  Column(modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Grid.u12), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Text(
        tr(Res.string.library_folders_title), style = GridText.BodyStrong, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f)
      )
      IconAction(Icons.Filled.Add, tr(Res.string.library_import_file), { chooseFolder(lib) { lib.app.showNotice(it) } })
    }
    Text(
      tr(Res.string.library_folders_note), style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = Grid.u12, vertical = Grid.u6)
    )
    if (rows.isEmpty()) {
      EmptyState(tr(Res.string.library_folders_empty_title), tr(Res.string.library_folders_empty_message)) {
        PrimaryButton(tr(Res.string.library_import_audio), { chooseFolder(lib) { lib.app.showNotice(it) } })
      }
      return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = LocalBarInset.current)) {
      items(rows, key = { it.node.path }) { row ->
        val node = row.node
        val state = node.state(rules)
        val stateLabel = when (state) {
          FolderCheck.ON -> tr(Res.string.library_folders_state_on)
          FolderCheck.OFF -> tr(Res.string.library_folders_state_off)
          FolderCheck.PARTIAL -> tr(Res.string.library_folders_state_partial)
        }
        Row(Modifier.fillMaxWidth().padding(start = Grid.u12 + Grid.u24 * row.depth), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
          Box(Modifier.weight(1f)) {
            ListRow(
              node.name.ifEmpty { tr(Res.string.library_folders_root) },
              { lib.setRules(rules.with(node.path, state == FolderCheck.OFF)) },
              subtitle = trPlural(Res.plurals.library_track_count, node.total, node.total),
              icon = Icons.Filled.Folder, value = stateLabel, chevron = false
            )
          }
          if (node.children.isNotEmpty()) {
            val open = node.path in expanded
            TextAction(
              if (open) tr(Res.string.library_folders_collapse) else tr(Res.string.library_folders_expand),
              { expanded = if (open) expanded - node.path else expanded + node.path }
            )
          }
        }
      }
      item(key = "roots") {
        InsetGroup(Modifier.padding(horizontal = Grid.u12, vertical = Grid.u12)) {
          for (r in roots) ListRow(r.path, { lib.removeRoot(r) }, icon = Icons.Filled.Delete, chevron = false)
        }
      }
    }
  }
}
