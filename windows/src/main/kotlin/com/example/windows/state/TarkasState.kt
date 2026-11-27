package com.example.windows.state

import com.example.data.db.AppDatabase
import com.example.data.db.SettingEntity
import com.example.data.model.EqSettings
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.NavStack
import com.example.ui.viewmodel.Route
import com.example.ui.viewmodel.SettingsSubPage
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.audio.PlayerState
import com.example.windows.base.Notice
import com.example.windows.base.Toaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Key/value settings on the Room `settings` table (same schema as the app: scope, key, value).
 * Reads are served from memory after [load]; writes go to the database on [scope].
 */
class SettingsStore(private val db: AppDatabase, private val scope: CoroutineScope) {
  private val cache = HashMap<String, String>()
  /** Writes that did not reach the database since start (shown by tests; the value stays in memory). */
  @Volatile var failures = 0
    private set
  private fun k(s: String, key: String) = "$s\u0000$key"

  fun load() = runBlocking { db.settings().all().forEach { cache[k(it.scope, it.key)] = it.value } }
  fun get(scope: String, key: String): String? = cache[k(scope, key)]
  fun put(scope: String, key: String, value: String) {
    cache[k(scope, key)] = value
    // The value is already live in memory. A write that fails (the database closed at exit, a locked file) is
    // reported and dropped, never thrown into the scope, where it would end the app or a test that has moved on.
    this.scope.launch {
      try { db.settings().put(SettingEntity(scope, key, value)) }
      catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) { failures++; System.err.println("T.A.R.K.A.S.: setting $scope/$key was not saved: ${e.message}") }
    }
  }
}

/**
 * Desktop substitute for TarkasViewModel: the core every screen needs, with the same member names.
 * Area sessions add their own state classes in their own packages and take this one as a parameter; they do
 * not grow this class. [scope] is injectable so tests can use a test dispatcher.
 */
class TarkasState(
  val player: DesktopPlayer,
  val db: AppDatabase,
  val toaster: Toaster = Toaster(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
  val settings = SettingsStore(db, scope).also { it.load() }

  // Navigation (NavStack is shared with the app and keeps its rules)
  private var nav = NavStack.decode(settings.get(GLOBAL, KEY_NAV)?.split('\n')?.filter(String::isNotEmpty))
  private val _route = MutableStateFlow(nav.current)
  val route: StateFlow<Route> = _route.asStateFlow()
  private val _currentTab = MutableStateFlow(nav.currentTab)
  val currentTab: StateFlow<TarkasNavTab> = _currentTab.asStateFlow()
  private val _settingsPage = MutableStateFlow(nav.settingsPage)
  val settingsPage: StateFlow<SettingsSubPage?> = _settingsPage.asStateFlow()
  private val _canGoBack = MutableStateFlow(nav.canGoBack)
  val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

  private fun navChanged() {
    _route.value = nav.current
    _currentTab.value = nav.currentTab
    _settingsPage.value = nav.settingsPage
    _canGoBack.value = nav.canGoBack
    settings.put(GLOBAL, KEY_NAV, nav.encode().joinToString("\n"))
  }

  fun selectTab(tab: TarkasNavTab) { nav.select(tab); navChanged() }
  fun setTab(tab: TarkasNavTab) = selectTab(tab)
  fun open(route: Route) { nav.open(route); navChanged() }
  fun openSettingsPage(page: SettingsSubPage) = open(Route.Settings(page))
  /** Back inside the app. False when already at the floor (Início). */
  fun goBack(): Boolean {
    if (!nav.canGoBack) return false
    nav.back(); navChanged(); return true
  }

  // Theme
  private val _themeMode = MutableStateFlow(settings.get(GLOBAL, KEY_THEME) ?: ThemeMode.SYSTEM)
  val themeMode: StateFlow<String> = _themeMode.asStateFlow()
  fun setThemeMode(mode: String) { _themeMode.value = mode; settings.put(GLOBAL, KEY_THEME, mode) }

  private val _reduceMotion = MutableStateFlow(settings.get(GLOBAL, KEY_REDUCE_MOTION) == "true")
  val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()
  fun setReduceMotion(on: Boolean) { _reduceMotion.value = on; settings.put(GLOBAL, KEY_REDUCE_MOTION, on.toString()) }

  // Nickname (D8, 2026-11-27): optional; empty falls back to the Windows user name in [displayName].
  private val _nickname = MutableStateFlow(cleanNickname(settings.get(GLOBAL, KEY_NICKNAME) ?: ""))
  val nickname: StateFlow<String> = _nickname.asStateFlow()
  fun setNickname(raw: String) {
    val v = cleanNickname(raw)
    if (v == _nickname.value) return
    _nickname.value = v; settings.put(GLOBAL, KEY_NICKNAME, v)
  }
  /** The name the Início greeting uses: the nickname, else the Windows user name, else "" (greeting without a name). */
  val displayName: StateFlow<String> = _nickname.map { greetingName(it, System.getProperty("user.name")) }
    .stateIn(scope, SharingStarted.Eagerly, greetingName(_nickname.value, System.getProperty("user.name")))

  // Playback (wired to DesktopPlayer)
  val playerState: StateFlow<PlayerState> = player.state
  val isPlaying: StateFlow<Boolean> = player.state.map { it.playing }.stateIn(scope, SharingStarted.Eagerly, false)
  val positionMs: StateFlow<Long> = player.state.map { it.positionMs }.stateIn(scope, SharingStarted.Eagerly, 0L)
  val durationMs: StateFlow<Long> = player.state.map { it.durationMs }.stateIn(scope, SharingStarted.Eagerly, 0L)
  fun playFile(file: java.io.File) = player.play(file)
  fun togglePlayPause() = player.togglePause()
  fun seekTo(ms: Long) = player.seek(ms)
  fun stop() = player.stop()

  // Equalizer settings (the DSP in DesktopPlayer follows them)
  private val _eqSettings = MutableStateFlow(EqSettings())
  val eqSettings: StateFlow<EqSettings> = _eqSettings.asStateFlow()
  fun updateEq(transform: (EqSettings) -> EqSettings) {
    val next = transform(_eqSettings.value)
    _eqSettings.value = next
    player.setEq(next)
  }
  fun setEqEnabled(on: Boolean) = updateEq { it.copy(enabled = on) }
  fun resetEq() = updateEq { EqSettings(enabled = it.enabled) }

  // Messages
  val notice: StateFlow<Notice?> = toaster.notice
  fun showNotice(text: String) = toaster.show(text)
  fun showNotice(notice: Notice) = toaster.show(notice)
  fun dismissNotice(id: Long) = toaster.dismiss(id)

  companion object {
    const val GLOBAL = "global"
    const val KEY_NAV = "nav_stack"
    const val KEY_THEME = "theme_mode"
    const val KEY_REDUCE_MOTION = "reduce_motion"
    const val KEY_NICKNAME = "nickname"
    const val NICKNAME_MAX = 24

    /** Control characters out, runs of spaces folded to one, trimmed, at most [NICKNAME_MAX] characters. */
    fun cleanNickname(raw: String): String =
      raw.filterNot { it.isISOControl() }.replace(Regex("\\s+"), " ").trim().take(NICKNAME_MAX).trimEnd()

    /** Nickname first; otherwise the account name with its first letter in upper case ("todyn" reads "Todyn"). */
    fun greetingName(nickname: String, systemUser: String?): String {
      cleanNickname(nickname).takeIf { it.isNotEmpty() }?.let { return it }
      val user = cleanNickname(systemUser ?: "")
      return user.replaceFirstChar { it.titlecase() }
    }
  }
}
