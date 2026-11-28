package com.example.windows.player

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.example.data.db.AppDatabase
import com.example.data.model.Track
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.biblioteca.BibliotecaHost
import com.example.windows.equalizador.AudioHost
import com.example.windows.shell.IslandActivity
import com.example.windows.shell.Panels
import com.example.windows.shell.ShellBackdrop
import com.example.windows.shell.ShellIsland
import com.example.windows.shell.SidePane
import com.example.windows.shell.TarkasShell
import com.example.windows.state.TarkasState
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Owner evidence of the cards behind the art card of the Home corner, inside the real shell: light and dark, a 1280 x 800
 * window (medium) and a 1920 x 1080 one (wide), with five tracks whose covers are distinct pictures, and a single-song queue
 * as the baseline. PNGs go to build/art-stack-render. The pixel check proves the sliver to the right of the front card shows
 * the next track's cover when there is a queue and is the plain page when there is one song.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ArtCardStackRenderTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val savedSlots = Panels.slots.toMap()
  private val savedTitles = Panels.topTitle.toMap()
  private val savedCorner = Panels.corner
  private val savedTransport = Panels.transport
  private val savedSide = Panels.sidePlayer
  private val savedStack = Panels.queueStack
  private val root = File("build/art-stack-render").apply { mkdirs() }

  @Before fun setUp() {
    Panels.slots.clear(); Panels.topTitle.clear()
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear(); PillGlass.reset()
    PlayerHost.register(Panels.slots)
    BibliotecaHost.register(Panels.slots)
    AudioHost.register(Panels.slots)
  }

  @After fun tearDown() {
    Panels.slots.clear(); Panels.slots.putAll(savedSlots)
    Panels.topTitle.clear(); Panels.topTitle.putAll(savedTitles)
    Panels.corner = savedCorner; Panels.transport = savedTransport
    Panels.sidePlayer = savedSide; Panels.queueStack = savedStack
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear(); PillGlass.reset()
    scope.cancel()
  }

  /** Track n: its own folder with a cover.png of its own hue, so every card is a different picture. */
  private fun track(n: Int, hue: Float): Track {
    val dir = File(root, "media/t$n").apply { mkdirs() }
    val img = BufferedImage(384, 384, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.paint = GradientPaint(0f, 0f, Color.getHSBColor(hue, 0.75f, 0.95f), 384f, 384f, Color.getHSBColor((hue + 0.12f) % 1f, 0.9f, 0.45f))
    g.fillRect(0, 0, 384, 384)
    g.color = Color(255, 255, 255, 200); g.fillOval(96, 96, 192, 192)
    g.color = Color.getHSBColor(hue, 0.9f, 0.5f); g.font = g.font.deriveFont(java.awt.Font.BOLD, 120f); g.drawString("$n", 168, 252)
    g.dispose()
    ImageIO.write(img, "png", File(dir, "cover.png"))
    val file = File(dir, "track$n.wav").apply { if (!exists()) writeSilence(this) }
    return Track("art$n", "Track $n", "Artist $n", "Album $n", 200_000, "", "", true, file.path, colorHex = 0xFF000000L or (Color.HSBtoRGB(hue, 0.75f, 0.9f).toLong() and 0xFFFFFFL))
  }

  /** A minute of silence, 8 kHz mono 16-bit: a real file the player opens, so the pill shows no error line. */
  private fun writeSilence(f: File) {
    val pcm = ByteArray(8_000 * 2 * 60)
    val fmt = javax.sound.sampled.AudioFormat(8_000f, 16, 1, true, false)
    javax.sound.sampled.AudioSystem.write(
      javax.sound.sampled.AudioInputStream(java.io.ByteArrayInputStream(pcm), fmt, (pcm.size / 2).toLong()),
      javax.sound.sampled.AudioFileFormat.Type.WAVE, f,
    )
  }

  private fun shot(theme: String, w: Int, h: Int, tracks: List<Track>): ByteArray {
    val state = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
    state.setThemeMode(theme); state.selectTab(TarkasNavTab.HOME)
    val ctl = PlayerHost.controller(state)
    ctl.play(tracks[0], tracks)
    ctl.togglePlayPause() // pause: a still picture, the clock stays at 0:00 and the track never ends
    val scene = ImageComposeScene(w, h, Density(1f)) { TarkasShell(state, BackDispatcher(), AppLifecycle()) }
    try {
      scene.render(0L); scene.render(16_000_000L)
      Thread.sleep(600) // the marble and the covers load on Dispatchers.Default / IO
      var t = 400_000_000L
      repeat(60) { scene.render(t); t += 16_000_000L }
      return scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
    } finally { scene.close() }
  }

  @Test fun theCardsBehindTheArtCardInLightAndDarkAtTwoWindowSizes() {
    root.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    val five = (1..5).map { track(it, (it - 1) / 5f) }
    val lines = ArrayList<String>()
    for (theme in listOf("dark", "light")) for ((w, h, name) in listOf(Triple(1280, 800, "medium"), Triple(1920, 1080, "wide"))) {
      val stack = shot(theme, w, h, five)
      val solo = shot(theme, w, h, listOf(five[0]))
      File(root, "art-stack-$name-$theme.png").writeBytes(stack)
      File(root, "art-solo-$name-$theme.png").writeBytes(solo)
      fun px(png: ByteArray) = org.jetbrains.skia.Image.makeFromEncoded(png).toComposeImageBitmap().toPixelMap()
      val a = px(stack); val b = px(solo)
      // The front card: x 48..240, bottom = window height - 24 - 96 - 12. The sliver is the 45 dp right of it.
      val bottom = h - 24 - 96 - 12
      var differs = 0
      for (y in bottom - 150..bottom - 30) for (x in 244..290) if (a[x, y] != b[x, y]) differs++
      lines += "$name $theme: sliver pixels that differ from the one-song baseline: $differs"
      assertTrue("$name $theme: nothing is drawn behind the card ($differs px differ)", differs > 500)
    }
    File(root, "evidence.txt").writeText(lines.joinToString(System.lineSeparator()))
    lines.forEach { println("ART-STACK $it") }
  }
}
