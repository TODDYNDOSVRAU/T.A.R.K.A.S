package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring

/** Molas T.A.L. (mesmos valores do Android, docs/TAL_DESIGN_SYSTEM.md §5). */
object Motion {
  fun <T> fluid() = spring<T>(dampingRatio = 0.85f, stiffness = 480f)
  fun <T> fluidFast() = spring<T>(dampingRatio = 0.86f, stiffness = 620f)
  fun <T> fluidExit() = spring<T>(dampingRatio = 1f, stiffness = 520f)
  fun <T> gel() = spring<T>(dampingRatio = 0.62f, stiffness = 348f)
  fun <T> defaultEffects() = spring<T>(dampingRatio = 1f, stiffness = 1600f)

  val Settle = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
  val PlayerEasing = CubicBezierEasing(0.45f, 0f, 0.15f, 1f)
  const val ExpandMillis = 300
  const val CollapseMillis = 260
  const val StaggerItems = 8
  const val StaggerStepMs = 30

  /** Resistência assintótica nos limites: f(x) = x*d*c / (d + c*x). */
  fun rubberBand(overshoot: Float, limit: Float, c: Float = 0.55f): Float {
    if (overshoot <= 0f || limit <= 0f) return 0f
    return (overshoot * limit * c) / (limit + c * overshoot)
  }
}
