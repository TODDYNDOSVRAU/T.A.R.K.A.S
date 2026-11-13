package com.example.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AudioCapabilities
import com.example.data.model.CuriosityConfig
import com.example.data.model.RepeatMode
import com.example.ui.components.GroupDivider
import com.example.ui.components.GroupFooter
import com.example.ui.components.GroupHeader
import com.example.ui.components.InfoRow
import com.example.ui.components.InsetGroup
import com.example.ui.components.ListRow
import com.example.ui.components.RadioRow
import com.example.ui.components.SubPageHeader
import com.example.ui.components.SwitchRow
import com.example.ui.components.TarkasMark
import com.example.ui.legal.LegalTexts
import com.example.ui.theme.AppMotion
import com.example.ui.theme.ListAnimation
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.SurfaceStyle
import com.example.ui.theme.liquidGlassSupported
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.subPageTransition

/** The pages inside Ajustes: the main list, Sobre, and the texts opened from them. */
private enum class SettingsPage { MAIN, ABOUT, TERMS, PRIVACY, LICENSES, CHANGELOG }

/** The name a sub-page's back control shows for the page it returns to. */
@Composable
private fun SettingsPage.backLabel(): String =
  stringResource(if (this == SettingsPage.ABOUT) R.string.settings_about else R.string.settings_back_settings)

/**
 * Ajustes, as a grouped list in the manner of the platform's own settings: Aparência, Reprodução,
 * Áudio, Streaming, Curiosidades e assistente, Notificações, Armazenamento, Privacidade and Sobre.
 * Every row either does something or plainly informs. Sobre and the long texts open as sub-pages,
 * and a text goes back to the page it was opened from (Ajustes or Sobre), named in its back control.
 *
 * [onSubPageChange] tells the host whether a sub-page is showing, so it can hide its own large
 * "Ajustes" title while the sub-page draws its own.
 */
@Composable
fun SettingsScreen(
  themeMode: String,
  capabilities: AudioCapabilities,
  onThemeModeChange: (String) -> Unit,
  curiosityConfig: CuriosityConfig? = null,
  onOpenCuriositySettings: () -> Unit = {},
  hasGeminiKey: Boolean = false,
  surfaceStyle: SurfaceStyle = SurfaceStyle.SOLID,
  onSurfaceStyleChange: (SurfaceStyle) -> Unit = {},
  reduceMotion: Boolean = false,
  onReduceMotionChange: (Boolean) -> Unit = {},
  onListAnimationChange: (com.example.ui.theme.ListAnimation) -> Unit = {},
  reduceTransparency: Boolean = false,
  onReduceTransparencyChange: (Boolean) -> Unit = {},
  isShuffle: Boolean = false,
  repeatMode: RepeatMode = RepeatMode.OFF,
  onToggleShuffle: () -> Unit = {},
  onCycleRepeat: () -> Unit = {},
  onOpenAudio: () -> Unit = {},
  cacheSizeBytes: suspend () -> Long = { 0L },
  onClearCache: (onDone: () -> Unit) -> Unit = {},
  onExportData: (Uri) -> Unit = {},
  onEraseAllData: () -> Unit = {},
  onRestartOnboarding: () -> Unit = {},
  termsAcceptedAt: Long = 0L,
  modifier: Modifier = Modifier,
  onSetRepeatMode: ((RepeatMode) -> Unit)? = null,
  onSubPageChange: (Boolean) -> Unit = {}
) {
  var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
  // Where the open text was reached from, so back returns there (Terms and Privacy open from both).
  var textOrigin by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
  val reduced = rememberReducedMotion()
  // Kept here, outside the page switch, so coming back from Sobre lands where the list was left.
  val mainListState = androidx.compose.foundation.lazy.rememberLazyListState()
  LaunchedEffect(page) { onSubPageChange(page != SettingsPage.MAIN) }

  fun openText(target: SettingsPage, from: SettingsPage) {
    textOrigin = from
    page = target
  }
  fun back() {
    page = if (page == SettingsPage.ABOUT) SettingsPage.MAIN else textOrigin
  }
  BackHandler(enabled = page != SettingsPage.MAIN) { back() }

  AnimatedContent(
    targetState = page,
    transitionSpec = { subPageTransition(forward = targetState.ordinal > initialState.ordinal, reduced = reduced) },
    label = "settingsPage",
    modifier = modifier.fillMaxSize()
  ) { shown ->
    when (shown) {
      SettingsPage.MAIN -> MainSettings(
        themeMode, capabilities, onThemeModeChange, curiosityConfig, onOpenCuriositySettings, hasGeminiKey,
        surfaceStyle, onSurfaceStyleChange, reduceMotion, onReduceMotionChange, onListAnimationChange, reduceTransparency, onReduceTransparencyChange,
        isShuffle, repeatMode, onToggleShuffle,
        onSetRepeatMode = { target ->
          // Without a direct setter, step the cycle (Desligado → Lista → Faixa) until it lands on the choice.
          if (onSetRepeatMode != null) onSetRepeatMode(target)
          else repeat((target.ordinal - repeatMode.ordinal + RepeatMode.entries.size) % RepeatMode.entries.size) { onCycleRepeat() }
        },
        onOpenAudio, cacheSizeBytes, onClearCache, onExportData, onEraseAllData,
        onOpenAbout = { page = SettingsPage.ABOUT },
        onOpenTerms = { openText(SettingsPage.TERMS, SettingsPage.MAIN) },
        onOpenPrivacy = { openText(SettingsPage.PRIVACY, SettingsPage.MAIN) },
        listState = mainListState
      )
      SettingsPage.ABOUT -> AboutPage(
        termsAcceptedAt = termsAcceptedAt,
        onBack = { page = SettingsPage.MAIN },
        onOpen = { openText(it, SettingsPage.ABOUT) },
        onRestartOnboarding = onRestartOnboarding
      )
      // These pages draw their own header, so they keep it below the status bar themselves.
      SettingsPage.TERMS -> LegalScreen(LegalTexts.terms, textOrigin.backLabel(), onBack = ::back, modifier = Modifier.statusBarsPadding())
      SettingsPage.PRIVACY -> LegalScreen(LegalTexts.privacy, textOrigin.backLabel(), onBack = ::back, modifier = Modifier.statusBarsPadding())
      SettingsPage.LICENSES -> LegalScreen(LegalTexts.licenses, textOrigin.backLabel(), onBack = ::back, modifier = Modifier.statusBarsPadding())
      SettingsPage.CHANGELOG -> LegalScreen(LegalTexts.changelog, textOrigin.backLabel(), onBack = ::back, modifier = Modifier.statusBarsPadding())
    }
  }
}

/** Adds a group with an optional header and footer, spaced from the group above. */
internal fun LazyListScope.settingsGroup(
  header: String?,
  footer: String? = null,
  tag: String? = null,
  content: @Composable () -> Unit
) {
  item {
    Column(Modifier.padding(top = Space.xl)) {
      if (header != null) GroupHeader(header)
      InsetGroup(modifier = if (tag != null) Modifier.testTag(tag) else Modifier) { content() }
      if (footer != null) GroupFooter(footer)
    }
  }
}

@Composable
private fun MainSettings(
  themeMode: String,
  capabilities: AudioCapabilities,
  onThemeModeChange: (String) -> Unit,
  curiosityConfig: CuriosityConfig?,
  onOpenCuriositySettings: () -> Unit,
  hasGeminiKey: Boolean,
  surfaceStyle: SurfaceStyle,
  onSurfaceStyleChange: (SurfaceStyle) -> Unit,
  reduceMotion: Boolean,
  onReduceMotionChange: (Boolean) -> Unit,
  onListAnimationChange: (ListAnimation) -> Unit,
  reduceTransparency: Boolean,
  onReduceTransparencyChange: (Boolean) -> Unit,
  isShuffle: Boolean,
  repeatMode: RepeatMode,
  onToggleShuffle: () -> Unit,
  onSetRepeatMode: (RepeatMode) -> Unit,
  onOpenAudio: () -> Unit,
  cacheSizeBytes: suspend () -> Long,
  onClearCache: (onDone: () -> Unit) -> Unit,
  onExportData: (Uri) -> Unit,
  onEraseAllData: () -> Unit,
  onOpenAbout: () -> Unit,
  onOpenTerms: () -> Unit,
  onOpenPrivacy: () -> Unit,
  listState: androidx.compose.foundation.lazy.LazyListState
) {
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current
  var confirmErase by rememberSaveable { mutableStateOf(false) }
  var cacheSize by remember { mutableStateOf<Long?>(null) }
  var cacheRefresh by remember { mutableIntStateOf(0) }
  LaunchedEffect(cacheRefresh) { cacheSize = cacheSizeBytes() }
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) onExportData(uri)
  }
  fun openSystem(intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }
  val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", context.packageName, null))
  // Read again every time the app comes back, since the usual way to change it is the system page.
  var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
  LifecycleResumeEffect(Unit) {
    notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    onPauseOrDispose {}
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    // Aparência
    settingsGroup(context.getString(R.string.settings_theme), tag = "settings_theme_card") {
      ThemeOptionRows(themeMode, onThemeModeChange, tagPrefix = "theme_chip_")
    }
    settingsGroup(
      context.getString(R.string.settings_floating_bars),
      footer = context.getString(R.string.settings_floating_bars_footer)
    ) {
      SurfaceOptionRows(surfaceStyle, onSurfaceStyleChange, tagPrefix = "surface_style_")
    }
    settingsGroup(
      context.getString(R.string.settings_list_animation),
      footer = context.getString(R.string.settings_list_animation_footer)
    ) {
      Row(
        modifier = Modifier
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = Space.s, vertical = Space.s)
          .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
      ) {
        ListAnimation.entries.forEach { style ->
          com.example.ui.components.FilterChip(
            label = style.label,
            selected = AppMotion.listAnimation == style,
            onClick = { onListAnimationChange(style) },
            modifier = Modifier.testTag("list_animation_${style.key}")
          )
        }
      }
    }
    settingsGroup(context.getString(R.string.settings_accessibility)) {
      SwitchRow(
        title = stringResource(R.string.settings_reduce_transparency),
        subtitle = if (AppMotion.systemBlurOff) stringResource(R.string.settings_on_in_android) else stringResource(R.string.settings_reduce_transparency_hint),
        checked = reduceTransparency || AppMotion.systemBlurOff,
        enabled = !AppMotion.systemBlurOff,
        onCheckedChange = onReduceTransparencyChange,
        modifier = Modifier.testTag("settings_reduce_transparency")
      )
      GroupDivider()
      SwitchRow(
        title = stringResource(R.string.settings_reduce_motion),
        subtitle = if (AppMotion.systemReduced) stringResource(R.string.settings_on_in_android) else stringResource(R.string.settings_reduce_motion_hint),
        checked = reduceMotion || AppMotion.systemReduced,
        enabled = !AppMotion.systemReduced,
        onCheckedChange = onReduceMotionChange,
        modifier = Modifier.testTag("settings_reduce_motion")
      )
    }

    // Reprodução
    settingsGroup(context.getString(R.string.settings_playback)) {
      SwitchRow(
        title = stringResource(R.string.settings_shuffle),
        checked = isShuffle,
        onCheckedChange = { onToggleShuffle() },
        modifier = Modifier.testTag("settings_shuffle")
      )
    }
    settingsGroup(
      context.getString(R.string.settings_repeat),
      footer = context.getString(R.string.settings_repeat_footer),
      tag = "settings_repeat"
    ) {
      Column(Modifier.selectableGroup()) {
        RepeatChoices.forEachIndexed { i, (mode, label) ->
          if (i > 0) GroupDivider()
          RadioRow(
            title = stringResource(label),
            selected = repeatMode == mode,
            onClick = { if (repeatMode != mode) onSetRepeatMode(mode) },
            modifier = Modifier.testTag("settings_repeat_${mode.name.lowercase()}")
          )
        }
      }
    }

    // Áudio
    settingsGroup(
      context.getString(R.string.settings_audio),
      footer = context.getString(R.string.settings_audio_footer)
    ) {
      ListRow(
        title = stringResource(R.string.settings_audio_eq),
        subtitle = stringResource(R.string.settings_audio_eq_subtitle),
        onClick = onOpenAudio,
        modifier = Modifier.testTag("settings_open_audio")
      )
      GroupDivider()
      InfoRow(
        title = stringResource(R.string.settings_audio_output),
        value = stringResource(
          R.string.settings_audio_output_value,
          capabilities.activeRouteName,
          String.format("%.1f", capabilities.sampleRateHz / 1000f)
        )
      )
      capabilities.trackFormat?.let { format ->
        GroupDivider()
        InfoRow(title = stringResource(R.string.settings_audio_playing), value = format)
      }
    }

    // Catálogos online
    settingsGroup(
      context.getString(R.string.settings_catalogs),
      footer = context.getString(R.string.settings_catalogs_footer)
    ) {
      InfoRow(title = "Audius", value = stringResource(R.string.settings_catalog_active))
      GroupDivider()
      InfoRow(
        title = "Jamendo",
        value = if (com.example.data.jamendo.JamendoApiService.isConfigured) stringResource(R.string.settings_catalog_active)
        else stringResource(R.string.settings_catalog_no_key)
      )
      GroupDivider()
      InfoRow(title = "MusicBrainz", value = stringResource(R.string.settings_catalog_active))
    }

    // Curiosidades e assistente
    settingsGroup(
      context.getString(R.string.settings_assistant),
      footer = if (hasGeminiKey) context.getString(R.string.settings_assistant_footer_gemini)
      else context.getString(R.string.settings_assistant_footer_local)
    ) {
      val enabled = curiosityConfig?.enabled != false
      val timing = curiosityConfig?.timing
      ListRow(
        title = stringResource(R.string.settings_curiosity_title),
        value = if (!enabled) stringResource(R.string.settings_curiosity_state_off)
        else stringResource(curiosityTimingChoices.firstOrNull { it.timing == timing }?.short ?: R.string.settings_curiosity_state_on),
        onClick = onOpenCuriositySettings,
        modifier = Modifier.testTag("settings_curiosidades_musicais_card")
      )
      GroupDivider()
      InfoRow(
        title = stringResource(R.string.settings_gemini),
        value = if (hasGeminiKey) stringResource(R.string.settings_gemini_connected) else stringResource(R.string.settings_gemini_not_set)
      )
    }

    // Notificações
    settingsGroup(
      context.getString(R.string.settings_notifications),
      footer = if (notificationsOn) context.getString(R.string.settings_notifications_footer_on)
      else context.getString(R.string.settings_notifications_footer_off)
    ) {
      ListRow(
        title = stringResource(R.string.settings_notifications),
        value = if (notificationsOn) stringResource(R.string.settings_notifications_on) else stringResource(R.string.settings_notifications_off),
        onClick = {
          openSystem(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        },
        modifier = Modifier.testTag("settings_notifications")
      )
    }

    updateSettingsGroup(context.getString(R.string.settings_update_header))

    // Armazenamento
    settingsGroup(context.getString(R.string.settings_storage), footer = context.getString(R.string.settings_storage_footer)) {
      ListRow(
        title = stringResource(R.string.settings_clear_cache),
        value = cacheSize?.let { formatBytes(it) } ?: stringResource(R.string.settings_calculating),
        onClick = { onClearCache { cacheRefresh++ } },
        chevron = false,
        modifier = Modifier.testTag("settings_clear_cache")
      )
    }

    // Privacidade
    settingsGroup(
      context.getString(R.string.settings_privacy),
      footer = context.getString(R.string.settings_privacy_footer)
    ) {
      ListRow(
        title = stringResource(R.string.settings_permissions),
        subtitle = stringResource(R.string.settings_permissions_subtitle),
        onClick = { openSystem(appDetails) },
        modifier = Modifier.testTag("settings_open_permissions")
      )
      GroupDivider()
      ListRow(title = stringResource(R.string.settings_export), onClick = { exportLauncher.launch("tarkas-meus-dados.json") }, modifier = Modifier.testTag("settings_export"))
      GroupDivider()
      ListRow(title = stringResource(R.string.settings_privacy_policy), onClick = onOpenPrivacy, modifier = Modifier.testTag("settings_privacy"))
      GroupDivider()
      ListRow(title = stringResource(R.string.settings_terms), onClick = onOpenTerms, modifier = Modifier.testTag("settings_terms"))
    }
    settingsGroup(header = null, footer = context.getString(R.string.settings_erase_footer)) {
      ListRow(
        title = stringResource(R.string.settings_erase),
        onClick = { confirmErase = true },
        chevron = false,
        destructive = true,
        modifier = Modifier.testTag("settings_erase")
      )
    }

    // Sobre
    settingsGroup(context.getString(R.string.settings_about)) {
      ListRow(
        title = stringResource(R.string.settings_about_row),
        value = BuildConfig.VERSION_NAME,
        onClick = onOpenAbout,
        modifier = Modifier.testTag("settings_about")
      )
    }
  }

  // Outside the list, so a dialog never closes because its row scrolled out of composition.
  if (confirmErase) {
    AlertDialog(
      onDismissRequest = { confirmErase = false },
      title = { Text(stringResource(R.string.settings_erase_dialog_title), style = AppText.Title3) },
      text = { Text(stringResource(R.string.settings_erase_dialog_text)) },
      confirmButton = {
        TextButton(onClick = { confirmErase = false; onEraseAllData() }, modifier = Modifier.testTag("settings_erase_confirm")) {
          Text(stringResource(R.string.settings_erase_confirm), color = colors.error)
        }
      },
      dismissButton = { TextButton(onClick = { confirmErase = false }) { Text(stringResource(R.string.settings_cancel)) } }
    )
  }
}

private val RepeatChoices = listOf(
  RepeatMode.OFF to R.string.settings_repeat_off,
  RepeatMode.ALL to R.string.settings_repeat_all,
  RepeatMode.ONE to R.string.settings_repeat_one
)

@Composable
private fun AboutPage(
  termsAcceptedAt: Long,
  onBack: () -> Unit,
  onOpen: (SettingsPage) -> Unit,
  onRestartOnboarding: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current
  var copied by remember { mutableStateOf(false) }
  fun open(url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }

  LazyColumn(
    modifier = Modifier.statusBarsPadding().fillMaxSize().testTag("settings_about_page"),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    item {
      SubPageHeader(
        title = stringResource(R.string.settings_about),
        backLabel = stringResource(R.string.settings_back_settings),
        onBack = onBack,
        modifier = Modifier.testTag("about_back")
      )
    }
    item {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s)
      ) {
        TarkasMark(modifier = Modifier.size(width = 72.dp, height = 44.dp))
        Spacer(Modifier.height(Space.m))
        Text("T.A.R.K.A.S.", style = AppText.Title2, color = colors.onSurface)
        Text(
          stringResource(R.string.settings_about_tagline),
          style = AppText.Subhead,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.xs))
        // Authorship line, in English as the T.A.L. identity asks for every product's About page.
        Text(
          "Developed by Totally Accurate Labs.",
          style = AppText.Footnote,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("about_developer")
        )
      }
    }
    settingsGroup(header = null) {
      ListRow(
        title = stringResource(R.string.settings_about_version),
        value = if (copied) stringResource(R.string.settings_about_copied) else "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        chevron = false,
        onClick = {
          val cm = context.getSystemService(android.content.ClipboardManager::class.java)
          cm?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_about_clip_label), "T.A.R.K.A.S. ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}"))
          copied = true
        },
        modifier = Modifier.testTag("about_version")
      )
      GroupDivider()
      ListRow(title = stringResource(R.string.settings_about_whats_new), onClick = { onOpen(SettingsPage.CHANGELOG) }, modifier = Modifier.testTag("about_changelog"))
      GroupDivider()
      ListRow(
        title = stringResource(R.string.settings_about_all_versions),
        subtitle = stringResource(R.string.settings_about_all_versions_subtitle),
        onClick = { open(LegalTexts.RELEASES_URL) }
      )
    }
    settingsGroup(context.getString(R.string.settings_about_legal)) {
      ListRow(title = stringResource(R.string.settings_terms), onClick = { onOpen(SettingsPage.TERMS) }, modifier = Modifier.testTag("about_terms"))
      GroupDivider()
      ListRow(
        title = stringResource(R.string.settings_privacy_policy),
        subtitle = if (termsAcceptedAt > 0) {
          stringResource(
            R.string.settings_about_accepted_on,
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(termsAcceptedAt))
          )
        } else null,
        onClick = { onOpen(SettingsPage.PRIVACY) },
        modifier = Modifier.testTag("about_privacy")
      )
      GroupDivider()
      ListRow(title = stringResource(R.string.settings_about_licenses), onClick = { onOpen(SettingsPage.LICENSES) }, modifier = Modifier.testTag("about_licenses"))
    }
    settingsGroup(context.getString(R.string.settings_about_credits)) {
      LegalTexts.credits.forEachIndexed { i, (title, body) ->
        if (i > 0) GroupDivider()
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.row)
            .padding(horizontal = Space.l, vertical = Space.m)
            .semantics(mergeDescendants = true) {}
        ) {
          Text(title, style = AppText.Body, color = colors.onSurface)
          Text(body, style = AppText.Footnote, color = colors.onSurfaceVariant)
        }
      }
    }
    settingsGroup(context.getString(R.string.settings_about_contact)) {
      ListRow(
        title = stringResource(R.string.settings_about_send_email),
        subtitle = LegalTexts.CONTACT_EMAIL,
        onClick = {
          val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${LegalTexts.CONTACT_EMAIL}"))
            .putExtra(Intent.EXTRA_SUBJECT, "T.A.R.K.A.S. ${BuildConfig.VERSION_NAME}")
          runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        modifier = Modifier.testTag("about_email")
      )
      GroupDivider()
      ListRow(title = "Instagram", subtitle = LegalTexts.CONTACT_INSTAGRAM, onClick = { open(LegalTexts.INSTAGRAM_URL) })
    }
    settingsGroup(header = null, footer = context.getString(R.string.settings_about_restart_footer)) {
      ListRow(
        title = stringResource(R.string.settings_about_restart),
        onClick = onRestartOnboarding,
        modifier = Modifier.testTag("about_restart_onboarding")
      )
    }
    item {
      // The official T.A.L. logo, single colour: white on the dark pages, black on the light one.
      val onDark = colors.background.luminance() < 0.5f
      androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(
          if (onDark) com.example.R.drawable.tal_logo_negative else com.example.R.drawable.tal_logo_positive
        ),
        contentDescription = "Totally Accurate Labs",
        modifier = Modifier.fillMaxWidth().padding(top = Space.xl).size(64.dp).testTag("about_tal_logo")
      )
    }
    item {
      Text(
        "© 2026 Totally Accurate Labs · MADE BY T.A.L.",
        style = AppText.Caption1.copy(fontFamily = com.example.ui.theme.JetBrainsMono),
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.m).testTag("about_seal")
      )
    }
  }
}

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
  bytes >= 1024L -> String.format("%.0f KB", bytes / 1024f)
  else -> "$bytes B"
}

// ---- appearance choices, shared with the first-run setup so both look and read the same ----

/** The theme options, in the order shown in Ajustes and in the setup. */
internal val ThemeChoices = listOf(
  Triple("system", R.string.settings_theme_system, R.string.settings_theme_system_desc),
  Triple("light", R.string.settings_theme_light, null),
  Triple("dark", R.string.settings_theme_dark, null),
  Triple("amoled", R.string.settings_theme_amoled, R.string.settings_theme_amoled_desc)
)

/** The theme as radio rows; put inside an [InsetGroup]. */
@Composable
internal fun ThemeOptionRows(themeMode: String, onThemeMode: (String) -> Unit, tagPrefix: String) {
  Column(Modifier.selectableGroup()) {
    ThemeChoices.forEachIndexed { i, (key, label, subtitle) ->
      if (i > 0) GroupDivider()
      RadioRow(
        title = stringResource(label),
        subtitle = subtitle?.let { stringResource(it) },
        selected = themeMode == key,
        onClick = { onThemeMode(key) },
        modifier = Modifier.testTag("$tagPrefix$key")
      )
    }
  }
}

/**
 * One line each, so the three options scan quickly. Liquid glass needs Android 13; on older devices
 * it says it falls back to frosted glass instead of silently looking the same.
 */
@Composable
private fun SurfaceStyle.shortDescription(): String = stringResource(
  when (this) {
    SurfaceStyle.SOLID -> R.string.settings_surface_solid_desc
    SurfaceStyle.FROSTED -> R.string.settings_surface_frosted_desc
    SurfaceStyle.LIQUID -> if (liquidGlassSupported) R.string.settings_surface_liquid_desc else R.string.settings_surface_liquid_unsupported
  }
)

/** The floating-surface style as radio rows; put inside an [InsetGroup]. */
@Composable
internal fun SurfaceOptionRows(surfaceStyle: SurfaceStyle, onSurfaceStyle: (SurfaceStyle) -> Unit, tagPrefix: String) {
  Column(Modifier.selectableGroup()) {
    SurfaceStyle.entries.forEachIndexed { i, style ->
      if (i > 0) GroupDivider()
      RadioRow(
        title = style.label,
        subtitle = style.shortDescription(),
        selected = surfaceStyle == style,
        onClick = { onSurfaceStyle(style) },
        modifier = Modifier.testTag("$tagPrefix${style.key}")
      )
    }
  }
}

