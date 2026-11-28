package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.TonalButton
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.*
import java.io.File

/*
 * The empty library's way in: the Glass Folder. Clicking it fans out two sheets, one for folders and one for audio
 * files; each opens the native Windows explorer in its own mode. The `+` key on the folder is the quick way to the folder
 * picker. The folder's label says what is watched.
 */

/** Test tags of the picker. */
const val TAG_PICKER = "library-picker"
const val TAG_PICKER_ERROR = "library-picker-error"

/** The count under the folder's name: how many folders are watched (short, since the folder can be 192 dp wide). */
internal fun watchedFoldersMeta(count: Int): String = when (count) {
  0 -> lt("No folders", "Nenhuma pasta")
  1 -> lt("1 folder", "1 pasta")
  else -> lt("$count folders", "$count pastas")
}

/**
 * The two sheets, in the order they fan: folders first, audio files second. Each carries its full name and a short one: the
 * label shows the full name where it fits in the part the front sheet leaves uncovered, and the short word where it does not.
 */
internal fun pickerSheets(): List<FolderSheet> = listOf(
  FolderSheet(
    lt("Music folder", "Pasta de músicas"),
    lt("Choose", "Escolher"), ArtPreset.Aurora, shortName = lt("Folder", "Pasta"),
  ),
  FolderSheet(
    lt("Audio files", "Arquivos de áudio"),
    TagReader.EXTENSIONS.joinToString(" · ") { it.uppercase() }, ArtPreset.Dusk, shortName = lt("Files", "Arquivos"),
  ),
)

/** Index of the sheet that opens the folder picker and of the one that opens the file picker. */
internal const val SHEET_FOLDERS = 0
internal const val SHEET_FILES = 1

/**
 * The Glass Folder with what goes with it: the refresh action when folders are already watched, and the error of the
 * native dialog when it could not open. [state] is the folder's, so the page can close it with a press outside.
 */
@Composable
fun LibraryFolderPicker(
  lib: BibliotecaState,
  roots: List<File>,
  state: GlassFolderState,
  onFiles: (List<File>) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  var error by remember { mutableStateOf<String?>(null) }
  val pickFolder = { error = null; chooseFolder(lib) { error = it } }
  val pickFiles = { error = null; chooseFiles(onFiles) { error = it } }
  BoxWithConstraints(modifier.fillMaxWidth()) {
    // Room under the folder for the refresh button and an error line; the folder takes the widest step of the 2x chain that fits.
    val width = glassFolderWidth(maxWidth.value, (maxHeight - Grid.u96).value).dp
    val sheets = remember(width) { pickerSheets() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
      GlassFolder(
        label = lt("Library", "Biblioteca"),
        meta = watchedFoldersMeta(roots.size),
        sheets = sheets,
        onSheet = { i ->
          state.close()
          if (i == SHEET_FOLDERS) pickFolder() else pickFiles()
        },
        width = width,
        state = state,
        action = GlassFolderAction.Add,
        onAction = { pickFolder() },
        modifier = Modifier.testTag(TAG_PICKER),
      )
      if (roots.isNotEmpty()) TonalButton(tr(Res.string.library_refresh), { lib.rescan() })
      error?.let {
        Text(
          it, style = GridText.Small, color = colors.error, textAlign = TextAlign.Center,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(TAG_PICKER_ERROR)
        )
      }
    }
  }
}
