package com.example.data.web

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.Track
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * Searches YouTube Music without an API key: it opens the service's own search page in a hidden WebView
 * (signed in when the user connected the account) and reads the song rows from the page. The page is
 * not an official interface, so [parse] is written to fail soft: an unreadable row is skipped, and a page
 * that changed gives an empty list instead of a crash.
 */
object WebSearchHost {
  private val main = Handler(Looper.getMainLooper())
  private var view: WebView? = null
  private val lock = Mutex()

  /** Search views whose last page failed to load (offline); each view has its own state. */
  private val failed = java.util.Collections.synchronizedSet(mutableSetOf<WebView>())

  private const val ROWS_SCRIPT = """
    (function(){
      var out=[];var rows=document.querySelectorAll('ytmusic-responsive-list-item-renderer');
      for(var i=0;i<rows.length;i++){
        var r=rows[i];var a=r.querySelector('a[href*="watch?v="]');if(!a)continue;
        var ar=r.querySelector('a[href*="channel/UC"]');
        var al=r.querySelector('a[href*="browse/MPRE"]');
        out.push({h:a.getAttribute('href'),l:a.getAttribute('aria-label')||'',t:r.innerText,ar:ar?ar.textContent.trim():'',al:al?al.textContent.trim():''});
      }
      return JSON.stringify(out);
    })()
  """

  private var spotifyView: WebView? = null

  private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

  private const val SPOTIFY_ROWS_SCRIPT = """
    (function(){
      var out=[];var rows=document.querySelectorAll('[data-testid="tracklist-row"]');
      for(var i=0;i<rows.length;i++){
        var r=rows[i];var a=r.querySelector('a[href*="/track/"]');if(!a)continue;
        var ars=r.querySelectorAll('a[href*="/artist/"]');var names=[];
        for(var j=0;j<ars.length;j++)names.push(ars[j].textContent.trim());
        var im=r.querySelector('img');
        out.push({h:a.getAttribute('href'),l:a.textContent.trim(),t:r.innerText,ar:names.join(', '),img:im?im.src:''});
      }
      return JSON.stringify(out);
    })()
  """

  @SuppressLint("SetJavaScriptEnabled")
  private fun newView(activity: Activity, desktop: Boolean): WebView {
    val web = WebView(activity.applicationContext).apply {
      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        // The page would otherwise start a preview of the first result.
        mediaPlaybackRequiresUserGesture = true
        if (desktop) {
          userAgentString = DESKTOP_UA
          useWideViewPort = true
          loadWithOverviewMode = true
        }
      }
      alpha = 0.01f
      webViewClient = object : WebViewClient() {
        override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) { v?.let(failed::remove) }
        override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
          if (request?.isForMainFrame == true) v?.let(failed::add)
        }
      }
    }
    if (desktop) { web.scaleX = 0.001f; web.scaleY = 0.001f }
    return web
  }

  /** Puts [web] on [activity]'s window, moving it off a previous (destroyed) window if needed. */
  private fun place(activity: Activity, web: WebView, desktop: Boolean) {
    val root = activity.window.decorView as ViewGroup
    if (web.parent === root) return
    (web.parent as? ViewGroup)?.removeView(web)
    // A desktop page lays out for a wide window; give it one, scaled to a single pixel on screen.
    root.addView(web, ViewGroup.LayoutParams(if (desktop) 1280 else 1, if (desktop) 900 else 1))
  }

  fun attach(activity: Activity) {
    val v = view ?: newView(activity, desktop = false).also { view = it }
    val s = spotifyView ?: newView(activity, desktop = true).also { spotifyView = it }
    place(activity, v, desktop = false)
    place(activity, s, desktop = true)
  }

  /**
   * The activity is going away (rotation, theme change, exit): take the views off its window so they
   * do not keep it in memory. They stay alive, and the next activity puts them on its own window.
   */
  fun release(activity: Activity) {
    val root = activity.window.decorView as ViewGroup
    listOf(view, spotifyView).forEach { w -> if (w != null && w.parent === root) root.removeView(w) }
  }

  fun detach() {
    listOf(view, spotifyView).forEach { w ->
      (w?.parent as? ViewGroup)?.removeView(w)
      w?.destroy()
    }
    view = null
    spotifyView = null
  }

  /** The songs for [query], or null when the page could not be loaded (offline). */
  suspend fun searchYouTube(query: String): List<Track>? = scrape(
    view, "https://music.youtube.com/search?q=" + URLEncoder.encode(query, "UTF-8"), ROWS_SCRIPT, ::parse
  )

  /** The songs for [query] on Spotify's web player; null when offline. Works signed out. */
  suspend fun searchSpotify(query: String): List<Track>? = scrape(
    spotifyView, "https://open.spotify.com/search/" + URLEncoder.encode(query, "UTF-8").replace("+", "%20"),
    SPOTIFY_ROWS_SCRIPT, ::parseSpotify
  )

  private suspend fun scrape(
    web: WebView?, url: String, script: String, parser: (String?) -> List<Track>
  ): List<Track>? = lock.withLock {
    web ?: return null
    suspendCancellableCoroutine { cont ->
      failed.remove(web)
      web.loadUrl(url)
      var tries = 0
      var lastCount = -1
      var stable = 0
      var best: List<Track> = emptyList()
      val probe = object : Runnable {
        override fun run() {
          if (!cont.isActive) return
          if (web in failed) { cont.resume(null); return }
          tries++
          web.evaluateJavascript(script) { raw ->
            if (!cont.isActive) return@evaluateJavascript
            val tracks = parser(raw)
            if (tracks.isNotEmpty()) best = tracks
            stable = if (tracks.size == lastCount && tracks.isNotEmpty()) stable + 1 else 0
            lastCount = tracks.size
            if (stable >= 1 || tries >= 16) {
              cont.resume(if (best.isEmpty() && web in failed) null else best)
            } else {
              main.postDelayed(this, 700L)
            }
          }
        }
      }
      main.postDelayed(probe, 1200L)
      cont.invokeOnCancellation { main.removeCallbacks(probe) }
    }
  }

  private val songTypes = Regex("^(m[úu]sica|song|single|epis[óo]dio)$", RegexOption.IGNORE_CASE)
  private val otherTypes = Regex("^(v[íi]deo|video|[áa]lbum|album|ep|playlist|artista|artist|perfil|profile)$", RegexOption.IGNORE_CASE)
  private val duration = Regex("^(\\d+):(\\d{2})(?::(\\d{2}))?$")
  private val counts = Regex("(tocou|plays?|visualiza|views|ouvintes|listeners|reprodu)", RegexOption.IGNORE_CASE)

  /** Turns the rows read from the page into tracks; the shape of the page is the only thing relied on. */
  internal fun parse(raw: String?): List<Track> {
    val text = try { JSONTokener(raw ?: return emptyList()).nextValue() as? String } catch (_: Exception) { null }
      ?: return emptyList()
    val rows = try { JSONArray(text) } catch (_: Exception) { return emptyList() }
    val seen = LinkedHashMap<String, Track>()
    for (i in 0 until rows.length()) {
      val row = rows.optJSONObject(i) ?: continue
      track(row)?.let { if (it.webId !in seen) seen[it.webId!!] = it }
    }
    // Songs listed under an artist's own shelf come without the artist: borrow the most common one.
    val common = seen.values.map { it.artist }.filter { it != UNKNOWN }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    return seen.values.map { if (it.artist == UNKNOWN && common != null) it.copy(artist = common) else it }
  }

  private const val UNKNOWN = "YouTube Music"

  internal fun parseSpotify(raw: String?): List<Track> {
    val text = try { JSONTokener(raw ?: return emptyList()).nextValue() as? String } catch (_: Exception) { null }
      ?: return emptyList()
    val rows = try { JSONArray(text) } catch (_: Exception) { return emptyList() }
    val seen = LinkedHashMap<String, Track>()
    for (i in 0 until rows.length()) {
      val row = rows.optJSONObject(i) ?: continue
      val id = Regex("/track/([A-Za-z0-9]{10,})").find(row.optString("h"))?.groupValues?.get(1) ?: continue
      val title = row.optString("l").ifBlank { continue }
      // The duration is the last m:ss in the row text.
      val ms = Regex("(\\d+):(\\d{2})").findAll(row.optString("t")).lastOrNull()?.destructured
        ?.let { (m, s) -> (m.toLong() * 60 + s.toLong()) * 1000 } ?: 0L
      seen.getOrPut(id) {
        Track(
          id = "sp:$id", title = title, artist = row.optString("ar").ifBlank { "Spotify" }, album = "Spotify",
          durationMs = ms, genre = "", era = "", colorHex = 0xFF1DB954, artworkUrl = row.optString("img").ifBlank { null },
          webSource = "spotify", webId = id
        )
      }
    }
    return seen.values.toList()
  }

  private fun track(row: JSONObject): Track? {
    val id = Regex("v=([A-Za-z0-9_-]{6,})").find(row.optString("h"))?.groupValues?.get(1) ?: return null
    // The row text mixes lines and "•"-joined parts depending on the layout: flatten both into tokens.
    val tokens = row.optString("t").split('\n').flatMap { it.split('•') }.map { it.trim() }.filter { it.isNotEmpty() }
    val title = row.optString("l").ifBlank { tokens.firstOrNull().orEmpty() }
    if (title.isBlank()) return null
    if (tokens.any { otherTypes.matches(it) }) return null
    var durationMs = 0L
    val rest = ArrayList<String>()
    for (token in tokens) {
      val d = duration.matchEntire(token)
      when {
        d != null -> {
          val (a, b, c) = d.destructured
          durationMs = if (c.isEmpty()) (a.toLong() * 60 + b.toLong()) * 1000 else ((a.toLong() * 60 + b.toLong()) * 60 + c.toLong()) * 1000
        }
        token == title || token == row.optString("al") || songTypes.matches(token) || counts.containsMatchIn(token) -> {}
        token !in rest -> rest.add(token)
      }
    }
    val artist = row.optString("ar").ifBlank { rest.firstOrNull().orEmpty() }
    return Track(
      id = "yt:$id", title = title, artist = artist.ifBlank { UNKNOWN }, album = "YouTube Music",
      durationMs = durationMs, genre = "", era = "", colorHex = 0xFFE04A4A,
      artworkUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
      webSource = "youtube", webId = id
    )
  }
}
