package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.BuildConfig
import com.example.R
import com.example.data.update.UpdateManager
import com.example.data.update.UpdateState
import com.example.data.update.UpdateState.Phase
import com.example.ui.components.GroupDivider
import com.example.ui.components.InfoRow
import com.example.ui.components.ListRow
import com.example.ui.components.SwitchRow
import com.example.ui.legal.LegalTexts
import com.example.ui.theme.AppText
import com.example.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Atualizações: the installed version, the last check, the switches and a button for the next step.
 * The footer says plainly what happens by itself and what still needs a tap. The development build
 * (installed by adb, other signing key) only tells that a newer version exists.
 */
internal fun LazyListScope.updateSettingsGroup(header: String) {
  settingsGroup(header, tag = "settings_updates", footer = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by UpdateManager.state.collectAsState()
    val installs = UpdateManager.installsUpdates
    var auto by remember { mutableStateOf(UpdateManager.autoEnabled) }
    var wifiOnly by remember { mutableStateOf(UpdateManager.wifiOnly) }
    var allowed by remember { mutableStateOf(UpdateManager.canInstallPackages()) }
    // The permission is changed on a system page, so read it again when the app comes back.
    LifecycleResumeEffect(Unit) {
      allowed = UpdateManager.canInstallPackages()
      onPauseOrDispose {}
    }

    InfoRow(
      title = stringResource(R.string.settings_update_installed),
      value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" + if (installs) "" else " · dev",
      modifier = Modifier.testTag("update_installed")
    )
    GroupDivider()
    InfoRow(
      title = stringResource(R.string.settings_update_last_check),
      value = if (state.lastCheckedAt > 0) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(state.lastCheckedAt))
      } else stringResource(R.string.settings_update_never),
      modifier = Modifier.testTag("update_last_checked")
    )
    GroupDivider()
    StepRow(state, installs, scope) { url ->
      runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    if (installs) {
      GroupDivider()
      SwitchRow(
        title = stringResource(R.string.settings_update_auto),
        subtitle = stringResource(R.string.settings_update_auto_subtitle),
        checked = auto,
        onCheckedChange = { auto = it; UpdateManager.setAutoEnabled(it) },
        modifier = Modifier.testTag("update_auto")
      )
      GroupDivider()
      SwitchRow(
        title = stringResource(R.string.settings_update_wifi_only),
        subtitle = stringResource(R.string.settings_update_wifi_only_subtitle),
        checked = wifiOnly,
        onCheckedChange = { wifiOnly = it; UpdateManager.setWifiOnly(it) },
        modifier = Modifier.testTag("update_wifi_only")
      )
      if (!allowed) {
        GroupDivider()
        ListRow(
          title = stringResource(R.string.settings_update_allow_install),
          subtitle = stringResource(R.string.settings_update_allow_install_subtitle),
          onClick = {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
          },
          modifier = Modifier.testTag("update_allow_install")
        )
      }
    }
    GroupDivider()
    Footer(state.problem?.message ?: if (installs) stringResource(R.string.settings_update_explanation) else UpdateManager.DEV_BUILD_NOTICE)
  }
}

/** The one row for the next step: check, download, install, or (development build) open the release. */
@Composable
private fun StepRow(state: UpdateState, installs: Boolean, scope: CoroutineScope, open: (String) -> Unit) {
  val release = state.release
  val tag = release?.tag.orEmpty()
  when (state.phase) {
    Phase.CHECKING -> InfoRow(stringResource(R.string.settings_update_row), stringResource(R.string.settings_update_checking))
    Phase.IDLE, Phase.UP_TO_DATE -> ListRow(
      title = stringResource(R.string.settings_update_check_now),
      value = if (state.phase == Phase.UP_TO_DATE && state.problem == null) stringResource(R.string.settings_update_up_to_date) else null,
      onClick = { scope.launch(Dispatchers.Default) { UpdateManager.check() } },
      chevron = false,
      modifier = Modifier.testTag("update_check")
    )
    else -> if (!installs) {
      ListRow(
        title = stringResource(R.string.settings_update_new_version, tag),
        value = stringResource(R.string.settings_update_view_github),
        onClick = { open(release?.pageUrl?.takeIf { it.startsWith("https://") } ?: LegalTexts.RELEASES_URL) },
        chevron = false,
        modifier = Modifier.testTag("update_open_release")
      )
    } else when (state.phase) {
      Phase.DOWNLOADING -> InfoRow(
        stringResource(R.string.settings_update_downloading, tag),
        stringResource(R.string.settings_update_progress, (state.progress * 100).toInt()),
        Modifier.testTag("update_progress")
      )
      Phase.INSTALLING -> InfoRow(stringResource(R.string.settings_update_row), stringResource(R.string.settings_update_installing))
      Phase.AWAITING_USER -> InfoRow(stringResource(R.string.settings_update_row), stringResource(R.string.settings_update_awaiting))
      Phase.READY -> ListRow(
        title = stringResource(R.string.settings_update_install_now, tag),
        subtitle = stringResource(R.string.settings_update_ready),
        onClick = { scope.launch(Dispatchers.Default) { UpdateManager.install(userInitiated = true) } },
        chevron = false,
        modifier = Modifier.testTag("update_install")
      )
      else -> ListRow(
        title = stringResource(R.string.settings_update_download_install, tag),
        value = release?.apkSize?.takeIf { it > 0 }?.let(::formatSize),
        onClick = {
          scope.launch(Dispatchers.Default) {
            if (UpdateManager.download(userInitiated = true).phase == Phase.READY) UpdateManager.install(userInitiated = true)
          }
        },
        chevron = false,
        modifier = Modifier.testTag("update_download")
      )
    }
  }
}

@Composable
private fun Footer(text: String) {
  val colors = MaterialTheme.colorScheme
  Text(
    text = text,
    style = AppText.Footnote,
    color = colors.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m).testTag("update_message")
  )
}

private fun formatSize(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / (1024f * 1024f))
