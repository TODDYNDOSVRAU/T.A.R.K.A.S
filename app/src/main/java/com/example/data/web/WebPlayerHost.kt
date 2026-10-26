package com.example.data.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONTokener

/** A streaming service whose web player the app drives. */
enum class WebService(val label: String, val home: String) {
  YOUTUBE_MUSIC("YouTube Music", "https://music.youtube.com/"),
  SPOTIFY("Spotify", "https://open.spotify.com/")
}

/** What the hidden web player is doing now, as read from the page. */
data class WebNowPlaying(
  val active: Boolean = false,
  val playing: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L
)

/**
 * A WebView that ignores "window hidden" (screen off, app in the background). Without this Chromium
 * marks the page hidden and the player's audio stops; the foreground playback service keeps the process alive.
 */
private class KeepAliveWebView(context: Context) : WebView(context) {
  override fun onWindowVisibilityChanged(visibility: Int) {
    if (visibility != android.view.View.GONE) super.onWindowVisibilityChanged(visibility)
  }
}

/**
 * The hidden web player that plays songs found on YouTube Music. The app's own search shows the results and
 * its own player shows the controls; this WebView only produces the sound. It is attached to the window at a
 * size of one pixel, because Chromium throttles pages that are not attached. The position and the end of the
 * song are read from the page's media element once a second.
 */
object WebPlayerHost {
  private val main = Handler(Looper.getMainLooper())
  private var view: WebView? = null
  private var wakeLock: PowerManager.WakeLock? = null

  private val _state = MutableStateFlow(WebNowPlaying())
  val state: StateFlow<WebNowPlaying> = _state.asStateFlow()

  /** Called once when the song that was started has ended by itself. */
  private var onEnded: (() -> Unit)? = null
  private var endedSeen = 0
  private var wantPlaying = false
  private var autoplayTries = 0
  private var polling = false
  private var clickToPlay: String? = null
  private var defaultUserAgent = ""

  private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

  private const val SPOTIFY_PLAY = """
    (function(){var b=document.querySelector('[data-testid="play-button"]')||document.querySelector('button[aria-label="Play"],button[aria-label="Reproduzir"]');if(b)b.click()})()
  """

  private const val READ_SCRIPT = """
    (function(){
      var e=document.querySelector('video,audio');
      if(e&&!e.__tk){e.__tk=1;e.addEventListener('ended',function(){window.__tkEnded=(window.__tkEnded||0)+1;try{e.pause()}catch(x){}});}
      return JSON.stringify({p:e?!e.paused:false,pos:e?e.currentTime:0,dur:(e&&isFinite(e.duration))?e.duration:0,
        end:window.__tkEnded||0,has:!!e,rs:e?e.readyState:-1,er:(e&&e.error)?e.error.code:0,u:location.href.slice(0,70),ad:!!document.querySelector('.ad-showing')});
    })()
  """

  /** Creates the hidden player once, on the activity's window. */
  @SuppressLint("SetJavaScriptEnabled")
  fun attach(activity: Activity) {
    if (view != null) return
    val web = KeepAliveWebView(activity.applicationContext).apply {
      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        mediaPlaybackRequiresUserGesture = false
        cacheMode = WebSettings.LOAD_DEFAULT
      }
      alpha = 0.01f
      webChromeClient = WebChromeClient()
      webViewClient = WebViewClient()
    }
    defaultUserAgent = web.settings.userAgentString
    CookieManager.getInstance().apply {
      setAcceptCookie(true)
      setAcceptThirdPartyCookies(web, true)
    }
    (activity.window.decorView as ViewGroup).addView(web, ViewGroup.LayoutParams(1, 1))
    view = web
    // The activity's window is gone with the activity; a new one attaches a new player.
  }

  fun detach() {
    stop()
    (view?.parent as? ViewGroup)?.removeView(view)
    view?.destroy()
    view = null
  }

  /** Plays a Spotify song in its web player (needs the account signed in; free accounts play with ads). */
  fun playSpotify(context: Context, trackId: String, onEnded: () -> Unit) {
    start(context, "https://open.spotify.com/track/$trackId", desktop = true, clickToPlay = SPOTIFY_PLAY, onEnded = onEnded)
  }

  /** Plays a YouTube Music song. [onEnded] runs when it finishes by itself. */
  fun playYouTube(context: Context, videoId: String, onEnded: () -> Unit) {
    start(context, "https://music.youtube.com/watch?v=$videoId", desktop = false, clickToPlay = null, onEnded = onEnded)
  }

  private fun start(context: Context, url: String, desktop: Boolean, clickToPlay: String?, onEnded: () -> Unit) {
    val web = view ?: return
    web.settings.userAgentString = if (desktop) DESKTOP_UA else defaultUserAgent
    this.clickToPlay = clickToPlay
    this.onEnded = onEnded
    endedSeen = 0
    wantPlaying = true
    autoplayTries = 0
    _state.value = WebNowPlaying(active = true)
    web.onResume()
    web.resumeTimers()
    web.loadUrl(url)
    acquireWakeLock(context)
    startPolling()
  }

  fun togglePlayPause() {
    val web = view ?: return
    wantPlaying = !_state.value.playing
    web.evaluateJavascript(
      "(function(){var e=document.querySelector('video,audio');if(e){e.paused?e.play():e.pause()}})()", null
    )
  }

  fun seekTo(ms: Long) {
    view?.evaluateJavascript(
      "(function(){var e=document.querySelector('video,audio');if(e){e.currentTime=${ms / 1000.0}}})()", null
    )
    _state.value = _state.value.copy(positionMs = ms)
  }

  /** Stops the sound and forgets the page. The signed-in cookies stay. */
  fun stop() {
    polling = false
    wantPlaying = false
    onEnded = null
    view?.let {
      it.evaluateJavascript("(function(){var e=document.querySelector('video,audio');if(e)e.pause()})()", null)
      it.loadUrl("about:blank")
    }
    releaseWakeLock()
    _state.value = WebNowPlaying()
  }

  /** Signs out of every service: removes the cookies and the site data. */
  fun signOut(done: () -> Unit = {}) {
    stop()
    view?.clearCache(true)
    android.webkit.WebStorage.getInstance().deleteAllData()
    CookieManager.getInstance().removeAllCookies { done() }
    CookieManager.getInstance().flush()
  }

  private fun startPolling() {
    if (polling) return
    polling = true
    main.post(object : Runnable {
      override fun run() {
        if (!polling) return
        read()
        main.postDelayed(this, 1000L)
      }
    })
  }

  private fun read() {
    val web = view ?: return
    web.evaluateJavascript(READ_SCRIPT) { raw ->
      try {
        val text = JSONTokener(raw ?: return@evaluateJavascript).nextValue() as? String ?: return@evaluateJavascript
        val json = JSONObject(text)
        val playing = json.optBoolean("p")
        _state.value = WebNowPlaying(
          active = polling,
          playing = playing,
          positionMs = (json.optDouble("pos") * 1000).toLong(),
          durationMs = (json.optDouble("dur") * 1000).toLong()
        )
        // The page sometimes loads paused; start it a few times, unless the user paused on purpose.
        val click = clickToPlay
        if (wantPlaying && !playing && autoplayTries < 12 && (json.optBoolean("has") || click != null)) {
          autoplayTries++
          // Spotify only starts from its own play button; YouTube accepts the media element's play().
          web.evaluateJavascript(click ?: "(function(){var e=document.querySelector('video,audio');if(e)e.play()})()", null)
        }
        val ended = json.optInt("end")
        if (ended > endedSeen) {
          endedSeen = ended
          val callback = onEnded
          onEnded = null
          callback?.invoke()
        }
      } catch (_: Exception) {
      }
    }
  }

  private fun acquireWakeLock(context: Context) {
    if (wakeLock?.isHeld == true) return
    wakeLock = (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tarkas:webplayer")
      .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
  }

  private fun releaseWakeLock() {
    runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    wakeLock = null
  }
}
