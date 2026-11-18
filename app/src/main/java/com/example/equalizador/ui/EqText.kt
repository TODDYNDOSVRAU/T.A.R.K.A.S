package com.example.ui.screens.audio

import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.GridText

/**
 * The equalizer screen's text: the grid's three sizes (12, 18, 24 sp) from [GridText]. Every number
 * and technical value is the mono face with tabular figures, so a value that changes while a finger
 * moves never shifts its neighbours.
 */
internal object EqType {
  val Label = GridText.Small
  val LabelStrong = GridText.Small.copy(fontWeight = FontWeight.SemiBold)
  val Body = GridText.Body
  val BodyStrong = GridText.BodyStrong
  val Title = GridText.Title
  val Mono = GridText.Label.copy(fontFeatureSettings = "tnum")
  val MonoStrong = Mono.copy(fontWeight = FontWeight.Medium)
}
