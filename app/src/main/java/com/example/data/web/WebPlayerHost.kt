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
 * The hidden web player that plays songs found on YouTube Music and Spotify. The app's own search shows the
 * results and its own player shows the controls; this WebView only produces the sound. It is attached to the
 * window, under the app's content (Chromium throttles pages that are not attached). The position and the end
 * of the song are read once a second: from the media element on YouTube, from the player's controls on Spotify.
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

  /** Also used by the sign-in page, so the Spotify session is made for the same browser that plays. */
  const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

  /**
   * Spotify keeps its media element out of the page, so the state is read from its own controls: the
   * play/pause button of the bottom bar (its label says "Pause" while playing), the position and length
   * texts beside the progress bar, and the now-playing widget, which links to the track that plays.
   * The script defines window.__tkSp once per page; the other Spotify scripts call it.
   */
  private const val SPOTIFY_LIB = """
    if(!window.__tkSp){window.__tkSp={
      bar:function(){return document.querySelector('[data-testid="control-button-playpause"]')},
      paused:function(b){var l=(b&&b.getAttribute('aria-label'))||'';return !/paus/i.test(l)},
      secs:function(s){if(!s)return 0;var p=s.trim().split(':'),v=0;for(var i=0;i<p.length;i++)v=v*60+(parseInt(p[i],10)||0);return v},
      mine:function(id){var w=document.querySelector('[data-testid="now-playing-widget"]');return !!w&&w.innerHTML.indexOf(id)>=0},
      big:function(){return document.querySelector('[data-testid="action-bar-row"] [data-testid="play-button"]')||document.querySelector('main [data-testid="play-button"]')}
    }}
  """

  /** Starts the track of this page, only if it is not playing yet: a click on a playing button would pause it. */
  private fun spotifyPlayScript(id: String) = SPOTIFY_LIB + """
    (function(){var s=window.__tkSp,b=s.bar();
      if(s.mine('$id')){if(s.paused(b)&&b)b.click();return}
      var g=s.big();if(g&&s.paused(g))g.click()})()
  """

  private fun spotifyReadScript(id: String) = SPOTIFY_LIB + """
    (function(){var s=window.__tkSp,b=s.bar(),mine=s.mine('$id'),playing=!!b&&!s.paused(b);
      var pos=s.secs((document.querySelector('[data-testid="playback-position"]')||{}).textContent);
      var dur=s.secs((document.querySelector('[data-testid="playback-duration"]')||{}).textContent);
      if(mine&&playing)window.__tkSeen=1;
      var last=window.__tkLast||0;
      if(mine)window.__tkLast=pos;
      // Ended: Spotify moved on to another song, or ours jumped back from its last seconds, or stopped there.
      if(window.__tkSeen&&!window.__tkDone&&((!mine&&b)||(dur>0&&last>=dur-3&&pos<last-5)||(dur>0&&!playing&&pos>=dur-2))){
        window.__tkDone=1;window.__tkEnded=(window.__tkEnded||0)+1;
        if(!mine&&playing)b.click();
      }
      return JSON.stringify({p:mine&&playing,pos:mine?pos:0,dur:mine?dur:0,end:window.__tkEnded||0,has:!!b});
    })()
  """

  private const val SPOTIFY_PAUSE = SPOTIFY_LIB + """
    (function(){var s=window.__tkSp,b=s.bar();if(b&&!s.paused(b))b.click()})()
  """

  private const val SPOTIFY_RESUME = SPOTIFY_LIB + """
    (function(){var s=window.__tkSp,b=s.bar();if(b&&s.paused(b))b.click()})()
  """

  private const val MEDIA_PAUSE = "(function(){var e=document.querySelector('video,audio');if(e)e.pause()})()"
  private const val MEDIA_PLAY = "(function(){var e=document.querySelector('video,audio');if(e)e.play()})()"

  /** The script that reads the page's state (Spotify's controls, or the media element elsewhere). */
  private var readScript: String = ""
  private var pauseScript: String = MEDIA_PAUSE
  private var resumeScript: String = MEDIA_PLAY

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
        root.addView(existing, 0, playerLayoutParams())
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
      // Under the app's content and never meant to be read or focused (TalkBack, keyboard).
      importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      isFocusable = false
      isFocusableInTouchMode = false
      webChromeClient = PlayerChromeClient()
      webViewClient = PlayerClient()
    }
    defaultUserAgent = web.settings.userAgentString
    CookieManager.getInstance().apply {
      setAcceptCookie(true)
      setAcceptThirdPartyCookies(web, true)
    }
    root.addView(web, 0, playerLayoutParams())
    view = web
    appContext = activity.applicationContext
  }

  /**
   * A page laid out on a one-pixel viewport drops what does not fit: Spotify's player then has no
   * play/pause bar, which is what the app reads and presses. So the view has a small desktop size, and
   * sits first in the window, under the app's own (opaque) content, where it is never seen or touched.
   */
  private fun playerLayoutParams() = android.widget.FrameLayout.LayoutParams(PLAYER_WIDTH_PX, PLAYER_HEIGHT_PX)

  private const val PLAYER_WIDTH_PX = 1024
  private const val PLAYER_HEIGHT_PX = 720

  /** The activity is going away: take the player off its window so it does not keep it in memory. */
  fun release(activity: Activity) {
    val root = activity.window.decorView as ViewGroup
    view?.let { if (it.parent === root) root.removeView(it) }
  }

  /**
   * Spotify's songs are DRM-protected (Widevine). The page asks for "protected media" first, and the
   * default client refuses it, which leaves the song silent. Only that, and only for the two services.
   */
  private class PlayerChromeClient : WebChromeClient() {
    override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
      val host = request.origin?.host.orEmpty()
      val trusted = host == "open.spotify.com" || host.endsWith(".spotify.com") || host == "music.youtube.com" ||
        host.endsWith(".youtube.com")
      val drm = android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
      if (trusted && drm in request.resources) request.grant(arrayOf(drm)) else request.deny()
    }
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
  fun playSpotify(context: Context, trackId: String, onEnded: () -> Unit, onFailed: () -> Unit): Boolean {
    // Spotify ids are base62; anything else never reaches the scripts.
    val id = trackId.filter { it.isLetterOrDigit() }
    if (id.isEmpty()) return false
    readScript = spotifyReadScript(id)
    pauseScript = SPOTIFY_PAUSE
    resumeScript = SPOTIFY_RESUME
    return start(context, "https://open.spotify.com/track/$id", desktop = true, clickToPlay = spotifyPlayScript(id), onEnded, onFailed)
  }

  /**
   * Plays a YouTube Music song. [onEnded] runs when it finishes by itself, [onFailed] if the page dies.
   * Returns false when there is no player to play on (no activity has attached one yet).
   */
  fun playYouTube(context: Context, videoId: String, onEnded: () -> Unit, onFailed: () -> Unit): Boolean {
    readScript = READ_SCRIPT
    pauseScript = MEDIA_PAUSE
    resumeScript = MEDIA_PLAY
    return start(context, "https://music.youtube.com/watch?v=$videoId", desktop = false, clickToPlay = null, onEnded, onFailed)
  }

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
    web.evaluateJavascript(pauseScript, null)
    releaseAudio()
  }

  fun resume() {
    val web = view ?: return
    val context = appContext ?: return
    if (!_state.value.active) return
    wantPlaying = true
    autoplayTries = 0
    takeAudio(context)
    web.evaluateJavascript(resumeScript, null)
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
      it.evaluateJavascript(pauseScript, null)
      it.loadUrl("about:blank")
    }
    releaseAudio()
    _state.value = WebNowPlaying()
  }

  /**
   * True when the service's sign-in cookie is on this device (sp_dc for Spotify, SAPISID for Google).
   * Only its presence is read; the value never leaves the cookie store.
   */
  fun isSignedIn(spotify: Boolean): Boolean {
    val cookies = runCatching {
      CookieManager.getInstance().getCookie(if (spotify) "https://open.spotify.com" else "https://music.youtube.com")
    }.getOrNull().orEmpty()
    val name = if (spotify) "sp_dc=" else "SAPISID="
    return cookies.split(';').any { it.trim().startsWith(name) }
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
    web.evaluateJavascript(readScript.ifEmpty { READ_SCRIPT }) { raw ->
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
          // Spotify only starts from its own play button (the script clicks it only while it shows
          // "play", so a retry never pauses a song that just started); YouTube accepts play().
          web.evaluateJavascript(click ?: MEDIA_PLAY, null)
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
