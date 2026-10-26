package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CuriosityConfig
import com.example.data.model.CuriosityTiming
import com.example.ui.components.GlassChip
import com.example.ui.components.TarkasMark
import com.example.ui.legal.LegalDoc
import com.example.ui.legal.LegalTexts
import com.example.ui.theme.AccentColor
import com.example.ui.theme.LocalSurfaceStyle
import com.example.ui.theme.SurfaceStyle
import com.example.ui.theme.glassContentColor
import com.example.ui.theme.glassControl
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.subPageTransition

private enum class Step { WELCOME, TERMS, LOOK, CURIOSITY, DONE }

/**
 * First-run setup, shown once after the opening animation: welcome, the Terms and Privacy Policy
 * (accepted with an unticked box, the one step that cannot be skipped), appearance with a live
 * preview, curiosities, done. When only the terms changed since the last acceptance, it shows just
 * the terms step. Permissions are not asked here: each is asked when its feature is first used.
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

  fun next() {
    if (step == Step.TERMS) onAcceptTerms()
    if (index < steps.lastIndex) index++ else onFinish()
  }

  // Back steps back through the setup; on the first step it does nothing (the app needs the terms).
  BackHandler(enabled = reading == null && index > 0) { index-- }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background)
      .clickable(enabled = false) {}
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
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
          LegalScreen(doc = shown, backLabel = "Voltar", onBack = { reading = null })
        }
      } else {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
          // Progress dots and "Pular" (not on the terms step)
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
              steps.indices.forEach { i ->
                val w by animateDpAsState(if (i == index) 22.dp else 8.dp, label = "dot")
                Box(
                  Modifier
                    .height(8.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(if (i <= index) colors.primary else colors.outline.copy(alpha = 0.6f))
                )
              }
            }
            if (step != Step.TERMS && step != Step.DONE) {
              TextButton(onClick = { index = steps.lastIndex }, modifier = Modifier.testTag("onboarding_skip")) { Text("Pular") }
            }
          }

          AnimatedContent(
            targetState = step,
            transitionSpec = { subPageTransition(forward = targetState.ordinal >= initialState.ordinal, reduced = reduced) },
            label = "onboardingStep",
            modifier = Modifier.weight(1f)
          ) { s ->
            LazyColumn(
              modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
              contentPadding = PaddingValues(bottom = 16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              item {
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

          // The main button: disabled on the terms step until the box is ticked.
          val canContinue = step != Step.TERMS || accepted
          GlassChip(
            label = when {
              step == Step.WELCOME -> "Começar"
              step == Step.TERMS && onlyTerms -> "Aceitar e continuar"
              step == Step.TERMS -> "Aceitar e continuar"
              index == steps.lastIndex -> "Entrar no app"
              else -> "Continuar"
            },
            selected = true,
            enabled = canContinue,
            onClick = { if (canContinue) next() },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 16.dp)
              .height(52.dp)
              .testTag("onboarding_next")
          )
        }
      }
    }
  }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
  Column {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun WelcomeStep() {
  val colors = MaterialTheme.colorScheme
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
    Box(
      modifier = Modifier
        .size(120.dp)
        .clip(RoundedCornerShape(32.dp))
        .background(Brush.linearGradient(listOf(colors.primary, colors.primary.copy(alpha = 0.45f)))),
      contentAlignment = Alignment.Center
    ) {
      TarkasMark(modifier = Modifier.size(width = 72.dp, height = 44.dp), symbolColor = Color.White, accentColor = Color.White)
    }
    Spacer(Modifier.height(28.dp))
    Text("Boas-vindas ao T.A.R.K.A.S.", fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center, color = colors.onBackground)
    Spacer(Modifier.height(10.dp))
    Text(
      "Música do aparelho, do Audius, do YouTube Music e do Spotify, um equalizador completo com correção para o seu fone, curiosidades sobre as faixas e um assistente por voz.",
      fontSize = 16.sp,
      lineHeight = 23.sp,
      textAlign = TextAlign.Center,
      color = colors.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    Text("Leva menos de um minuto para deixar do seu jeito.", fontSize = 14.sp, color = colors.onSurfaceVariant)
  }
}

@Composable
private fun TermsStep(accepted: Boolean, onAccepted: (Boolean) -> Unit, onRead: (String) -> Unit, changed: Boolean) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    StepTitle(
      if (changed) "Os termos mudaram" else "Termos e privacidade",
      if (changed) "Leia o que mudou e aceite a nova versão para continuar usando o app." else "O essencial, em poucas linhas. O texto completo está nos links abaixo."
    )
    LegalTexts.summary.forEach { line ->
      Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(line, fontSize = 15.sp, lineHeight = 21.sp, color = colors.onSurface)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      GlassChip(label = "Termos de Uso", onClick = { onRead("terms") }, modifier = Modifier.testTag("onboarding_read_terms"))
      GlassChip(label = "Privacidade", onClick = { onRead("privacy") }, modifier = Modifier.testTag("onboarding_read_privacy"))
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable { onAccepted(!accepted) }
        .padding(vertical = 6.dp)
        .testTag("onboarding_accept"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Checkbox(checked = accepted, onCheckedChange = onAccepted)
      Text(
        "Li e aceito os Termos de Uso e a Política de Privacidade.",
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = colors.onSurface
      )
    }
    Text(
      "Responsável: ${LegalTexts.CONTROLLER} · ${LegalTexts.CONTACT_EMAIL}",
      fontSize = 13.sp,
      color = colors.onSurfaceVariant
    )
  }
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
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    StepTitle("Aparência", "Tudo isso muda na hora e pode ser trocado depois em Ajustes.")

    // Live preview: a tab capsule and a selected chip in the chosen theme, surface and accent.
    CompositionLocalProvider(LocalSurfaceStyle provides surfaceStyle) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(24.dp))
          .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.55f), Color(0xFF2B1B4D), Color(0xFF0E7C86))))
          .padding(18.dp)
          .testTag("onboarding_preview")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().glassControl(RoundedCornerShape(28.dp)).padding(8.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          listOf(Icons.Default.PlayCircleFilled to true, Icons.Default.LibraryMusic to false, Icons.Default.Equalizer to false, Icons.Default.Psychology to false)
            .forEach { (icon, selected) ->
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(RoundedCornerShape(20.dp))
                  .then(if (selected) Modifier.background(colors.onSurface.copy(alpha = 0.10f)) else Modifier),
                contentAlignment = Alignment.Center
              ) {
                Icon(icon, contentDescription = null, tint = if (selected) colors.primary else colors.onSurfaceVariant)
              }
            }
        }
      }
    }

    SectionLabel("Tema")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
      listOf("amoled" to "AMOLED", "dark" to "Escuro", "light" to "Claro", "system" to "Sistema").forEach { (key, label) ->
        GlassChip(label = label, selected = themeMode == key, onClick = { onThemeMode(key) }, modifier = Modifier.testTag("onboarding_theme_$key"))
      }
    }

    SectionLabel("Cor de destaque")
    AccentPicker(accent, onAccent)

    SectionLabel("Superfícies")
    SurfaceStyle.entries.forEach { style ->
      val selected = surfaceStyle == style
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .glassControl(RoundedCornerShape(18.dp), selected = selected)
          .clickable { onSurfaceStyle(style) }
          .padding(14.dp)
          .testTag("onboarding_surface_${style.key}"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text(style.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassContentColor(selected))
          // On a filled (selected, solid) row the grey would vanish: use the row's own text colour.
          Text(
            style.description,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = if (selected) glassContentColor(true).copy(alpha = 0.8f) else colors.onSurfaceVariant
          )
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = glassContentColor(true))
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Reduzir animações", fontSize = 16.sp, color = colors.onSurface)
        Text("Troca de tela sem movimento, só no app.", fontSize = 13.sp, color = colors.onSurfaceVariant)
      }
      Switch(checked = reduceMotion, onCheckedChange = onReduceMotion, modifier = Modifier.testTag("onboarding_reduce_motion"))
    }
  }
}

/** Round swatches for the accent colour; the wallpaper one appears on Android 12 and newer. */
@Composable
fun AccentPicker(accent: AccentColor, onAccent: (AccentColor) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val isDark = colors.background.red + colors.background.green + colors.background.blue < 1.5f
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
    AccentColor.entries.filter { it != AccentColor.WALLPAPER || AccentColor.wallpaperSupported }.forEach { a ->
      val selected = a == accent
      val swatch = when {
        a == AccentColor.WALLPAPER && selected -> colors.primary
        a == AccentColor.WALLPAPER -> colors.onSurfaceVariant
        isDark -> a.dark
        else -> a.light
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
              if (a == AccentColor.WALLPAPER) Brush.sweepGradient(listOf(Color(0xFFE57373), Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFBA68C8), Color(0xFFE57373)))
              else Brush.linearGradient(listOf(swatch, swatch))
            )
            .border(if (selected) 3.dp else 0.dp, if (selected) colors.onBackground else Color.Transparent, CircleShape)
            .clickable { onAccent(a) }
            .testTag("accent_${a.key}"),
          contentAlignment = Alignment.Center
        ) {
          if (selected) Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(a.label, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
      }
    }
  }
}

@Composable
private fun CuriosityStep(config: CuriosityConfig, onConfig: (CuriosityConfig) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    StepTitle(
      "Curiosidades",
      "Para faixas com uma curiosidade verificada, o app pode contar um fato curto em voz alta. Ela também aparece no player e na notificação."
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Curiosidades faladas", fontSize = 16.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
      Switch(checked = config.enabled, onCheckedChange = { onConfig(config.copy(enabled = it)) }, modifier = Modifier.testTag("onboarding_curiosity_switch"))
    }
    if (config.enabled) {
      SectionLabel("Quando falar")
      listOf(
        CuriosityTiming.END_OF_TRACK to "Ao final da música",
        CuriosityTiming.START_OF_TRACK to "Antes da música",
        CuriosityTiming.MANUAL_ONLY to "Só quando eu tocar na lâmpada"
      ).forEach { (timing, label) ->
        val selected = config.timing == timing
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .glassControl(RoundedCornerShape(18.dp), selected = selected)
            .clickable { onConfig(config.copy(timing = timing)) }
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(label, fontSize = 16.sp, color = glassContentColor(selected), modifier = Modifier.weight(1f))
          if (selected) Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = glassContentColor(true))
        }
      }
    }
  }
}

@Composable
private fun DoneStep() {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 24.dp)) {
    StepTitle("Tudo pronto", "Algumas coisas para saber:")
    listOf(
      "Busque músicas na barra do Início. As do aparelho entram pela Biblioteca.",
      "O equalizador fica na aba Áudio, com correção para mais de 8 mil fones.",
      "Permissões, como notificações e microfone, são pedidas só quando você usa o recurso.",
      "Aparência, privacidade e o Sobre ficam na engrenagem no alto de cada tela."
    ).forEach { line ->
      Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(line, fontSize = 15.sp, lineHeight = 21.sp, color = colors.onSurface)
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
