package com.example.windows.geral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.theme.Grid
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.SquircleShape
import com.example.ui.theme.studioCard
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GridText
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SettingsSubPage
import com.example.windows.res.Res
import com.example.windows.res.settings_language
import com.example.windows.res.settings_language_en
import com.example.windows.res.settings_language_pt
import com.example.windows.res.settings_language_system
import com.example.windows.res.settings_layer_advanced
import com.example.windows.res.settings_layer_engineering
import com.example.windows.res.settings_layer_essential
import com.example.windows.res.settings_search_empty
import com.example.windows.res.settings_search_hint
import com.example.windows.res.settings_theme
import com.example.windows.res.settings_theme_amoled
import com.example.windows.res.settings_theme_dark
import com.example.windows.res.settings_theme_light
import com.example.windows.res.settings_theme_system
import com.example.windows.res.tr
import com.example.windows.state.TarkasState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import java.time.Instant

/** Everything the settings screen needs besides [TarkasState]; built once in [GeralPanels]. */
class GeralServices(
  val geral: GeralState,
  val updater: Updater,
  val autostart: Autostart,
  val diagnostics: Diagnostics,
  val backup: UserDataBackup
)

/** Settings categories, in the Windows Settings pattern: a list on the left, the category page on the right. */
internal enum class Cat(val pt: String, val en: String) {
  LOOK("Aparência", "Appearance"), LANGUAGE("Idioma", "Language"), WINDOWS("Windows", "Windows"),
  UPDATE("Atualização", "Update"), DATA("Backup e dados", "Backup and data"),
  LEGAL("Privacidade e legal", "Privacy and legal"), DIAGNOSTICS("Diagnóstico", "Diagnostics")
}

private data class Row(val keywords: String, val layer: SettingsLayer, val cat: Cat, val content: @Composable () -> Unit)

/** Which rows a page shows. A search looks through every category and layer; otherwise the chosen category at the chosen layer and below. */
internal fun <T> visibleRows(rows: List<T>, key: (T) -> Triple<String, SettingsLayer, Cat>, query: String, layer: SettingsLayer, cat: Cat): List<T> {
  val q = query.trim().lowercase()
  return rows.filter { r ->
    val (kw, l, c) = key(r)
    if (q.isNotEmpty()) kw.lowercase().contains(q) else c == cat && l.ordinal <= layer.ordinal
  }
}

@Composable
fun SettingsScreen(state: TarkasState, svc: GeralServices) {
  val geral = svc.geral
  val c = MaterialTheme.colorScheme
  val layer by geral.layer.collectAsState()
  var query by remember { mutableStateOf("") }
  var cat by remember { mutableStateOf(Cat.LOOK) }
  val groups = settingsGroups(state, svc)
  // A category with nothing at the current layer is hidden from the list, so every entry leads somewhere.
  val cats = Cat.entries.filter { k -> groups.any { it.cat == k && it.layer.ordinal <= layer.ordinal } }
  if (cat !in cats && cats.isNotEmpty()) cat = cats.first()
  val shown = visibleRows(groups, { Triple(it.keywords, it.layer, it.cat) }, query, layer, cat)
  SettingsLayout(
    cats = cats, cat = cat, onCat = { query = ""; cat = it }, query = query, onQuery = { query = it },
    layer = layer, onLayer = { geral.setLayer(it) }, empty = shown.isEmpty()
  ) { shown.forEach { it.content() } }
}

/** Sidebar (250 dp) + content card, NOVA UI. Stateless so it can be tested without [TarkasState]. */
@Composable
internal fun SettingsLayout(
  cats: List<Cat>, cat: Cat, onCat: (Cat) -> Unit, query: String, onQuery: (String) -> Unit,
  layer: SettingsLayer, onLayer: (SettingsLayer) -> Unit, empty: Boolean, content: @Composable () -> Unit
) {
  val c = MaterialTheme.colorScheme
  androidx.compose.foundation.layout.Row(
    Modifier.fillMaxSize().padding(start = Grid.margin, end = Grid.margin, top = Grid.u12, bottom = LocalBarInset.current + Grid.u12),
    horizontalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    Column(
      Modifier.testTag("settings_sidebar").width(SidebarWidth).fillMaxHeight().studioCard().padding(Grid.u6),
      verticalArrangement = Arrangement.spacedBy(Grid.u6)
    ) {
      SearchField(query, onQuery, tr(Res.string.settings_search_hint))
      cats.forEach { k ->
        val sel = query.isBlank() && k == cat
        val hover = remember { MutableInteractionSource() }
        val hovered by hover.collectIsHoveredAsState()
        val bg = when { sel -> c.primary.copy(alpha = 0.16f); hovered -> c.onSurface.copy(alpha = 0.04f); else -> Color.Transparent }
        Box(
          Modifier.testTag("settings_cat_" + k.name).fillMaxWidth().height(ItemHeight).clip(SquircleShape(Grid.u12)).background(bg)
            .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable { onCat(k) }.padding(horizontal = Grid.u12),
          contentAlignment = Alignment.CenterStart
        ) { Text(w(k.pt, k.en), style = GridText.Small.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal), color = if (sel) c.primary else c.onSurfaceVariant, maxLines = 1) }
      }
    }
    val scroll = rememberScrollState()
    Column(
      Modifier.testTag("settings_content").weight(1f).fillMaxHeight().studioCard().verticalScroll(scroll).padding(Grid.margin),
      verticalArrangement = Arrangement.spacedBy(Grid.u12)
    ) {
      androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
        Text(if (query.isBlank()) w(cat.pt, cat.en) else "“${query.trim()}”", style = GridText.Title, color = c.onSurface, modifier = Modifier.weight(1f))
        SegmentedTabs {
          Segment(tr(Res.string.settings_layer_essential), layer == SettingsLayer.ESSENTIAL, { onLayer(SettingsLayer.ESSENTIAL) })
          Segment(tr(Res.string.settings_layer_advanced), layer == SettingsLayer.ADVANCED, { onLayer(SettingsLayer.ADVANCED) })
          Segment(tr(Res.string.settings_layer_engineering), layer == SettingsLayer.ENGINEERING, { onLayer(SettingsLayer.ENGINEERING) })
        }
      }
      if (empty) Text(tr(Res.string.settings_search_empty), style = GridText.Body, color = c.onSurfaceVariant)
      content()
    }
  }
}

private val SidebarWidth = 250.dp // NOVA UI: Ajustes sidebar
private val ItemHeight = 42.dp

@Composable
private fun settingsGroups(state: TarkasState, svc: GeralServices): List<Row> {
  val geral = svc.geral
  val scope = rememberCoroutineScope()
  val theme by state.themeMode.collectAsState()
  val reduce by state.reduceMotion.collectAsState()
  val lang by geral.language.collectAsState()
  val autoUpdate by geral.autoUpdate.collectAsState()
  val tray by geral.tray.collectAsState()
  val closeToTray by geral.closeToTray.collectAsState()
  var startWithWindows by remember { mutableStateOf(false) }
  var eraseAsk by remember { mutableStateOf(false) }
  var upd by remember { mutableStateOf(UpdateState()) }
  var diagLine by remember { mutableStateOf<String?>(null) }
  var restoreLine by remember { mutableStateOf<String?>(null) }
  androidx.compose.runtime.LaunchedEffect(Unit) { startWithWindows = withContext(Dispatchers.IO) { svc.autostart.isEnabled() } }

  fun note(t: String) = state.showNotice(t)

  return listOf(
    Row("theme tema dark light escuro claro amoled", SettingsLayer.ESSENTIAL, Cat.LOOK) {
      Column {
        GroupHeader(tr(Res.string.settings_theme))
        InsetGroup {
          listOf(
            ThemeMode.SYSTEM to tr(Res.string.settings_theme_system), ThemeMode.LIGHT to tr(Res.string.settings_theme_light),
            ThemeMode.DARK to tr(Res.string.settings_theme_dark), ThemeMode.AMOLED to tr(Res.string.settings_theme_amoled)
          ).forEachIndexed { i, (mode, label) ->
            if (i > 0) GroupDivider()
            RadioRow(label, theme == mode, { state.setThemeMode(mode) })
          }
        }
      }
    },
    Row("language idioma", SettingsLayer.ESSENTIAL, Cat.LANGUAGE) {
      Column {
        GroupHeader(tr(Res.string.settings_language))
        InsetGroup {
          listOf(
            AppLanguage.SYSTEM to tr(Res.string.settings_language_system), AppLanguage.PT to tr(Res.string.settings_language_pt),
            AppLanguage.EN to tr(Res.string.settings_language_en)
          ).forEachIndexed { i, (code, label) ->
            if (i > 0) GroupDivider()
            RadioRow(label, lang == code, { geral.setLanguage(code); note(w("Idioma aplicado. Reabra o app para trocar todos os textos.", "Language applied. Reopen the app to switch every text.")) })
          }
        }
      }
    },
    Row("accessibility acessibilidade motion movimento", SettingsLayer.ESSENTIAL, Cat.LOOK) {
      Column {
        GroupHeader(w("Acessibilidade", "Accessibility"))
        InsetGroup {
          SwitchRow(w("Reduzir movimento", "Reduce motion"), reduce, state::setReduceMotion, subtitle = w("Troca animações por mudanças diretas.", "Replaces animations with direct changes."))
        }
      }
    },
    Row("update atualização installer instalador version versão", SettingsLayer.ESSENTIAL, Cat.UPDATE) {
      Column {
        GroupHeader(w("Atualização", "Update"))
        InsetGroup {
          InfoRow(w("Versão instalada", "Installed version"), Diagnostics.APP_VERSION)
          GroupDivider()
          InfoRow(
            w("Última verificação", "Last check"),
            geral.lastUpdateCheck.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString().take(16).replace('T', ' ') } ?: w("nunca", "never")
          )
          GroupDivider()
          SwitchRow(w("Verificar ao abrir", "Check on start"), autoUpdate, geral::setAutoUpdate, subtitle = w("Consulta o GitHub; nunca instala sozinho.", "Asks GitHub; never installs by itself."))
          GroupDivider()
          val label = when (upd.phase) {
            UpdateState.Phase.CHECKING -> w("Verificando…", "Checking…")
            UpdateState.Phase.UP_TO_DATE -> w("Você está na versão mais recente", "You are on the latest version")
            UpdateState.Phase.AVAILABLE -> w("Baixar e instalar ${upd.release?.versionName}", "Download and install ${upd.release?.versionName}")
            UpdateState.Phase.DOWNLOADING -> w("Baixando… ${(upd.progress * 100).toInt()}%", "Downloading… ${(upd.progress * 100).toInt()}%")
            UpdateState.Phase.READY -> w("Instalar agora", "Install now")
            UpdateState.Phase.INSTALLING -> w("Abrindo o instalador…", "Opening the installer…")
            else -> upd.problem?.message ?: w("Verificar agora", "Check now")
          }
          ListRow(label, onClick = {
            scope.launch {
              when (upd.phase) {
                UpdateState.Phase.AVAILABLE -> {
                  val rel = upd.release ?: return@launch
                  upd = upd.copy(phase = UpdateState.Phase.DOWNLOADING, progress = 0f, problem = null)
                  val (file, problem) = withContext(Dispatchers.IO) { svc.updater.download(rel, { p -> upd = upd.copy(progress = p) }) }
                  upd = if (file != null) upd.copy(phase = UpdateState.Phase.READY, problem = null) .also { readyFile = file } else UpdateState(UpdateState.Phase.AVAILABLE, rel, problem = problem)
                }
                UpdateState.Phase.READY -> {
                  if (state.isPlaying.value) { upd = upd.copy(problem = UpdateProblem.BUSY_PLAYING); return@launch }
                  val f = readyFile ?: return@launch
                  upd = upd.copy(phase = UpdateState.Phase.INSTALLING)
                  if (!svc.updater.install(f)) upd = upd.copy(phase = UpdateState.Phase.READY, problem = UpdateProblem.INSTALL_FAILED)
                }
                UpdateState.Phase.CHECKING, UpdateState.Phase.DOWNLOADING, UpdateState.Phase.INSTALLING -> Unit
                else -> {
                  upd = UpdateState(UpdateState.Phase.CHECKING)
                  upd = withContext(Dispatchers.IO) { svc.updater.check() }
                  if (upd.lastCheckedAt > 0) geral.lastUpdateCheck = upd.lastCheckedAt
                }
              }
            }
          })
          upd.problem?.let { GroupDivider(); Text(it.message, style = GridText.Small, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Grid.inset)) }
        }
        GroupFooter(w("Instalador MSI ou EXE do GitHub, conferido pelo SHA-256 antes de abrir.", "MSI or EXE installer from GitHub, checked against its SHA-256 before it opens."))
      }
    },
    Row("windows tray bandeja autostart iniciar", SettingsLayer.ADVANCED, Cat.WINDOWS) {
      Column {
        GroupHeader("Windows")
        InsetGroup {
          val canAutostart = svc.autostart.installedExe() != null
          SwitchRow(
            w("Iniciar com o Windows", "Start with Windows"), startWithWindows,
            { on -> scope.launch { val ok = withContext(Dispatchers.IO) { svc.autostart.set(on) }; startWithWindows = if (ok) on else startWithWindows; if (!ok) note(w("Só o app instalado pode se registrar para iniciar com o Windows.", "Only the installed app can register to start with Windows.")) } },
            subtitle = if (canAutostart) w("Abre minimizado na bandeja.", "Opens minimised to the tray.") else w("Disponível no app instalado.", "Available in the installed app.")
          )
          GroupDivider()
          SwitchRow(w("Ícone na bandeja", "Tray icon"), tray, geral::setTray)
          GroupDivider()
          SwitchRow(w("Fechar para a bandeja", "Close to tray"), closeToTray, geral::setCloseToTray, enabled = tray, subtitle = w("O botão fechar esconde a janela; a música continua.", "The close button hides the window; music keeps playing."))
        }
      }
    },
    Row("backup export restore exportar restaurar dados", SettingsLayer.ADVANCED, Cat.DATA) {
      Column {
        GroupHeader(w("Seus dados", "Your data"))
        InsetGroup {
          ListRow(w("Exportar dados", "Export data"), subtitle = w("Biblioteca, listas, favoritos, estatísticas e ajustes em um arquivo.", "Library, playlists, favourites, stats and settings in one file."), onClick = {
            scope.launch {
              val file = pickFile(FileDialog.SAVE, "tarkas-backup.json") ?: return@launch
              val json = withContext(Dispatchers.IO) { svc.backup.export(Diagnostics.APP_VERSION, Instant.now().toString()).toString(2) }
              withContext(Dispatchers.IO) { file.writeText(json) }
              restoreLine = w("Exportado para ${file.name}", "Exported to ${file.name}")
            }
          })
          GroupDivider()
          ListRow(w("Restaurar dados", "Restore data"), subtitle = w("Substitui os dados atuais. Reabra o app depois.", "Replaces the current data. Reopen the app afterwards."), onClick = {
            scope.launch {
              val file = pickFile(FileDialog.LOAD, "*.json") ?: return@launch
              restoreLine = try {
                withContext(Dispatchers.IO) { svc.backup.restore(file.readText()) }
                w("Restaurado. Reabra o app.", "Restored. Reopen the app.")
              } catch (e: RestoreException) {
                w("Arquivo recusado, nada foi alterado: ${e.reason}", "File refused, nothing was changed: ${e.reason}")
              }
            }
          })
          restoreLine?.let { GroupDivider(); Text(it, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Grid.inset)) }
        }
      }
    },
    Row("privacy privacidade terms termos legal licenses licenças about sobre", SettingsLayer.ESSENTIAL, Cat.LEGAL) {
      Column {
        GroupHeader(w("Privacidade e legal", "Privacy and legal"))
        InsetGroup {
          ListRow(w("Termos de uso", "Terms of use"), onClick = { state.openSettingsPage(SettingsSubPage.TERMS) })
          GroupDivider()
          ListRow(w("Política de privacidade", "Privacy policy"), onClick = { state.openSettingsPage(SettingsSubPage.PRIVACY) })
          GroupDivider()
          ListRow(w("Licenças de código aberto", "Open-source licences"), onClick = { state.openSettingsPage(SettingsSubPage.LICENSES) })
          GroupDivider()
          ListRow(w("Novidades", "What's new"), onClick = { state.openSettingsPage(SettingsSubPage.CHANGELOG) })
          GroupDivider()
          ListRow(w("Sobre o T.A.R.K.A.S.", "About T.A.R.K.A.S."), onClick = { state.openSettingsPage(SettingsSubPage.ABOUT) })
        }
      }
    },
    Row("erase apagar dados reset", SettingsLayer.ADVANCED, Cat.DATA) {
      Column {
        GroupHeader(w("Apagar", "Erase"))
        InsetGroup { ListRow(w("Apagar todos os dados", "Erase all data"), destructive = true, chevron = false, onClick = { eraseAsk = true }) }
        GroupFooter(w("Remove biblioteca, listas, favoritos e ajustes deste computador. Seus arquivos de música não são tocados.", "Removes library, playlists, favourites and settings from this computer. Your music files are not touched."))
      }
    },
    Row("diagnostics diagnóstico crash log engineering engenharia", SettingsLayer.ENGINEERING, Cat.DIAGNOSTICS) {
      Column {
        GroupHeader(w("Diagnóstico", "Diagnostics"))
        InsetGroup {
          InfoRow(w("Sistema", "System"), svc.diagnostics.header())
          GroupDivider()
          InfoRow(w("Quedas registradas", "Recorded crashes"), svc.diagnostics.store.crashes().size.toString())
          GroupDivider()
          ListRow(w("Exportar relatório", "Export report"), subtitle = w("Sem músicas, listas nem chaves.", "No music, playlists or keys."), onClick = {
            scope.launch {
              val file = pickFile(FileDialog.SAVE, "tarkas-diagnostico.txt") ?: return@launch
              withContext(Dispatchers.IO) { svc.diagnostics.export(file) }
              diagLine = w("Relatório salvo em ${file.name}", "Report saved to ${file.name}")
            }
          })
          diagLine?.let { GroupDivider(); Text(it, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Grid.inset)) }
          GroupDivider()
          ListRow(w("Refazer a introdução", "Redo the introduction"), onClick = { geral.redoOnboarding(); note(w("A introdução volta na próxima abertura.", "The introduction returns on next start.")) })
        }
        GroupFooter(w("Pasta: ${svc.diagnostics.dir.absolutePath}", "Folder: ${svc.diagnostics.dir.absolutePath}"))
      }
    }
  ).also {
    if (eraseAsk) AlertDialog(
      onDismissRequest = { eraseAsk = false },
      title = { Text(w("Apagar todos os dados?", "Erase all data?")) },
      text = { Text(w("Isso não pode ser desfeito. Exporte antes se quiser guardar uma cópia.", "This cannot be undone. Export first if you want a copy.")) },
      confirmButton = { TextButton(onClick = { eraseAsk = false; scope.launch { withContext(Dispatchers.IO) { svc.backup.restore(EMPTY_EXPORT) }; note(w("Dados apagados. Reabra o app.", "Data erased. Reopen the app.")) } }) { Text(w("Apagar", "Erase"), color = MaterialTheme.colorScheme.error) } },
      dismissButton = { TextButton(onClick = { eraseAsk = false }) { Text(w("Cancelar", "Cancel")) } }
    )
  }
}

private var readyFile: File? = null

/** A valid export with nothing in it: restoring it is how everything is erased through one checked path. */
private const val EMPTY_EXPORT =
  """{"app":"T.A.R.K.A.S.","format":2,"tracks":[],"favorites":[],"playlists":[],"playStats":[],"playback":null,"settings":[]}"""

private fun pickFile(mode: Int, name: String): File? {
  val d = FileDialog(null as java.awt.Frame?, if (mode == FileDialog.SAVE) "Salvar" else "Abrir", mode).apply { file = name; isVisible = true }
  return d.file?.let { File(d.directory, it) }
}
