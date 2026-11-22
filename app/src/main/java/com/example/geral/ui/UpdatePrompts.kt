package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.update.ReleaseNotes
import com.example.data.update.UpdateProblem
import com.example.data.update.UpdateState.Phase
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.Shapes
import com.example.ui.theme.SquircleShape
import com.example.ui.viewmodel.StartupPrompt
import com.example.ui.viewmodel.UpdateViewModel

/**
 * Two prompts shown on the start screen, one at a time and only when [enabled] (after the opening
 * animation, onboarding and the notification question):
 *
 * 1. Permission to install updates ("install unknown apps"), explained once.
 * 2. An interactive update popup when a newer release exists: downloads, verifies and installs
 *    directly in-app (showing live progress, cancel, permission flow and inline errors) without
 *    redirecting to GitHub. "Depois" hides it for that release; the next release asks again.
 */
@Composable
fun StartupUpdatePrompts(enabled: Boolean) {
  if (!enabled) return
  val context = LocalContext.current
  val vm: UpdateViewModel = viewModel()
  val state by vm.state.collectAsState()
  val switches by vm.switches.collectAsState()
  val installs = vm.installsUpdates
  // Keeps the dialog open while the user-initiated download/install is running for this tag.
  var activeTag by rememberSaveable { mutableStateOf<String?>(null) }
  // Automatically resumes download + install when the user returns after granting unknown-sources permission.
  var resumeAfterPermission by rememberSaveable { mutableStateOf(false) }

  // The permission is changed on a system page, so read it again when the app comes back.
  LifecycleResumeEffect(Unit) {
    vm.refresh()
    onPauseOrDispose {}
  }

  LaunchedEffect(switches.allowed, resumeAfterPermission) {
    if (resumeAfterPermission && switches.allowed) {
      resumeAfterPermission = false
      vm.downloadAndInstall()
    }
  }

  fun openUnknownAppsPermission() {
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }

  val prompt = UpdateViewModel.startupPrompt(state, installs, switches)
  if (prompt == StartupPrompt.PERMISSION && activeTag == null) {
    TarkasAlertDialog(
      onDismissRequest = { vm.permissionPromptAnswered() },
      title = { Text(stringResource(R.string.update_permission_title)) },
      text = { Text(stringResource(R.string.update_permission_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            vm.permissionPromptAnswered()
            openUnknownAppsPermission()
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
  val keepOpenForActive = release != null && activeTag == release.tag &&
    state.phase in setOf(Phase.AVAILABLE, Phase.DOWNLOADING, Phase.READY, Phase.INSTALLING, Phase.AWAITING_USER)
  if (release != null && (prompt == StartupPrompt.RELEASE || keepOpenForActive)) {
    val colors = MaterialTheme.colorScheme
    val size = release.apkSize.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    val isDownloading = state.phase == Phase.DOWNLOADING
    val isInstalling = state.phase == Phase.INSTALLING || state.phase == Phase.AWAITING_USER
    val isBusy = isDownloading || isInstalling
    val needsPermission = state.problem == UpdateProblem.NOT_ALLOWED || (!switches.allowed && state.phase == Phase.READY)

    TarkasAlertDialog(
      onDismissRequest = {
        if (!isBusy) {
          activeTag = null
          resumeAfterPermission = false
          vm.dismissRelease(release.tag)
        }
      },
      title = {
        Column(verticalArrangement = Arrangement.spacedBy(Grid.u6)) {
          Text(stringResource(R.string.update_popup_title, release.versionName))
          if (size != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Grid.u6), verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .clip(Shapes.pill)
                  .background(colors.primary.copy(alpha = 0.14f))
                  .padding(horizontal = 10.dp, vertical = 3.dp)
              ) {
                Text(
                  text = "${release.tag} · $size",
                  style = GridText.Small,
                  color = colors.primary
                )
              }
            }
          }
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
          when {
            isDownloading -> {
              val pct = (state.progress * 100f).toInt().coerceIn(0, 100)
              Text(
                text = stringResource(R.string.update_popup_downloading, pct),
                style = GridText.Body,
                color = colors.onSurface,
                modifier = Modifier.testTag("update_popup_progress_text")
              )
              LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                color = colors.primary,
                trackColor = colors.onSurface.copy(alpha = 0.12f),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(Shapes.pill)
                  .testTag("update_popup_progress")
              )
            }
            state.phase == Phase.INSTALLING -> {
              Text(
                text = stringResource(R.string.update_popup_installing),
                style = GridText.Body,
                color = colors.onSurface
              )
              LinearProgressIndicator(
                color = colors.primary,
                trackColor = colors.onSurface.copy(alpha = 0.12f),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(Shapes.pill)
              )
            }
            state.phase == Phase.AWAITING_USER -> {
              Text(
                text = stringResource(R.string.update_popup_awaiting_user),
                style = GridText.Body,
                color = colors.primary
              )
              LinearProgressIndicator(
                color = colors.primary,
                trackColor = colors.onSurface.copy(alpha = 0.12f),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(Shapes.pill)
              )
            }
            else -> {
              Text(
                text = when {
                  state.phase == Phase.READY -> stringResource(R.string.update_popup_ready)
                  size != null -> stringResource(R.string.update_popup_message_size, size)
                  else -> stringResource(R.string.update_popup_message)
                },
                style = GridText.Body,
                color = colors.onSurface
              )
            }
          }

          state.problem?.let { problem ->
            val errShape = SquircleShape(12.dp)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(errShape)
                .background(colors.error.copy(alpha = 0.12f))
                .border(Grid.hairline, colors.error.copy(alpha = 0.3f), errShape)
                .padding(Grid.u12)
                .testTag("update_popup_error")
            ) {
              Text(
                text = stringResource(problem.messageRes),
                style = GridText.Small,
                color = colors.error
              )
            }
          }

          if (ReleaseNotes.plain(release.notes) != null) {
            val notesShape = SquircleShape(14.dp)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(notesShape)
                .background(colors.surfaceContainer)
                .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), notesShape)
                .padding(Grid.u12)
            ) {
              UpdateNotes(release.notes, release.tag, maxHeight = Grid.u192)
            }
          }
        }
      },
      confirmButton = {
        when {
          isDownloading -> {
            TextButton(
              onClick = { vm.cancelDownload() },
              modifier = Modifier.testTag("update_popup_cancel_download")
            ) {
              Text(stringResource(R.string.settings_update_cancel_download), color = colors.error)
            }
          }
          isInstalling -> {
            // Installation is in progress in PackageInstaller; no confirm button needed.
          }
          needsPermission -> {
            TextButton(
              onClick = {
                activeTag = release.tag
                resumeAfterPermission = true
                openUnknownAppsPermission()
              },
              modifier = Modifier.testTag("update_popup_open_permission")
            ) {
              Text(stringResource(R.string.update_permission_open))
            }
          }
          else -> {
            TextButton(
              onClick = {
                activeTag = release.tag
                if (!switches.allowed) {
                  resumeAfterPermission = true
                  openUnknownAppsPermission()
                } else {
                  vm.downloadAndInstall()
                }
              },
              modifier = Modifier.testTag("update_popup_confirm")
            ) {
              Text(
                stringResource(
                  when {
                    state.problem != null -> R.string.update_popup_retry
                    state.phase == Phase.READY -> R.string.update_popup_install_now
                    else -> R.string.update_popup_confirm
                  }
                )
              )
            }
          }
        }
      },
      dismissButton = {
        if (!isDownloading) {
          TextButton(
            onClick = {
              activeTag = null
              resumeAfterPermission = false
              vm.dismissRelease(release.tag)
            },
            modifier = Modifier.testTag("update_popup_later")
          ) { Text(stringResource(R.string.update_later)) }
        }
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
