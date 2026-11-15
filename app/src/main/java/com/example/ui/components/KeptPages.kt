package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Fade-through between pages: the old one is gone in 150 ms, the new one fades in over 200 ms after 90 ms. */
internal const val PageExitMs = 150
internal const val PageEnterMs = 200
internal const val PageEnterDelayMs = 90

/**
 * Shows the page for [target] and fades between pages. A page for which [keep] is true stays composed after
 * it is left, so going back to it shows a page that is already built instead of composing it again (on the
 * S23 that composition cost 1–2 frames of 20–35 ms in every tab switch). Other pages are dropped once their
 * fade-out ends.
 *
 * A page that is not shown is not drawn, takes no touches, is out of the accessibility tree, and sees its
 * lifecycle held at CREATED: back handlers, loops that run while RESUMED and lifecycle-aware collection
 * stop until it is shown again. Animations a page runs on its own must be stopped by the caller (for
 * example by passing it "not playing" while it is hidden).
 */
@Composable
fun <K : Any> KeptPages(
  target: K,
  keep: (K) -> Boolean,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false,
  content: @Composable (K) -> Unit
) {
  val pages = remember { mutableStateListOf<K>() }
  // The first page shows at once, as the app opens; every later one fades in.
  val opened = remember { booleanArrayOf(false) }
  val focus = LocalFocusManager.current
  LaunchedEffect(target) {
    // A text field on the page being left must not keep the keyboard open over the next one.
    if (opened[0]) focus.clearFocus(force = true)
    opened[0] = true
  }
  SideEffect { if (target !in pages) pages.add(target) }
  val visible = if (target in pages) pages.toList() else pages + target

  Box(modifier) {
    for (page in visible) {
      key(page) {
        val shown = page == target
        val alpha = remember { Animatable(if (opened[0]) 0f else 1f) }
        LaunchedEffect(shown, reducedMotion) {
          if (reducedMotion) alpha.snapTo(if (shown) 1f else 0f)
          else if (shown) alpha.animateTo(1f, tween(PageEnterMs, delayMillis = PageEnterDelayMs, easing = LinearOutSlowInEasing))
          else alpha.animateTo(0f, tween(PageExitMs, easing = FastOutLinearInEasing))
          if (!shown && !keep(page)) pages.remove(page)
        }
        val lifecycle = rememberPageLifecycle(active = shown)
        Box(
          Modifier
            .zIndex(if (shown) 1f else 0f)
            .then(if (shown) Modifier else Modifier.clearAndSetSemantics {})
            .layout { measurable, constraints ->
              val placeable = measurable.measure(constraints)
              layout(placeable.width, placeable.height) {
                // Not placed once faded out: not drawn and not reachable by touch.
                if (shown || alpha.value > 0f) placeable.placeWithLayer(0, 0) { this.alpha = alpha.value }
              }
            }
        ) {
          CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) { content(page) }
        }
      }
    }
  }
}

/** A lifecycle that follows the screen's while [active], and is held at CREATED at most while not. */
@Composable
private fun rememberPageLifecycle(active: Boolean): LifecycleOwner {
  val parent = LocalLifecycleOwner.current
  val owner = remember(parent) { PageLifecycleOwner() }
  // Changed after the composition is applied, never in the middle of it.
  SideEffect { owner.active = active }
  DisposableEffect(parent, owner) {
    val observer = LifecycleEventObserver { _, _ -> owner.parentState = parent.lifecycle.currentState }
    parent.lifecycle.addObserver(observer)
    onDispose { parent.lifecycle.removeObserver(observer) }
  }
  return owner
}

internal class PageLifecycleOwner : LifecycleOwner {
  private val registry = LifecycleRegistry(this)
  override val lifecycle: Lifecycle get() = registry

  var parentState: Lifecycle.State = Lifecycle.State.INITIALIZED
    set(value) { field = value; update() }
  var active: Boolean = true
    set(value) { if (field != value) { field = value; update() } }

  private fun update() {
    val wanted = if (active) parentState else parentState.coerceAtMost(Lifecycle.State.CREATED)
    // A registry cannot leave DESTROYED nor go back to INITIALIZED.
    if (wanted == Lifecycle.State.INITIALIZED || registry.currentState == Lifecycle.State.DESTROYED) return
    registry.currentState = wanted
  }
}

private fun Lifecycle.State.coerceAtMost(max: Lifecycle.State): Lifecycle.State = if (this > max) max else this
