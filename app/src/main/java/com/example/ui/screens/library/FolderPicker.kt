package com.example.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.library.FolderCheck
import com.example.data.library.FolderNode
import com.example.data.library.FolderRules
import com.example.data.library.FolderTree
import com.example.ui.components.EmptyState
import com.example.ui.components.SubPageHeader
import com.example.ui.theme.AppText
import com.example.ui.theme.Space

/**
 * Which folders of the device are in the library. The tree shows the folders that hold music; ticking one
 * puts its whole branch in or out, and a folder can be switched back on inside a branch that is off.
 * The choice is a filter over Android's media library: nothing is moved, and the files stay as they are.
 */
@Composable
fun FolderPicker(
  songsPerFolder: Map<String, Int>,
  rules: FolderRules,
  onSetIncluded: (folder: String, included: Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  val tree = remember(songsPerFolder) { FolderTree.build(songsPerFolder) }
  var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
  val rows = remember(tree, expanded) { FolderTree.rows(tree, expanded) }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(
          title = stringResource(R.string.library_folders_title),
          backLabel = stringResource(R.string.library_back_label),
          onBack = onDismiss
        )
        Text(
          text = stringResource(R.string.library_folders_note),
          style = AppText.Footnote,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s)
        )
        if (rows.isEmpty()) {
          EmptyState(
            icon = Icons.Rounded.ChevronRight,
            title = stringResource(R.string.library_folders_empty_title),
            message = stringResource(R.string.library_folders_empty_message),
            modifier = Modifier.padding(horizontal = Space.page)
          )
        } else {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.node.path.ifEmpty { "/" } }) { row ->
              FolderRowItem(
                node = row.node,
                depth = row.depth,
                rules = rules,
                open = row.node.path in expanded,
                onToggleOpen = {
                  val p = row.node.path
                  expanded = if (p in expanded) expanded - p else expanded + p
                },
                onSetIncluded = onSetIncluded
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FolderRowItem(
  node: FolderNode,
  depth: Int,
  rules: FolderRules,
  open: Boolean,
  onToggleOpen: () -> Unit,
  onSetIncluded: (String, Boolean) -> Unit
) {
  val check = node.state(rules)
  val name = node.name.ifEmpty { stringResource(R.string.library_folders_root) }
  val stateText = stringResource(
    when (check) {
      FolderCheck.ON -> R.string.library_folders_state_on
      FolderCheck.OFF -> R.string.library_folders_state_off
      FolderCheck.PARTIAL -> R.string.library_folders_state_partial
    }
  )
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 48.dp)
      .padding(start = Space.page + 20.dp * depth, end = Space.page)
  ) {
    if (node.children.isNotEmpty()) {
      Icon(
        imageVector = if (open) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
        contentDescription = stringResource(if (open) R.string.library_folders_collapse else R.string.library_folders_expand, name),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .size(40.dp)
          .clickable(onClick = onToggleOpen)
          .padding(8.dp)
      )
    } else {
      Spacer(Modifier.width(40.dp))
    }
    // The row is the target: tapping the name or the box does the same. A partly ticked branch goes fully in.
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .weight(1f)
        .clickable(role = Role.Checkbox) { onSetIncluded(node.path, check != FolderCheck.ON) }
        .semantics(mergeDescendants = true) { stateDescription = stateText }
        .defaultMinSize(minHeight = 48.dp)
    ) {
      TriStateCheckbox(
        state = when (check) {
          FolderCheck.ON -> ToggleableState.On
          FolderCheck.OFF -> ToggleableState.Off
          FolderCheck.PARTIAL -> ToggleableState.Indeterminate
        },
        onClick = null
      )
      Column(modifier = Modifier.weight(1f).padding(start = Space.m)) {
        Text(name, style = AppText.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          pluralStringResource(R.plurals.library_track_count, node.total, node.total),
          style = AppText.Footnote,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
