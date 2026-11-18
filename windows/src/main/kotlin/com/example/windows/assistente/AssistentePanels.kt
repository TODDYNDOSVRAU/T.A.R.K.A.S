package com.example.windows.assistente

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.data.model.EqSettings
import com.example.data.model.Track
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.shell.PanelContent
import com.example.windows.state.TarkasState

/**
 * The assistant's view of the desktop app. The equalizer and play/pause go through [TarkasState]. Everything that
 * needs the queue or the library (play, queue, sleep timer, volume) goes through [actions], which the player and
 * library sessions set; until then those replies say honestly that the action could not run.
 */
class DesktopAssistantHost(
  private val app: TarkasState,
  @Volatile var actions: AssistantActions = NoAssistantActions,
  /** Supplied by the player session: the track playing now, or null. */
  @Volatile var currentTrackProvider: () -> Track? = { null },
  /** Supplied by the player session: runs next, previous, pause, play, shuffle, repeat, favorite. Null result: not run. */
  @Volatile var playerCommands: suspend (PlayerCommand) -> String? = { null },
  @Volatile var routeProvider: () -> Pair<String, Int> = { "" to 48000 },
  private val curiosities: CuriosityRepository = CuriosityRepository()
) : AssistantHost, AssistantActions by DelegatingActions {

  /** Set by [AssistentePanels]; receives the timers the engine creates. */
  @Volatile var timerSink: (String, String, Int) -> Unit = { _, _, _ -> }
  @Volatile var timerClear: () -> Unit = {}
  @Volatile var curiosityListener: (Curiosity?) -> Unit = {}

  override fun library() = actions.library()
  override fun playTracks(tracks: List<Track>) = actions.playTracks(tracks)
  override fun queueTracks(tracks: List<Track>, asNext: Boolean) = actions.queueTracks(tracks, asNext)
  override fun clearQueue() = actions.clearQueue()
  override fun sleepIn(minutes: Int) = actions.sleepIn(minutes)
  override fun sleepAtEndOfTrack() = actions.sleepAtEndOfTrack()
  override fun cancelSleep() = actions.cancelSleep()
  override fun setSpatial(on: Boolean) = actions.setSpatial(on)
  override fun setVolumePercent(percent: Int) = actions.setVolumePercent(percent)

  override fun currentTrack(): Track? = currentTrackProvider()
  override suspend fun playerCommand(command: PlayerCommand): String? = playerCommands(command)
  override fun eqSettings(): EqSettings = app.eqSettings.value
  override fun applyEq(settings: EqSettings) = app.updateEq { settings }
  override fun audioRoute(): Pair<String, Int> = routeProvider()
  override fun createTimer(id: String, label: String, seconds: Int) = timerSink(id, label, seconds)
  override fun cancelTimers() = timerClear()
  override fun curiosityFor(track: Track): Curiosity? = curiosities.getCuriosityForTrack(track)
  override fun curiosityUpdated(curiosity: Curiosity?) = curiosityListener(curiosity)
}

/** Only here so the class header compiles with the delegation clause; every member is overridden above. */
private object DelegatingActions : AssistantActions by NoAssistantActions

/** What the assistant area gives the shell: the ASSISTANT panel and the curiosity settings page. */
class AssistentePanels(val state: AssistenteState, val host: DesktopAssistantHost) {

  /** Panel for [TarkasNavTab.ASSISTANT]. The playback state tells which quick requests make sense. */
  fun install(slots: MutableMap<TarkasNavTab, PanelContent>) {
    slots[TarkasNavTab.ASSISTANT] = PanelContent { s ->
      val player by s.player.state.collectAsState()
      val reduce by s.reduceMotion.collectAsState()
      val playback = when {
        player.file == null -> AssistantPlayback.NONE
        player.playing -> AssistantPlayback.PLAYING
        else -> AssistantPlayback.PAUSED
      }
      AssistantPanel(state, playback, reduce)
    }
  }

  companion object {
    fun create(app: TarkasState): AssistentePanels {
      val host = DesktopAssistantHost(app)
      val state = AssistenteState(app, host)
      host.timerSink = state::addTimer
      host.timerClear = state::cancelAllTimers
      return AssistentePanels(state, host)
    }
  }
}
