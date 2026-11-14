package com.example.data.library

/** Folder paths as the media library gives them, written one way: "Music/Rock", no slashes at the ends. */
object FolderPaths {
  const val ROOT = ""

  fun normalize(raw: String?): String = raw.orEmpty().replace('\\', '/').trim('/')

  /** The folder of a full file path, relative to the storage root ("/storage/emulated/0/Music/a.mp3" -> "Music"). */
  fun ofFile(path: String?): String {
    val p = path.orEmpty().replace('\\', '/')
    val parent = p.substringBeforeLast('/', "")
    val parts = parent.split('/').filter { it.isNotEmpty() }
    // /storage/<volume>/<folders...> and /sdcard/<folders...>
    val rest = when {
      parts.size >= 3 && parts[0] == "storage" && parts[1] == "emulated" -> parts.drop(3)
      parts.size >= 2 && parts[0] == "storage" -> parts.drop(2)
      parts.isNotEmpty() && parts[0] == "sdcard" -> parts.drop(1)
      else -> parts
    }
    return rest.joinToString("/")
  }

  fun parent(path: String): String = path.substringBeforeLast('/', ROOT)

  fun name(path: String): String = path.substringAfterLast('/')
}

/**
 * Which folders of the device are in the library. The nearest rule above a folder (or on it) decides;
 * with no rule, a folder is in. Checking or unchecking a folder sets the whole branch below it.
 */
class FolderRules private constructor(private val rules: Map<String, Boolean>) {
  /** True when some folder is left out. */
  val isFiltering: Boolean get() = rules.values.any { !it }

  fun isIncluded(folder: String): Boolean {
    var f = folder
    // The storage root ("") is a folder of its own: it does not decide for the folders below it.
    while (f.isNotEmpty()) {
      rules[f]?.let { return it }
      f = FolderPaths.parent(f)
    }
    return if (folder.isEmpty()) rules[""] ?: true else true
  }

  /** Puts [folder] and everything below it in or out, forgetting rules made inside it before. */
  fun with(folder: String, included: Boolean): FolderRules {
    val kept = rules.filterKeys { it != folder && !isBelow(it, folder) }
    return prune(kept + (folder to included))
  }

  /** The rules as saved: "1|Music" (in) or "0|Music/Podcasts" (out). */
  fun encode(): Set<String> = rules.mapTo(HashSet()) { (path, on) -> "${if (on) 1 else 0}|$path" }

  override fun equals(other: Any?) = other is FolderRules && other.rules == rules
  override fun hashCode() = rules.hashCode()

  companion object {
    val None = FolderRules(emptyMap())

    fun decode(saved: Set<String>): FolderRules = prune(
      saved.mapNotNull { s ->
        val bar = s.indexOf('|')
        if (bar != 1 || (s[0] != '0' && s[0] != '1')) null else FolderPaths.normalize(s.substring(2)) to (s[0] == '1')
      }.toMap()
    )

    private fun isBelow(path: String, folder: String) = folder.isNotEmpty() && path.startsWith("$folder/")

    /** Drops every rule that says what the rules above it already say. */
    private fun prune(all: Map<String, Boolean>): FolderRules {
      val kept = LinkedHashMap<String, Boolean>()
      for (path in all.keys.sortedBy { it.count { c -> c == '/' } }) {
        val value = all.getValue(path)
        val inherited = if (!path.contains('/')) true else FolderRules(kept).isIncluded(FolderPaths.parent(path))
        if (value != inherited) kept[path] = value
      }
      return FolderRules(kept)
    }
  }
}

/** A folder in the browser: [own] tracks directly in it, [total] with everything below. */
class FolderNode(val path: String, val own: Int, val children: List<FolderNode>) {
  val name: String get() = FolderPaths.name(path)
  val total: Int = own + children.sumOf { it.total }

  /** Tracks of this branch that the [rules] keep. */
  fun included(rules: FolderRules): Int =
    (if (rules.isIncluded(path)) own else 0) + children.sumOf { it.included(rules) }

  fun state(rules: FolderRules): FolderCheck {
    val n = included(rules)
    return when {
      n == total -> FolderCheck.ON
      n == 0 -> FolderCheck.OFF
      else -> FolderCheck.PARTIAL
    }
  }
}

enum class FolderCheck { ON, OFF, PARTIAL }

object FolderTree {
  /**
   * The folder tree of the music on the device, from the number of tracks per folder. Folders only
   * hold music directly or through their children; music straight in the storage root gets a row of its own.
   */
  fun build(countsByFolder: Map<String, Int>): List<FolderNode> {
    val own = HashMap<String, Int>()
    val childrenOf = HashMap<String, MutableSet<String>>()
    for ((raw, n) in countsByFolder) {
      val folder = FolderPaths.normalize(raw)
      own[folder] = (own[folder] ?: 0) + n
      var f = folder
      while (f.isNotEmpty()) {
        val p = FolderPaths.parent(f)
        if (!childrenOf.getOrPut(p) { java.util.TreeSet(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())) }.add(f)) break
        f = p
      }
    }
    fun node(path: String): FolderNode =
      FolderNode(path, own[path] ?: 0, childrenOf[path].orEmpty().map { node(it) })
    val root = node(FolderPaths.ROOT)
    // Music straight in the storage root is shown as its own row so it can still be switched off.
    return if (root.own > 0) listOf(FolderNode(FolderPaths.ROOT, root.own, emptyList())) + root.children else root.children
  }

  /** Rows to draw: a branch's children follow it while it is in [expanded]. */
  fun rows(nodes: List<FolderNode>, expanded: Set<String>, depth: Int = 0): List<FolderRow> =
    nodes.flatMap { n ->
      listOf(FolderRow(n, depth)) + if (n.path in expanded) rows(n.children, expanded, depth + 1) else emptyList()
    }
}

class FolderRow(val node: FolderNode, val depth: Int)
