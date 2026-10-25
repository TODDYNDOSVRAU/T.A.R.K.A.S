package com.example.data.autoeq

import android.content.Context
import com.example.data.model.DeviceCorrection
import com.example.data.model.EqBand
import com.example.data.model.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** One fone in the AutoEq results: who measured it and where its curve lives. */
data class AutoEqEntry(
  val name: String,
  val source: String,
  val rig: String,
  /** Path inside results/, URL-encoded, e.g. "oratory1990/over-ear/Sennheiser%20HD%20650". */
  val path: String
) {
  /** Form factor in plain words, from the rig folder. */
  val form: String get() = when {
    "in-ear" in path.lowercase() || "in-ear" in rig.lowercase() -> "Intra-auricular"
    "earbud" in path.lowercase() -> "Earbud"
    else -> "Over-ear"
  }
}

/**
 * Parsers for the AutoEq project's published files (github.com/jaakkopasanen/AutoEq, MIT). The
 * curves come from measurements by oratory1990, crinacle, Rtings and others, fitted to the Harman
 * target. Pure functions, so they are unit tested.
 */
object AutoEqParser {
  // - [Sennheiser HD 650](./oratory1990/over-ear/Sennheiser%20HD%20650) by oratory1990
  // - [1Custom SA02](./crinacle/711%20in-ear/1Custom%20SA02) by crinacle on 711
  // Names and paths may hold parentheses ("(ANC Off)"), so the link runs up to ") by ".
  private val entryLine = Regex("""^- \[(.+?)]\(\./(.+?)\) by (.+?)(?: on (.+))?\s*$""")

  fun parseIndex(markdown: String): List<AutoEqEntry> = markdown.lineSequence().mapNotNull { line ->
    val m = entryLine.matchEntire(line.trim()) ?: return@mapNotNull null
    val (name, path, source, rig) = m.destructured
    val rigFolder = path.split('/').getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()
    AutoEqEntry(name = name, source = source.trim(), rig = rig.ifBlank { rigFolder }, path = path)
  }.toList()

  // Preamp: -6.1 dB
  // Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
  private val preampLine = Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
  private val filterLine = Regex(
    """Filter\s+\d+:\s*(ON|OFF)\s+(\w+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""",
    RegexOption.IGNORE_CASE
  )

  fun parseParametric(text: String, entry: AutoEqEntry): DeviceCorrection? {
    val preamp = preampLine.find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    val bands = filterLine.findAll(text).mapNotNull { m ->
      val type = when (m.groupValues[2].uppercase()) {
        "PK", "PEQ" -> FilterType.PEAK
        "LSC", "LS", "LSQ" -> FilterType.LOW_SHELF
        "HSC", "HS", "HSQ" -> FilterType.HIGH_SHELF
        "HP", "HPQ" -> FilterType.HIGH_PASS
        "LP", "LPQ" -> FilterType.LOW_PASS
        else -> return@mapNotNull null
      }
      EqBand(
        freqHz = (m.groupValues[3].toFloatOrNull() ?: return@mapNotNull null).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ),
        gainDb = (m.groupValues[4].toFloatOrNull() ?: 0f).coerceIn(-20f, 20f),
        q = (m.groupValues[5].toFloatOrNull() ?: 0.7f).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q),
        type = type,
        enabled = m.groupValues[1].equals("ON", ignoreCase = true)
      )
    }.toList()
    if (bands.isEmpty()) return null
    return DeviceCorrection(name = entry.name, source = entry.source, rig = entry.rig, bands = bands, preampDb = preamp)
  }

  /** Lowercase letters and digits only, so "hd650", "HD 650" and "HD-650" all match. */
  fun searchKey(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

  /** Entries whose name contains every word of [query], best matches (name starts with it) first. */
  fun search(entries: List<AutoEqEntry>, query: String, source: String? = null, limit: Int = 200): List<AutoEqEntry> {
    val words = query.trim().split(Regex("\\s+")).map(::searchKey).filter { it.isNotEmpty() }
    val pool = if (source == null) entries else entries.filter { it.source == source }
    if (words.isEmpty()) return pool.take(limit)
    val joined = words.joinToString("")
    return pool.asSequence()
      .map { it to searchKey(it.name) }
      .filter { (_, key) -> words.all { it in key } }
      .sortedWith(compareBy({ !it.second.startsWith(joined) }, { it.second.length }))
      .map { it.first }
      .take(limit)
      .toList()
  }
}

/** Downloads the AutoEq index once (cached for a week) and each fone's curve on demand. */
class AutoEqRepository(context: Context) {
  private val cacheFile = File(context.filesDir, "autoeq_index.md")
  @Volatile private var entries: List<AutoEqEntry>? = null

  suspend fun index(forceRefresh: Boolean = false): List<AutoEqEntry> = withContext(Dispatchers.IO) {
    entries?.takeIf { !forceRefresh }?.let { return@withContext it }
    val fresh = cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < WEEK_MS
    val text = if (fresh && !forceRefresh) {
      cacheFile.readText()
    } else {
      try {
        download("$BASE/INDEX.md").also { cacheFile.writeText(it) }
      } catch (e: Exception) {
        // Offline: an old index is better than none.
        if (cacheFile.exists()) cacheFile.readText() else throw e
      }
    }
    AutoEqParser.parseIndex(text).also { entries = it }
  }

  suspend fun correction(entry: AutoEqEntry): DeviceCorrection? = withContext(Dispatchers.IO) {
    val folder = entry.path.substringAfterLast('/')
    AutoEqParser.parseParametric(download("$BASE/${entry.path}/$folder%20ParametricEQ.txt"), entry)
  }

  private fun download(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 20_000
    try {
      if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
      return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  companion object {
    private const val BASE = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
  }
}
