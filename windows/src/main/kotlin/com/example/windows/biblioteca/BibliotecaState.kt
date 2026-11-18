package com.example.windows.biblioteca

import com.example.data.library.DeviceCatalog
import com.example.data.library.FolderRules
import com.example.data.playback.LibrarySearch
import com.example.data.model.Track
import com.example.data.playback.LibrarySort
import com.example.data.playback.SongOrder
import com.example.windows.player.PlayerController
import com.example.windows.res.Res
import com.example.windows.res.player_problem_offline
import com.example.windows.res.trNow
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything the Biblioteca and Início screens read and do: the folders that are scanned, what the scan found,
 * the order, favourites, playlists and counters ([LibraryStore]), and playing through the shared
 * [PlayerController]. Desktop twin of the library half of the Android `LibraryState`/`LibraryActions`.
 * One instance per window; the screens only read flows and call methods.
 */
class BibliotecaState(
  val app: TarkasState,
  val player: PlayerController,
  private val scope: CoroutineScope,
  val dataDir: File = defaultDataDir(),
  val scanner: LibraryScanner = LibraryScanner(File(dataDir, "library-cache.json")),
  val store: LibraryStore = LibraryStore(app.db, scope) { app.showNotice(it.message ?: it.toString()) }
) {
  private val settings = app.settings

  // Folders and rules
  private val _roots = MutableStateFlow(loadRoots())
  /** The folders that are scanned. */
  val roots: StateFlow<List<File>> = _roots.asStateFlow()

  private val _rules = MutableStateFlow(FolderRules.decode(settings.get(SCOPE, KEY_RULES).orEmpty().lines().filter { it.isNotBlank() }.toSet()))
  /** Which scanned folders are listed. */
  val rules: StateFlow<FolderRules> = _rules.asStateFlow()

  private val _order = MutableStateFlow(SongOrder.fromKey(settings.get(SCOPE, KEY_ORDER)))
  val order: StateFlow<SongOrder> = _order.asStateFlow()

  // The scan
  private val _catalog = MutableStateFlow(scanner.loadCache())
  /** Everything the scan found (before the folder rules). */
  val catalog: StateFlow<DeviceCatalog> = _catalog.asStateFlow()
  private val _scanning = MutableStateFlow(false)
  val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
  private val _scanned = MutableStateFlow(0)
  /** Files read so far in a scan that is running. */
  val scanned: StateFlow<Int> = _scanned.asStateFlow()
  private val _everScanned = MutableStateFlow(false)
  /** False until the first scan of this run ends, so an empty library says "loading" first. */
  val everScanned: StateFlow<Boolean> = _everScanned.asStateFlow()

  /** The scanned songs the folder rules let through, plus the files picked one by one. */
  val library: StateFlow<List<Track>> = combine(_catalog, _rules, store.picked) { c, r, picked ->
    val device = if (r.isFiltering) c.tracks.filter { r.isIncluded(c.folderOf[it.id].orEmpty()) } else c.tracks
    val ids = device.mapTo(HashSet()) { it.id }
    device + picked.filter { it.id !in ids }
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  /** The library in the chosen order. */
  val sorted: StateFlow<List<Track>> = combine(library, _order, store.stats) { tracks, order, stats ->
    LibrarySort.sorted(tracks, order, stats.mapValues { it.value.plays })
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  private var scanJob: kotlinx.coroutines.Job? = null

  init {
    JamendoCatalog.clientIdOverride = settings.get(SCOPE, KEY_JAMENDO)?.takeIf { it.isNotBlank() }
    rescan()
    scope.launch { countPlays() }
  }

  fun rescan() {
    scanJob?.cancel()
    scanJob = scope.launch {
      _scanning.value = true
      _scanned.value = 0
      try {
        val before = _catalog.value
        val after = withContext(Dispatchers.IO) { scanner.scan(_roots.value, before) { _scanned.value = it } }
        if (after !== before) {
          _catalog.value = after
          withContext(Dispatchers.IO) { scanner.saveCache(after) }
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        app.showNotice(e.message ?: e.toString())
      } finally {
        _scanning.value = false
        _everScanned.value = true
      }
    }
  }

  fun addRoot(dir: File) {
    if (!dir.isDirectory || _roots.value.any { it.absoluteFile == dir.absoluteFile }) return
    setRoots(_roots.value + dir.absoluteFile)
  }

  fun removeRoot(dir: File) = setRoots(_roots.value.filter { it != dir })

  private fun setRoots(list: List<File>) {
    _roots.value = list
    settings.put(SCOPE, KEY_ROOTS, list.joinToString("\n") { it.path })
    rescan()
  }

  fun setRules(rules: FolderRules) {
    _rules.value = rules
    settings.put(SCOPE, KEY_RULES, rules.encode().sorted().joinToString("\n"))
  }

  fun setOrder(order: SongOrder) {
    _order.value = order
    settings.put(SCOPE, KEY_ORDER, order.key)
  }

  fun setJamendoClientId(id: String) {
    JamendoCatalog.clientIdOverride = id.trim().ifBlank { null }
    settings.put(SCOPE, KEY_JAMENDO, id.trim())
  }

  /** The folder name of each root, as the folder picker shows the tree. */
  fun rootLabels(): Map<File, String> = scanner.rootLabels(_roots.value)

  // Search over the library (Início and Biblioteca use the same rules)
  fun search(query: String): List<Track> = LibrarySearch.filter(library.value, query)

  // Playing
  /** Plays [track] inside [context] (the list it was chosen from). Online songs are fetched first. */
  fun play(track: Track, context: List<Track> = listOf(track)) {
    if (track.isLocalFile || track.filePath?.let { File(it).isFile } == true) {
      player.play(track, context.filter { it.isLocalFile }.ifEmpty { listOf(track) })
    } else {
      scope.launch { fetch(track)?.let { player.play(it, listOf(it)) } }
    }
  }

  fun playNext(track: Track) = withLocal(track) { player.playNext(it) }

  fun addToQueue(track: Track) = withLocal(track) { player.addToQueue(it) }

  private fun withLocal(track: Track, then: (Track) -> Unit) {
    if (track.isLocalFile) then(track) else scope.launch { fetch(track)?.let(then) }
  }

  /** Downloads an online song to the cache and returns it as a local one (same id), or null when it failed. */
  private suspend fun fetch(track: Track): Track? {
    val url = track.filePath ?: return null
    val file = File(dataDir, "cache/" + track.id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".mp3")
    if (!file.isFile) {
      val ok = withContext(Dispatchers.IO) { runCatching { CatalogHttp.download(url, file) }.isSuccess }
      if (!ok) { app.showNotice(trNow(Res.string.player_problem_offline)); return null }
    }
    return track.copy(isLocalFile = true, filePath = file.absolutePath)
  }

  // Counting plays: one is counted when the track passes 30 s or half of its length, whichever is shorter.
  private suspend fun countPlays() {
    var armed = true
    player.engine.collect { s ->
      val current = player.ui.value.current ?: return@collect
      if (s.positionMs < RESET_MS) armed = true
      val limit = if (s.durationMs > 0) minOf(COUNT_AFTER_MS, s.durationMs / 2) else COUNT_AFTER_MS
      if (armed && s.playing && s.positionMs >= limit) {
        armed = false
        store.recordPlay(current.id)
      }
    }
  }

  private fun loadRoots(): List<File> {
    val saved = settings.get(SCOPE, KEY_ROOTS)
    if (saved != null) return saved.lines().filter { it.isNotBlank() }.map(::File)
    val music = File(System.getProperty("user.home"), "Music")
    return if (music.isDirectory) listOf(music) else emptyList()
  }

  companion object {
    const val SCOPE = "library"
    const val KEY_ROOTS = "roots"
    const val KEY_RULES = "folder_rules"
    const val KEY_ORDER = "song_order"
    const val KEY_JAMENDO = "jamendo_client_id"
    const val COUNT_AFTER_MS = 30_000L
    private const val RESET_MS = 2_000L

    fun defaultDataDir(): File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "TARKAS")
  }
}
