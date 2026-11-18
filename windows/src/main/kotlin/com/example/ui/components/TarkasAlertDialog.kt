package com.example.ui.components

import com.example.windows.res.Res
import com.example.windows.res.tr
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.Grid

/**
 * The app's alert dialog: Material's, with a side margin of its own. The window is given the full
 * width and the card keeps [Grid.margin] clear of both screen edges (Material still caps it at
 * 560 dp and gives it at least 280 dp), so no dialog touches the edges whatever the device's window
 * theme does with the platform default width. Title, text and buttons are Material's, unchanged.
 */
@Composable
fun TarkasAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  text: (@Composable () -> Unit)? = null,
  shape: Shape = AlertDialogDefaults.shape
) {
  AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = confirmButton,
    modifier = modifier.padding(horizontal = Grid.margin),
    dismissButton = dismissButton,
    title = title,
    text = text,
    shape = shape,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  )
}
