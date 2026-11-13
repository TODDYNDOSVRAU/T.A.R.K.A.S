package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * The grab bar at the top of a bottom sheet, with the app's own description instead of the library's
 * default ("Alça de arrastar"), in the app's language.
 */
@Composable
fun SheetHandle() {
  val description = stringResource(R.string.player_sheet_handle)
  Box(
    modifier = Modifier
      .padding(vertical = 12.dp)
      .width(32.dp)
      .height(4.dp)
      .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
      .semantics { contentDescription = description }
  )
}
