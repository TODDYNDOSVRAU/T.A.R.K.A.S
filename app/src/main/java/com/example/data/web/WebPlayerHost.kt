package com.example.data.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
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
  /** False from loadUrl until the new page has committed: reads before that come from the old page. */
  private var pageReady = false
  /** Runs when the renderer dies or the song cannot start; the controller moves on. */
  private var onFailed: (() -> Unit)? = null
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
        end:window.__tkEnded||0,has:!!e});
    })()
  """

  /**
   * Puts the hidden player on [activity]'s window, creating it the first time. A new activity (after a
   * rotation, say) takes the existing player over, so a song keeps playing through it.
   */
  @SuppressLint("SetJavaScriptEnabled")
  fun attach(activity: Activity) {
    val root = activity.window.decorView as ViewGroup
    view?.let { existing ->
      if (existing.parent !== root) {
        (existing.parent as? ViewGroup)?.removeView(existing)
        root.addView(existing, ViewGroup.LayoutParams(1, 1))
      }
      return
    }
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
      webViewClient = PlayerClient()
    }
    defaultUserAgent = web.settings.userAgentString
    CookieManager.getInstance().apply {
      setAcceptCookie(true)
      setAcceptThirdPartyCookies(web, true)
    }
    root.addView(web, ViewGroup.LayoutParams(1, 1))
    view = web
    appContext = activity.applicationContext
  }

  /** The activity is going away: take the player off its window so it does not keep it in memory. */
  fun release(activity: Activity) {
    val root = activity.window.decorView as ViewGroup
    view?.let { if (it.parent === root) root.removeView(it) }
  }

  private class PlayerClient : WebViewClient() {
    override fun onPageCommitVisible(view: WebView, url: String?) {
      if (url != null && url != "about:blank") pageReady = true
    }

    /**
     * The renderer of a hidden page is often the first thing the system kills under memory pressure.
     * Left unhandled, that takes the whole app down; instead the view is dropped (the next activity
     * creates a fresh one) and the current song is reported as failed.
     */
    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
      if (view !== WebPlayerHost.view) return true
      val failed = onFailed
      stopPolling()
      wantPlaying = false
      onEnded = null
      onFailed = null
      releaseAudio()
      (view.parent as? ViewGroup)?.removeView(view)
      view.destroy()
      WebPlayerHost.view = null
      _state.value = WebNowPlaying()
      failed?.invoke()
      return true
    }
  }

  fun detach() {
    stop()
    (view?.parent as? ViewGroup)?.removeView(view)
    view?.destroy()
    view = null
  }

  /** Plays a Spotify song in its web player (needs the account signed in; free accounts play with ads). */
  fun playSpotify(context: Context, trackId: String, onEnded: () -> Unit, onFailed: () -> Unit): Boolean =
    start(context, "https://open.spotify.com/track/$trackId", desktop = true, clickToPlay = SPOTIFY_PLAY, onEnded, onFailed)

  /**
   * Plays a YouTube Music song. [onEnded] runs when it finishes by itself, [onFailed] if the page dies.
   * Returns false when there is no player to play on (no activity has attached one yet).
   */
  fun playYouTube(context: Context, videoId: String, onEnded: () -> Unit, onFailed: () -> Unit): Boolean =
    start(context, "https://music.youtube.com/watch?v=$videoId", desktop = false, clickToPlay = null, onEnded, onFailed)

  private fun start(
    context: Context,
    url: String,
    desktop: Boolean,
    clickToPlay: String?,
    onEnded: () -> Unit,
    onFailed: () -> Unit
  ): Boolean {
    val web = view ?: return false
    web.settings.userAgentString = if (desktop) DESKTOP_UA else defaultUserAgent
    this.clickToPlay = clickToPlay
    this.onEnded = onEnded
    this.onFailed = onFailed
    endedSeen = 0
    pageReady = false
    wantPlaying = true
    autoplayTries = 0
    _state.value = WebNowPlaying(active = true)
    web.onResume()
    web.resumeTimers()
    web.loadUrl(url)
    takeAudio(context)
    startPolling()
    return true
  }

  /** True while the user wants the song to play (false once they paused it). */
  val wantsToPlay: Boolean get() = wantPlaying

  fun togglePlayPause() {
    if (wantPlaying) pause() else resume()
  }

  /** Pauses on the user's request, or when the headphones are unplugged. */
  fun pause() {
    val web = view ?: return
    wantPlaying = false
    web.evaluateJavascript("(function(){var e=document.querySelector('video,audio');if(e)e.pause()})()", null)
    releaseAudio()
  }

  fun resume() {
    val web = view ?: return
    val context = appContext ?: return
    if (!_state.value.active) return
    wantPlaying = true
    autoplayTries = 0
    takeAudio(context)
    web.evaluateJavascript("(function(){var e=document.querySelector('video,audio');if(e)e.play()})()", null)
    startPolling()
  }

  // ---- headphones and the wake lock ----
  // Audio focus is not requested here: the WebView takes it itself when the page plays (and pauses on a
  // call), so a request of our own would be lost to the page at once and pause the song.

  private var appContext: Context? = null
  private var noisyRegistered = false

  private val noisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      // Headphones unplugged or Bluetooth gone: do not carry on through the speaker.
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && wantPlaying) pause()
    }
  }

  private fun takeAudio(context: Context) {
    if (!noisyRegistered) {
      ContextCompat.registerReceiver(
        context.applicationContext, noisyReceiver,
        IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED
      )
      noisyRegistered = true
    }
    acquireWakeLock(context)
  }

  private fun releaseAudio() {
    if (noisyRegistered) {
      runCatching { appContext?.unregisterReceiver(noisyReceiver) }
      noisyRegistered = false
    }
    releaseWakeLock()
  }

  fun seekTo(ms: Long) {
    view?.evaluateJavascript(
      "(function(){var e=document.querySelector('video,audio');if(e){e.currentTime=${ms / 1000.0}}})()", null
    )
    _state.value = _state.value.copy(positionMs = ms)
  }

  /** Stops the sound and forgets the page. The signed-in cookies stay. */
  fun stop() {
    stopPolling()
    wantPlaying = false
    onEnded = null
    onFailed = null
    pageReady = false
    view?.let {
      it.evaluateJavascript("(function(){var e=document.querySelector('video,audio');if(e)e.pause()})()", null)
      it.loadUrl("about:blank")
    }
    releaseAudio()
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

  /** The one polling loop; removed before every new post, so quick track changes never stack loops. */
  private val poll = object : Runnable {
    override fun run() {
      if (!polling) return
      read()
      main.postDelayed(this, 1000L)
    }
  }

  private fun startPolling() {
    polling = true
    main.removeCallbacks(poll)
    main.post(poll)
  }

  private fun stopPolling() {
    polling = false
    main.removeCallbacks(poll)
  }

  private fun read() {
    val web = view ?: return
    if (!pageReady) return
    web.evaluateJavascript(READ_SCRIPT) { raw ->
      // A read that started before a new song was loaded belongs to the old page.
      if (!pageReady || web !== view) return@evaluateJavascript
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
        // Paused by the user: read once more to show it, then stop polling until they resume.
        if (!wantPlaying && !playing) stopPolling()
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
