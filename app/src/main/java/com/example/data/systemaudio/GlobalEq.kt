package com.example.data.systemaudio

import com.example.data.model.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How far the equalizer reaches. Chosen on the Áudio screen. */
enum class EqReach(val key: String, val label: String, val description: String) {
  APP(
    "app", "Sem root",
    "Equaliza as músicas e os áudios que o T.A.R.K.A.S. toca. Não precisa de nada."
  ),
  ADB(
    "adb", "ADB",
    "Equaliza também os outros apps (Spotify, YouTube, jogos), como o Poweramp Equalizer. " +
      "Precisa da permissão DUMP, liberada uma vez por um computador."
  ),
  ROOT(
    "root", "Root",
    "Tudo do modo ADB sem computador: o app libera a permissão sozinho pelo root e tenta também " +
      "a saída geral, para os sons sem sessão própria (sistema, notificações)."
  );

  companion object {
    fun fromKey(key: String?): EqReach = entries.firstOrNull { it.key == key } ?: APP
  }
}

/** What the system-wide equalizer is doing, for the Áudio screen. */
data class GlobalEqStatus(
  val running: Boolean = false,
  /** Apps whose sound is being equalized now. */
  val apps: List<String> = emptyList(),
  /** ADB mode without the DUMP permission: only players that announce their session are reached. */
  val needsDump: Boolean = false,
  /** Root mode, but `su` is missing or the user refused it. */
  val rootUnavailable: Boolean = false,
  /** Root mode reached the global output (sounds without a session of their own). */
  val globalOutput: Boolean = false
)

/** Shared between the view model (which owns the curve) and [GlobalEqService] (which applies it). */
object GlobalEq {
  val settings = MutableStateFlow(EqSettings())
  val reach = MutableStateFlow(EqReach.APP)

  private val _status = MutableStateFlow(GlobalEqStatus())
  val status: StateFlow<GlobalEqStatus> = _status.asStateFlow()

  internal fun publish(status: GlobalEqStatus) {
    _status.value = status
  }
}
