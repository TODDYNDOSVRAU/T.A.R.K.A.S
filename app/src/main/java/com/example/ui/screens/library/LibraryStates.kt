package com.example.ui.screens.library

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.R
import com.example.ui.components.EmptyState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.TextAction
import com.example.ui.theme.AppText
import com.example.ui.theme.Size
import com.example.ui.theme.Space

/** Asks for the device's music in plain words, with the one action that can still grant it. */
@Composable
internal fun MusicPermissionPrompt(blocked: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page, vertical = Space.xl),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      stringResource(R.string.library_permission_title),
      style = AppText.Title3,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.size(Space.s))
    Text(
      stringResource(if (blocked) R.string.library_permission_blocked else R.string.library_permission_request),
      style = AppText.Subhead,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(Modifier.size(Space.l))
    if (blocked) {
      PrimaryButton(stringResource(R.string.library_permission_open_settings), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().testTag("library_open_settings"))
      // The detection cannot tell "don't ask again" from a dismissed dialog, so asking stays possible.
      TextAction(stringResource(R.string.library_permission_ask_again), onClick = onAllow)
    } else {
      PrimaryButton(stringResource(R.string.library_permission_allow), onClick = onAllow, modifier = Modifier.fillMaxWidth().testTag("library_allow_music"))
    }
  }
}

/** A search that found nothing: says so, and the one thing to do next. */
@Composable
internal fun NoMatch(message: String, onClear: () -> Unit) {
  EmptyState(
    icon = Icons.Rounded.SearchOff,
    title = stringResource(R.string.library_search_empty_title),
    message = message,
    modifier = Modifier.padding(horizontal = Space.page),
    actionLabel = stringResource(R.string.library_search_clear),
    onAction = onClear
  )
}

/** The device has no music the app can see: refresh the scan, or import one file by hand. */
@Composable
internal fun EmptyLibrary(onRefresh: () -> Unit, onImport: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    EmptyState(
      icon = Icons.Rounded.LibraryMusic,
      title = stringResource(R.string.library_empty_songs_title),
      message = stringResource(R.string.library_empty_songs_message),
      modifier = Modifier.padding(horizontal = Space.page),
      actionLabel = stringResource(R.string.library_refresh),
      onAction = onRefresh
    )
    TextAction(
      stringResource(R.string.library_import_file),
      onClick = onImport,
      modifier = Modifier.testTag("library_empty_import")
    )
  }
}

/** A spinner with what is being waited for. */
@Composable
internal fun Loading(text: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page, vertical = Space.xl),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(Size.iconSmall),
      strokeWidth = Space.xxs,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.width(Space.m))
    Text(text, style = AppText.Subhead, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** One line of secondary text inside a list. */
@Composable
internal fun Note(text: String) {
  Text(
    text,
    style = AppText.Subhead,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Space.page, vertical = Space.l)
  )
}
