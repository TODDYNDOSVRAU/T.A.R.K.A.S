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
import com.example.data.update.ReleaseNotes
import com.example.data.update.UpdateState.Phase
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.R
import com.example.ui.viewmodel.StartupPrompt
import com.example.ui.viewmodel.UpdateViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.legal.LegalTexts

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
  val vm: UpdateViewModel = viewModel()
  val state by vm.state.collectAsState()
  val switches by vm.switches.collectAsState()
  val installs = vm.installsUpdates
  // The permission is changed on a system page, so read it again when the app comes back.
  LifecycleResumeEffect(Unit) {
    vm.refresh()
    onPauseOrDispose {}
  }

  val prompt = UpdateViewModel.startupPrompt(state, installs, switches)
  if (prompt == StartupPrompt.PERMISSION) {
    TarkasAlertDialog(
      onDismissRequest = { vm.permissionPromptAnswered() },
      title = { Text(stringResource(R.string.update_permission_title)) },
      text = { Text(stringResource(R.string.update_permission_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            vm.permissionPromptAnswered()
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
          },
          modifier = Modifier.testTag("update_permission_allow")
        ) { Text(stringResource(R.string.update_permission_open)) }
      },
      dismissButton = {
        TextButton(onClick = { vm.permissionPromptAnswered() }) {
          Text(stringResource(R.string.update_later))
        }
      }
    )
    return
  }

  val release = state.release
  if (release != null && prompt == StartupPrompt.RELEASE) {
    val size = release.apkSize.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    TarkasAlertDialog(
      onDismissRequest = { vm.dismissRelease(release.tag) },
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
          UpdateNotes(release.notes, release.tag, maxHeight = Grid.u192, modifier = Modifier.padding(top = Grid.u12))
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            vm.dismissRelease(release.tag)
            if (!installs) {
              val url = release.pageUrl.takeIf { it.startsWith("https://") } ?: LegalTexts.RELEASES_URL
              runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            } else {
              vm.downloadAndInstall()
            }
          },
          modifier = Modifier.testTag("update_popup_confirm")
        ) {
          Text(stringResource(if (installs) R.string.update_popup_confirm else R.string.settings_update_view_github))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { vm.dismissRelease(release.tag) },
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
    Text(stringResource(R.string.settings_update_notes_title, tag), style = GridText.Small, color = colors.onSurfaceVariant)
    Text(
      text = notes,
      style = GridText.Body,
      color = colors.onSurface,
      modifier = Modifier.padding(top = Grid.u6).heightIn(max = maxHeight).verticalScroll(rememberScrollState())
    )
  }
}
