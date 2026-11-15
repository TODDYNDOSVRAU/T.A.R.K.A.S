package com.example.ui.viewmodel

/** The pages Ajustes opens inside itself: About and the texts. */
enum class SettingsSubPage { ABOUT, TERMS, PRIVACY, LICENSES, CHANGELOG }

/** One place the user can be. The whole navigation is a list of these, kept across process death. */
sealed interface Route {
  /** A page of the tab bar, or Ajustes/Áudio/the player opened on top of another page. */
  data class Tab(val tab: TarkasNavTab) : Route

  /** A page inside Ajustes (About, Terms, …), over the Ajustes tab. */
  data class Settings(val page: SettingsSubPage) : Route

  /** The full-screen Curiosidade Musical page, opened from Ajustes. */
  data object CuriositySettings : Route

  /** A stable text form, for the saved state. */
  fun encode(): String = when (this) {
    is Tab -> "tab:${tab.name}"
    is Settings -> "settings:${page.name}"
    CuriositySettings -> "curiosity"
  }

  companion object {
    /** The route [text] names, or null when it names none (an old or damaged saved value). */
    fun decode(text: String): Route? = when {
      text == "curiosity" -> CuriositySettings
      text.startsWith("tab:") -> TarkasNavTab.entries.firstOrNull { it.name == text.removePrefix("tab:") }?.let(::Tab)
      text.startsWith("settings:") -> SettingsSubPage.entries.firstOrNull { it.name == text.removePrefix("settings:") }?.let(::Settings)
      else -> null
    }
  }
}

/**
 * The route stack: the last route is the one shown, the ones before it are where back goes. Pure, so every
 * rule is tested without a screen.
 */
class NavStack(initial: List<Route> = emptyList()) {
  private val routes = ArrayDeque<Route>().apply {
    addAll(initial)
    // A stack always shows something; Início is the floor.
    if (isEmpty()) addLast(Route.Tab(TarkasNavTab.HOME))
  }

  val current: Route get() = routes.last()
  val snapshot: List<Route> get() = routes.toList()

  /** The tab the user is on: the nearest tab route, so a page inside Ajustes still counts as Ajustes. */
  val currentTab: TarkasNavTab get() = routes.last { it is Route.Tab }.let { (it as Route.Tab).tab }

  val settingsPage: SettingsSubPage? get() = (current as? Route.Settings)?.page
  val showingCuriosity: Boolean get() = current == Route.CuriositySettings

  /** True while back has somewhere to go inside the app. */
  val canGoBack: Boolean get() = routes.size > 1 || currentTab != TarkasNavTab.HOME

  /**
   * Opens [route] on top of the current one; back returns to where it was opened from. Opening a route that
   * is already lower in the stack goes back to it instead of piling up copies.
   */
  fun open(route: Route) {
    if (route == current) return
    val existing = routes.indexOf(route)
    if (existing >= 0) while (routes.size > existing + 1) routes.removeLast() else routes.addLast(route)
  }

  /** A tab picked in the tab bar: a fresh start at that tab, like every tab bar on the platform. */
  fun select(tab: TarkasNavTab) {
    routes.clear()
    if (tab != TarkasNavTab.HOME) routes.addLast(Route.Tab(TarkasNavTab.HOME))
    routes.addLast(Route.Tab(tab))
  }

  /** Back: the route it was opened from; from a tab with nothing under it, Início. */
  fun back() {
    if (routes.size > 1) routes.removeLast() else {
      routes.clear()
      routes.addLast(Route.Tab(TarkasNavTab.HOME))
    }
  }

  /** The saved form of the whole stack. */
  fun encode(): ArrayList<String> = ArrayList(routes.map { it.encode() })

  companion object {
    fun decode(saved: List<String>?): NavStack = NavStack(saved.orEmpty().mapNotNull(Route::decode))
  }
}
