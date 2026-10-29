package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.Hairline
import com.example.ui.components.TextAction
import com.example.ui.theme.AppText
import com.example.ui.theme.Size
import com.example.ui.theme.Space

private const val LOGIN_URL =
  "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

/**
 * One-time sign-in to the account that the hidden YouTube Music (or Spotify) player uses. The page is
 * the service's own; the app never sees the password, only the browser cookies the service sets,
 * which stay on the device.
 *
 * Full screen, with a plain top bar (Fechar, title). System back walks back through the web pages
 * first and closes the sign-in only from the first page. The page lifts above the keyboard so the
 * fields being typed in stay visible. Rotation recreates the activity (it does not handle config
 * changes), so the WebView's history is kept in saved state and restored; what was typed in a field
 * is lost, while a finished sign-in is not (it lives in the cookies).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginDialog(spotify: Boolean = false, onClose: () -> Unit) {
  var canGoBack by remember { mutableStateOf(false) }
  var webView by remember { mutableStateOf<WebView?>(null) }
  // A Bundle is Parcelable, so it survives rotation and process death with the dialog's saved state.
  val webState = rememberSaveable { Bundle() }

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    // Registered after the dialog's own back callback, so it runs first while the page can go back.
    BackHandler(enabled = canGoBack) { webView?.goBack() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .imePadding()
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.touch)
            .padding(horizontal = Space.s)
        ) {
          TextAction("Fechar", onClick = onClose, modifier = Modifier.align(Alignment.CenterStart).testTag("web_login_done"))
          Text(
            if (spotify) "Entrar no Spotify" else "Entrar no YouTube Music",
            style = AppText.Headline,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
              .align(Alignment.Center)
              .padding(horizontal = 88.dp)
              .semantics { heading() }
          )
        }
        Hairline()
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { context ->
            val web = WebView(context)
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            CookieManager.getInstance().apply {
              setAcceptCookie(true)
              setAcceptThirdPartyCookies(web, true)
            }
            web.webChromeClient = WebChromeClient()
            web.webViewClient = object : WebViewClient() {
              override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
                view.saveState(webState)
              }
            }
            if (webState.isEmpty || web.restoreState(webState) == null) {
              web.loadUrl(if (spotify) SPOTIFY_LOGIN_URL else LOGIN_URL)
            }
            canGoBack = web.canGoBack()
            webView = web
            web
          },
          onRelease = { web ->
            CookieManager.getInstance().flush()
            webView = null
            web.destroy()
          }
        )
      }
    }
  }
}
