package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.CuriosityConfig
import com.example.ui.components.GroupDivider
import com.example.ui.components.GroupFooter
import com.example.ui.components.GroupHeader
import com.example.ui.components.InsetGroup
import com.example.ui.components.ListRow
import com.example.ui.components.PrimaryButton
import com.example.ui.components.RowIcon
import com.example.ui.components.SwitchRow
import com.example.ui.components.TarkasMark
import com.example.ui.components.TextAction
import com.example.ui.legal.LegalDoc
import com.example.ui.legal.LegalTexts
import com.example.ui.theme.AccentColor
import com.example.ui.theme.AppText
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.SurfaceStyle
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.subPageTransition

private enum class Step {
  WELCOME, TERMS, LOOK, CURIOSITY, DONE;

  /**
   * Only the preference steps after the terms can be skipped. Welcome and the terms cannot, so "Pular"
   * never jumps past the acceptance.
   */
  val skippable: Boolean get() = this == LOOK || this == CURIOSITY
}

/** Content stays at a comfortable reading width on tablets and in landscape. */
private val ContentWidth = 560.dp

/**
 * First-run setup, shown once after the opening animation: welcome, the Terms and Privacy Policy
 * (accepted with an unticked box), appearance, curiosities, done. When only the terms changed since
 * the last acceptance, it shows just the terms step. Permissions are not asked here: each is asked
 * when its feature is first used.
 *
 * It covers the app completely: touches never reach the screen beneath, and back steps back through
 * the setup, then leaves the app from the first step instead of navigating the screen beneath. The
 * setup cannot finish without the terms accepted, whichever way the user moves through it.
 */
@Composable
fun OnboardingScreen(
  onlyTerms: Boolean,
  themeMode: String,
  onThemeMode: (String) -> Unit,
  surfaceStyle: SurfaceStyle,
  onSurfaceStyle: (SurfaceStyle) -> Unit,
  accent: AccentColor,
  onAccent: (AccentColor) -> Unit,
  reduceMotion: Boolean,
  onReduceMotion: (Boolean) -> Unit,
  curiosityConfig: CuriosityConfig,
  onCuriosityConfig: (CuriosityConfig) -> Unit,
  onAcceptTerms: () -> Unit,
  onFinish: () -> Unit
) {
  val steps = if (onlyTerms) listOf(Step.TERMS) else Step.entries
  var index by rememberSaveable { mutableIntStateOf(0) }
  var accepted by rememberSaveable { mutableStateOf(false) }
  var reading by rememberSaveable { mutableStateOf<String?>(null) }
  val step = steps[index.coerceIn(0, steps.lastIndex)]
  val reduced = rememberReducedMotion()
  val colors = MaterialTheme.colorScheme
  val activity = LocalContext.current.findActivity()

  fun finish() {
    // The last guard: without acceptance, go to the terms step rather than into the app.
    if (accepted) onFinish() else index = steps.indexOf(Step.TERMS)
  }
  fun next() {
    if (step == Step.TERMS) {
      if (!accepted) return
      onAcceptTerms()
    }
    if (index < steps.lastIndex) index++ else finish()
  }

  BackHandler {
    when {
      reading != null -> reading = null
      index > 0 -> index--
      // As back does on any first screen: the app goes to the background, the setup waits for later.
      else -> activity?.moveTaskToBack(true)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background)
      // A pointer handler over the whole screen makes it the hit target, and Compose does not pass a
      // hit on to the sibling drawn beneath (the app). It only listens: consuming here would cancel
      // the taps of the controls inside, which check for consumption by their parents.
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent()
        }
      }
      .testTag("onboarding")
  ) {
    val doc: LegalDoc? = when (reading) {
      "terms" -> LegalTexts.terms
      "privacy" -> LegalTexts.privacy
      else -> null
    }
    AnimatedContent(
      targetState = doc,
      transitionSpec = { subPageTransition(forward = targetState != null, reduced = reduced) },
      label = "onboardingReading"
    ) { shown ->
      if (shown != null) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
          LegalScreen(doc = shown, backLabel = "Configuração", onBack = { reading = null })
        }
      } else {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = Size.touch)
              .padding(horizontal = Space.s)
          ) {
            if (index > 0) {
              TextAction("Voltar", onClick = { index-- }, modifier = Modifier.align(Alignment.CenterStart).testTag("onboarding_back"))
            }
            if (step.skippable) {
              TextAction(
                "Pular",
                onClick = { index = steps.lastIndex },
                modifier = Modifier.align(Alignment.CenterEnd).testTag("onboarding_skip")
              )
            }
          }

          AnimatedContent(
            targetState = step,
            transitionSpec = { subPageTransition(forward = targetState.ordinal >= initialState.ordinal, reduced = reduced) },
            label = "onboardingStep",
            modifier = Modifier.weight(1f)
          ) { s ->
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.xl),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) {
                when (s) {
                  Step.WELCOME -> WelcomeStep()
                  Step.TERMS -> TermsStep(
                    accepted = accepted,
                    onAccepted = { accepted = it },
                    onRead = { reading = it },
                    changed = onlyTerms
                  )
                  Step.LOOK -> LookStep(themeMode, onThemeMode, surfaceStyle, onSurfaceStyle, accent, onAccent, reduceMotion, onReduceMotion)
                  Step.CURIOSITY -> CuriosityStep(curiosityConfig, onCuriosityConfig)
                  Step.DONE -> DoneStep()
                }
              }
            }
          }

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = Space.page, vertical = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            if (steps.size > 1) {
              PageIndicator(index, steps.size)
              Spacer(Modifier.height(Space.l))
            }
            PrimaryButton(
              text = if (step == Step.DONE) "Começar a usar" else "Continuar",
              enabled = step != Step.TERMS || accepted,
              onClick = ::next,
              modifier = Modifier
                .widthIn(max = ContentWidth)
                .fillMaxWidth()
                .testTag("onboarding_next")
            )
          }
        }
      }
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

/** Small static dots; read as "Passo 2 de 5". */
@Composable
private fun PageIndicator(current: Int, count: Int) {
  val colors = MaterialTheme.colorScheme
  Row(
    horizontalArrangement = Arrangement.spacedBy(Space.s),
    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Passo ${current + 1} de $count" }
  ) {
    repeat(count) { i ->
      Box(
        Modifier
          .size(7.dp)
          .background(if (i == current) colors.onSurface else colors.outline, CircleShape)
      )
    }
  }
}

/** The mark (optional), a large title and a short line, centred, as the platform's setup screens do. */
@Composable
private fun StepHeader(title: String, body: String, mark: Boolean = false) {
  val colors = MaterialTheme.colorScheme
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.l)
  ) {
    if (mark) {
      TarkasMark(modifier = Modifier.size(width = 88.dp, height = 54.dp))
      Spacer(Modifier.height(Space.xl))
    }
    Text(
      title,
      style = AppText.LargeTitle,
      color = colors.onSurface,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(Space.s))
    Text(body, style = AppText.Body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
  }
}

@Composable
private fun SectionGap() = Spacer(Modifier.height(Space.xl))

@Composable
private fun WelcomeStep() {
  StepHeader(
    title = "Boas-vindas ao T.A.R.K.A.S.",
    body = "Leva menos de um minuto para deixar do seu jeito.",
    mark = true
  )
  SectionGap()
  FeatureRow(Icons.Rounded.LibraryMusic, "Sua música, de vários lugares", "Do aparelho, do Audius, do YouTube Music e do Spotify, numa biblioteca só.")
  FeatureRow(Icons.Rounded.Equalizer, "Som ajustado ao seu fone", "Equalizador de 15 bandas ou paramétrico, com correção para mais de 8 mil fones.")
  FeatureRow(Icons.Rounded.Lightbulb, "Curiosidades e assistente", "Fatos verificados sobre a faixa e comandos por voz.")
}

/** One feature on the welcome screen: an icon, a title and one line, read as a single item. */
@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page + Space.s, vertical = Space.m)
      .semantics(mergeDescendants = true) {},
    verticalAlignment = Alignment.Top
  ) {
    RowIcon(icon)
    Spacer(Modifier.width(Space.l))
    Column(Modifier.weight(1f)) {
      Text(title, style = AppText.Headline, color = colors.onSurface)
      Text(body, style = AppText.Subhead, color = colors.onSurfaceVariant)
    }
  }
}

@Composable
private fun TermsStep(accepted: Boolean, onAccepted: (Boolean) -> Unit, onRead: (String) -> Unit, changed: Boolean) {
  val colors = MaterialTheme.colorScheme
  StepHeader(
    title = if (changed) "Os termos mudaram" else "Termos e privacidade",
    body = if (changed) "Leia o que mudou e aceite a nova versão para continuar usando o app."
    else "O essencial em poucas linhas. O texto completo está logo abaixo."
  )
  SectionGap()
  InsetGroup {
    LegalTexts.summary.forEachIndexed { i, line ->
      if (i > 0) GroupDivider()
      Text(
        line,
        style = AppText.Subhead,
        color = colors.onSurface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)
      )
    }
  }
  SectionGap()
  InsetGroup {
    ListRow(title = "Termos de Uso", onClick = { onRead("terms") }, modifier = Modifier.testTag("onboarding_read_terms"))
    GroupDivider()
    ListRow(title = "Política de Privacidade", onClick = { onRead("privacy") }, modifier = Modifier.testTag("onboarding_read_privacy"))
  }
  SectionGap()
  InsetGroup {
    // The whole row is one checkbox: one focus stop, announced with its label and state.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = Size.row)
        .toggleable(value = accepted, role = Role.Checkbox, onValueChange = onAccepted)
        .padding(start = Space.xs, end = Space.l, top = Space.xs, bottom = Space.xs)
        .testTag("onboarding_accept"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Checkbox(
        checked = accepted,
        onCheckedChange = null,
        colors = CheckboxDefaults.colors(checkedColor = colors.primary, checkmarkColor = colors.onPrimary),
        modifier = Modifier.size(Size.touch)
      )
      Text("Li e aceito os Termos de Uso e a Política de Privacidade", style = AppText.Body, color = colors.onSurface)
    }
  }
  GroupFooter("Responsável: ${LegalTexts.CONTROLLER} · ${LegalTexts.CONTACT_EMAIL}")
}

@Composable
private fun LookStep(
  themeMode: String,
  onThemeMode: (String) -> Unit,
  surfaceStyle: SurfaceStyle,
  onSurfaceStyle: (SurfaceStyle) -> Unit,
  accent: AccentColor,
  onAccent: (AccentColor) -> Unit,
  reduceMotion: Boolean,
  onReduceMotion: (Boolean) -> Unit
) {
  StepHeader("Aparência", "Muda na hora. Dá para trocar depois em Ajustes.")
  SectionGap()
  GroupHeader("Tema")
  InsetGroup { ThemeOptionRows(themeMode, onThemeMode, tagPrefix = "onboarding_theme_") }
  SectionGap()
  GroupHeader("Cor de destaque")
  InsetGroup { AccentSwatches(accent, onAccent) }
  SectionGap()
  GroupHeader("Barras flutuantes")
  InsetGroup { SurfaceOptionRows(surfaceStyle, onSurfaceStyle, tagPrefix = "onboarding_surface_") }
  GroupFooter("Como são desenhadas a barra de abas e o mini player.")
  SectionGap()
  InsetGroup {
    SwitchRow(
      title = "Reduzir animações",
      subtitle = "Troca de tela sem movimento",
      checked = reduceMotion,
      onCheckedChange = onReduceMotion,
      modifier = Modifier.testTag("onboarding_reduce_motion")
    )
  }
}

@Composable
private fun CuriosityStep(config: CuriosityConfig, onConfig: (CuriosityConfig) -> Unit) {
  StepHeader(
    "Curiosidades",
    "Para faixas com uma curiosidade verificada, o app conta um fato curto em voz alta. Ele também aparece no player e na notificação."
  )
  SectionGap()
  InsetGroup {
    SwitchRow(
      title = "Curiosidades faladas",
      checked = config.enabled,
      onCheckedChange = { onConfig(config.copy(enabled = it)) },
      modifier = Modifier.testTag("onboarding_curiosity_switch")
    )
  }
  if (config.enabled) {
    SectionGap()
    GroupHeader("Quando falar")
    InsetGroup { CuriosityTimingRows(config.timing, { onConfig(config.copy(timing = it)) }, tagPrefix = "onboarding_timing_") }
  }
}

@Composable
private fun DoneStep() {
  val colors = MaterialTheme.colorScheme
  StepHeader("Tudo pronto", "Algumas coisas para saber.", mark = true)
  SectionGap()
  InsetGroup {
    listOf(
      "Busque músicas na barra do Início. As do aparelho entram pela Biblioteca.",
      "O equalizador fica na aba Áudio, com correção para mais de 8 mil fones.",
      "Notificações e microfone são pedidos só quando você usa o recurso.",
      "Aparência, privacidade e Sobre ficam em Ajustes, no alto do Início."
    ).forEachIndexed { i, line ->
      if (i > 0) GroupDivider()
      Text(
        line,
        style = AppText.Subhead,
        color = colors.onSurface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)
      )
    }
  }
}
