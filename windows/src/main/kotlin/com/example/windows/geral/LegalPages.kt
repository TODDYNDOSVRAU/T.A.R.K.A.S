package com.example.windows.geral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.legal.LegalDoc
import com.example.ui.legal.LegalSet
import com.example.ui.legal.LegalTexts
import com.example.ui.legal.OpenSourceNotices
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.viewmodel.SettingsSubPage
import com.example.windows.base.DesktopClipboard
import com.example.windows.state.TarkasState

/** Licences of what the Windows build ships. The Android library list is not shown here: those libraries are not in this build. */
object WindowsLicenses {
  private const val APACHE = "Apache License 2.0"
  private const val LGPL = "GNU Lesser General Public License 2.1 or later"

  val libraries: List<Pair<String, String>> = listOf(
    "Compose Multiplatform Desktop, Material 3" to "JetBrains · $APACHE",
    "Kotlin, kotlinx.coroutines" to "JetBrains · $APACHE",
    "Room, SQLite (bundled driver)" to "Google · $APACHE",
    "org.json" to "Douglas Crockford · JSON License (public domain)",
    "JNA" to "Java Native Access contributors · $APACHE / $LGPL",
    "JLayer (MP3 decoding)" to "JavaZOOM · $LGPL. Used as a separate library; you can replace it with your own build of JLayer. Source: https://github.com/umjammer/jlayer",
    "jFLAC (FLAC decoding)" to "The jFLAC contributors · $APACHE"
  )

  /** Both languages; the non-library entries (AutoEq, SADIE, fonts, MIT, Apache) come straight from the shared texts. */
  fun doc(set: LegalSet): LegalDoc {
    val android = OpenSourceNotices.librarySections.toSet()
    return set.licenses.copy(sections = libraries + set.licenses.sections.filterNot { it in android })
  }
}

@Composable
private fun Reading(doc: LegalDoc, state: TarkasState, extra: (@Composable () -> Unit)? = null) {
  val c = MaterialTheme.colorScheme
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.readingColumn().verticalScroll(rememberScrollState()).padding(bottom = Grid.margin)) {
      SubPageHeader(doc.title, w("Configurações", "Settings"), { state.goBack() })
      Text(doc.subtitle, style = GridText.Body, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = Grid.u12))
      Column(verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
        doc.sections.forEach { (title, body) ->
          Column {
            Text(title, style = GridText.BodyStrong, color = c.onSurface)
            Text(body, style = GridText.Body, color = c.onSurfaceVariant)
          }
        }
        extra?.invoke()
      }
    }
  }
}


@Composable
fun LegalPage(page: SettingsSubPage, state: TarkasState, geral: GeralState) {
  val set = LegalTexts.forLanguage(java.util.Locale.getDefault().language)
  when (page) {
    SettingsSubPage.TERMS -> Reading(set.terms, state)
    SettingsSubPage.PRIVACY -> Reading(set.privacy, state)
    SettingsSubPage.CHANGELOG -> Reading(set.changelog, state)
    SettingsSubPage.LICENSES -> Reading(WindowsLicenses.doc(set), state)
    SettingsSubPage.ABOUT -> AboutPage(set, state, geral)
  }
}

@Composable
private fun AboutPage(set: LegalSet, state: TarkasState, geral: GeralState) {
  val c = MaterialTheme.colorScheme
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.readingColumn().verticalScroll(rememberScrollState()).padding(bottom = Grid.margin), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
      SubPageHeader("T.A.R.K.A.S.", w("Configurações", "Settings"), { state.goBack() })
      Text("Totally Accurate Real-Time Kind of Audio System", style = GridText.Body, color = c.onSurfaceVariant)
      InsetGroup {
        InfoRow(w("Versão", "Version"), Diagnostics.APP_VERSION)
        GroupDivider()
        InfoRow(w("Plataforma", "Platform"), "Windows ${System.getProperty("os.arch")}")
        GroupDivider()
        InfoRow(w("Termos aceitos em", "Terms accepted on"), geral.termsAcceptedAt?.take(10) ?: "-")
        GroupDivider()
        ListRow(w("Copiar informações", "Copy information"), chevron = false, onClick = {
          DesktopClipboard.copy("T.A.R.K.A.S. ${Diagnostics.APP_VERSION} · Windows ${System.getProperty("os.version")}")
          state.showNotice(w("Copiado.", "Copied."))
        })
      }
      GroupHeader(w("Responsável", "Controller"))
      InsetGroup {
        InfoRow(w("Controlador", "Controller"), LegalTexts.CONTROLLER)
        GroupDivider()
        InfoRow(w("Contato", "Contact"), LegalTexts.CONTACT_EMAIL)
      }
      GroupHeader(w("Créditos", "Credits"))
      InsetGroup {
        set.credits.forEachIndexed { i, (name, role) -> if (i > 0) GroupDivider(); InfoRow(name, role) }
      }
      Text("Developed by Totally Accurate Labs.", style = GridText.Small, color = c.onSurfaceVariant)
    }
  }
}
