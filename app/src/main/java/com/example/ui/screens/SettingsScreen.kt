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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.example.BuildConfig
import com.example.data.model.AudioCapabilities
import com.example.data.model.CuriosityConfig
import com.example.data.model.RepeatMode
import com.example.ui.components.TarkasMark
import com.example.ui.components.TarkasSectionTitle
import com.example.ui.legal.LegalTexts
import com.example.ui.theme.AccentColor
import com.example.ui.theme.AppMotion
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.SurfaceStyle
import com.example.ui.theme.glassContentColor
import com.example.ui.theme.glassControl
import com.example.ui.theme.liquidGlassSupported
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.subPageTransition

/** The pages inside Ajustes: the main list, Sobre, and the texts opened from them. */
private enum class SettingsPage { MAIN, ABOUT, TERMS, PRIVACY, LICENSES, CHANGELOG }

/**
 * Ajustes, organised like the settings of the big music apps: Aparência, Reprodução, Áudio,
 * Curiosidades e assistente, Notificações, Armazenamento, Privacidade and Sobre. Every row either
 * does something or says plainly that it is information. Sobre and the long texts open as sub-pages.
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
  onRestartOnboarding: () -> Unit = {},
  termsAcceptedAt: Long = 0L,
  modifier: Modifier = Modifier
) {
  var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
  val reduced = rememberReducedMotion()
  BackHandler(enabled = page != SettingsPage.MAIN) {
    page = if (page == SettingsPage.ABOUT) SettingsPage.MAIN else SettingsPage.ABOUT
  }

  AnimatedContent(
    targetState = page,
    transitionSpec = { subPageTransition(forward = targetState.ordinal > initialState.ordinal, reduced = reduced) },
    label = "settingsPage",
    modifier = modifier.fillMaxSize()
  ) { shown ->
    when (shown) {
      SettingsPage.MAIN -> MainSettings(
        themeMode, capabilities, onThemeModeChange, curiosityConfig, onOpenCuriositySettings, hasGeminiKey,
        surfaceStyle, onSurfaceStyleChange, reduceMotion, onReduceMotionChange, reduceTransparency, onReduceTransparencyChange,
        accent, onAccentChange, isShuffle, repeatMode, onToggleShuffle, onCycleRepeat, onOpenAudio,
        cacheSizeBytes, onClearCache, onExportData, onEraseAllData,
        onOpenAbout = { page = SettingsPage.ABOUT },
        onOpenTerms = { page = SettingsPage.TERMS },
        onOpenPrivacy = { page = SettingsPage.PRIVACY }
      )
      SettingsPage.ABOUT -> AboutPage(
        termsAcceptedAt = termsAcceptedAt,
        onBack = { page = SettingsPage.MAIN },
        onOpen = { page = it },
        onRestartOnboarding = onRestartOnboarding
      )
      SettingsPage.TERMS -> LegalScreen(LegalTexts.terms, "Sobre", onBack = { page = SettingsPage.ABOUT })
      SettingsPage.PRIVACY -> LegalScreen(LegalTexts.privacy, "Sobre", onBack = { page = SettingsPage.ABOUT })
      SettingsPage.LICENSES -> LegalScreen(LegalTexts.licenses, "Sobre", onBack = { page = SettingsPage.ABOUT })
      SettingsPage.CHANGELOG -> LegalScreen(LegalTexts.changelog, "Sobre", onBack = { page = SettingsPage.ABOUT })
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
  reduceTransparency: Boolean,
  onReduceTransparencyChange: (Boolean) -> Unit,
  accent: AccentColor,
  onAccentChange: (AccentColor) -> Unit,
  isShuffle: Boolean,
  repeatMode: RepeatMode,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onOpenAudio: () -> Unit,
  cacheSizeBytes: suspend () -> Long,
  onClearCache: (onDone: () -> Unit) -> Unit,
  onExportData: (Uri) -> Unit,
  onEraseAllData: () -> Unit,
  onOpenAbout: () -> Unit,
  onOpenTerms: () -> Unit,
  onOpenPrivacy: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current
  val hairline = colors.outline.copy(alpha = 0.4f)
  var confirmErase by remember { mutableStateOf(false) }
  var cacheSize by remember { mutableStateOf<Long?>(null) }
  var cacheRefresh by remember { mutableStateOf(0) }
  LaunchedEffect(cacheRefresh) { cacheSize = cacheSizeBytes() }
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) onExportData(uri)
  }
  fun openSystem(intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }
  val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", context.packageName, null))
  val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {

    // Aparência
    item { SectionTitle("Aparência") }
    item {
      SettingsGroup(modifier = Modifier.testTag("settings_theme_card")) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(
            Triple("amoled", "AMOLED", Icons.Default.Brightness1),
            Triple("dark", "Escuro", Icons.Default.DarkMode),
            Triple("light", "Claro", Icons.Default.LightMode),
            Triple("system", "Sistema", Icons.Default.SettingsBrightness)
          ).forEach { (modeKey, label, icon) ->
            val selected = themeMode == modeKey
            Column(
              modifier = Modifier
                .weight(1f)
                .glassControl(RoundedCornerShape(18.dp), selected = selected)
                .clickable { onThemeModeChange(modeKey) }
                .padding(vertical = 12.dp)
                .testTag("theme_chip_$modeKey"),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(icon, contentDescription = null, tint = glassContentColor(selected), modifier = Modifier.size(22.dp))
              Spacer(Modifier.height(6.dp))
              Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = glassContentColor(selected), maxLines = 1)
            }
          }
        }
        HorizontalDivider(color = hairline)
        Column(Modifier.padding(vertical = 14.dp)) {
          Text("Cor de destaque", fontSize = 17.sp, color = colors.onSurface)
          Spacer(Modifier.height(10.dp))
          AccentPicker(accent, onAccentChange)
        }
        HorizontalDivider(color = hairline)
        SurfaceStyle.entries.forEach { style ->
          val selected = surfaceStyle == style
          val unavailable = style == SurfaceStyle.LIQUID && !liquidGlassSupported
          SettingsRow(
            title = style.label,
            subtitle = if (unavailable) "${style.description} Neste aparelho, aparece como vidro fosco." else style.description,
            titleColor = if (selected) colors.primary else colors.onSurface,
            onClick = { onSurfaceStyleChange(style) },
            modifier = Modifier.testTag("surface_style_${style.key}"),
            trailing = { if (selected) Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = colors.primary) }
          )
          HorizontalDivider(color = hairline)
        }
        SwitchRow(
          title = "Reduzir transparência",
          subtitle = if (AppMotion.systemBlurOff) "O sistema já pediu menos transparência, e o app segue." else "Barras e cápsulas sólidas, sem vidro. Mais fácil de ler e mais leve.",
          checked = reduceTransparency || AppMotion.systemBlurOff,
          enabled = !AppMotion.systemBlurOff,
          onChange = onReduceTransparencyChange,
          tag = "settings_reduce_transparency"
        )
        HorizontalDivider(color = hairline)
        SwitchRow(
          title = "Reduzir animações",
          subtitle = if (AppMotion.systemReduced) "As animações estão removidas no sistema, e o app segue essa configuração." else "Pula a abertura e troca de tela sem movimento, só no app.",
          checked = reduceMotion || AppMotion.systemReduced,
          enabled = !AppMotion.systemReduced,
          onChange = onReduceMotionChange,
          tag = "settings_reduce_motion"
        )
      }
    }

    // Reprodução
    item { SectionTitle("Reprodução") }
    item {
      SettingsGroup {
        SwitchRow(
          title = "Aleatório",
          subtitle = "Toca a lista em ordem aleatória. Também no botão do player.",
          checked = isShuffle,
          onChange = { onToggleShuffle() },
          tag = "settings_shuffle"
        )
        HorizontalDivider(color = hairline)
        SettingsRow(
          title = "Repetir",
          subtitle = when (repeatMode) {
            RepeatMode.OFF -> "Desligado. Toque para repetir a lista toda."
            RepeatMode.ALL -> "Repetindo a lista toda. Toque para repetir só uma faixa."
            RepeatMode.ONE -> "Repetindo uma faixa. Toque para desligar."
          },
          onClick = onCycleRepeat,
          modifier = Modifier.testTag("settings_repeat")
        )
        HorizontalDivider(color = hairline)
        SettingsRow(
          title = "Ao desconectar o fone",
          subtitle = "A música pausa sozinha quando o fone com fio ou Bluetooth desconecta, para não tocar no alto-falante."
        )
      }
    }

    // Áudio
    item { SectionTitle("Áudio") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "Equalizador",
          subtitle = "Gráfico de 15 bandas ou paramétrico, com correção para o seu fone. Começa em 0 dB (Flat).",
          onClick = onOpenAudio,
          chevron = true,
          modifier = Modifier.testTag("settings_open_audio")
        )
        HorizontalDivider(color = hairline)
        SettingsRow(title = "Saída atual", subtitle = "${capabilities.activeRouteName} • ${String.format("%.1f", capabilities.sampleRateHz / 1000f)} kHz")
        HorizontalDivider(color = hairline)
        SettingsRow(
          title = "Áudio de outros apps",
          subtitle = "Para equalizar Spotify, YouTube e os outros apps, escolha ADB ou Root em Áudio > Ajustes avançados.",
          onClick = onOpenAudio,
          chevron = true
        )
      }
    }

    // Curiosidades e assistente
    item { SectionTitle("Curiosidades e assistente") }
    item {
      SettingsGroup {
        val enabled = curiosityConfig?.enabled != false
        SettingsRow(
          title = "Curiosidades musicais",
          subtitle = "${if (enabled) "Ativadas" else "Desativadas"} • ${curiosityConfig?.timing?.label ?: "Ao final da música"}",
          onClick = onOpenCuriositySettings,
          chevron = true,
          modifier = Modifier.testTag("settings_curiosidades_musicais_card")
        )
        HorizontalDivider(color = hairline)
        SettingsRow(
          title = "IA online (Gemini)",
          subtitle = if (hasGeminiKey) "Conectada. Mensagens que não são comandos locais vão para o Gemini." else "Não configurada. O assistente responde só com comandos locais."
        )
      }
    }

    // Notificações
    item { SectionTitle("Notificações") }
    item {
      SettingsGroup {
        SettingsRow(
          title = if (notificationsOn) "Notificações ligadas" else "Notificações desligadas",
          subtitle = if (notificationsOn) "Controles da música na tela bloqueada e na barra, com a curiosidade da faixa quando houver. Toque para ajustar no sistema."
          else "Sem os controles na tela bloqueada. Toque para ligar nas configurações do Android.",
          onClick = {
            openSystem(
              Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
          },
          chevron = true,
          modifier = Modifier.testTag("settings_notifications")
        )
      }
    }

    // Armazenamento
    item { SectionTitle("Armazenamento") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "Limpar cache",
          subtitle = "Capas e lista de fones guardadas: ${cacheSize?.let { formatBytes(it) } ?: "calculando…"}. Elas voltam a ser baixadas quando precisar.",
          onClick = { onClearCache { cacheRefresh++ } },
          modifier = Modifier.testTag("settings_clear_cache")
        )
      }
    }

    // Privacidade
    item { SectionTitle("Privacidade") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "Dados neste aparelho",
          subtitle = "Favoritos, playlists e ajustes ficam só aqui. O T.A.R.K.A.S. não tem conta nem servidor próprio."
        )
        HorizontalDivider(color = hairline)
        SettingsRow(
          title = "Permissões",
          subtitle = "Microfone (espectro ao vivo) e notificações. O app pede cada uma na hora de usar. Toque para revisar.",
          onClick = { openSystem(appDetails) },
          chevron = true,
          modifier = Modifier.testTag("settings_open_permissions")
        )
        HorizontalDivider(color = hairline)
        SettingsRow(
          title = "Exportar meus dados",
          subtitle = "Salva um arquivo com seus ajustes, favoritos e playlists (LGPD: acesso e portabilidade).",
          onClick = { exportLauncher.launch("tarkas-meus-dados.json") },
          chevron = true,
          modifier = Modifier.testTag("settings_export")
        )
        HorizontalDivider(color = hairline)
        SettingsRow(title = "Política de Privacidade", onClick = onOpenPrivacy, chevron = true, modifier = Modifier.testTag("settings_privacy"))
        HorizontalDivider(color = hairline)
        SettingsRow(title = "Termos de Uso", onClick = onOpenTerms, chevron = true)
        HorizontalDivider(color = hairline)
        SettingsRow(
          title = "Apagar todos os dados",
          subtitle = "Apaga ajustes, favoritos, playlists e o cache deste aparelho, e fecha o app.",
          titleColor = colors.error,
          onClick = { confirmErase = true },
          modifier = Modifier.testTag("settings_erase")
        )
      }
    }

    // Sobre
    item { SectionTitle("Sobre") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "T.A.R.K.A.S.",
          subtitle = "Versão ${BuildConfig.VERSION_NAME} • licenças, novidades e contato",
          onClick = onOpenAbout,
          chevron = true,
          modifier = Modifier.testTag("settings_about")
        )
      }
    }
  }

  if (confirmErase) {
    AlertDialog(
      onDismissRequest = { confirmErase = false },
      title = { Text("Apagar todos os dados?") },
      text = {
        Text(
          "Favoritos, playlists, predefinições do equalizador, ajustes e o cache serão apagados deste aparelho. " +
            "Isso não pode ser desfeito. O app vai fechar, e na próxima abertura começa do zero. Se quiser guardar uma cópia, use antes \"Exportar meus dados\"."
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

@Composable
private fun AboutPage(
  termsAcceptedAt: Long,
  onBack: () -> Unit,
  onOpen: (SettingsPage) -> Unit,
  onRestartOnboarding: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current
  val hairline = colors.outline.copy(alpha = 0.4f)
  var copied by remember { mutableStateOf(false) }
  fun open(url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("settings_about_page"),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    item {
      Row(
        modifier = Modifier
          .heightIn(min = 48.dp)
          .clip(RoundedCornerShape(12.dp))
          .clickable(onClick = onBack)
          .padding(end = 12.dp)
          .testTag("about_back"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.ChevronLeft, contentDescription = "Voltar", tint = colors.primary, modifier = Modifier.size(32.dp))
        Text("Ajustes", fontSize = 20.sp, color = colors.primary)
      }
    }
    item {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Box(
          modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(colors.primary, colors.primary.copy(alpha = 0.45f)))),
          contentAlignment = Alignment.Center
        ) {
          TarkasMark(modifier = Modifier.size(width = 58.dp, height = 35.dp), symbolColor = Color.White, accentColor = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text("T.A.R.K.A.S.", fontWeight = FontWeight.Bold, fontSize = 26.sp, color = colors.onBackground)
        Text("Música, equalizador e curiosidades", fontSize = 15.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
      }
    }
    item {
      SettingsGroup {
        SettingsRow(
          title = "Versão",
          subtitle = if (copied) "Copiado" else "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · toque para copiar",
          onClick = {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("Versão do T.A.R.K.A.S.", "T.A.R.K.A.S. ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}"))
            copied = true
          },
          modifier = Modifier.testTag("about_version")
        )
        HorizontalDivider(color = hairline)
        SettingsRow(title = "Novidades", subtitle = "O que mudou em cada versão", onClick = { onOpen(SettingsPage.CHANGELOG) }, chevron = true, modifier = Modifier.testTag("about_changelog"))
        HorizontalDivider(color = hairline)
        SettingsRow(title = "Todas as versões no GitHub", subtitle = "Baixe o APK mais recente", onClick = { open(LegalTexts.RELEASES_URL) }, chevron = true)
      }
    }
    item { SectionTitle("Legal") }
    item {
      SettingsGroup {
        SettingsRow(title = "Termos de Uso", onClick = { onOpen(SettingsPage.TERMS) }, chevron = true, modifier = Modifier.testTag("about_terms"))
        HorizontalDivider(color = hairline)
        SettingsRow(
          title = "Política de Privacidade",
          subtitle = if (termsAcceptedAt > 0) "Aceita em ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(termsAcceptedAt))}" else null,
          onClick = { onOpen(SettingsPage.PRIVACY) },
          chevron = true,
          modifier = Modifier.testTag("about_privacy")
        )
        HorizontalDivider(color = hairline)
        SettingsRow(title = "Licenças de código aberto", onClick = { onOpen(SettingsPage.LICENSES) }, chevron = true, modifier = Modifier.testTag("about_licenses"))
      }
    }
    item { SectionTitle("Créditos") }
    item {
      SettingsGroup {
        LegalTexts.credits.forEachIndexed { i, (title, body) ->
          if (i > 0) HorizontalDivider(color = hairline)
          SettingsRow(title = title, subtitle = body)
        }
      }
    }
    item { SectionTitle("Contato") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "Enviar e-mail",
          subtitle = LegalTexts.CONTACT_EMAIL,
          onClick = {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${LegalTexts.CONTACT_EMAIL}"))
              .putExtra(Intent.EXTRA_SUBJECT, "T.A.R.K.A.S. ${BuildConfig.VERSION_NAME}")
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
          },
          chevron = true,
          modifier = Modifier.testTag("about_email")
        )
        HorizontalDivider(color = hairline)
        SettingsRow(title = "Instagram", subtitle = LegalTexts.CONTACT_INSTAGRAM, onClick = { open(LegalTexts.INSTAGRAM_URL) }, chevron = true)
      }
    }
    item { SectionTitle("Configuração") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "Refazer configuração inicial",
          subtitle = "Mostra de novo as boas-vindas, a aparência e as curiosidades. Seus dados ficam como estão.",
          onClick = onRestartOnboarding,
          chevron = true,
          modifier = Modifier.testTag("about_restart_onboarding")
        )
      }
    }
  }
}

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
  bytes >= 1024L -> String.format("%.0f KB", bytes / 1024f)
  else -> "$bytes B"
}

@Composable
private fun SectionTitle(text: String) {
  TarkasSectionTitle(text, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

/** Flat list between hairlines, like an Apple Music list: no card behind the rows. */
@Composable
private fun SettingsGroup(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Column(modifier = modifier.fillMaxWidth()) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    content()
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
  }
}

/**
 * A row of text. Tappable rows have [onClick] (and a chevron when they open something); rows
 * without it are information and do not react to touch.
 */
@Composable
private fun SettingsRow(
  title: String,
  subtitle: String? = null,
  onClick: (() -> Unit)? = null,
  chevron: Boolean = false,
  titleColor: Color = MaterialTheme.colorScheme.onSurface,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
      .padding(vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontSize = 17.sp, color = titleColor)
      if (subtitle != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = subtitle, fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (trailing != null) {
      Spacer(modifier = Modifier.width(12.dp))
      trailing()
    }
    if (chevron) {
      Spacer(modifier = Modifier.width(8.dp))
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun SwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
  tag: String,
  enabled: Boolean = true
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled) { onChange(!checked) }
      .padding(vertical = 14.dp)
      .testTag(tag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
      Text(subtitle, fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(modifier = Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
  }
}
