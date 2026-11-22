package com.example.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.example.ui.components.TarkasAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import com.example.ui.theme.drawLiquidGlassThumb
import com.example.ui.theme.liquidLensPill
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
import com.example.ui.layout.readingColumn
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.components.Segment
import com.example.ui.components.SegmentedTabs
import com.example.ui.components.SubPageHeader
import com.example.ui.components.SwitchRow
import com.example.ui.components.TarkasMark
import com.example.ui.legal.LegalTexts
import com.example.ui.legal.currentLegalTexts
import com.example.ui.theme.AppMotion
import com.example.ui.theme.ListAnimation
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.gridHeight
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Motion
import com.example.ui.theme.SurfaceStyle
import com.example.ui.theme.liquidGlassSupported
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.subPageTransition
import com.example.ui.viewmodel.SettingsSubPage

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
 * [subPage] is the page open inside Ajustes, kept by the host in its route stack; the host hides its own large
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
  liquidGlassOpacity: Float = com.example.ui.theme.DefaultLiquidGlassOpacity,
  onLiquidGlassOpacityChange: (Float) -> Unit = {},
  reduceMotion: Boolean = false,
  onReduceMotionChange: (Boolean) -> Unit = {},
  onListAnimationChange: (com.example.ui.theme.ListAnimation) -> Unit = {},
  reduceTransparency: Boolean = false,
  onReduceTransparencyChange: (Boolean) -> Unit = {},
  waveformBar: Boolean = false,
  onWaveformBarChange: (Boolean) -> Unit = {},
  homeMusicFirst: Boolean = true,
  onHomeMusicFirstChange: (Boolean) -> Unit = {},
  isShuffle: Boolean = false,
  repeatMode: RepeatMode = RepeatMode.OFF,
  onToggleShuffle: () -> Unit = {},
  onCycleRepeat: () -> Unit = {},
  onOpenAudio: () -> Unit = {},
  cacheSizeBytes: suspend () -> Long = { 0L },
  onClearCache: (onDone: () -> Unit) -> Unit = {},
  onExportData: (Uri) -> Unit = {},
  onRestoreData: (Uri) -> Unit = {},
  onExportDiagnostics: (Uri) -> Unit = {},
  onEraseAllData: () -> Unit = {},
  onRestartOnboarding: () -> Unit = {},
  termsAcceptedAt: Long = 0L,
  modifier: Modifier = Modifier,
  onSetRepeatMode: ((RepeatMode) -> Unit)? = null,
  subPage: SettingsSubPage? = null,
  onOpenSubPage: (SettingsSubPage) -> Unit = {},
  onSubPageBack: () -> Unit = {},
  systemColors: Boolean = false,
  onSystemColorsChange: (Boolean) -> Unit = {}
) {
  // Which page shows comes from the host's route stack, so back, the saved state and the tab bar agree.
  val page = subPage?.let { SettingsPage.valueOf(it.name) } ?: SettingsPage.MAIN
  // Where the open text was reached from, so back returns there (Terms and Privacy open from both).
  var textOrigin by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
  val reduced = rememberReducedMotion()
  // Kept here, outside the page switch, so coming back from Sobre lands where the list was left.
  val mainListState = androidx.compose.foundation.lazy.rememberLazyListState()

  fun openText(target: SettingsPage, from: SettingsPage) {
    textOrigin = from
    onOpenSubPage(SettingsSubPage.valueOf(target.name))
  }
  val back = onSubPageBack

  AnimatedContent(
    targetState = page,
    transitionSpec = { subPageTransition(forward = targetState.ordinal > initialState.ordinal, reduced = reduced) },
    label = "settingsPage",
    modifier = modifier.fillMaxSize()
  ) { shown ->
    when (shown) {
      SettingsPage.MAIN -> MainSettings(
        themeMode, capabilities, onThemeModeChange, curiosityConfig, onOpenCuriositySettings, hasGeminiKey,
        surfaceStyle, onSurfaceStyleChange, liquidGlassOpacity, onLiquidGlassOpacityChange, reduceMotion, onReduceMotionChange, onListAnimationChange, reduceTransparency, onReduceTransparencyChange,
        waveformBar, onWaveformBarChange,
        homeMusicFirst, onHomeMusicFirstChange,
        isShuffle, repeatMode, onToggleShuffle,
        onSetRepeatMode = { target ->
          // Without a direct setter, step the cycle (Desligado → Lista → Faixa) until it lands on the choice.
          if (onSetRepeatMode != null) onSetRepeatMode(target)
          else repeat((target.ordinal - repeatMode.ordinal + RepeatMode.entries.size) % RepeatMode.entries.size) { onCycleRepeat() }
        },
        onOpenAudio, cacheSizeBytes, onClearCache, onExportData, onRestoreData, onExportDiagnostics, onEraseAllData,
        onOpenAbout = { onOpenSubPage(SettingsSubPage.ABOUT) },
        onOpenTerms = { openText(SettingsPage.TERMS, SettingsPage.MAIN) },
        onOpenPrivacy = { openText(SettingsPage.PRIVACY, SettingsPage.MAIN) },
        listState = mainListState,
        systemColors = systemColors,
        onSystemColorsChange = onSystemColorsChange
      )
      SettingsPage.ABOUT -> AboutPage(
        termsAcceptedAt = termsAcceptedAt,
        onBack = back,
        onOpen = { openText(it, SettingsPage.ABOUT) },
        onRestartOnboarding = onRestartOnboarding
      )
      // These pages draw their own header, so they keep it below the status bar themselves.
      SettingsPage.TERMS -> LegalScreen(currentLegalTexts().terms, textOrigin.backLabel(), onBack = back, modifier = Modifier.statusBarsPadding())
      SettingsPage.PRIVACY -> LegalScreen(currentLegalTexts().privacy, textOrigin.backLabel(), onBack = back, modifier = Modifier.statusBarsPadding())
      SettingsPage.LICENSES -> LegalScreen(currentLegalTexts().licenses, textOrigin.backLabel(), onBack = back, modifier = Modifier.statusBarsPadding())
      SettingsPage.CHANGELOG -> LegalScreen(currentLegalTexts().changelog, textOrigin.backLabel(), onBack = back, modifier = Modifier.statusBarsPadding())
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
    Column(Modifier.padding(top = Grid.u24)) {
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
  liquidGlassOpacity: Float,
  onLiquidGlassOpacityChange: (Float) -> Unit,
  reduceMotion: Boolean,
  onReduceMotionChange: (Boolean) -> Unit,
  onListAnimationChange: (ListAnimation) -> Unit,
  reduceTransparency: Boolean,
  onReduceTransparencyChange: (Boolean) -> Unit,
  waveformBar: Boolean,
  onWaveformBarChange: (Boolean) -> Unit,
  homeMusicFirst: Boolean,
  onHomeMusicFirstChange: (Boolean) -> Unit,
  isShuffle: Boolean,
  repeatMode: RepeatMode,
  onToggleShuffle: () -> Unit,
  onSetRepeatMode: (RepeatMode) -> Unit,
  onOpenAudio: () -> Unit,
  cacheSizeBytes: suspend () -> Long,
  onClearCache: (onDone: () -> Unit) -> Unit,
  onExportData: (Uri) -> Unit,
  onRestoreData: (Uri) -> Unit,
  onExportDiagnostics: (Uri) -> Unit,
  onEraseAllData: () -> Unit,
  onOpenAbout: () -> Unit,
  onOpenTerms: () -> Unit,
  onOpenPrivacy: () -> Unit,
  listState: androidx.compose.foundation.lazy.LazyListState,
  systemColors: Boolean = false,
  onSystemColorsChange: (Boolean) -> Unit = {}
) {
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current
  val resources = LocalResources.current
  var confirmErase by rememberSaveable { mutableStateOf(false) }
  var cacheSize by remember { mutableStateOf<Long?>(null) }
  var cacheRefresh by remember { mutableIntStateOf(0) }
  LaunchedEffect(cacheRefresh) { cacheSize = cacheSizeBytes() }
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) onExportData(uri)
  }
  val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onRestoreData(uri)
  }
  val diagnosticsLauncher =rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    if (uri != null) onExportDiagnostics(uri)
  }
  val languageHeader = stringResource(R.string.settings_language)
  val languageFooter = stringResource(R.string.settings_language_footer)
  val diagnosticsHeader = stringResource(R.string.settings_diagnostics)
  val diagnosticsFooter = stringResource(R.string.settings_diagnostics_footer)
  val savedCrashes = remember { com.example.data.diagnostics.Diagnostics.crashCount() }
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

  // Row titles the search looks in, read here (not inside the list builder) so they follow the language.
  val rows1 = listOf(stringResource(R.string.settings_reduce_transparency), stringResource(R.string.settings_reduce_motion))
  val rows2 = listOf(stringResource(R.string.settings_shuffle))
  val rows3 = listOf(stringResource(R.string.settings_repeat_off), stringResource(R.string.settings_repeat_all), stringResource(R.string.settings_repeat_one))
  val rows4 = listOf(stringResource(R.string.settings_audio_eq), stringResource(R.string.settings_audio_output))
  val rows5 = listOf(stringResource(R.string.settings_curiosity_title), stringResource(R.string.settings_gemini))
  val rows6 = listOf(stringResource(R.string.settings_clear_cache))
  val rows7 = listOf(stringResource(R.string.settings_permissions), stringResource(R.string.settings_export), stringResource(R.string.settings_restore), stringResource(R.string.settings_privacy_policy), stringResource(R.string.settings_terms))
  val rows8 = listOf(stringResource(R.string.settings_home_music_first))
  val rows9 = listOf(stringResource(R.string.settings_waveform))
  val rows10 = listOf(stringResource(R.string.settings_diagnostics_export))
  val rows11 = listOf(stringResource(R.string.settings_erase))
  val rows12 = listOf(stringResource(R.string.settings_about_row))

  var layerKey by rememberSaveable { mutableStateOf(SettingsLayer.ESSENTIAL.key) }
  var query by rememberSaveable { mutableStateOf("") }
  val layer = SettingsLayer.fromKey(layerKey)
  val searching = query.isNotBlank()

  LazyColumn(
    state = listState,
    modifier = Modifier.readingColumn().fillMaxHeight(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Grid.u24)
  ) {
    item {
      Column(Modifier.padding(horizontal = Grid.margin)) {
        com.example.ui.components.SearchField(
          value = query,
          onValueChange = { query = it },
          placeholder = stringResource(R.string.settings_search_hint),
          modifier = Modifier.testTag("settings_search")
        )
        // A search looks in every layer, so the layer control only applies while the box is empty.
        SegmentedTabs(modifier = Modifier.padding(top = Grid.u12)) {
          SettingsLayer.entries.forEach { l ->
            Segment(
              label = stringResource(layerLabel(l)),
              selected = !searching && layer == l,
              onClick = { query = ""; layerKey = l.key },
              modifier = Modifier.testTag("settings_layer_${l.key}")
            )
          }
        }
      }
    }
    var shownGroups = 0
    fun group(
      groupLayer: SettingsLayer,
      header: String?,
      rows: List<String> = emptyList(),
      footer: String? = null,
      tag: String? = null,
      content: @Composable () -> Unit
    ) {
      if (!settingsGroupVisible(query, layer, groupLayer, listOfNotNull(header) + rows)) return
      shownGroups++
      settingsGroup(header, footer, tag, content)
    }

    // Aparência
    group(SettingsLayer.ESSENTIAL, resources.getString(R.string.settings_theme), tag = "settings_theme_card") {
      ThemeOptionRows(themeMode, onThemeModeChange, tagPrefix = "theme_chip_", systemColors = systemColors, onSystemColors = onSystemColorsChange)
    }
    group(
      SettingsLayer.ADVANCED,
      resources.getString(R.string.settings_floating_bars),
      footer = resources.getString(R.string.settings_floating_bars_footer)
    ) {
      SurfaceOptionRows(
        surfaceStyle = surfaceStyle,
        onSurfaceStyle = onSurfaceStyleChange,
        tagPrefix = "surface_style_",
        liquidOpacity = liquidGlassOpacity,
        onLiquidOpacity = onLiquidGlassOpacityChange
      )
    }
    // Animação das listas: chips Liquid Glass soltos sobre o fundo (sem card externo) para evitar conflito de concentricidade.
    if (settingsGroupVisible(query, layer, SettingsLayer.ADVANCED, listOf(resources.getString(R.string.settings_list_animation)))) {
      shownGroups++
      item {
        Column(Modifier.padding(top = Grid.u24)) {
          GroupHeader(resources.getString(R.string.settings_list_animation))
          androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
              .fillMaxWidth()
              .selectableGroup(),
            contentPadding = PaddingValues(horizontal = Grid.margin),
            horizontalArrangement = Arrangement.spacedBy(Grid.u6)
          ) {
            items(ListAnimation.entries.size) { idx ->
              val style = ListAnimation.entries[idx]
              com.example.ui.components.FilterChip(
                label = stringResource(style.labelRes),
                selected = AppMotion.listAnimation == style,
                onClick = { onListAnimationChange(style) },
                modifier = Modifier.testTag("list_animation_${style.key}")
              )
            }
          }
          GroupFooter(resources.getString(R.string.settings_list_animation_footer))
        }
      }
    }
    group(SettingsLayer.ESSENTIAL, resources.getString(R.string.settings_accessibility), rows = rows1) {
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

    // Idioma: Android keeps a per-app language only from version 13; below it the app follows the system.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      group(SettingsLayer.ESSENTIAL, languageHeader, footer = languageFooter, tag = "settings_language_card") { LanguageOptionRows() }
    }

    // Reprodução
    group(SettingsLayer.ESSENTIAL, resources.getString(R.string.settings_playback), rows = rows2) {
      SwitchRow(
        title = stringResource(R.string.settings_shuffle),
        checked = isShuffle,
        onCheckedChange = { onToggleShuffle() },
        modifier = Modifier.testTag("settings_shuffle")
      )
    }
    group(
      SettingsLayer.ESSENTIAL,
      resources.getString(R.string.settings_repeat),
      rows = rows3,
      footer = resources.getString(R.string.settings_repeat_footer),
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
    group(
      SettingsLayer.ESSENTIAL,
      resources.getString(R.string.settings_audio),
      rows = rows4,
      footer = resources.getString(R.string.settings_audio_footer)
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
    group(
      SettingsLayer.ADVANCED,
      resources.getString(R.string.settings_catalogs),
      rows = listOf("Audius", "Jamendo", "MusicBrainz"),
      footer = resources.getString(R.string.settings_catalogs_footer)
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
    group(
      SettingsLayer.ADVANCED,
      resources.getString(R.string.settings_assistant),
      rows = rows5,
      footer = if (hasGeminiKey) resources.getString(R.string.settings_assistant_footer_gemini)
      else resources.getString(R.string.settings_assistant_footer_local)
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
    group(
      SettingsLayer.ESSENTIAL,
      resources.getString(R.string.settings_notifications),
      footer = if (notificationsOn) resources.getString(R.string.settings_notifications_footer_on)
      else resources.getString(R.string.settings_notifications_footer_off)
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

    val updateHeader = resources.getString(R.string.settings_update_header)
    if (settingsGroupVisible(query, layer, SettingsLayer.ESSENTIAL, listOf(updateHeader))) {
      shownGroups++
      updateSettingsGroup(updateHeader)
    }

    // Armazenamento
    group(SettingsLayer.ADVANCED, resources.getString(R.string.settings_storage), rows = rows6, footer = resources.getString(R.string.settings_storage_footer)) {
      ListRow(
        title = stringResource(R.string.settings_clear_cache),
        value = cacheSize?.let { formatBytes(it) } ?: stringResource(R.string.settings_calculating),
        onClick = { onClearCache { cacheRefresh++ } },
        chevron = false,
        modifier = Modifier.testTag("settings_clear_cache")
      )
    }

    // Privacidade
    group(
      SettingsLayer.ADVANCED,
      resources.getString(R.string.settings_privacy),
      rows = rows7,
      footer = resources.getString(R.string.settings_privacy_footer)
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
      ListRow(
        title = stringResource(R.string.settings_restore),
        subtitle = stringResource(R.string.settings_restore_subtitle),
        onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        modifier = Modifier.testTag("settings_restore")
      )
      GroupDivider()
      ListRow(title = stringResource(R.string.settings_privacy_policy), onClick = onOpenPrivacy, modifier = Modifier.testTag("settings_privacy"))
      GroupDivider()
      ListRow(title = stringResource(R.string.settings_terms), onClick = onOpenTerms, modifier = Modifier.testTag("settings_terms"))
    }
    group(
      SettingsLayer.ESSENTIAL,
      resources.getString(R.string.settings_home_header),
      rows = rows8,
      footer = resources.getString(R.string.settings_home_footer)
    ) {
      SwitchRow(
        title = stringResource(R.string.settings_home_music_first),
        checked = homeMusicFirst,
        onCheckedChange = onHomeMusicFirstChange,
        modifier = Modifier.testTag("settings_home_music_first")
      )
    }
    group(
      SettingsLayer.ADVANCED,
      null,
      rows = rows9,
      footer = resources.getString(R.string.settings_waveform_footer)
    ) {
      SwitchRow(
        title = stringResource(R.string.settings_waveform),
        checked = waveformBar,
        onCheckedChange = onWaveformBarChange,
        modifier = Modifier.testTag("settings_waveform")
      )
    }
    // Diagnóstico
    group(
      SettingsLayer.ENGINEERING,
      diagnosticsHeader,
      rows = rows10,
      footer = diagnosticsFooter
    ) {
      ListRow(
        title = stringResource(R.string.settings_diagnostics_export),
        subtitle = pluralStringResource(R.plurals.settings_diagnostics_crashes, savedCrashes, savedCrashes),
        onClick = { diagnosticsLauncher.launch("tarkas-diagnostics.txt") },
        modifier = Modifier.testTag("settings_diagnostics_export")
      )
    }
    group(SettingsLayer.ADVANCED, header = null, rows = rows11, footer = resources.getString(R.string.settings_erase_footer)) {
      ListRow(
        title = stringResource(R.string.settings_erase),
        onClick = { confirmErase = true },
        chevron = false,
        destructive = true,
        modifier = Modifier.testTag("settings_erase")
      )
    }

    // Sobre
    group(SettingsLayer.ESSENTIAL, resources.getString(R.string.settings_about), rows = rows12) {
      ListRow(
        title = stringResource(R.string.settings_about_row),
        value = BuildConfig.VERSION_NAME,
        onClick = onOpenAbout,
        modifier = Modifier.testTag("settings_about")
      )
    }
    if (searching && shownGroups == 0) {
      item {
        Text(
          stringResource(R.string.settings_search_empty, query.trim()),
          style = GridText.Body,
          color = colors.onSurfaceVariant,
          modifier = Modifier.padding(start = Grid.margin, end = Grid.margin, top = Grid.u24).testTag("settings_search_empty")
        )
      }
    }
  }

  // Outside the list, so a dialog never closes because its row scrolled out of composition.
  if (confirmErase) {
    TarkasAlertDialog(
      onDismissRequest = { confirmErase = false },
      title = { Text(stringResource(R.string.settings_erase_dialog_title), style = GridText.Title) },
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

private fun layerLabel(layer: SettingsLayer): Int = when (layer) {
  SettingsLayer.ESSENTIAL -> R.string.settings_layer_essential
  SettingsLayer.ADVANCED -> R.string.settings_layer_advanced
  SettingsLayer.ENGINEERING -> R.string.settings_layer_engineering
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
  val resources = LocalResources.current
  val credits = currentLegalTexts().credits
  val contactHeader = stringResource(R.string.settings_about_contact)
  val restartFooter = stringResource(R.string.settings_about_restart_footer)
  var copied by remember { mutableStateOf(false) }
  fun open(url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }

  LazyColumn(
    modifier = Modifier.statusBarsPadding().fillMaxSize().testTag("settings_about_page"),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Grid.u24)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = Grid.u12).gridHeight()
      ) {
        // The mark sits on the grid chain: 96 x 48.
        TarkasMark(modifier = Modifier.size(width = Grid.u96, height = Grid.u48))
        Spacer(Modifier.height(Grid.u12))
        Text("T.A.R.K.A.S.", style = GridText.Title, color = colors.onSurface)
        Text(
          stringResource(R.string.settings_about_tagline),
          style = GridText.Small,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Grid.u6))
        // Authorship line, in English as the T.A.L. identity asks for every product's About page.
        Text(
          "Developed by Totally Accurate Labs.",
          style = GridText.Small,
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
          cm?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.settings_about_clip_label), "T.A.R.K.A.S. ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}"))
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
    settingsGroup(resources.getString(R.string.settings_about_legal)) {
      ListRow(title = stringResource(R.string.settings_terms), onClick = { onOpen(SettingsPage.TERMS) }, modifier = Modifier.testTag("about_terms"))
      GroupDivider()
      ListRow(
        title = stringResource(R.string.settings_privacy_policy),
        subtitle = if (termsAcceptedAt > 0) {
          stringResource(
            R.string.settings_about_accepted_on,
            java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, java.util.Locale.getDefault()).format(java.util.Date(termsAcceptedAt))
          )
        } else null,
        onClick = { onOpen(SettingsPage.PRIVACY) },
        modifier = Modifier.testTag("about_privacy")
      )
      GroupDivider()
      ListRow(title = stringResource(R.string.settings_about_licenses), onClick = { onOpen(SettingsPage.LICENSES) }, modifier = Modifier.testTag("about_licenses"))
    }
    settingsGroup(resources.getString(R.string.settings_about_credits)) {
      credits.forEachIndexed { i, (title, body) ->
        if (i > 0) GroupDivider()
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .gridHeight(min = Grid.u48)
            .padding(horizontal = Grid.inset, vertical = Grid.u6)
            .semantics(mergeDescendants = true) {}
        ) {
          Text(title, style = GridText.Body, color = colors.onSurface)
          Text(body, style = GridText.Small, color = colors.onSurfaceVariant)
        }
      }
    }
    settingsGroup(contactHeader) {
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
    settingsGroup(header = null, footer = restartFooter) {
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
        modifier = Modifier.fillMaxWidth().padding(top = Grid.u24).size(Grid.u48).testTag("about_tal_logo")
      )
    }
    item {
      Text(
        "© 2026 Totally Accurate Labs · MADE BY T.A.L.",
        style = GridText.Label,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = Grid.u12).gridHeight().padding(horizontal = Grid.margin).testTag("about_seal")
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

/**
 * The theme as radio rows; put inside an [InsetGroup]. With [onSystemColors], a last row offers the phone's
 * colours (Material You). It only appears where Android has them (12 and later): no dead switch on older phones.
 */
@Composable
internal fun ThemeOptionRows(
  themeMode: String,
  onThemeMode: (String) -> Unit,
  tagPrefix: String,
  systemColors: Boolean = false,
  onSystemColors: ((Boolean) -> Unit)? = null,
  systemColorsAvailable: Boolean = com.example.ui.theme.systemColorsSupported()
) {
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
    if (onSystemColors != null && systemColorsAvailable) {
      GroupDivider()
      SwitchRow(
        title = stringResource(R.string.settings_system_colors),
        subtitle = stringResource(R.string.settings_system_colors_desc),
        checked = systemColors,
        onCheckedChange = onSystemColors,
        modifier = Modifier.testTag("${tagPrefix}system_colors")
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SurfaceOptionRows(
  surfaceStyle: SurfaceStyle,
  onSurfaceStyle: (SurfaceStyle) -> Unit,
  tagPrefix: String,
  liquidOpacity: Float? = null,
  onLiquidOpacity: ((Float) -> Unit)? = null
) {
  val reduced = rememberReducedMotion()
  val colors = MaterialTheme.colorScheme
  Column(Modifier.selectableGroup()) {
    SurfaceStyle.entries.forEachIndexed { i, style ->
      if (i > 0) GroupDivider()
      RadioRow(
        title = stringResource(style.labelRes),
        subtitle = style.shortDescription(),
        selected = surfaceStyle == style,
        onClick = { onSurfaceStyle(style) },
        modifier = Modifier.testTag("$tagPrefix${style.key}")
      )
      if (style == SurfaceStyle.LIQUID && liquidOpacity != null && onLiquidOpacity != null) {
        AnimatedVisibility(
          visible = surfaceStyle == SurfaceStyle.LIQUID && liquidGlassSupported,
          enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn(),
          exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = Grid.inset, end = Grid.inset, bottom = Grid.inset)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                  onClick = {},
                  onDoubleClick = { onLiquidOpacity(com.example.ui.theme.DefaultLiquidGlassOpacity) }
                )
                .padding(top = Grid.u6),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(R.string.settings_surface_liquid_opacity),
                style = GridText.Small,
                color = colors.onSurfaceVariant
              )
              Text(
                text = "${kotlin.math.round(liquidOpacity * 100f).toInt()}%",
                style = GridText.Small.copy(fontFamily = com.example.ui.theme.JetBrainsMono),
                color = colors.primary,
                modifier = Modifier.testTag("liquid_opacity_value")
              )
            }
            val isDarkTheme = colors.background.luminance() < 0.5f
            val sliderBg = colors.surface
            val sliderBackdrop = rememberLayerBackdrop(
              onDraw = {
                drawRect(sliderBg)
                drawContent()
              }
            )
            Slider(
              value = liquidOpacity,
              onValueChange = onLiquidOpacity,
              valueRange = 0f..1f,
              thumb = {
                Box(
                  modifier = Modifier
                    .size(width = 34.dp, height = 22.dp)
                    .liquidLensPill(sliderBackdrop, isDark = isDarkTheme, trackAccent = colors.primary)
                )
              },
              track = { sliderState ->
                SliderDefaults.Track(
                  sliderState = sliderState,
                  drawStopIndicator = null,
                  thumbTrackGapSize = 0.dp,
                  colors = SliderDefaults.colors(
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.onSurface.copy(alpha = 0.16f)
                  ),
                  modifier = Modifier
                    .height(Grid.u6)
                    .layerBackdrop(sliderBackdrop)
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("liquid_opacity_slider")
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = stringResource(R.string.settings_surface_liquid_transparent),
                style = GridText.Small,
                color = colors.onSurfaceVariant
              )
              Text(
                text = stringResource(R.string.settings_surface_liquid_opaque),
                style = GridText.Small,
                color = colors.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

