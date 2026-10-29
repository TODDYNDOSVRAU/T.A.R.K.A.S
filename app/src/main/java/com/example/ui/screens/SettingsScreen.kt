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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.BuildConfig
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
import com.example.ui.theme.AccentColor
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
private fun SettingsPage.backLabel(): String = if (this == SettingsPage.ABOUT) "Sobre" else "Ajustes"

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
  accent: AccentColor = AccentColor.ICE,
  onAccentChange: (AccentColor) -> Unit = {},
  isShuffle: Boolean = false,
  repeatMode: RepeatMode = RepeatMode.OFF,
  onToggleShuffle: () -> Unit = {},
  onCycleRepeat: () -> Unit = {},
  onOpenAudio: () -> Unit = {},
  cacheSizeBytes: suspend () -> Long = { 0L },
  onClearCache: (onDone: () -> Unit) -> Unit = {},
  onExportData: (Uri) -> Unit = {},
  onEraseAllData: () -> Unit = {},
  onSignOutWeb: () -> Unit = {},
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
        accent, onAccentChange, isShuffle, repeatMode, onToggleShuffle,
        onSetRepeatMode = { target ->
          // Without a direct setter, step the cycle (Desligado → Lista → Faixa) until it lands on the choice.
          if (onSetRepeatMode != null) onSetRepeatMode(target)
          else repeat((target.ordinal - repeatMode.ordinal + RepeatMode.entries.size) % RepeatMode.entries.size) { onCycleRepeat() }
        },
        onOpenAudio, cacheSizeBytes, onClearCache, onExportData, onEraseAllData, onSignOutWeb,
        onOpenAbout = { page = SettingsPage.ABOUT },
        onOpenTerms = { openText(SettingsPage.TERMS, SettingsPage.MAIN) },
        onOpenPrivacy = { openText(SettingsPage.PRIVACY, SettingsPage.MAIN) }
      )
      SettingsPage.ABOUT -> AboutPage(
        termsAcceptedAt = termsAcceptedAt,
        onBack = { page = SettingsPage.MAIN },
        onOpen = { openText(it, SettingsPage.ABOUT) },
        onRestartOnboarding = onRestartOnboarding
      )
      SettingsPage.TERMS -> LegalScreen(LegalTexts.terms, textOrigin.backLabel(), onBack = ::back)
      SettingsPage.PRIVACY -> LegalScreen(LegalTexts.privacy, textOrigin.backLabel(), onBack = ::back)
      SettingsPage.LICENSES -> LegalScreen(LegalTexts.licenses, textOrigin.backLabel(), onBack = ::back)
      SettingsPage.CHANGELOG -> LegalScreen(LegalTexts.changelog, textOrigin.backLabel(), onBack = ::back)
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
  accent: AccentColor,
  onAccentChange: (AccentColor) -> Unit,
  isShuffle: Boolean,
  repeatMode: RepeatMode,
  onToggleShuffle: () -> Unit,
  onSetRepeatMode: (RepeatMode) -> Unit,
  onOpenAudio: () -> Unit,
  cacheSizeBytes: suspend () -> Long,
  onClearCache: (onDone: () -> Unit) -> Unit,
  onExportData: (Uri) -> Unit,
  onEraseAllData: () -> Unit,
  onSignOutWeb: () -> Unit,
  onOpenAbout: () -> Unit,
  onOpenTerms: () -> Unit,
  onOpenPrivacy: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current
  var confirmErase by rememberSaveable { mutableStateOf(false) }
  var confirmSignOut by rememberSaveable { mutableStateOf(false) }
  var showLogin by rememberSaveable { mutableStateOf(false) }
  var showSpotifyLogin by rememberSaveable { mutableStateOf(false) }
  // Read again when a sign-in page closes and after signing out (the cookies go away a moment later).
  var accountsRefresh by remember { mutableIntStateOf(0) }
  val youtubeSignedIn = remember(showLogin, accountsRefresh) { com.example.data.web.WebPlayerHost.isSignedIn(spotify = false) }
  val spotifySignedIn = remember(showSpotifyLogin, accountsRefresh) { com.example.data.web.WebPlayerHost.isSignedIn(spotify = true) }
  var signOuts by remember { mutableIntStateOf(0) }
  LaunchedEffect(signOuts) {
    if (signOuts > 0) {
      kotlinx.coroutines.delay(800)
      accountsRefresh++
    }
  }
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
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    // Aparência
    settingsGroup("Tema", tag = "settings_theme_card") {
      ThemeOptionRows(themeMode, onThemeModeChange, tagPrefix = "theme_chip_")
    }
    settingsGroup("Cor de destaque", footer = "Usada em botões, seleções e no que está tocando.") {
      AccentSwatches(accent, onAccentChange)
    }
    settingsGroup(
      "Barras flutuantes",
      footer = "Como são desenhadas a barra de abas e o mini player. O conteúdo das páginas é sempre sólido."
    ) {
      SurfaceOptionRows(surfaceStyle, onSurfaceStyleChange, tagPrefix = "surface_style_")
    }
    settingsGroup(
      "Animação das listas",
      footer = "Como as músicas entram na tela ao rolar as listas. Com Reduzir animações, nenhuma."
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
    settingsGroup("Acessibilidade") {
      SwitchRow(
        title = "Reduzir transparência",
        subtitle = if (AppMotion.systemBlurOff) "Ativado no Android" else "Barras sólidas, sem vidro",
        checked = reduceTransparency || AppMotion.systemBlurOff,
        enabled = !AppMotion.systemBlurOff,
        onCheckedChange = onReduceTransparencyChange,
        modifier = Modifier.testTag("settings_reduce_transparency")
      )
      GroupDivider()
      SwitchRow(
        title = "Reduzir animações",
        subtitle = if (AppMotion.systemReduced) "Ativado no Android" else "Troca de tela sem movimento",
        checked = reduceMotion || AppMotion.systemReduced,
        enabled = !AppMotion.systemReduced,
        onCheckedChange = onReduceMotionChange,
        modifier = Modifier.testTag("settings_reduce_motion")
      )
    }

    // Reprodução
    settingsGroup("Reprodução") {
      SwitchRow(
        title = "Aleatório",
        checked = isShuffle,
        onCheckedChange = { onToggleShuffle() },
        modifier = Modifier.testTag("settings_shuffle")
      )
    }
    settingsGroup(
      "Repetir",
      footer = "A música pausa sozinha quando o fone com fio ou Bluetooth desconecta, para não sair pelo alto-falante.",
      tag = "settings_repeat"
    ) {
      Column(Modifier.selectableGroup()) {
        RepeatChoices.forEachIndexed { i, (mode, label) ->
          if (i > 0) GroupDivider()
          RadioRow(
            title = label,
            selected = repeatMode == mode,
            onClick = { if (repeatMode != mode) onSetRepeatMode(mode) },
            modifier = Modifier.testTag("settings_repeat_${mode.name.lowercase()}")
          )
        }
      }
    }

    // Áudio
    settingsGroup(
      "Áudio",
      footer = "Para equalizar o Spotify, o YouTube e os outros apps, abra a aba Áudio, toque em Ajustes avançados e escolha ADB ou Root."
    ) {
      ListRow(
        title = "Equalizador e som",
        subtitle = "15 bandas ou paramétrico, com correção para o seu fone",
        onClick = onOpenAudio,
        modifier = Modifier.testTag("settings_open_audio")
      )
      GroupDivider()
      InfoRow(
        title = "Saída atual",
        value = "${capabilities.activeRouteName} · ${String.format("%.1f", capabilities.sampleRateHz / 1000f)} kHz"
      )
    }

    // Streaming
    settingsGroup(
      "Streaming",
      footer = "Com o Premium, o YouTube Music toca sem anúncios e com a tela apagada. Sem Premium, o Spotify toca no plano grátis, com anúncios. As senhas ficam só nas páginas dos serviços."
    ) {
      ListRow(
        title = "YouTube Music",
        value = if (youtubeSignedIn) "Conectado" else "Entrar",
        onClick = { showLogin = true },
        modifier = Modifier.testTag("settings_web_login")
      )
      GroupDivider()
      ListRow(
        title = "Spotify",
        value = if (spotifySignedIn) "Conectado" else "Entrar",
        onClick = { showSpotifyLogin = true },
        modifier = Modifier.testTag("settings_spotify_login")
      )
      GroupDivider()
      ListRow(
        title = "Sair das contas",
        onClick = { confirmSignOut = true },
        chevron = false,
        destructive = true,
        modifier = Modifier.testTag("settings_web_signout")
      )
    }

    // Curiosidades e assistente
    settingsGroup(
      "Curiosidades e assistente",
      footer = if (hasGeminiKey) "Mensagens que não são comandos locais vão para o Gemini."
      else "Sem uma chave do Gemini, o assistente responde só aos comandos locais."
    ) {
      val enabled = curiosityConfig?.enabled != false
      val timing = curiosityConfig?.timing
      ListRow(
        title = "Curiosidades musicais",
        value = if (!enabled) "Desligadas" else curiosityTimingChoices.firstOrNull { it.timing == timing }?.short ?: "Ligadas",
        onClick = onOpenCuriositySettings,
        modifier = Modifier.testTag("settings_curiosidades_musicais_card")
      )
      GroupDivider()
      InfoRow(title = "IA online (Gemini)", value = if (hasGeminiKey) "Conectada" else "Não configurada")
    }

    // Notificações
    settingsGroup(
      "Notificações",
      footer = if (notificationsOn) "Controles da música na tela bloqueada e na barra, com a curiosidade da faixa quando houver."
      else "Sem notificações, os controles não aparecem na tela bloqueada."
    ) {
      ListRow(
        title = "Notificações",
        value = if (notificationsOn) "Ativadas" else "Desativadas",
        onClick = {
          openSystem(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        },
        modifier = Modifier.testTag("settings_notifications")
      )
    }

    // Armazenamento
    settingsGroup("Armazenamento", footer = "Capas e a lista de fones guardadas. São baixadas de novo quando precisar.") {
      ListRow(
        title = "Limpar cache",
        value = cacheSize?.let { formatBytes(it) } ?: "Calculando…",
        onClick = { onClearCache { cacheRefresh++ } },
        chevron = false,
        modifier = Modifier.testTag("settings_clear_cache")
      )
    }

    // Privacidade
    settingsGroup(
      "Privacidade",
      footer = "Favoritos, playlists e ajustes ficam só neste aparelho. O T.A.R.K.A.S. não tem conta nem servidor próprio. A exportação segue a LGPD (acesso e portabilidade)."
    ) {
      ListRow(
        title = "Permissões",
        subtitle = "Microfone e notificações, pedidas na hora do uso",
        onClick = { openSystem(appDetails) },
        modifier = Modifier.testTag("settings_open_permissions")
      )
      GroupDivider()
      ListRow(title = "Exportar meus dados", onClick = { exportLauncher.launch("tarkas-meus-dados.json") }, modifier = Modifier.testTag("settings_export"))
      GroupDivider()
      ListRow(title = "Política de Privacidade", onClick = onOpenPrivacy, modifier = Modifier.testTag("settings_privacy"))
      GroupDivider()
      ListRow(title = "Termos de Uso", onClick = onOpenTerms, modifier = Modifier.testTag("settings_terms"))
    }
    settingsGroup(header = null, footer = "Apaga ajustes, favoritos, playlists e o cache deste aparelho, e fecha o app.") {
      ListRow(
        title = "Apagar todos os dados",
        onClick = { confirmErase = true },
        chevron = false,
        destructive = true,
        modifier = Modifier.testTag("settings_erase")
      )
    }

    // Sobre
    settingsGroup("Sobre") {
      ListRow(
        title = "Sobre o T.A.R.K.A.S.",
        value = BuildConfig.VERSION_NAME,
        onClick = onOpenAbout,
        modifier = Modifier.testTag("settings_about")
      )
    }
  }

  // Outside the list, so a dialog never closes because its row scrolled out of composition.
  if (showLogin) WebLoginDialog(onClose = { showLogin = false })
  if (showSpotifyLogin) WebLoginDialog(spotify = true, onClose = { showSpotifyLogin = false })

  if (confirmSignOut) {
    AlertDialog(
      onDismissRequest = { confirmSignOut = false },
      title = { Text("Sair das contas?", style = AppText.Title3) },
      text = { Text("As sessões do YouTube Music e do Spotify guardadas neste aparelho serão apagadas. Para tocar de novo, entre outra vez.") },
      confirmButton = {
        TextButton(onClick = {
          confirmSignOut = false
          onSignOutWeb()
          signOuts++
        }) { Text("Sair", color = colors.error) }
      },
      dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } }
    )
  }

  if (confirmErase) {
    AlertDialog(
      onDismissRequest = { confirmErase = false },
      title = { Text("Apagar todos os dados?", style = AppText.Title3) },
      text = {
        Text(
          "Favoritos, playlists, predefinições do equalizador, ajustes e o cache serão apagados deste aparelho. " +
            "Não dá para desfazer. O app fecha e, na próxima abertura, começa do zero. Para guardar uma cópia, use antes \"Exportar meus dados\"."
        )
      },
      confirmButton = {
        TextButton(onClick = { confirmErase = false; onEraseAllData() }, modifier = Modifier.testTag("settings_erase_confirm")) {
          Text("Apagar tudo", color = colors.error)
        }
      },
      dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } }
    )
  }
}

private val RepeatChoices = listOf(
  RepeatMode.OFF to "Desligado",
  RepeatMode.ALL to "Repetir a lista",
  RepeatMode.ONE to "Repetir a faixa"
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
    modifier = Modifier.fillMaxSize().testTag("settings_about_page"),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    item { SubPageHeader(title = "Sobre", backLabel = "Ajustes", onBack = onBack, modifier = Modifier.testTag("about_back")) }
    item {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s)
      ) {
        TarkasMark(modifier = Modifier.size(width = 72.dp, height = 44.dp))
        Spacer(Modifier.height(Space.m))
        Text("T.A.R.K.A.S.", style = AppText.Title2, color = colors.onSurface)
        Text(
          "Música, equalizador e curiosidades",
          style = AppText.Subhead,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    }
    settingsGroup(header = null) {
      ListRow(
        title = "Versão",
        value = if (copied) "Copiado" else "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        chevron = false,
        onClick = {
          val cm = context.getSystemService(android.content.ClipboardManager::class.java)
          cm?.setPrimaryClip(ClipData.newPlainText("Versão do T.A.R.K.A.S.", "T.A.R.K.A.S. ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}"))
          copied = true
        },
        modifier = Modifier.testTag("about_version")
      )
      GroupDivider()
      ListRow(title = "Novidades", onClick = { onOpen(SettingsPage.CHANGELOG) }, modifier = Modifier.testTag("about_changelog"))
      GroupDivider()
      ListRow(title = "Todas as versões no GitHub", subtitle = "Baixe o APK mais recente", onClick = { open(LegalTexts.RELEASES_URL) })
    }
    settingsGroup("Legal") {
      ListRow(title = "Termos de Uso", onClick = { onOpen(SettingsPage.TERMS) }, modifier = Modifier.testTag("about_terms"))
      GroupDivider()
      ListRow(
        title = "Política de Privacidade",
        subtitle = if (termsAcceptedAt > 0) "Aceita em ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(termsAcceptedAt))}" else null,
        onClick = { onOpen(SettingsPage.PRIVACY) },
        modifier = Modifier.testTag("about_privacy")
      )
      GroupDivider()
      ListRow(title = "Licenças de código aberto", onClick = { onOpen(SettingsPage.LICENSES) }, modifier = Modifier.testTag("about_licenses"))
    }
    settingsGroup("Créditos") {
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
    settingsGroup("Contato") {
      ListRow(
        title = "Enviar e-mail",
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
    settingsGroup(header = null, footer = "Mostra de novo as boas-vindas, a aparência e as curiosidades. Seus dados ficam como estão.") {
      ListRow(
        title = "Refazer configuração inicial",
        onClick = onRestartOnboarding,
        modifier = Modifier.testTag("about_restart_onboarding")
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
  Triple("system", "Automático", "Segue o tema claro ou escuro do Android"),
  Triple("light", "Claro", null),
  Triple("dark", "Escuro", null),
  Triple("amoled", "Preto (AMOLED)", "Preto puro, poupa bateria em telas OLED")
)

/** The theme as radio rows; put inside an [InsetGroup]. */
@Composable
internal fun ThemeOptionRows(themeMode: String, onThemeMode: (String) -> Unit, tagPrefix: String) {
  Column(Modifier.selectableGroup()) {
    ThemeChoices.forEachIndexed { i, (key, label, subtitle) ->
      if (i > 0) GroupDivider()
      RadioRow(
        title = label,
        subtitle = subtitle,
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
private fun SurfaceStyle.shortDescription(): String = when (this) {
  SurfaceStyle.SOLID -> "Sem transparência, o mais leve"
  SurfaceStyle.FROSTED -> "Desfoca o que passa por trás"
  SurfaceStyle.LIQUID -> if (liquidGlassSupported) "Vidro que curva o fundo, usa mais a GPU" else "Precisa do Android 13; aqui vira vidro fosco"
}

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

/**
 * The accent colours as small labelled swatches. Each one is a radio button with its name, so a
 * screen reader hears "Gelo, selecionado" and the label stays visible at every font size (it wraps
 * instead of being cut). The wallpaper colour appears on Android 12 and newer.
 */
@Composable
internal fun AccentSwatches(accent: AccentColor, onAccent: (AccentColor) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val isDark = colors.background.luminance() < 0.5f
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = Space.s, vertical = Space.s)
      .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(Space.xs)
  ) {
    AccentColor.entries.filter { it != AccentColor.WALLPAPER || AccentColor.wallpaperSupported }.forEach { a ->
      val selected = a == accent
      val wallpaper = a == AccentColor.WALLPAPER
      val swatch = when {
        wallpaper && selected -> colors.primary
        wallpaper -> colors.surfaceContainerHigh
        isDark -> a.dark
        else -> a.light
      }
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .width(72.dp)
          .clip(Shapes.s)
          .selectable(selected = selected, role = Role.RadioButton, onClick = { onAccent(a) })
          .padding(vertical = Space.s)
          .testTag("accent_${a.key}")
      ) {
        Box(
          modifier = Modifier.size(36.dp).clip(CircleShape).background(swatch),
          contentAlignment = Alignment.Center
        ) {
          val mark = if (swatch.luminance() > 0.5f) Color.Black else Color.White
          when {
            selected -> Icon(Icons.Rounded.Check, contentDescription = null, tint = mark, modifier = Modifier.size(Size.iconSmall))
            wallpaper -> Icon(Icons.Rounded.Palette, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Size.iconSmall))
          }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
          a.label,
          style = AppText.Caption1,
          color = if (selected) colors.onSurface else colors.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
