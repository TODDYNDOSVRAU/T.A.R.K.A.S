package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val LOGIN_URL =
  "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

/**
 * One-time sign-in to the account that the hidden YouTube Music player uses (a Premium account plays
 * without ads and with the screen off). The page is the service's own; the app never sees the password,
 * only the browser cookies that the service sets, which stay on the device.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginDialog(spotify: Boolean = false, onClose: () -> Unit) {
  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(if (spotify) "Entrar no Spotify" else "Entrar no YouTube Music", fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
          TextButton(onClick = onClose, modifier = Modifier.testTag("web_login_done")) { Text("Concluir") }
        }
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
            web.webViewClient = WebViewClient()
            web.loadUrl(if (spotify) SPOTIFY_LOGIN_URL else LOGIN_URL)
            web
          },
          onRelease = { web ->
            CookieManager.getInstance().flush()
            web.destroy()
          }
        )
      }
    }
  }
}
