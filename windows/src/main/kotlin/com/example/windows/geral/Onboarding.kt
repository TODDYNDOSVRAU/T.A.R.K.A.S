package com.example.windows.geral

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.legal.LegalTexts
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.ThemeMode
import com.example.windows.state.TarkasState
import kotlinx.coroutines.delay
import java.time.Instant

/** Splash: the fixed signature, shown briefly at start. Motion 150-300 ms with deceleration; with reduced motion it only shows. */
@Composable
fun BootScreen(reduceMotion: Boolean, onDone: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val a = remember { Animatable(if (reduceMotion) 1f else 0f) }
  LaunchedEffect(Unit) {
    if (!reduceMotion) a.animateTo(1f, tween(300))
    delay(if (reduceMotion) 600 else 900)
    onDone()
  }
  Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(a.value)) {
      Text("T.A.R.K.A.S.", style = GridText.Title, color = c.onBackground)
      Text("MADE BY T.A.L.", style = GridText.Label, color = c.onSurfaceVariant, modifier = Modifier.padding(top = Grid.u12))
    }
  }
}

private const val STEPS = 5 // welcome, look, language, terms, done

/** First run and changed terms: welcome, look, language, terms (accepted on purpose), done. */
@Composable
fun OnboardingScreen(state: TarkasState, geral: GeralState, onFinished: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val termsOnly = geral.termsChanged
  var step by remember { mutableIntStateOf(if (termsOnly) 3 else 0) }
  val theme by state.themeMode.collectAsState()
  val reduce by state.reduceMotion.collectAsState()
  val lang by geral.language.collectAsState()
  Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.readingColumn().fillMaxSize().padding(vertical = Grid.margin), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
      Text(w("Passo ${step + 1} de $STEPS", "Step ${step + 1} of $STEPS"), style = GridText.Label, color = c.onSurfaceVariant)
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
        when (step) {
          0 -> {
            Text(w("Bem-vindo ao T.A.R.K.A.S.", "Welcome to T.A.R.K.A.S."), style = GridText.Title, color = c.onSurface)
            Text(w("Seu player de música com equalizador de precisão. Tudo roda neste computador.", "Your music player with a precision equaliser. Everything runs on this computer."), style = GridText.Body, color = c.onSurfaceVariant)
            LegalTexts.forLanguage(java.util.Locale.getDefault().language).summary.forEach { Text("• $it", style = GridText.Body, color = c.onSurfaceVariant) }
          }
          1 -> {
            Text(w("Aparência", "Look"), style = GridText.Title, color = c.onSurface)
            InsetGroup {
              listOf(ThemeMode.SYSTEM to w("Seguir o Windows", "Follow Windows"), ThemeMode.LIGHT to w("Claro", "Light"), ThemeMode.DARK to w("Escuro", "Dark"), ThemeMode.AMOLED to "AMOLED")
                .forEachIndexed { i, (m, l) -> if (i > 0) GroupDivider(); RadioRow(l, theme == m, { state.setThemeMode(m) }) }
              GroupDivider()
              SwitchRow(w("Reduzir movimento", "Reduce motion"), reduce, state::setReduceMotion)
            }
          }
          2 -> {
            Text(w("Idioma", "Language"), style = GridText.Title, color = c.onSurface)
            InsetGroup {
              listOf(AppLanguage.SYSTEM to w("Seguir o Windows", "Follow Windows"), AppLanguage.PT to "Português (Brasil)", AppLanguage.EN to "English")
                .forEachIndexed { i, (code, l) -> if (i > 0) GroupDivider(); RadioRow(l, lang == code, { geral.setLanguage(code) }) }
            }
          }
          3 -> {
            val set = LegalTexts.forLanguage(java.util.Locale.getDefault().language)
            Text(
              if (termsOnly) w("Os termos mudaram", "The terms changed") else w("Termos e privacidade", "Terms and privacy"),
              style = GridText.Title, color = c.onSurface
            )
            Text(
              w("Controlador dos dados: ${LegalTexts.CONTROLLER} (${LegalTexts.CONTACT_EMAIL}). Leia antes de aceitar; você pode reler em Configurações.", "Data controller: ${LegalTexts.CONTROLLER} (${LegalTexts.CONTACT_EMAIL}). Read before accepting; you can read again in Settings."),
              style = GridText.Body, color = c.onSurfaceVariant
            )
            InsetGroup {
              ListRow(set.terms.title, onClick = { state.openSettingsPage(com.example.ui.viewmodel.SettingsSubPage.TERMS) })
              GroupDivider()
              ListRow(set.privacy.title, onClick = { state.openSettingsPage(com.example.ui.viewmodel.SettingsSubPage.PRIVACY) })
            }
          }
          else -> {
            Text(w("Tudo pronto", "All set"), style = GridText.Title, color = c.onSurface)
            Text(w("Abra uma faixa na aba Música, ajuste o som na aba Áudio e mude tudo em Configurações.", "Open a track in Music, shape the sound in Audio and change anything in Settings."), style = GridText.Body, color = c.onSurfaceVariant)
          }
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
        if (step > 0 && !termsOnly) OutlinedButton({ step-- }, Modifier.height(Grid.u48)) { Text(w("Voltar", "Back")) }
        Box(Modifier.weight(1f))
        if (step == 3) {
          Button({ geral.acceptTerms(Instant.now().toString()); if (termsOnly) onFinished() else step = 4 }, Modifier.height(Grid.u48)) { Text(w("Li e aceito", "I have read and accept")) }
        } else if (step == STEPS - 1) {
          Button(onFinished, Modifier.height(Grid.u48)) { Text(w("Começar", "Start")) }
        } else {
          Button({ step++ }, Modifier.height(Grid.u48)) { Text(w("Continuar", "Continue")) }
        }
      }
    }
  }
}
