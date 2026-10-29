package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/** Same value as @color/tarkas_ink and the video's background, so nothing shows a seam. */
private val BootInk = Color(0xFF07090B)

/** The video's own size (boot-animation/, rendered with Remotion). */
private const val VIDEO_W = 1080f
private const val VIDEO_H = 2400f

/** Longest the opening may hold the app back, whatever happens to the video. */
private const val MAX_BOOT_MS = 4000L

/**
 * The opening: a 2.5 s title rendered with Remotion (boot-animation/) and played full screen over the
 * window's own ink colour, then dissolved into the app. A tap skips it. With reduced motion, or if the
 * video cannot play, the app appears after a short fade instead.
 */
@Composable
fun BootScreen(onFinished: () -> Unit) {
  val reduced = rememberReducedMotion()
  val finish by rememberUpdatedState(onFinished)
  var ended by remember { mutableStateOf(reduced) }
  val exit = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    // A watchdog: a stalled decoder must never leave the user on the opening.
    delay(MAX_BOOT_MS)
    ended = true
  }
  LaunchedEffect(ended) {
    if (!ended) return@LaunchedEffect
    if (reduced) delay(150)
    exit.animateTo(1f, tween(if (reduced) 160 else 420, easing = Motion.Settle))
    finish()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer {
        alpha = 1f - exit.value
        val s = 1f + 0.06f * exit.value
        scaleX = s
        scaleY = s
      }
      .background(BootInk)
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ended = true }
      .semantics { contentDescription = "T.A.R.K.A.S." }
  ) {
    if (!reduced) BootVideo(onEnded = { ended = true })
  }
}

/** Plays res/raw/boot.mp4 on a TextureView, cropped to fill the screen, shown from its first frame. */
@Composable
private fun BootVideo(onEnded: () -> Unit) {
  val context = LocalContext.current
  val ended by rememberUpdatedState(onEnded)
  val player = remember { MediaPlayer() }
  DisposableEffect(Unit) {
    onDispose { runCatching { player.release() } }
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      TextureView(ctx).apply {
        // Hidden until the decoder draws its first frame, so a black texture never flashes.
        alpha = 0f
        isOpaque = false
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
          override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            fitCrop(this@apply, width, height)
            val started = runCatching {
              context.resources.openRawResourceFd(R.raw.boot).use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
              }
              player.setSurface(Surface(texture))
              player.setVolume(0f, 0f)
              player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) alpha = 1f
                false
              }
              // The last frame stays on screen while the app dissolves in.
              player.setOnCompletionListener { ended() }
              player.setOnErrorListener { _, _, _ -> ended(); true }
              player.setOnPreparedListener { it.start() }
              player.prepareAsync()
            }
            if (started.isFailure) ended()
          }

          override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) =
            fitCrop(this@apply, width, height)

          override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
          override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
      }
    }
  )
}

/** Scales the video to cover the view (centre crop), whatever the screen's aspect ratio. */
private fun fitCrop(view: TextureView, width: Int, height: Int) {
  if (width == 0 || height == 0) return
  val scale = maxOf(width / VIDEO_W, height / VIDEO_H)
  val sx = VIDEO_W * scale / width
  val sy = VIDEO_H * scale / height
  view.setTransform(Matrix().apply { setScale(sx, sy, width / 2f, height / 2f) })
}
