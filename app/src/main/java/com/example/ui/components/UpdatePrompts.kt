package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.update.ReleaseNotes
import com.example.ui.theme.AppText
import com.example.ui.theme.Space
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.R
import com.example.data.update.UpdateManager
import com.example.data.update.UpdateState.Phase
import com.example.ui.legal.LegalTexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Two prompts shown on the start screen, one at a time and only when [enabled] (after the opening
 * animation, onboarding and the notification question):
 *
 * 1. Permission to install updates ("install unknown apps"), explained once. Only the published build
 *    installs updates, so the development build never asks.
 * 2. A popup when a newer release exists. "Depois" hides it for that release; the next release asks again.
 */
@Composable
fun StartupUpdatePrompts(enabled: Boolean) {
  if (!enabled) return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state by UpdateManager.state.collectAsState()
  val installs = UpdateManager.installsUpdates
  var allowed by remember { mutableStateOf(UpdateManager.canInstallPackages()) }
  var permissionAsked by remember { mutableStateOf(UpdateManager.installPromptAsked) }
  var dismissedTag by remember { mutableStateOf(UpdateManager.dismissedTag) }
  // The permission is changed on a system page, so read it again when the app comes back.
  LifecycleResumeEffect(Unit) {
    allowed = UpdateManager.canInstallPackages()
    onPauseOrDispose {}
  }

  if (installs && !allowed && !permissionAsked) {
    TarkasAlertDialog(
      onDismissRequest = { UpdateManager.installPromptAsked = true; permissionAsked = true },
      title = { Text(stringResource(R.string.update_permission_title)) },
      text = { Text(stringResource(R.string.update_permission_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            UpdateManager.installPromptAsked = true
            permissionAsked = true
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
          },
          modifier = Modifier.testTag("update_permission_allow")
        ) { Text(stringResource(R.string.update_permission_open)) }
      },
      dismissButton = {
        TextButton(onClick = { UpdateManager.installPromptAsked = true; permissionAsked = true }) {
          Text(stringResource(R.string.update_later))
        }
      }
    )
    return
  }

  val release = state.release
  val waiting = state.phase == Phase.AVAILABLE || state.phase == Phase.READY
  if (release != null && waiting && dismissedTag != release.tag) {
    val size = release.apkSize.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    TarkasAlertDialog(
      onDismissRequest = { UpdateManager.dismissedTag = release.tag; dismissedTag = release.tag },
      title = { Text(stringResource(R.string.update_popup_title, release.versionName)) },
      text = {
        Column {
          Text(
            when {
              !installs -> stringResource(R.string.update_popup_dev)
              state.phase == Phase.READY -> stringResource(R.string.update_popup_ready)
              size != null -> stringResource(R.string.update_popup_message_size, size)
              else -> stringResource(R.string.update_popup_message)
            }
          )
          UpdateNotes(release.notes, release.tag, maxHeight = 160.dp, modifier = Modifier.padding(top = Space.m))
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            UpdateManager.dismissedTag = release.tag
            dismissedTag = release.tag
            if (!installs) {
              val url = release.pageUrl.takeIf { it.startsWith("https://") } ?: LegalTexts.RELEASES_URL
              runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            } else {
              scope.launch(Dispatchers.Default) {
                if (state.phase == Phase.READY || UpdateManager.download(userInitiated = true).phase == Phase.READY) {
                  UpdateManager.install(userInitiated = true)
                }
              }
            }
          },
          modifier = Modifier.testTag("update_popup_confirm")
        ) {
          Text(stringResource(if (installs) R.string.update_popup_confirm else R.string.settings_update_view_github))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { UpdateManager.dismissedTag = release.tag; dismissedTag = release.tag },
          modifier = Modifier.testTag("update_popup_later")
        ) { Text(stringResource(R.string.update_later)) }
      }
    )
  }
}

/**
 * The release notes as plain text under a small title, scrollable when long. Draws nothing when the
 * notes are empty.
 */
@Composable
fun UpdateNotes(rawNotes: String, tag: String, maxHeight: Dp, modifier: Modifier = Modifier) {
  val notes = remember(rawNotes) { ReleaseNotes.plain(rawNotes) } ?: return
  val colors = MaterialTheme.colorScheme
  Column(modifier.testTag("update_notes")) {
    Text(stringResource(R.string.settings_update_notes_title, tag), style = AppText.Footnote, color = colors.onSurfaceVariant)
    Text(
      text = notes,
      style = AppText.Subhead,
      color = colors.onSurface,
      modifier = Modifier.padding(top = Space.xs).heightIn(max = maxHeight).verticalScroll(rememberScrollState())
    )
  }
}
