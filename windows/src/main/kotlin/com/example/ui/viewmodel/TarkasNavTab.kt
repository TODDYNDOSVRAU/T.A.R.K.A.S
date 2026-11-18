package com.example.ui.viewmodel

import com.example.windows.res.Res
import com.example.windows.res.player_common_settings
import com.example.windows.res.player_common_tab_assistant
import com.example.windows.res.player_common_tab_audio
import com.example.windows.res.player_common_tab_home
import com.example.windows.res.player_common_tab_library
import com.example.windows.res.player_common_tab_music
import org.jetbrains.compose.resources.StringResource

/** The pages (desktop twin of the enum in TarkasViewModel.kt); [label] is the title in the system language. */
enum class TarkasNavTab(val label: StringResource) {
  HOME(Res.string.player_common_tab_home),
  MUSIC(Res.string.player_common_tab_music),
  LIBRARY(Res.string.player_common_tab_library),
  AUDIO(Res.string.player_common_tab_audio),
  ASSISTANT(Res.string.player_common_tab_assistant),
  SETTINGS(Res.string.player_common_settings)
}
