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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
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
          LegalScreen(doc = shown, backLabel = stringResource(R.string.onboarding_back_label), onBack = { reading = null })
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
              TextAction(stringResource(R.string.onboarding_back), onClick = { index-- }, modifier = Modifier.align(Alignment.CenterStart).testTag("onboarding_back"))
            }
            if (step.skippable) {
              TextAction(
                stringResource(R.string.onboarding_skip),
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
                  Step.LOOK -> LookStep(themeMode, onThemeMode, surfaceStyle, onSurfaceStyle, reduceMotion, onReduceMotion)
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
              text = stringResource(if (step == Step.DONE) R.string.onboarding_start else R.string.onboarding_continue),
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
  val description = stringResource(R.string.onboarding_step_of, current + 1, count)
  Row(
    horizontalArrangement = Arrangement.spacedBy(Space.s),
    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
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
    title = stringResource(R.string.onboarding_welcome_title),
    body = stringResource(R.string.onboarding_welcome_body),
    mark = true
  )
  SectionGap()
  FeatureRow(Icons.Rounded.LibraryMusic, stringResource(R.string.onboarding_feature_music), stringResource(R.string.onboarding_feature_music_body))
  FeatureRow(Icons.Rounded.Equalizer, stringResource(R.string.onboarding_feature_sound), stringResource(R.string.onboarding_feature_sound_body))
  FeatureRow(Icons.Rounded.Lightbulb, stringResource(R.string.onboarding_feature_assistant), stringResource(R.string.onboarding_feature_assistant_body))
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
    title = stringResource(if (changed) R.string.onboarding_terms_changed_title else R.string.onboarding_terms_title),
    body = stringResource(if (changed) R.string.onboarding_terms_changed_body else R.string.onboarding_terms_body)
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
    ListRow(title = stringResource(R.string.onboarding_read_terms), onClick = { onRead("terms") }, modifier = Modifier.testTag("onboarding_read_terms"))
    GroupDivider()
    ListRow(title = stringResource(R.string.onboarding_read_privacy), onClick = { onRead("privacy") }, modifier = Modifier.testTag("onboarding_read_privacy"))
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
      Text(stringResource(R.string.onboarding_accept), style = AppText.Body, color = colors.onSurface)
    }
  }
  GroupFooter(stringResource(R.string.onboarding_controller, LegalTexts.CONTROLLER, LegalTexts.CONTACT_EMAIL))
}

@Composable
private fun LookStep(
  themeMode: String,
  onThemeMode: (String) -> Unit,
  surfaceStyle: SurfaceStyle,
  onSurfaceStyle: (SurfaceStyle) -> Unit,
  reduceMotion: Boolean,
  onReduceMotion: (Boolean) -> Unit
) {
  StepHeader(stringResource(R.string.onboarding_look_title), stringResource(R.string.onboarding_look_body))
  SectionGap()
  GroupHeader(stringResource(R.string.onboarding_theme))
  InsetGroup { ThemeOptionRows(themeMode, onThemeMode, tagPrefix = "onboarding_theme_") }
  SectionGap()
  GroupHeader(stringResource(R.string.onboarding_floating_bars))
  InsetGroup { SurfaceOptionRows(surfaceStyle, onSurfaceStyle, tagPrefix = "onboarding_surface_") }
  GroupFooter(stringResource(R.string.onboarding_floating_bars_footer))
  SectionGap()
  InsetGroup {
    SwitchRow(
      title = stringResource(R.string.onboarding_reduce_motion),
      subtitle = stringResource(R.string.onboarding_reduce_motion_hint),
      checked = reduceMotion,
      onCheckedChange = onReduceMotion,
      modifier = Modifier.testTag("onboarding_reduce_motion")
    )
  }
}

@Composable
private fun CuriosityStep(config: CuriosityConfig, onConfig: (CuriosityConfig) -> Unit) {
  StepHeader(
    stringResource(R.string.onboarding_curiosity_title),
    stringResource(R.string.onboarding_curiosity_body)
  )
  SectionGap()
  InsetGroup {
    SwitchRow(
      title = stringResource(R.string.onboarding_curiosity_spoken),
      checked = config.enabled,
      onCheckedChange = { onConfig(config.copy(enabled = it)) },
      modifier = Modifier.testTag("onboarding_curiosity_switch")
    )
  }
  if (config.enabled) {
    SectionGap()
    GroupHeader(stringResource(R.string.onboarding_curiosity_when))
    InsetGroup { CuriosityTimingRows(config.timing, { onConfig(config.copy(timing = it)) }, tagPrefix = "onboarding_timing_") }
  }
}

@Composable
private fun DoneStep() {
  val colors = MaterialTheme.colorScheme
  StepHeader(stringResource(R.string.onboarding_done_title), stringResource(R.string.onboarding_done_body), mark = true)
  SectionGap()
  InsetGroup {
    listOf(
      stringResource(R.string.onboarding_done_tip_search),
      stringResource(R.string.onboarding_done_tip_eq),
      stringResource(R.string.onboarding_done_tip_permissions),
      stringResource(R.string.onboarding_done_tip_settings)
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
